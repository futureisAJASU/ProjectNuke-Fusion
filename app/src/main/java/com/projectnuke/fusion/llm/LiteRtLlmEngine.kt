package com.projectnuke.fusion.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Capabilities
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.SamplerConfig
import com.projectnuke.fusion.model.AcceleratorMode
import com.projectnuke.fusion.model.ChatMessage
import com.projectnuke.fusion.model.ConversationOptions
import com.projectnuke.fusion.model.RequestedEngineProfile
import com.projectnuke.fusion.modelzoo.FusionPromptAdapters
import com.projectnuke.fusion.util.AttachmentStorageManager
import com.projectnuke.fusion.util.ManagedModelPathPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

@OptIn(ExperimentalApi::class)
class LiteRtLlmEngine(
    private val context: Context,
    private val engineFactory: (EngineConfig) -> Engine = { config ->
        Engine(config).also { it.initialize() }
    },
    private val flagSetter: (Boolean) -> Boolean = { enabled ->
        runCatching { ExperimentalFlags.enableSpeculativeDecoding = enabled }.isSuccess
    },
    private val flagReader: () -> Boolean? = {
        ExperimentalFlags.enableSpeculativeDecoding
    },
    private val failureMemoryStorage: MtpFailureMemoryStorage = NoopMtpFailureMemoryStorage,
    private val mtpCapabilityProbe: (modelPath: String) -> Boolean? = { modelPath ->
        runCatching { Capabilities(modelPath).use { it.hasSpeculativeDecodingSupport() } }.getOrNull()
    }
) : LlmEngine {

    private var loadedState: LoadedRuntimeState? = null
    private val mtpFailureMemory = MtpFailureMemory(failureMemoryStorage)
    @Volatile
    var lastMtpStatus: MtpRuntimeStatus = MtpRuntimeStatus.OFF
        private set

    val lastRuntimeSelection: EngineSelectionRuntime?
        get() = loadedState?.runtimeSelection

    override suspend fun generate(
        messages: List<ChatMessage>,
        profile: RequestedEngineProfile,
        options: ConversationOptions
    ): GenerationOutcome {
        return generateStreaming(
            messages = messages,
            profile = profile,
            options = options,
            onToken = {}
        )
    }

    override suspend fun generateStreaming(
        messages: List<ChatMessage>,
        profile: RequestedEngineProfile,
        options: ConversationOptions,
        onToken: (String) -> Unit
    ): GenerationOutcome {
        return withContext(Dispatchers.IO) {
            val modelFile = ManagedModelPathPolicy.resolveRunnableModel(context, profile.modelPath)
            if (modelFile == null) {
                Log.e("FusionEngine", "Selected model path is missing or unmanaged: ${File(profile.modelPath).name}")
                return@withContext GenerationOutcome.Failure(
                    kind = FailureKind.MODEL_NOT_FOUND,
                    message = "선택한 모델 파일을 찾을 수 없습니다. 모델을 다시 선택해 주세요."
                )
            }

            val resolvedProfile = profile.copy(modelPath = modelFile.absolutePath)
            val engine = try {
                getOrCreateEngine(resolvedProfile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                lastMtpStatus = MtpRuntimeStatus.OFF
                return@withContext GenerationOutcome.Failure(
                    kind = FailureKind.MODEL_LOAD_FAILED,
                    message = "메모리가 부족하여 모델을 불러올 수 없습니다."
                )
            } catch (e: VirtualMachineError) {
                return@withContext GenerationOutcome.Failure(
                    kind = FailureKind.MODEL_LOAD_FAILED,
                    message = "런타임 오류로 모델을 불러올 수 없습니다."
                )
            } catch (e: LinkageError) {
                return@withContext GenerationOutcome.Failure(
                    kind = FailureKind.MODEL_LOAD_FAILED,
                    message = "런타임 오류로 모델을 불러올 수 없습니다."
                )
            } catch (e: Exception) {
                Log.e("FusionEngine", "LiteRT-LM engine init failed", e)
                return@withContext GenerationOutcome.Failure(
                    kind = FailureKind.MODEL_LOAD_FAILED,
                    message = "모델을 불러올 수 없습니다. 모델 설정을 확인한 뒤 다시 시도해 주세요."
                )
            }
            logGenerationSettings(
                profile = resolvedProfile,
                options = options
            )

            val promptAdapter = FusionPromptAdapters.inferFromMessages(messages)
            val adaptedMessages = promptAdapter.buildMessages(messages)
            val systemText = buildSystemInstruction(adaptedMessages)
            val promptText = buildPrompt(adaptedMessages)

            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(systemText),
                samplerConfig = buildSamplerConfig(options)
            )

            val output = StringBuilder()
            var outputTruncated = false
            var nativeStats: GenerationBenchmarkStats? = null
            try {
                engine.createConversation(conversationConfig).use { conversation ->
                    try {
                        conversation
                            .sendMessageAsync(promptText)
                            .collect { chunk ->
                                val token = chunk.toString()
                                output.append(token)
                                onToken(token)
                                if (isAppOutputLimitReached(options, output)) {
                                    outputTruncated = true
                                    runCatching { conversation.cancelProcess() }
                                    throw AppOutputLimitReachedException
                                }
                            }
                    } catch (e: AppOutputLimitReachedException) {
                        Log.i("FusionLiteRT", "App-level output limit reached: ${options.maxOutputToken} estimated tokens")
                    }
                    nativeStats = conversationBenchmarkStats(conversation)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FusionEngine", "LiteRT-LM generation failed", e)
                return@withContext classifyGenerationException(e, isMultimodal = false)
            }

            val sanitized = promptAdapter.sanitizeOutput(output.toString())
            if (sanitized.isBlank()) {
                GenerationOutcome.Empty
            } else {
                GenerationOutcome.Success(
                    text = sanitized,
                    actualBackend = loadedState?.actualTextBackend,
                    truncated = outputTruncated,
                    stats = nativeStats
                )
            }
        }
    }

    override suspend fun generateMultimodalStreaming(
        messages: List<ChatMessage>,
        profile: RequestedEngineProfile,
        options: ConversationOptions,
        imagePaths: List<String>,
        onToken: (String) -> Unit
    ): GenerationOutcome {
        return withContext(Dispatchers.IO) {
            val modelFile = ManagedModelPathPolicy.resolveRunnableModel(context, profile.modelPath)
            if (modelFile == null) {
                Log.e("FusionEngine", "Selected multimodal model path is missing or unmanaged: ${File(profile.modelPath).name}")
                return@withContext GenerationOutcome.Failure(
                    kind = FailureKind.MODEL_NOT_FOUND,
                    message = "선택한 모델 파일을 찾을 수 없습니다. 모델을 다시 선택해 주세요."
                )
            }

            val managedImagePaths = imagePaths.mapNotNull { path ->
                AttachmentStorageManager.resolveManagedAttachment(context, path)?.absolutePath
            }
            if (managedImagePaths.size != imagePaths.size) {
                return@withContext GenerationOutcome.Failure(
                    kind = FailureKind.IMAGE_NOT_FOUND,
                    message = "이미지 입력 처리 실패: 이미지 파일을 찾을 수 없습니다."
                )
            }

            val resolvedProfile = profile.copy(modelPath = modelFile.absolutePath)
            val engine = try {
                getOrCreateEngine(resolvedProfile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                lastMtpStatus = MtpRuntimeStatus.OFF
                return@withContext GenerationOutcome.Failure(
                    kind = FailureKind.MODEL_LOAD_FAILED,
                    message = "메모리가 부족하여 모델을 불러올 수 없습니다."
                )
            } catch (e: VirtualMachineError) {
                return@withContext GenerationOutcome.Failure(
                    kind = FailureKind.MODEL_LOAD_FAILED,
                    message = "런타임 오류로 모델을 불러올 수 없습니다."
                )
            } catch (e: LinkageError) {
                return@withContext GenerationOutcome.Failure(
                    kind = FailureKind.MODEL_LOAD_FAILED,
                    message = "런타임 오류로 모델을 불러올 수 없습니다."
                )
            } catch (e: Exception) {
                if (isVisionBackendUnsupported(e)) {
                    return@withContext GenerationOutcome.Failure(
                        kind = FailureKind.MODEL_MULTIMODAL_UNSUPPORTED,
                        message = "이 모델은 이미지 입력을 지원하지 않습니다."
                    )
                }
                Log.e("FusionEngine", "LiteRT-LM multimodal engine init failed", e)
                return@withContext GenerationOutcome.Failure(
                    kind = FailureKind.MODEL_LOAD_FAILED,
                    message = "이미지 입력 처리 실패: 모델 설정을 확인한 뒤 다시 시도해 주세요."
                )
            }
            logGenerationSettings(
                profile = resolvedProfile,
                options = options
            )

            val promptAdapter = FusionPromptAdapters.inferFromMessages(messages)
            val adaptedMessages = promptAdapter.buildMessages(messages)
            val systemText = buildSystemInstruction(adaptedMessages)
            val promptText = buildPrompt(adaptedMessages)

            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(systemText),
                samplerConfig = buildSamplerConfig(options)
            )

            val contentParts = buildList<Content> {
                managedImagePaths.forEach { imagePath ->
                    add(Content.ImageFile(imagePath))
                }
                add(Content.Text(promptText))
            }

            val output = StringBuilder()
            var outputTruncated = false
            var nativeStats: GenerationBenchmarkStats? = null
            try {
                engine.createConversation(conversationConfig).use { conversation ->
                    try {
                        conversation
                            .sendMessageAsync(Contents.of(contentParts))
                            .collect { chunk ->
                                val token = chunk.toString()
                                output.append(token)
                                onToken(token)
                                if (isAppOutputLimitReached(options, output)) {
                                    outputTruncated = true
                                    runCatching { conversation.cancelProcess() }
                                    throw AppOutputLimitReachedException
                                }
                            }
                    } catch (e: AppOutputLimitReachedException) {
                        Log.i("FusionLiteRT", "App-level output limit reached: ${options.maxOutputToken} estimated tokens")
                    }
                    nativeStats = conversationBenchmarkStats(conversation)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FusionEngine", "LiteRT-LM multimodal generation failed", e)
                return@withContext classifyGenerationException(e, isMultimodal = true)
            }

            val sanitized = promptAdapter.sanitizeOutput(output.toString())
            if (sanitized.isBlank()) {
                GenerationOutcome.Empty
            } else {
                GenerationOutcome.Success(
                    text = sanitized,
                    actualBackend = loadedState?.actualTextBackend,
                    truncated = outputTruncated,
                    stats = nativeStats
                )
            }
        }
    }

    private fun isIrrecoverableLoadException(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return message.contains("Failed to create engine", ignoreCase = true) ||
            message.contains("litert_compiled_model", ignoreCase = true) ||
            message.contains("INTERNAL", ignoreCase = true)
    }

    private fun isVisionBackendUnsupported(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return message.contains("vision", ignoreCase = true) ||
            message.contains("image", ignoreCase = true) ||
            message.contains("multimodal", ignoreCase = true)
    }

    private fun classifyGenerationException(
        error: Throwable,
        isMultimodal: Boolean
    ): GenerationOutcome.Failure {
        val kind = when {
            isMultimodal && isVisionBackendUnsupported(error) -> FailureKind.MODEL_MULTIMODAL_UNSUPPORTED
            isIrrecoverableLoadException(error) -> FailureKind.MODEL_LOAD_FAILED
            error is IOException -> FailureKind.GENERATION_IO
            else -> FailureKind.GENERATION_INTERRUPTED
        }
        val message = when (kind) {
            FailureKind.MODEL_MULTIMODAL_UNSUPPORTED -> "이 모델은 이미지 입력을 지원하지 않습니다."
            FailureKind.MODEL_LOAD_FAILED -> "모델을 불러올 수 없습니다. 모델 설정을 확인한 뒤 다시 시도해 주세요."
            FailureKind.GENERATION_IO -> "모델 응답 중 입출력 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
            FailureKind.GENERATION_INTERRUPTED -> "모델 응답을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요."
            else -> "모델 응답을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요."
        }
        return GenerationOutcome.Failure(kind = kind, message = message)
    }

    private fun getOrCreateEngine(profile: RequestedEngineProfile): Engine {
        val mtpRequested = profile.mtpRequested
        val fingerprint = ModelFingerprint.of(profile.modelPath)
        val mtpSupported = fingerprint.mtpSupported
        val maxNumTokens = profile.kvCacheCapacityTokens.coerceAtLeast(1)

        // Failure memory is consulted per candidate backend: an MTP failure on
        // GPU must never poison an explicit CPU request, and AUTO failures are
        // remembered under the exact backend that failed, not the accelerator.
        var mtpSkippedByMemory = false
        val ladder = buildEngineCandidateLadder(
            accelerator = profile.accelerator,
            mtpRequested = mtpRequested,
            mtpSupported = mtpSupported
        ).filter { candidate ->
            if (!candidate.mtpEnabled) return@filter true
            val skipReason = mtpFailureMemory.shouldSkipMtp(
                modelPath = fingerprint.canonicalPath,
                backendName = candidate.backend,
                mtpEnabled = true,
                validationVersion = fingerprint.validationVersion,
                kvCacheCapacityTokens = profile.kvCacheCapacityTokens,
                enableVisionBackend = profile.enableVisionBackend
            )
            if (skipReason != null) {
                mtpSkippedByMemory = true
                Log.i(
                    "FusionLiteRT",
                    "MTP skipped due to recent failure: $skipReason (backend=${candidate.backend})"
                )
                return@filter false
            }
            true
        }
        val preferredBackendName = ladder.first().backend
        val requestedKey = EngineRuntimeKey(
            fingerprint = fingerprint,
            accelerator = profile.accelerator,
            kvCacheCapacityTokens = profile.kvCacheCapacityTokens,
            enableVisionBackend = profile.enableVisionBackend,
            mtpEnabled = ladder.first().mtpEnabled
        )

        val currentState = loadedState
        if (currentState != null && currentState.key == requestedKey) {
            lastMtpStatus = currentState.mtpStatus
            Log.i("FusionLiteRT", "MTP requested: $mtpRequested (cached engine reused)")
            Log.i("FusionLiteRT", "MTP status: $lastMtpStatus")
            Log.i("FusionLiteRT", "Backend: $preferredBackendName")
            Log.i("FusionLiteRT", "Vision backend requested: ${profile.enableVisionBackend}")
            Log.i("FusionLiteRT", "Model path: ${File(profile.modelPath).name}")
            return currentState.engine
        }

        unload()

        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)

        Log.i("FusionLiteRT", "MTP requested: $mtpRequested")
        Log.i("FusionLiteRT", "Backend: $preferredBackendName")
        Log.i("FusionLiteRT", "Vision backend requested: ${profile.enableVisionBackend}")
        Log.i("FusionLiteRT", "Model path: ${File(profile.modelPath).name}")
        if (mtpRequested && !mtpSupported) {
            lastMtpStatus = MtpRuntimeStatus.UNSUPPORTED
            Log.i("FusionLiteRT", "MTP unsupported model/runtime")
        } else if (mtpRequested && mtpSupported) {
            lastMtpStatus = MtpRuntimeStatus.REQUESTED
            Log.i("FusionLiteRT", "MTP requested, model supports it")
        } else {
            lastMtpStatus = MtpRuntimeStatus.OFF
        }

        var newEngine: Engine? = null
        var selectedMtpEnabled = false
        var mtpFlagAppliedForMtp = false
        var mtpAttempted = false
        var mtpCapabilityResult: Boolean? = null
        var lastMtpAttemptBackend: String? = null
        var failure: Throwable? = null
        val selection = selectFirstWorkingEngine(
            ladder = ladder,
            enableVisionBackend = profile.enableVisionBackend,
            configureFlag = { settleSpeculativeDecodingFlag(it) },
            tryCreate = { backendName, mtpEnabled, visionBackendIsCpu ->
                if (mtpEnabled) {
                    mtpAttempted = true
                    when (mtpCapabilityProbe(profile.modelPath)) {
                        true -> mtpCapabilityResult = true
                        false -> {
                            // Official native capability check says the runtime
                            // has no speculative-decoding support: treat the
                            // candidate as failed so the ladder falls back and
                            // the failure is remembered per backend. A positive
                            // capability result is NOT runtime-activity evidence;
                            // only the MTP flag being applied during a successful
                            // Engine init justifies INITIALIZED_WITH_MTP_REQUEST.
                            mtpCapabilityResult = false
                            lastMtpAttemptBackend = backendName
                            return@selectFirstWorkingEngine Result.failure(
                                IllegalStateException("MTP capability probe: no speculative decoding support")
                            )
                        }
                        null -> Unit // capability unavailable; proceed optimistically
                    }
                }
                val backend = if (backendName == "CPU") Backend.CPU() else Backend.GPU()
                val result = createEngine(
                    modelPath = profile.modelPath,
                    backend = backend,
                    visionBackend = if (profile.enableVisionBackend) {
                        if (visionBackendIsCpu) Backend.CPU() else backend
                    } else {
                        null
                    },
                    maxNumTokens = maxNumTokens
                )
                if (mtpEnabled && result.isFailure) lastMtpAttemptBackend = backendName
                result
            }
        )
        val selectionResult = selection.first
        if (selectionResult != null) {
            newEngine = selectionResult.engine
            selectedMtpEnabled = selectionResult.selectedMtpEnabled
            mtpFlagAppliedForMtp = selectionResult.mtpFlagAppliedForMtp
            Log.i(
                "FusionLiteRT",
                "Engine initialized with ${selectionResult.backendName}" +
                    (if (selectedMtpEnabled) " + MTP" else " without MTP")
            )
        } else {
            failure = selection.second
        }

        val resolvedEngine = newEngine ?: run {
            // Best-effort reset; if this also fails the flag state is unknown
            // and the next engine selection will re-settle before any init.
            settleSpeculativeDecodingFlag(false)
            lastMtpStatus = MtpRuntimeStatus.FAILED
            throw (failure ?: IllegalStateException("LiteRT engine candidates exhausted"))
        }

        lastMtpStatus = resolveMtpRuntimeStatus(
            mtpRequested = mtpRequested,
            mtpSupported = mtpSupported,
            selectedMtpEnabled = selectedMtpEnabled,
            mtpFlagAppliedForMtp = mtpFlagAppliedForMtp,
            mtpCapabilityResult = mtpCapabilityResult,
            mtpSkippedByMemory = mtpSkippedByMemory,
            mtpAttempted = mtpAttempted
        )

        val fallbackReason = resolveMtpFallbackReason(
            mtpRequested = mtpRequested,
            mtpSupported = mtpSupported,
            selectedMtpEnabled = selectedMtpEnabled,
            mtpFlagAppliedForMtp = mtpFlagAppliedForMtp,
            mtpCapabilityResult = mtpCapabilityResult,
            mtpSkippedByMemory = mtpSkippedByMemory,
            mtpAttempted = mtpAttempted
        )
        val initializedWithMtp = mtpRequested && mtpSupported && selectedMtpEnabled && mtpFlagAppliedForMtp

        // Record the failure under the exact candidate backend that failed so
        // the next load skips only that (backend, MTP) combination.
        if (mtpRequested && mtpSupported && !selectedMtpEnabled && lastMtpAttemptBackend != null) {
            mtpFailureMemory.recordFailure(
                modelPath = fingerprint.canonicalPath,
                backendName = lastMtpAttemptBackend!!,
                mtpEnabled = true,
                validationVersion = fingerprint.validationVersion,
                kvCacheCapacityTokens = profile.kvCacheCapacityTokens,
                enableVisionBackend = profile.enableVisionBackend,
                fallbackReason = fallbackReason ?: "MTP initialization failed"
            )
        }

        val actualTextBackend = selectionResult?.backendName ?: preferredBackendName
        val runtimeSelection = EngineSelectionRuntime(
            requestedAccelerator = profile.accelerator.name,
            actualTextBackend = actualTextBackend,
            actualVisionBackend = selectionResult?.visionBackend,
            requestedMtp = mtpRequested,
            initializedWithMtp = initializedWithMtp,
            fallbackReason = fallbackReason
        )

        // The stored key reflects the engine actually loaded (the selected MTP
        // state), so a later fallback is reused until the memory skip kicks in.
        loadedState = LoadedRuntimeState(
            engine = resolvedEngine,
            key = requestedKey.copy(mtpEnabled = selectedMtpEnabled),
            mtpStatus = lastMtpStatus,
            runtimeSelection = runtimeSelection,
            actualTextBackend = actualTextBackend,
            actualVisionBackend = selectionResult?.visionBackend
        )

        return resolvedEngine
    }

    private fun createEngine(
        modelPath: String,
        backend: Backend,
        visionBackend: Backend?,
        maxNumTokens: Int
    ): Result<Engine> {
        return runCatching {
            val config = EngineConfig(
                modelPath = modelPath,
                backend = backend,
                visionBackend = visionBackend,
                maxNumTokens = maxNumTokens,
                maxNumImages = if (visionBackend != null) 8 else null,
                cacheDir = context.cacheDir.absolutePath
            )

            engineFactory(config)
        }
    }

    /**
     * Distills the vendor BenchmarkInfo of the last conversation run. Returns
     * null when the runtime reports none (e.g., no message was sent yet).
     */
    private fun conversationBenchmarkStats(conversation: Conversation): GenerationBenchmarkStats? {
        return runCatching {
            val info = conversation.getBenchmarkInfo()
            GenerationBenchmarkStats(
                initTimeSeconds = info.initTimeInSecond,
                timeToFirstTokenSeconds = info.timeToFirstTokenInSecond,
                prefillTokenCount = info.lastPrefillTokenCount,
                decodeTokenCount = info.lastDecodeTokenCount,
                prefillTokensPerSecond = info.lastPrefillTokensPerSecond,
                decodeTokensPerSecond = info.lastDecodeTokensPerSecond
            )
        }.getOrNull()
    }

    private fun buildSamplerConfig(options: ConversationOptions): SamplerConfig {        val topK = options.topK.coerceAtLeast(1)
        val topP = options.topP.coerceIn(0f, 1f).toDouble()
        val temperature = options.temperature.coerceAtLeast(0f).toDouble()
        return if (options.seed != null) {
            SamplerConfig(topK, topP, temperature, options.seed)
        } else {
            SamplerConfig(topK, topP, temperature)
        }
    }

    private fun logGenerationSettings(
        profile: RequestedEngineProfile,
        options: ConversationOptions
    ) {
        Log.i(
            "FusionLiteRT",
            buildString {
                appendLine("Generation settings before request")
                appendLine("modelPath=${File(profile.modelPath).name}")
                appendLine("accelerator=${profile.accelerator.name} (runtime EngineConfig.backend)")
                appendLine("kvCacheCapacityTokens=${profile.kvCacheCapacityTokens} (runtime EngineConfig.maxNumTokens)")
                appendLine("topK=${options.topK} (runtime SamplerConfig.topK)")
                appendLine("topP=${options.topP} (runtime SamplerConfig.topP)")
                appendLine("temperature=${options.temperature} (runtime SamplerConfig.temperature)")
                appendLine("MTP requested=${profile.mtpRequested} (runtime ExperimentalFlags.enableSpeculativeDecoding)")
                appendLine("visionBackend=${profile.enableVisionBackend} (runtime EngineConfig.visionBackend when true)")
            }.trimEnd()
        )
    }

    override fun unload() {
        try {
            loadedState?.engine?.close()
        } catch (throwable: Throwable) {
            Log.w("FusionEngine", "Failed to close LiteRT engine", throwable)
        }

        loadedState = null
        lastMtpStatus = MtpRuntimeStatus.OFF
        // Reset the global flag on unload; the next selection re-settles it
        // before any engine init, so a failed reset cannot poison a later load.
        runCatching {
            settleSpeculativeDecodingFlag(false)
        }
    }

    /**
     * Clears MTP failure memory for the given model path.
     * Call this when the model file has changed (size/mtime) or on explicit manual retry.
     */
    fun clearMtpFailureMemory(modelPath: String) {
        val canonicalPath = runCatching { File(modelPath).canonicalPath }.getOrElse { modelPath }
        mtpFailureMemory.clearForModel(canonicalPath)
    }

    /**
     * Clears all MTP failure memory (e.g., on explicit user retry).
     */
    fun clearAllMtpFailureMemory() {
        mtpFailureMemory.clearAll()
    }

    /**
     * Settles the speculative-decoding flag to [desired] and verifies the
     * result before any Engine initialization. Returns true only when the
     * flag reached the desired state:
     * - the setter must report success, and
     * - if the runtime exposes the flag value, the read-back must match.
     * A failed settle means the flag state is unknown; the caller must skip
     * the candidate instead of initializing an engine on faith.
     */
    private fun settleSpeculativeDecodingFlag(desired: Boolean): Boolean {
        val applied = flagSetter(desired)
        if (!applied) {
            Log.w(
                "FusionLiteRT",
                "Failed to ${if (desired) "enable" else "disable"} MTP speculative decoding flag"
            )
            return false
        }
        val readBack = runCatching { flagReader() }.getOrNull()
        if (readBack != null && readBack != desired) {
            Log.w(
                "FusionLiteRT",
                "MTP speculative decoding flag read-back mismatch: desired=$desired actual=$readBack"
            )
            return false
        }
        return true
    }
}

enum class MtpRuntimeStatus {
    OFF,
    UNSUPPORTED,
    REQUESTED,
    INITIALIZED_WITH_MTP_REQUEST,
    RUNTIME_CONFIRMED_ACTIVE,
    FALLBACK_DISABLED,
    FAILED
}

public data class EngineSelectionRuntime(
    val requestedAccelerator: String,
    val actualTextBackend: String,
    val actualVisionBackend: String?,
    val requestedMtp: Boolean,
    val initializedWithMtp: Boolean,
    val fallbackReason: String?
)

/**
 * Resolves the authoritative MTP runtime status from the selection evidence.
 *
 * A pre-Engine [Capabilities.hasSpeculativeDecodingSupport] capability check
 * cannot prove that speculative decoding was active during generation; it only
 * filters out candidates that definitely lack support. A successful MTP Engine
 * initialization therefore reports [MtpRuntimeStatus.INITIALIZED_WITH_MTP_REQUEST],
 * never `RUNTIME_CONFIRMED_ACTIVE`. The latter remains in the enum as a reserved
 * value that becomes reachable only if LiteRT-LM later exposes positive
 * execution evidence (e.g. drafted/accepted-token counters); absent such an
 * API it is deliberately unreachable from this resolver.
 */
internal fun resolveMtpRuntimeStatus(
    mtpRequested: Boolean,
    mtpSupported: Boolean,
    selectedMtpEnabled: Boolean,
    mtpFlagAppliedForMtp: Boolean,
    mtpCapabilityResult: Boolean?,
    mtpSkippedByMemory: Boolean,
    mtpAttempted: Boolean
): MtpRuntimeStatus = when {
    !mtpRequested -> MtpRuntimeStatus.OFF
    !mtpSupported -> MtpRuntimeStatus.UNSUPPORTED
    selectedMtpEnabled && mtpFlagAppliedForMtp -> MtpRuntimeStatus.INITIALIZED_WITH_MTP_REQUEST
    mtpSkippedByMemory -> MtpRuntimeStatus.FALLBACK_DISABLED
    mtpAttempted -> MtpRuntimeStatus.FALLBACK_DISABLED
    else -> MtpRuntimeStatus.FAILED
}

internal fun resolveMtpFallbackReason(
    mtpRequested: Boolean,
    mtpSupported: Boolean,
    selectedMtpEnabled: Boolean,
    mtpFlagAppliedForMtp: Boolean,
    mtpCapabilityResult: Boolean?,
    mtpSkippedByMemory: Boolean,
    mtpAttempted: Boolean
): String? = when {
    mtpRequested && !mtpSupported -> "Model does not support MTP"
    mtpSkippedByMemory -> "MTP disabled due to previous failure"
    mtpAttempted && !selectedMtpEnabled && mtpCapabilityResult == false -> "MTP capability probe: no speculative decoding support"
    mtpAttempted && !selectedMtpEnabled -> "MTP initialization failed, fell back to non-MTP"
    mtpRequested && mtpSupported && !mtpFlagAppliedForMtp -> "MTP flag application failed"
    else -> null
}

/**
 * Builds the system instruction from the given messages. Deliberately takes no
 * settings: runtime settings (accelerator/MTP/KV capacity/sampling) must never
 * appear in prompt bytes, so MTP on/off produce identical prompts.
 */
internal fun buildSystemInstruction(messages: List<ChatMessage>): String {
    val systemMessages = messages
        .filter { it.role == "system" }
        .joinToString("\n\n") { it.content }

    return buildString {
        appendLine("당신은 기기 내에서 실행되는 AI 비서 Fusion입니다.")
        appendLine("한국어로 자연스럽게 답변하며 일관되게 존댓말을 사용합니다.")
        appendLine("모르는 내용은 모른다고 명확히 밝힙니다.")
        appendLine("추론이나 추정은 그 사실을 명확히 구분합니다.")

        if (systemMessages.isNotBlank()) {
            appendLine()
            appendLine(systemMessages)
        }
    }
}

/**
 * Builds the user turn prompt. Takes no settings for the same reason as
 * [buildSystemInstruction].
 */
internal fun buildPrompt(
    messages: List<ChatMessage>
): String {
    val nonSystemMessages = messages.filter { it.role != "system" }

    val recentMessages = nonSystemMessages.takeLast(12)

    return buildString {
        recentMessages.forEach { message ->
            when (message.role) {
                "user" -> {
                    appendLine("User:")
                    appendLine(message.content)
                    appendLine()
                }

                "assistant" -> {
                    appendLine("Assistant:")
                    appendLine(message.content)
                    appendLine()
                }
            }
        }

        appendLine("Assistant:")
    }
}

/**
 * Heuristic estimate of the token count in the accumulated streaming output
 * (~4 characters per token, matching the existing benchmark estimator). Used
 * only to enforce the app-level streaming output limit; the hard bound on
 * memory is the KV cache capacity, not this estimate.
 */
internal fun estimateStreamOutputTokens(text: String): Int = (text.length / 4.0).toInt().coerceAtLeast(0)

internal fun isAppOutputLimitReached(options: ConversationOptions, accumulatedOutput: StringBuilder): Boolean {
    val limit = options.maxOutputToken ?: return false
    return estimateStreamOutputTokens(accumulatedOutput.toString()) >= limit
}

private object AppOutputLimitReachedException : RuntimeException("app-level output limit reached")

internal data class EngineCandidate(
    val backend: String,
    val mtpEnabled: Boolean,
)

internal data class EngineSelectionResult<T>(
    val engine: T,
    val selectedMtpEnabled: Boolean,
    val mtpFlagAppliedForMtp: Boolean,
    val backendName: String,
    val visionBackend: String?,
)

internal fun <T> selectFirstWorkingEngine(
    ladder: List<EngineCandidate>,
    enableVisionBackend: Boolean,
    configureFlag: (Boolean) -> Boolean,
    tryCreate: (backendName: String, mtpEnabled: Boolean, visionBackendIsCpu: Boolean) -> Result<T>
): Pair<EngineSelectionResult<T>?, Throwable?> {
    var mtpFlagAppliedForMtp = false
    var lastFailure: Throwable? = null
    for (candidate in ladder) {
        // Mandatory settlement: never initialize an engine while the
        // speculative-decoding flag is in an unknown state. A failed settle
        // skips the candidate entirely instead of proceeding on faith.
        val flagApplied = configureFlag(candidate.mtpEnabled)
        if (!flagApplied) {
            lastFailure = IllegalStateException("Speculative decoding flag settle failed for mtp=${candidate.mtpEnabled}")
            continue
        }
        if (candidate.mtpEnabled) {
            mtpFlagAppliedForMtp = true
        }
        val attempt = tryCreate(candidate.backend, candidate.mtpEnabled, false)
        if (attempt.isSuccess) {
            return Pair(
                EngineSelectionResult(
                    engine = attempt.getOrThrow(),
                    selectedMtpEnabled = candidate.mtpEnabled,
                    mtpFlagAppliedForMtp = mtpFlagAppliedForMtp,
                    backendName = candidate.backend,
                    visionBackend = if (enableVisionBackend) candidate.backend else null
                ),
                null
            )
        }
        if (enableVisionBackend && candidate.backend == "GPU") {
            val visionRetry = tryCreate(candidate.backend, candidate.mtpEnabled, true)
            if (visionRetry.isSuccess) {
                return Pair(
                    EngineSelectionResult(
                        engine = visionRetry.getOrThrow(),
                        selectedMtpEnabled = candidate.mtpEnabled,
                        mtpFlagAppliedForMtp = mtpFlagAppliedForMtp,
                        backendName = candidate.backend,
                        visionBackend = "CPU"
                    ),
                    null
                )
            }
            lastFailure = attempt.exceptionOrNull() ?: visionRetry.exceptionOrNull()
        } else {
            lastFailure = attempt.exceptionOrNull()
        }
    }
    return Pair(null, lastFailure)
}

internal fun buildEngineCandidateLadder(
    accelerator: AcceleratorMode,
    mtpRequested: Boolean,
    mtpSupported: Boolean
): List<EngineCandidate> {
    val canUseMtp = mtpRequested && mtpSupported
    return when (accelerator) {
        AcceleratorMode.CPU -> buildList {
            // CPU+MTP requires an explicit user MTP request (the AUTO policy
            // never enables MTP on CPU), so this stays as an explicit
            // experimental path only.
            if (canUseMtp) add(EngineCandidate("CPU", mtpEnabled = true))
            add(EngineCandidate("CPU", mtpEnabled = false))
        }
        AcceleratorMode.GPU -> buildList {
            if (canUseMtp) add(EngineCandidate("GPU", mtpEnabled = true))
            add(EngineCandidate("GPU", mtpEnabled = false))
        }
        AcceleratorMode.AUTO -> buildList {
            // Beta AUTO ladder: GPU+MTP -> GPU -> CPU. CPU+MTP is never an
            // automatic fallback, and the ladder stays at most 3 candidates
            // to avoid sequential engine inits after MTP failure.
            if (canUseMtp) add(EngineCandidate("GPU", mtpEnabled = true))
            add(EngineCandidate("GPU", mtpEnabled = false))
            add(EngineCandidate("CPU", mtpEnabled = false))
        }
    }
}

/**
 * Persistence bridge for MTP failure memory. Entries survive engine unloads and
 * process restarts so the app does not repeatedly attempt a known-bad MTP
 * configuration across restarts.
 */
interface MtpFailureMemoryStorage {
    fun load(): Map<String, String>
    fun save(entries: Map<String, String>)
    fun clear()
}

object NoopMtpFailureMemoryStorage : MtpFailureMemoryStorage {
    override fun load(): Map<String, String> = emptyMap()
    override fun save(entries: Map<String, String>) {}
    override fun clear() {}
}

/**
 * Tracks MTP initialization failures to prevent repeated failed rebuilds.
 * Keyed by canonical model identity, actual backend, package capability
 * version, and engine settings. Entries are persisted through [storage].
 */
internal class MtpFailureMemory(
    private val storage: MtpFailureMemoryStorage = NoopMtpFailureMemoryStorage
) {
    private data class FailureKey(
        val modelPath: String,
        val backendName: String,
        val mtpEnabled: Boolean,
        val validationVersion: Int,
        val kvCacheCapacityTokens: Int,
        val enableVisionBackend: Boolean
    )

    private data class FailureRecord(
        val failedAt: Long,
        val fallbackReason: String
    )

    private val failures = mutableMapOf<FailureKey, FailureRecord>()
    private val lock = Any()

    companion object {
        private const val COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes
        private const val MAX_ENTRIES = 32
        private const val KEY_SEPARATOR = "\u001f"

        private fun serializeKey(key: FailureKey): String = listOf(
            key.modelPath,
            key.backendName,
            key.mtpEnabled.toString(),
            key.validationVersion.toString(),
            key.kvCacheCapacityTokens.toString(),
            key.enableVisionBackend.toString()
        ).joinToString(KEY_SEPARATOR)

        private fun deserializeKey(serialized: String): FailureKey? {
            val parts = serialized.split(KEY_SEPARATOR)
            if (parts.size != 6) return null
            return FailureKey(
                modelPath = parts[0],
                backendName = parts[1],
                mtpEnabled = parts[2].toBooleanStrictOrNull() ?: return null,
                validationVersion = parts[3].toIntOrNull() ?: return null,
                kvCacheCapacityTokens = parts[4].toIntOrNull() ?: return null,
                enableVisionBackend = parts[5].toBooleanStrictOrNull() ?: return null
            )
        }
    }

    init {
        storage.load().forEach { (serializedKey, serializedValue) ->
            val key = deserializeKey(serializedKey) ?: return@forEach
            val parts = serializedValue.split("|", limit = 2)
            val failedAt = parts.getOrNull(0)?.toLongOrNull() ?: return@forEach
            failures[key] = FailureRecord(failedAt = failedAt, fallbackReason = parts.getOrNull(1).orEmpty())
        }
    }

    /**
     * Checks if an MTP attempt should be skipped due to recent failure.
     * Returns the fallback reason if MTP should be skipped, null otherwise.
     */
    fun shouldSkipMtp(
        modelPath: String,
        backendName: String,
        mtpEnabled: Boolean,
        validationVersion: Int,
        kvCacheCapacityTokens: Int,
        enableVisionBackend: Boolean
    ): String? = synchronized(lock) {
        val key = FailureKey(
            modelPath = modelPath,
            backendName = backendName,
            mtpEnabled = mtpEnabled,
            validationVersion = validationVersion,
            kvCacheCapacityTokens = kvCacheCapacityTokens,
            enableVisionBackend = enableVisionBackend
        )
        val record = failures[key]
        if (record != null) {
            val elapsed = System.currentTimeMillis() - record.failedAt
            if (elapsed < COOLDOWN_MS) {
                return record.fallbackReason
            } else {
                // Cooldown expired, allow retry
                failures.remove(key)
                persist()
            }
        }
        return null
    }

    /**
     * Records an MTP failure for the given key.
     */
    fun recordFailure(
        modelPath: String,
        backendName: String,
        mtpEnabled: Boolean,
        validationVersion: Int,
        kvCacheCapacityTokens: Int,
        enableVisionBackend: Boolean,
        fallbackReason: String
    ) = synchronized(lock) {
        val key = FailureKey(
            modelPath = modelPath,
            backendName = backendName,
            mtpEnabled = mtpEnabled,
            validationVersion = validationVersion,
            kvCacheCapacityTokens = kvCacheCapacityTokens,
            enableVisionBackend = enableVisionBackend
        )
        failures[key] = FailureRecord(
            failedAt = System.currentTimeMillis(),
            fallbackReason = fallbackReason
        )
        persist()
        // Evict oldest if over limit
        if (failures.size > MAX_ENTRIES) {
            val oldestKey = failures.minByOrNull { it.value.failedAt }?.key
            oldestKey?.let { failures.remove(it) }
            persist()
        }
    }

    /**
     * Clears failure memory for a specific model (e.g., when model file changes).
     */
    fun clearForModel(modelPath: String) = synchronized(lock) {
        failures.keys.filter { it.modelPath == modelPath }.forEach { failures.remove(it) }
        persist()
    }

    /**
     * Clears all failure memory (e.g., on explicit manual retry).
     */
    fun clearAll() = synchronized(lock) {
        failures.clear()
        storage.clear()
    }

    internal fun persistedEntryCount(): Int = synchronized(lock) { storage.load().size }

    private fun persist() {
        runCatching {
            storage.save(
                failures.entries.associate { (key, record) ->
                    serializeKey(key) to "${record.failedAt}|${record.fallbackReason}"
                }
            )
        }
    }
}
