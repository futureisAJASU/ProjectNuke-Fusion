package com.projectnuke.fusion.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
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
import com.projectnuke.fusion.modelzoo.LiteRtLmPackageValidator
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
    }
) : LlmEngine {

    private var engine: Engine? = null
    private var loadedKey: String? = null
    private var actualBackend: String? = null
    private var actualVisionBackend: String? = null
    private var cachedMtpStatus: MtpRuntimeStatus = MtpRuntimeStatus.OFF
    private var cachedRuntimeSelection: EngineSelectionRuntime? = null
    private val mtpFailureMemory = MtpFailureMemory()
    @Volatile
    var lastMtpStatus: MtpRuntimeStatus = MtpRuntimeStatus.OFF
        private set

    val lastRuntimeSelection: EngineSelectionRuntime?
        get() = cachedRuntimeSelection

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
            val systemText = buildSystemInstruction(adaptedMessages, resolvedProfile, options)
            val promptText = buildPrompt(adaptedMessages)

            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(systemText),
                samplerConfig = buildSamplerConfig(options)
            )

            val output = StringBuilder()
            try {
                engine.createConversation(conversationConfig).use { conversation ->
                    conversation
                        .sendMessageAsync(promptText)
                        .collect { chunk ->
                            val token = chunk.toString()
                            output.append(token)
                            onToken(token)
                        }
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
                GenerationOutcome.Success(text = sanitized, actualBackend = actualBackend)
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
            val systemText = buildSystemInstruction(adaptedMessages, resolvedProfile, options)
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
            try {
                engine.createConversation(conversationConfig).use { conversation ->
                    conversation
                        .sendMessageAsync(Contents.of(contentParts))
                        .collect { chunk ->
                            val token = chunk.toString()
                            output.append(token)
                            onToken(token)
                        }
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
                GenerationOutcome.Success(text = sanitized, actualBackend = actualBackend)
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
        val mtpSupported = isSpeculativeDecodingSupportedModel(profile.modelPath)
        val maxNumTokens = profile.maxTokens.coerceAtLeast(1)

        // Check failure memory before attempting MTP
        var effectiveMtpSupported = mtpSupported
        var mtpSkipReason: String? = null
        if (mtpRequested && mtpSupported) {
            val validation = LiteRtLmPackageValidator.validate(File(profile.modelPath))
            val validationVersion = validation.getOrNull()?.validationVersion ?: 0
            mtpSkipReason = mtpFailureMemory.shouldSkipMtp(
                modelPath = profile.modelPath,
                actualBackend = profile.accelerator.name, // Use requested accelerator as proxy for actual backend
                validationVersion = validationVersion,
                accelerator = profile.accelerator.name,
                maxTokens = profile.maxTokens,
                enableVisionBackend = profile.enableVisionBackend
            )
            if (mtpSkipReason != null) {
                effectiveMtpSupported = false
                Log.i("FusionLiteRT", "MTP skipped due to recent failure: $mtpSkipReason")
            }
        }

        val ladder = buildEngineCandidateLadder(
            accelerator = profile.accelerator,
            mtpRequested = mtpRequested,
            mtpSupported = effectiveMtpSupported
        )
        val preferredBackendName = ladder.first().backend
        val requestedKeyProfile = profile.copy(mtpRequested = ladder.first().mtpEnabled)
        val key = buildLiteRtEngineCacheKey(requestedKeyProfile)

        val currentEngine = engine
        if (currentEngine != null && loadedKey == key) {
            lastMtpStatus = cachedMtpStatus
            Log.i("FusionLiteRT", "MTP requested: $mtpRequested (cached engine reused)")
            Log.i("FusionLiteRT", "MTP status: $lastMtpStatus")
            Log.i("FusionLiteRT", "Backend: $preferredBackendName")
            Log.i("FusionLiteRT", "Vision backend requested: ${profile.enableVisionBackend}")
            Log.i("FusionLiteRT", "Model path: ${File(profile.modelPath).name}")
            return currentEngine
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
        } else if (mtpRequested && effectiveMtpSupported) {
            lastMtpStatus = MtpRuntimeStatus.REQUESTED
            Log.i("FusionLiteRT", "MTP requested, model supports it")
        } else if (mtpRequested && mtpSupported && !effectiveMtpSupported) {
            lastMtpStatus = MtpRuntimeStatus.FALLBACK_DISABLED
            Log.i("FusionLiteRT", "MTP fallback disabled due to previous failure")
        } else {
            lastMtpStatus = MtpRuntimeStatus.OFF
        }

        var newEngine: Engine? = null
        var selectedMtpEnabled = false
        var mtpFlagAppliedForMtp = false
        var failure: Throwable? = null
        val selection = selectFirstWorkingEngine(
            ladder = ladder,
            enableVisionBackend = profile.enableVisionBackend,
            configureFlag = { configureSpeculativeDecodingFlag(it) },
            tryCreate = { backendName, mtpEnabled, visionBackendIsCpu ->
                val backend = if (backendName == "CPU") Backend.CPU() else Backend.GPU()
                createEngine(
                    modelPath = profile.modelPath,
                    backend = backend,
                    visionBackend = if (profile.enableVisionBackend) {
                        if (visionBackendIsCpu) Backend.CPU() else backend
                    } else {
                        null
                    },
                    maxNumTokens = maxNumTokens
                )
            }
        )
        val selectionResult = selection.first
        if (selectionResult != null) {
            newEngine = selectionResult.engine
            selectedMtpEnabled = selectionResult.selectedMtpEnabled
            mtpFlagAppliedForMtp = selectionResult.mtpFlagAppliedForMtp
            actualBackend = selectionResult.backendName
            actualVisionBackend = selectionResult.visionBackend
            Log.i(
                "FusionLiteRT",
                "Engine initialized with ${selectionResult.backendName}" +
                    (if (selectedMtpEnabled) " + MTP" else " without MTP")
            )
        } else {
            failure = selection.second
        }

        val resolvedEngine = newEngine ?: run {
            configureSpeculativeDecodingFlag(false)
            lastMtpStatus = MtpRuntimeStatus.FAILED
            cachedMtpStatus = MtpRuntimeStatus.FAILED
            throw (failure ?: IllegalStateException("LiteRT engine candidates exhausted"))
        }

        lastMtpStatus = when {
            mtpRequested && !mtpSupported -> MtpRuntimeStatus.UNSUPPORTED
            mtpRequested && mtpSupported && !effectiveMtpSupported -> MtpRuntimeStatus.FALLBACK_DISABLED
            mtpRequested && mtpSupported && selectedMtpEnabled && mtpFlagAppliedForMtp -> MtpRuntimeStatus.INITIALIZED_WITH_MTP_REQUEST
            mtpRequested && mtpSupported && !mtpFlagAppliedForMtp -> MtpRuntimeStatus.FAILED
            mtpRequested && mtpSupported -> MtpRuntimeStatus.FALLBACK_DISABLED
            else -> MtpRuntimeStatus.OFF
        }
        cachedMtpStatus = lastMtpStatus

        val fallbackReason = when {
            mtpRequested && !mtpSupported -> "Model does not support MTP"
            mtpRequested && mtpSupported && !mtpFlagAppliedForMtp -> "MTP flag application failed"
            mtpRequested && mtpSupported && !selectedMtpEnabled -> "MTP initialization failed, fell back to non-MTP"
            mtpRequested && selectedMtpEnabled && mtpFlagAppliedForMtp -> null
            else -> null
        }
        val initializedWithMtp = mtpRequested && mtpSupported && selectedMtpEnabled && mtpFlagAppliedForMtp

        // Record failure if MTP was requested but fell back
        if (mtpRequested && mtpSupported && !selectedMtpEnabled && fallbackReason != null) {
            val validation = LiteRtLmPackageValidator.validate(File(profile.modelPath))
            val validationVersion = validation.getOrNull()?.validationVersion ?: 0
            mtpFailureMemory.recordFailure(
                modelPath = profile.modelPath,
                actualBackend = actualBackend!!,
                validationVersion = validationVersion,
                accelerator = profile.accelerator.name,
                maxTokens = profile.maxTokens,
                enableVisionBackend = profile.enableVisionBackend,
                fallbackReason = fallbackReason
            )
        }

        cachedRuntimeSelection = EngineSelectionRuntime(
            requestedAccelerator = profile.accelerator.name,
            actualTextBackend = actualBackend!!,
            actualVisionBackend = actualVisionBackend,
            requestedMtp = mtpRequested,
            initializedWithMtp = initializedWithMtp,
            fallbackReason = fallbackReason
        )

        engine = resolvedEngine
        loadedKey = buildLiteRtEngineCacheKey(profile.copy(mtpRequested = selectedMtpEnabled))

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

    private fun buildSamplerConfig(options: ConversationOptions): SamplerConfig {
        val topK = options.topK.coerceAtLeast(1)
        val topP = options.topP.coerceIn(0f, 1f).toDouble()
        val temperature = options.temperature.coerceAtLeast(0f).toDouble()
        return if (options.seed != null) {
            SamplerConfig(topK, topP, temperature, options.seed)
        } else {
            SamplerConfig(topK, topP, temperature)
        }
    }

    private fun buildSystemInstruction(
        messages: List<ChatMessage>,
        profile: RequestedEngineProfile,
        options: ConversationOptions
    ): String {
        val systemMessages = messages
            .filter { it.role == "system" }
            .joinToString("\n\n") { it.content }

        return buildString {
            appendLine("당신은 기기 내에서 실행되는 AI 비서 Fusion입니다.")
            appendLine("한국어로 자연스럽게 답변하며 일관되게 존댓말을 사용합니다.")
            appendLine("모르는 내용은 모른다고 명확히 밝힙니다.")
            appendLine("추론이나 추정은 그 사실을 명확히 구분합니다.")
            appendLine()
            appendLine("GENERATION_SETTINGS")
            appendLine("maxTokens=${profile.maxTokens}")
            appendLine("topK=${options.topK}")
            appendLine("topP=${options.topP}")
            appendLine("temperature=${options.temperature}")
            appendLine("accelerator=${profile.accelerator.name}")
            appendLine("speculativeDecoding=${profile.mtpRequested}")
            appendLine("reasoningBudgetTokens=${options.reasoningBudgetTokens} (prompt-only; LiteRT-LM API does not expose a reasoning budget config here)")

            if (systemMessages.isNotBlank()) {
                appendLine()
                appendLine(systemMessages)
            }
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
                appendLine("maxTokens=${profile.maxTokens} (runtime EngineConfig.maxNumTokens)")
                appendLine("topK=${options.topK} (runtime SamplerConfig.topK)")
                appendLine("topP=${options.topP} (runtime SamplerConfig.topP)")
                appendLine("temperature=${options.temperature} (runtime SamplerConfig.temperature)")
                appendLine("reasoningBudgetTokens=${options.reasoningBudgetTokens} (prompt-only unsupported by current LiteRT-LM API)")
                appendLine("MTP requested=${profile.mtpRequested} (runtime ExperimentalFlags.enableSpeculativeDecoding)")
                appendLine("visionBackend=${profile.enableVisionBackend} (runtime EngineConfig.visionBackend when true)")
            }.trimEnd()
        )
    }

    private fun buildPrompt(
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

    override fun unload() {
        try {
            engine?.close()
        } catch (throwable: Throwable) {
            Log.w("FusionEngine", "Failed to close LiteRT engine", throwable)
        }

        engine = null
        loadedKey = null
        actualBackend = null
        actualVisionBackend = null
        cachedMtpStatus = MtpRuntimeStatus.OFF
        cachedRuntimeSelection = null
        lastMtpStatus = MtpRuntimeStatus.OFF
        runCatching {
            configureSpeculativeDecodingFlag(false)
        }
    }

    /**
     * Clears MTP failure memory for the given model path.
     * Call this when the model file has changed (size/mtime) or on explicit manual retry.
     */
    fun clearMtpFailureMemory(modelPath: String) {
        mtpFailureMemory.clearForModel(modelPath)
    }

    /**
     * Clears all MTP failure memory (e.g., on explicit user retry).
     */
    fun clearAllMtpFailureMemory() {
        mtpFailureMemory.clearAll()
    }

    private fun isSpeculativeDecodingSupportedModel(modelPath: String): Boolean {
        return LiteRtLmPackageValidator.validate(File(modelPath)).getOrNull()?.hasDrafter == true
    }

    private fun configureSpeculativeDecodingFlag(enabled: Boolean): Boolean {
        return flagSetter(enabled).also { applied ->
            if (!applied) {
                Log.w(
                    "FusionLiteRT",
                    "Failed to ${if (enabled) "enable" else "disable"} MTP speculative decoding flag"
                )
            }
        }
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

internal fun buildLiteRtEngineCacheKey(profile: RequestedEngineProfile): String = buildString {
    append(profile.modelPath)
    append("|")
    append(profile.accelerator.name)
    append("|")
    append(profile.maxTokens)
    append("|")
    append(profile.mtpRequested)
    append("|vision=")
    append(profile.enableVisionBackend)
}

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
        val flagApplied = configureFlag(candidate.mtpEnabled)
        if (candidate.mtpEnabled && flagApplied) {
            mtpFlagAppliedForMtp = true
        }
        val attempt = tryCreate(candidate.backend, candidate.mtpEnabled, false)
        if (attempt.isSuccess) {
            return Pair(
                EngineSelectionResult(
                    engine = attempt.getOrThrow(),
                    selectedMtpEnabled = candidate.mtpEnabled && flagApplied,
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
                        selectedMtpEnabled = candidate.mtpEnabled && flagApplied,
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
            if (canUseMtp) add(EngineCandidate("CPU", mtpEnabled = true))
            add(EngineCandidate("CPU", mtpEnabled = false))
        }
        AcceleratorMode.GPU -> buildList {
            if (canUseMtp) add(EngineCandidate("GPU", mtpEnabled = true))
            add(EngineCandidate("GPU", mtpEnabled = false))
        }
        AcceleratorMode.AUTO -> buildList {
            if (canUseMtp) add(EngineCandidate("GPU", mtpEnabled = true))
            add(EngineCandidate("GPU", mtpEnabled = false))
            if (canUseMtp) add(EngineCandidate("CPU", mtpEnabled = true))
            add(EngineCandidate("CPU", mtpEnabled = false))
        }
    }
}

/**
 * Tracks MTP initialization failures to prevent repeated failed rebuilds.
 * Keyed by canonical model identity, actual backend, package capability version, and engine settings.
 */
internal class MtpFailureMemory {
    private data class FailureKey(
        val modelPath: String,
        val actualBackend: String,
        val validationVersion: Int,
        val accelerator: String,
        val maxTokens: Int,
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
    }

    /**
     * Checks if an MTP attempt should be skipped due to recent failure.
     * Returns the fallback reason if MTP should be skipped, null otherwise.
     */
    fun shouldSkipMtp(
        modelPath: String,
        actualBackend: String,
        validationVersion: Int,
        accelerator: String,
        maxTokens: Int,
        enableVisionBackend: Boolean
    ): String? = synchronized(lock) {
        val key = FailureKey(
            modelPath = modelPath,
            actualBackend = actualBackend,
            validationVersion = validationVersion,
            accelerator = accelerator,
            maxTokens = maxTokens,
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
            }
        }
        return null
    }

    /**
     * Records an MTP failure for the given key.
     */
    fun recordFailure(
        modelPath: String,
        actualBackend: String,
        validationVersion: Int,
        accelerator: String,
        maxTokens: Int,
        enableVisionBackend: Boolean,
        fallbackReason: String
    ) = synchronized(lock) {
        val key = FailureKey(
            modelPath = modelPath,
            actualBackend = actualBackend,
            validationVersion = validationVersion,
            accelerator = accelerator,
            maxTokens = maxTokens,
            enableVisionBackend = enableVisionBackend
        )
        failures[key] = FailureRecord(
            failedAt = System.currentTimeMillis(),
            fallbackReason = fallbackReason
        )
        // Evict oldest if over limit
        if (failures.size > MAX_ENTRIES) {
            val oldestKey = failures.minByOrNull { it.value.failedAt }?.key
            oldestKey?.let { failures.remove(it) }
        }
    }

    /**
     * Clears failure memory for a specific model (e.g., when model file changes).
     */
    fun clearForModel(modelPath: String) = synchronized(lock) {
        failures.keys.filter { it.modelPath == modelPath }.forEach { failures.remove(it) }
    }

    /**
     * Clears all failure memory (e.g., on explicit manual retry).
     */
    fun clearAll() = synchronized(lock) {
        failures.clear()
    }
}
