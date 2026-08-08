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
import com.projectnuke.fusion.ui.FusionDeveloperLogStore
import com.projectnuke.fusion.util.AttachmentStorageManager
import com.projectnuke.fusion.util.ManagedModelPathPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Exception thrown when engine selection fails, carrying the attempt snapshot
 * so callers can construct a [GenerationOutcome.Failure] with the full
 * fallback context without rereading engine state.
 */
internal class EngineSelectionFailedException(
    val attemptSnapshot: RuntimeAttemptSnapshot,
    cause: Throwable? = null
) : RuntimeException("Engine selection failed", cause)

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
    },
    /**
     * Native LiteRT-LM minimum log severity. Production keeps [LogSeverity.ERROR]
     * to stay quiet; benchmark / debug mode may lower this to
     * [LogSeverity.WARNING] to surface component-placement diagnostics without
     * honoring unstable warning strings as permanent correctness state (Phase 9).
     */
    private val nativeMinLogSeverity: () -> LogSeverity = { LogSeverity.ERROR },
    /**
     * Clock for deterministic cooldown timing in tests and for production timing.
     * Injected to allow deterministic testing of failure memory cooldown boundaries.
     */
    private val clock: () -> Long = { System.currentTimeMillis() }
) : LlmEngine {

    private var loadedState: LoadedRuntimeState<Engine>? = null
    private val mtpFailureMemory = MtpFailureMemory(failureMemoryStorage, clock)
    @Volatile
    var lastMtpStatus: MtpRuntimeStatus = MtpRuntimeStatus.OFF
        private set
    
    @Volatile
    private var lastRecordedDurability: FailureMemoryDurability = FailureMemoryDurability.NotAttempted

    val lastRuntimeSelection: EngineSelectionRuntime?
        get() = loadedState?.runtimeSelection

    /**
     * Phase 1: request-local fallback events live here for the lifetime of one
     * generate/generateStreaming call. Combined with [loadedState].fallbackEvents
     * (stable acquisition events) at snapshot-build time. Never written back
     * into loadedState, so a reused Engine never inherits another request's
     * cooldown or skip events.
     */
    @Volatile
    private var currentRequestFallbackEvents: List<RuntimeFallbackEvent> = emptyList()

    /**
     * Updates the native LiteRT-LM minimum log severity at runtime. Used by
     * benchmark / debug mode to lower the threshold to WARNING so internal
     * component-placement (sampler) diagnostics surface; production mode keeps
     * ERROR so warnings are never honored as permanent correctness state.
     * Must only be called while the caller owns the exclusive runtime section.
     */
    fun setNativeMinLogSeverity(severity: LogSeverity) {
        Engine.setNativeMinLogSeverity(severity)
    }

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
            } catch (e: EngineSelectionFailedException) {
                return@withContext GenerationOutcome.Failure(
                    kind = FailureKind.MODEL_LOAD_FAILED,
                    message = "모델을 불러올 수 없습니다. 모델 설정을 확인한 뒤 다시 시도해 주세요.",
                    attemptSnapshot = e.attemptSnapshot
                )
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

            val prepared = FusionPromptAdapters.prepareMessagesForModel(messages)
            val promptAdapter = FusionPromptAdapters.forFamily(prepared.modelFamily)
            val adaptedMessages = promptAdapter.buildMessages(prepared.messages)
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
                return@withContext classifyGenerationException(e, isMultimodal = false, profile = resolvedProfile)
            }

            val sanitized = promptAdapter.sanitizeOutput(output.toString())
            if (sanitized.isBlank()) {
                GenerationOutcome.Empty
            } else {
                GenerationOutcome.Success(
                    text = sanitized,
                    actualBackend = loadedState?.actualTextBackend,
                    truncated = outputTruncated,
                    stats = nativeStats,
                    snapshot = buildRuntimeExecutionSnapshot(profile)
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
            } catch (e: EngineSelectionFailedException) {
                return@withContext GenerationOutcome.Failure(
                    kind = FailureKind.MODEL_LOAD_FAILED,
                    message = "모델을 불러올 수 없습니다. 모델 설정을 확인한 뒤 다시 시도해 주세요.",
                    attemptSnapshot = e.attemptSnapshot
                )
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

            val prepared = FusionPromptAdapters.prepareMessagesForModel(messages)
            val promptAdapter = FusionPromptAdapters.forFamily(prepared.modelFamily)
            val adaptedMessages = promptAdapter.buildMessages(prepared.messages)
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
                return@withContext classifyGenerationException(e, isMultimodal = true, profile = resolvedProfile)
            }

            val sanitized = promptAdapter.sanitizeOutput(output.toString())
            if (sanitized.isBlank()) {
                GenerationOutcome.Empty
            } else {
                GenerationOutcome.Success(
                    text = sanitized,
                    actualBackend = loadedState?.actualTextBackend,
                    truncated = outputTruncated,
                    stats = nativeStats,
                    snapshot = buildRuntimeExecutionSnapshot(profile)
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
        isMultimodal: Boolean,
        profile: RequestedEngineProfile
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
        // Build the immutable generation-after-acquisition failure snapshot
        // from the currently loaded engine state. Callers will read this
        // snapshot, never `engine.lastMtpStatus` or `engine.lastRuntimeSelection`.
        val loaded = loadedState
        val fingerprint = ModelFingerprint.of(profile.modelPath)
        val combinedEvents = combineFallbackEvents(
            loaded?.fallbackEvents ?: emptyList(),
            currentRequestFallbackEvents
        )
        val failureSnapshot = if (loaded != null) {
            RuntimeFailureSnapshot(
                requestedAccelerator = profile.accelerator,
                selectedTextBackend = loaded.actualTextBackend.toRuntimeBackend(),
                selectedVisionBackend = loaded.actualVisionBackend?.toRuntimeBackend(),
                mtpRequested = profile.mtpRequested,
                mtpRuntimeStatus = loaded.mtpStatus,
                fallbackEventsFromAcquisition = combinedEvents,
                modelFingerprint = ModelFingerprintSummary(
                    canonicalPath = fingerprint.canonicalPath,
                    fileSize = fingerprint.fileSize,
                    modifiedAt = fingerprint.modifiedAt,
                    validationVersion = fingerprint.validationVersion,
                    mtpSupported = fingerprint.mtpSupported
                ),
                failureKind = kind
            )
        } else {
            null
        }
        return GenerationOutcome.Failure(
            kind = kind,
            message = message,
            attemptSnapshot = null,
            failureSnapshot = failureSnapshot
        )
    }

    private fun getOrCreateEngine(profile: RequestedEngineProfile): Engine {
        val coordinator = EngineAcquisitionCoordinator(
            mtpFailureMemory = mtpFailureMemory,
            mtpCapabilityProbe = mtpCapabilityProbe,
            configureFlag = { settleSpeculativeDecodingFlag(it) },
            engineFactory = { backendName, mtpEnabled, visionBackendIsCpu ->
                val backend = if (backendName == "CPU") Backend.CPU() else Backend.GPU()
                val result = createEngine(
                    modelPath = profile.modelPath,
                    backend = backend,
                    visionBackend = if (profile.enableVisionBackend) {
                        if (visionBackendIsCpu) Backend.CPU() else backend
                    } else {
                        null
                    },
                    maxNumTokens = profile.kvCacheCapacityTokens.coerceAtLeast(1)
                )
                if (result.isSuccess) {
                    EngineCandidateAttempt.Success(result.getOrThrow())
                } else {
                    EngineCandidateAttempt.InitializationFailed(result.exceptionOrNull() ?: IllegalStateException("Engine creation failed"))
                }
            },
            clock = clock,
            onPersistenceCompleted = { checkAndRecordDurabilityTransition() }
        )

        val outcome = coordinator.acquire(profile, loadedState)
        val outcomeFingerprint = outcome.fingerprint ?: ModelFingerprint.of(profile.modelPath)
        val outcomeCapabilityResult = outcome.mtpCapabilityResult

        // Phase 2: Separate stable acquisition events from request-local events
        // Coordinator returns all events in outcome.fallbackEvents; we must split them.
        val (stableAcquisitionEvents, requestLocalEvents) = outcome.fallbackEvents.partition { event ->
            // Stable events: from actual engine initialization attempts
            when (event.reason) {
                FallbackReason.MTP_ENGINE_INIT_FAILED,
                FallbackReason.BACKEND_ENGINE_INIT_FAILED,
                FallbackReason.GPU_VISION_BACKEND_FAILED_CPU_VISION_SELECTED,
                FallbackReason.GPU_TEXT_ENGINE_FAILED_CPU_SELECTED,
                FallbackReason.SPECULATIVE_ENABLE_FLAG_SETTLEMENT_FAILED,
                FallbackReason.SPECULATIVE_DISABLE_FLAG_SETTLEMENT_FAILED,
                FallbackReason.MTP_UNSUPPORTED,
                FallbackReason.ALL_CANDIDATES_EXHAUSTED -> true
                // Request-local (cooldown/skip) events:
                FallbackReason.MTP_SKIPPED_RECENT_FAILURE,
                FallbackReason.BACKEND_SKIPPED_RECENT_FAILURE,
                FallbackReason.ALL_CANDIDATES_SKIPPED_RECENT_FAILURE -> false
            }
        }

        // Update runtime state based on outcome
        val selectionResult = outcome.selection
        if (selectionResult != null) {
            val resolvedEngine = selectionResult.engine
            val selectedMtpEnabled = selectionResult.selectedMtpEnabled
            val mtpFlagAppliedForMtp = selectionResult.mtpFlagAppliedForMtp
            val actualTextBackend = selectionResult.backendName
            
            lastMtpStatus = resolveMtpRuntimeStatus(
                mtpRequested = profile.mtpRequested,
                mtpSupported = outcomeFingerprint.mtpSupported,
                selectedMtpEnabled = selectedMtpEnabled,
                mtpFlagAppliedForMtp = mtpFlagAppliedForMtp,
                mtpCapabilityResult = outcomeCapabilityResult,
                mtpSkippedByMemory = requestLocalEvents.any { it.reason == FallbackReason.MTP_SKIPPED_RECENT_FAILURE },
                mtpAttempted = outcomeCapabilityResult != null || outcome.fallbackEvents.any { it.attemptedMtpEnabled == true && it.reason == FallbackReason.MTP_ENGINE_INIT_FAILED } || selectedMtpEnabled
            )
            
            val fallbackReason = resolveMtpFallbackReason(
                mtpRequested = profile.mtpRequested,
                mtpSupported = outcomeFingerprint.mtpSupported,
                selectedMtpEnabled = selectedMtpEnabled,
                mtpFlagAppliedForMtp = mtpFlagAppliedForMtp,
                mtpCapabilityResult = outcomeCapabilityResult,
                mtpSkippedByMemory = requestLocalEvents.any { it.reason == FallbackReason.MTP_SKIPPED_RECENT_FAILURE },
                mtpAttempted = outcomeCapabilityResult != null || outcome.fallbackEvents.any { it.attemptedMtpEnabled == true && it.reason == FallbackReason.MTP_ENGINE_INIT_FAILED } || selectedMtpEnabled
            )
            
            val initializedWithMtp = profile.mtpRequested && outcomeFingerprint.mtpSupported && selectedMtpEnabled && mtpFlagAppliedForMtp
            
            val runtimeSelection = EngineSelectionRuntime(
                requestedAccelerator = profile.accelerator.name,
                actualTextBackend = actualTextBackend,
                actualVisionBackend = selectionResult.visionBackend,
                requestedMtp = profile.mtpRequested,
                initializedWithMtp = initializedWithMtp,
                fallbackReason = outcome.fallbackEvents.firstOrNull()?.reason?.name ?: fallbackReason
            )

            // Phase 2 + Phase 3: Request-local events live in currentRequestFallbackEvents
            // Stable acquisition events go into LoadedRuntimeState.fallbackEvents
            // When reusing an engine, stable events AND actual vision backend are preserved
            // Close replaced native Engines exactly once
            val currentLoadedState = loadedState
            val isFreshEngine = currentLoadedState == null || currentLoadedState.engine != resolvedEngine
            if (isFreshEngine) {
                currentRequestFallbackEvents = requestLocalEvents
                // Close the old engine if we're replacing it
                currentLoadedState?.engine?.close()
                loadedState = LoadedRuntimeState(
                    engine = resolvedEngine,
                    key = EngineRuntimeKey(
                        fingerprint = outcomeFingerprint,
                        accelerator = profile.accelerator,
                        kvCacheCapacityTokens = profile.kvCacheCapacityTokens,
                        enableVisionBackend = profile.enableVisionBackend,
                        mtpEnabled = selectedMtpEnabled,
                        selectedBackend = actualTextBackend
                    ),
                    mtpStatus = lastMtpStatus,
                    runtimeSelection = runtimeSelection,
                    actualTextBackend = actualTextBackend,
                    actualVisionBackend = selectionResult.visionBackend,
                    fallbackEvents = stableAcquisitionEvents
                )
            } else {
                // Reusing existing engine: preserve stable events AND actual vision backend
                currentRequestFallbackEvents = requestLocalEvents
                loadedState = currentLoadedState.copy(
                    mtpStatus = lastMtpStatus,
                    runtimeSelection = runtimeSelection,
                    actualTextBackend = actualTextBackend
                    // actualVisionBackend preserved from loaded state (Phase 3)
                    // fallbackEvents (stable) unchanged
                )
            }
            return resolvedEngine
        } else {
            // Failure path: no engine acquired
            currentRequestFallbackEvents = requestLocalEvents
            settleSpeculativeDecodingFlag(false)
            lastMtpStatus = MtpRuntimeStatus.FAILED
            val attemptSnapshot = outcome.failure?.let { failure ->
                if (failure is EngineSelectionFailedException) {
                    failure.attemptSnapshot
                } else {
                    RuntimeAttemptSnapshot(
                        requestedAccelerator = profile.accelerator,
                        fallbackEvents = outcome.fallbackEvents,
                        modelFingerprint = ModelFingerprintSummary(
                            canonicalPath = outcomeFingerprint.canonicalPath,
                            fileSize = outcomeFingerprint.fileSize,
                            modifiedAt = outcomeFingerprint.modifiedAt,
                            validationVersion = outcomeFingerprint.validationVersion,
                            mtpSupported = outcomeFingerprint.mtpSupported
                        ),
                        mtpRequested = profile.mtpRequested
                    )
                }
            } ?: RuntimeAttemptSnapshot(
                requestedAccelerator = profile.accelerator,
                fallbackEvents = outcome.fallbackEvents,
                modelFingerprint = ModelFingerprintSummary(
                    canonicalPath = outcomeFingerprint.canonicalPath,
                    fileSize = outcomeFingerprint.fileSize,
                    modifiedAt = outcomeFingerprint.modifiedAt,
                    validationVersion = outcomeFingerprint.validationVersion,
                    mtpSupported = outcomeFingerprint.mtpSupported
                ),
                mtpRequested = profile.mtpRequested
            )
            throw EngineSelectionFailedException(attemptSnapshot, outcome.failure)
        }
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

    /**
     * Builds the immutable [RuntimeExecutionSnapshot] for the currently loaded
     * runtime. The fallback event list comes from the typed events captured
     * during candidate selection (Phase 3) rather than from a single fallback
     * reason string, so multiple concurrent fallbacks (MTP + backend) survive.
     * [samplerBackend] is UNKNOWN until a stable LiteRT-LM API reports sampler
     * placement (see Phase 9).
     *
     * Phase 1: combines stable acquisition events from [loadedState] with the
     * current request's local cooldown/skip events. Neither list is ever
     * mutated by this merge.
     */
    private fun buildRuntimeExecutionSnapshot(
        profile: RequestedEngineProfile
    ): RuntimeExecutionSnapshot? {
        val state = loadedState ?: return null
        val fingerprint = state.key.fingerprint
        val combinedEvents = combineFallbackEvents(
            state.fallbackEvents,
            currentRequestFallbackEvents
        )
        return RuntimeExecutionSnapshot(
            requestedAccelerator = profile.accelerator,
            selectedTextBackend = state.actualTextBackend.toRuntimeBackend(),
            selectedVisionBackend = state.actualVisionBackend?.toRuntimeBackend(),
            samplerBackend = RuntimeComponentBackend.UNKNOWN,
            mtpRequested = profile.mtpRequested,
            mtpStatus = state.mtpStatus,
            fallbackEvents = combinedEvents,
            modelFingerprint = ModelFingerprintSummary(
                canonicalPath = fingerprint.canonicalPath,
                fileSize = fingerprint.fileSize,
                modifiedAt = fingerprint.modifiedAt,
                validationVersion = fingerprint.validationVersion,
                mtpSupported = fingerprint.mtpSupported
            )
        )
    }

    /**
     * Phase 1: deterministic combination of stable Engine acquisition events
     * and request-local cooldown/skip events. Both lists are immutable.
     * Duplicate consecutive events are collapsed using an explicit bounded
     * deduplication policy: a duplicate is defined as having the same
     * [RuntimeFallbackEvent.reason], [RuntimeFallbackEvent.attemptedTextBackend],
     * [RuntimeFallbackEvent.attemptedMtpEnabled], and
     * [RuntimeFallbackEvent.attemptedVisionBackend]; no more than 2 consecutive
     * duplicates are kept.
     */
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
        checkAndRecordDurabilityTransition()
    }

    /**
     * Clears all MTP failure memory (e.g., on explicit user retry).
     */
    fun clearAllMtpFailureMemory() {
        mtpFailureMemory.clearAll()
        checkAndRecordDurabilityTransition()
    }

    /**
     * Phase D: typed durability state of the Engine-owned failure memory.
     *
     *  - [FailureMemoryDurability.NotAttempted]: no save/clear operation has
     *    been attempted yet since the Engine was constructed.
     *  - [FailureMemoryDurability.Durable]: the last save/clear succeeded;
     *    cooldown is expected to survive a process restart.
     *  - [FailureMemoryDurability.InMemoryOnly]: the last save/clear either
     *    returned false or threw. Cooldown still works for the lifetime of
     *    this Engine instance but will NOT survive a restart. [cause]
     *    carries the typed exception (or a synthetic IllegalStateException
     *    when storage returned false without throwing) so callers can
     *    route the failure into diagnostics/developer logging.
     */
    fun failureMemoryDurability(): FailureMemoryDurability {
        val result = mtpFailureMemory.lastDurabilityResult()
        val cause = mtpFailureMemory.lastDurabilityException()
        return when (result) {
            null -> FailureMemoryDurability.NotAttempted
            true -> FailureMemoryDurability.Durable
            false -> FailureMemoryDurability.InMemoryOnly(
                cause = cause ?: IllegalStateException("Failure memory storage returned false")
            )
        }
    }

    /**
     * Checks for durability state transitions and records them to the developer log.
     * Called after operations that may modify failure memory persistence.
     */
    private fun checkAndRecordDurabilityTransition() {
        val current = failureMemoryDurability()
        if (current != lastRecordedDurability) {
            when {
                lastRecordedDurability == FailureMemoryDurability.Durable && current is FailureMemoryDurability.InMemoryOnly -> {
                    FusionDeveloperLogStore.recordDurabilityState(context, current)
                }
                lastRecordedDurability is FailureMemoryDurability.InMemoryOnly && current == FailureMemoryDurability.Durable -> {
                    FusionDeveloperLogStore.recordDurabilityState(context, current)
                }
                lastRecordedDurability == FailureMemoryDurability.NotAttempted && current != FailureMemoryDurability.NotAttempted -> {
                    FusionDeveloperLogStore.recordDurabilityState(context, current)
                }
            }
            lastRecordedDurability = current
        }
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

/**
 * Phase D: typed durability state of the Engine-owned failure memory.
 *
 * Exposed through [LiteRtLlmEngine.failureMemoryDurability] and observable
 * through diagnostics/developer logging so the app never claims cooldown
 * will survive a restart when persistence is broken.
 */
sealed interface FailureMemoryDurability {
    /** No save/clear operation has been attempted yet. */
    data object NotAttempted : FailureMemoryDurability
    /** The last save/clear succeeded; cooldown is durable. */
    data object Durable : FailureMemoryDurability
    /**
     * The last save/clear either returned false or threw. Cooldown still
     * works in-memory for the lifetime of this Engine instance but will
     * NOT survive a restart. [cause] is the typed exception (or a
     * synthetic IllegalStateException when storage returned false without
     * throwing).
     */
    data class InMemoryOnly(val cause: Throwable) : FailureMemoryDurability
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
 *
 * Phase 4: [MtpRuntimeStatus.FAILED] is reserved for the no-usable-Engine case
 * (set explicitly in [LiteRtLlmEngine.getOrCreateEngine] before the throw). This
 * resolver is reached only after a usable Engine initialized, so any "MTP was
 * requested but a non-MTP engine succeeded" path returns
 * [MtpRuntimeStatus.FALLBACK_DISABLED] — including the flag-settlement-failed
 * path previously encoded as FAILED. The flag-settle-failed event carries the
 * [FallbackReason.SPECULATIVE_ENABLE_FLAG_SETTLEMENT_FAILED] reason.
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
    else -> MtpRuntimeStatus.FALLBACK_DISABLED
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

sealed interface EngineCandidateAttempt<out T> {
    data class Success<T>(val engine: T) : EngineCandidateAttempt<T>
    data class CapabilityRejected(val backendName: String, val mtpEnabled: Boolean) : EngineCandidateAttempt<Nothing>
    data class InitializationFailed(val throwable: Throwable) : EngineCandidateAttempt<Nothing>
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
    val fingerprint: ModelFingerprint? = null,
    val mtpCapabilityResult: Boolean? = null
)

/**
 * Outcome of iterating the candidate ladder: the first working selection (if
 * any), the last failure when nothing worked, and the typed fallback events
 * captured along the way. Replaces the previous `Pair<EngineSelectionResult<T>?, Throwable?>`
 * so app-level fallbacks are recorded as stable enums instead of English strings.
 */
internal data class EngineSelectionOutcome<T>(
    val selection: EngineSelectionResult<T>?,
    val failure: Throwable?,
    val fallbackEvents: List<RuntimeFallbackEvent> = emptyList(),
    val fingerprint: ModelFingerprint? = null,
    val mtpCapabilityResult: Boolean? = null
)

internal fun <T> selectFirstWorkingEngine(
    ladder: List<EngineCandidate>,
    enableVisionBackend: Boolean,
    configureFlag: (Boolean) -> Boolean,
    tryCreate: (backendName: String, mtpEnabled: Boolean, visionBackendIsCpu: Boolean) -> EngineCandidateAttempt<T>,
    loadedState: LoadedRuntimeState<T>? = null,
    fingerprint: ModelFingerprint? = null,
    accelerator: AcceleratorMode? = null,
    kvCacheCapacityTokens: Int? = null,
    enableVisionBackendProfile: Boolean? = null
): EngineSelectionOutcome<T> {
    // If loadedState reuse check is requested, all parameters must be provided
    val reuseEnabled = loadedState != null && fingerprint != null && accelerator != null && kvCacheCapacityTokens != null && enableVisionBackendProfile != null
    var mtpFlagAppliedForMtp = false
    var lastFailure: Throwable? = null
    val fallbackEvents = mutableListOf<RuntimeFallbackEvent>()
    var pendingGpuPlainFailure = false
    for (candidate in ladder) {
        // Check for exact loaded-engine reuse at this candidate level
        if (reuseEnabled) {
            val candidateKey = EngineRuntimeKey(
                fingerprint = fingerprint!!,
                accelerator = accelerator!!,
                kvCacheCapacityTokens = kvCacheCapacityTokens!!,
                enableVisionBackend = enableVisionBackendProfile!!,
                mtpEnabled = candidate.mtpEnabled,
                selectedBackend = candidate.backend
            )
            if (loadedState!!.key == candidateKey) {
                return EngineSelectionOutcome(
                    selection = EngineSelectionResult(
                        engine = loadedState!!.engine,
                        selectedMtpEnabled = candidate.mtpEnabled,
                        mtpFlagAppliedForMtp = loadedState!!.mtpStatus == MtpRuntimeStatus.INITIALIZED_WITH_MTP_REQUEST,
                        backendName = candidate.backend,
                        visionBackend = loadedState!!.actualVisionBackend,
                        fingerprint = fingerprint!!,
                        mtpCapabilityResult = null
                    ),
                    failure = null,
                    fallbackEvents = fallbackEvents
                )
            }
        }

        val flagApplied = configureFlag(candidate.mtpEnabled)
        if (!flagApplied) {
            fallbackEvents += RuntimeFallbackEvent(
                attemptedTextBackend = candidate.backend.toRuntimeBackend(),
                attemptedMtpEnabled = candidate.mtpEnabled,
                reason = if (candidate.mtpEnabled) FallbackReason.SPECULATIVE_ENABLE_FLAG_SETTLEMENT_FAILED
                    else FallbackReason.SPECULATIVE_DISABLE_FLAG_SETTLEMENT_FAILED
            )
            lastFailure = IllegalStateException("Speculative decoding flag settle failed for mtp=${candidate.mtpEnabled}")
            continue
        }
        if (candidate.mtpEnabled) {
            mtpFlagAppliedForMtp = true
        }
        val attempt = tryCreate(candidate.backend, candidate.mtpEnabled, false)
        when (attempt) {
            is EngineCandidateAttempt.Success -> {
                if (pendingGpuPlainFailure && candidate.backend == "CPU" && !candidate.mtpEnabled) {
                    fallbackEvents += RuntimeFallbackEvent(
                        attemptedTextBackend = RuntimeBackend.GPU,
                        attemptedMtpEnabled = false,
                        selectedReplacementBackend = RuntimeBackend.CPU,
                        reason = FallbackReason.GPU_TEXT_ENGINE_FAILED_CPU_SELECTED
                    )
                }
                return EngineSelectionOutcome(
                    selection = EngineSelectionResult(
                        engine = attempt.engine,
                        selectedMtpEnabled = candidate.mtpEnabled,
                        mtpFlagAppliedForMtp = mtpFlagAppliedForMtp,
                        backendName = candidate.backend,
                        visionBackend = if (enableVisionBackend) candidate.backend else null,
                        fingerprint = fingerprint,
                        mtpCapabilityResult = null
                    ),
                    failure = null,
                    fallbackEvents = fallbackEvents
                )
            }
            is EngineCandidateAttempt.CapabilityRejected -> {
                // Capability rejection (e.g. MTP unsupported) must not
                // trigger a vision-backend retry for the same candidate.
                // Record MTP_UNSUPPORTED and move to the next candidate.
                fallbackEvents += RuntimeFallbackEvent(
                    attemptedTextBackend = attempt.backendName.toRuntimeBackend(),
                    attemptedMtpEnabled = attempt.mtpEnabled,
                    reason = FallbackReason.MTP_UNSUPPORTED
                )
                lastFailure = IllegalStateException("MTP capability probe: no speculative decoding support")
            }
            is EngineCandidateAttempt.InitializationFailed -> {
                lastFailure = attempt.throwable
                if (enableVisionBackend && candidate.backend == "GPU") {
                    val visionRetry = tryCreate(candidate.backend, candidate.mtpEnabled, true)
                    if (visionRetry is EngineCandidateAttempt.Success) {
                        fallbackEvents += RuntimeFallbackEvent(
                            attemptedTextBackend = RuntimeBackend.GPU,
                            attemptedVisionBackend = RuntimeBackend.GPU,
                            attemptedMtpEnabled = candidate.mtpEnabled,
                            selectedReplacementBackend = RuntimeBackend.CPU,
                            reason = FallbackReason.GPU_VISION_BACKEND_FAILED_CPU_VISION_SELECTED
                        )
                        return EngineSelectionOutcome(
                            selection = EngineSelectionResult(
                                engine = visionRetry.engine,
                                selectedMtpEnabled = candidate.mtpEnabled,
                                mtpFlagAppliedForMtp = mtpFlagAppliedForMtp,
                                backendName = candidate.backend,
                                visionBackend = "CPU"
                            ),
                            failure = null,
                            fallbackEvents = fallbackEvents
                        )
                    }
                    // Both first attempt and vision retry failed for a GPU candidate.
                    // Only set pendingGpuPlainFailure for plain GPU (non-MTP) failures.
                    // If GPU+MTP failed but plain GPU was skipped, don't set this flag.
                    if (!candidate.mtpEnabled) {
                        pendingGpuPlainFailure = true
                    }
                    if (candidate.mtpEnabled) {
                        fallbackEvents += RuntimeFallbackEvent(
                            attemptedTextBackend = candidate.backend.toRuntimeBackend(),
                            attemptedMtpEnabled = true,
                            reason = FallbackReason.MTP_ENGINE_INIT_FAILED
                        )
                    } else {
                        fallbackEvents += RuntimeFallbackEvent(
                            attemptedTextBackend = RuntimeBackend.GPU,
                            attemptedMtpEnabled = false,
                            reason = FallbackReason.BACKEND_ENGINE_INIT_FAILED
                        )
                    }
                } else {
                    if (candidate.mtpEnabled) {
                        fallbackEvents += RuntimeFallbackEvent(
                            attemptedTextBackend = candidate.backend.toRuntimeBackend(),
                            attemptedMtpEnabled = true,
                            reason = FallbackReason.MTP_ENGINE_INIT_FAILED
                        )
                    } else {
                        pendingGpuPlainFailure = candidate.backend == "GPU"
                        fallbackEvents += RuntimeFallbackEvent(
                            attemptedTextBackend = candidate.backend.toRuntimeBackend(),
                            attemptedMtpEnabled = false,
                            reason = FallbackReason.BACKEND_ENGINE_INIT_FAILED
                        )
                    }
                }
            }
        }
    }
    return EngineSelectionOutcome(
        selection = null,
        failure = lastFailure,
        fallbackEvents = fallbackEvents
    )
}

internal fun String.toRuntimeBackend(): RuntimeBackend = when (this) {
    "CPU" -> RuntimeBackend.CPU
    "GPU" -> RuntimeBackend.GPU
    else -> RuntimeBackend.UNKNOWN
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
 * Checks whether the currently loaded engine exactly matches the requested
 * profile for safe reuse. All relevant identity fields must match exactly.
 * This is a pure function so it can be tested independently.
 *
 * Phase A repair: this function previously rebuilt the candidate ladder and
 * re-queried failure memory, which made the all-skipped reuse branch
 * internally contradictory (the loaded engine would always be re-vetoed).
 * The all-skipped reuse decision is now inlined in `getOrCreateEngine` and
 * compares the loaded Engine's [EngineRuntimeKey] against the *originally
 * planned* candidates (not the failure-memory-filtered ladder), so a live,
 * successfully initialized Engine is treated as alive — failure memory can
 * only skip *new* initialization attempts, not already-live engines.
 */
internal fun canReuseLoadedEngine(
    loadedState: LoadedRuntimeState<*>?,
    requestedFingerprint: ModelFingerprint,
    requestedAccelerator: AcceleratorMode,
    requestedKvCacheCapacityTokens: Int,
    requestedEnableVisionBackend: Boolean,
    requestedMtpEnabled: Boolean,
    requestedSelectedBackend: String,
    @Suppress("UNUSED_PARAMETER") failureMemory: MtpFailureMemory,
    @Suppress("UNUSED_PARAMETER") profile: RequestedEngineProfile
): Boolean {
    val loaded = loadedState ?: return false
    val key = loaded.key
    if (key.fingerprint != requestedFingerprint) return false
    if (key.accelerator != requestedAccelerator) return false
    if (key.kvCacheCapacityTokens != requestedKvCacheCapacityTokens) return false
    if (key.enableVisionBackend != requestedEnableVisionBackend) return false
    if (key.mtpEnabled != requestedMtpEnabled) return false
    if (key.selectedBackend != requestedSelectedBackend) return false
    return true
}
interface MtpFailureMemoryStorage {
    fun load(): Map<String, String>
    fun save(entries: Map<String, String>): Boolean
    fun clear(): Boolean
}

object NoopMtpFailureMemoryStorage : MtpFailureMemoryStorage {
    override fun load(): Map<String, String> = emptyMap()
    override fun save(entries: Map<String, String>): Boolean = true
    override fun clear(): Boolean = true
}

/**
 * Groups failure memory entries by the model's immutable identity.
 * When a model is replaced at the same path (different file size,
 * modified time, validator version, or capability fingerprint),
 * all entries belonging to the old fingerprint are invalidated.
 */
internal data class FailureModelFingerprint(
    val canonicalPath: String,
    val fileSize: Long,
    val modifiedAt: Long,
    val validationVersion: Int,
    val mtpSupported: Boolean,
) {
    fun matches(other: FailureModelFingerprint): Boolean =
        canonicalPath == other.canonicalPath &&
            fileSize == other.fileSize &&
            modifiedAt == other.modifiedAt &&
            validationVersion == other.validationVersion &&
            mtpSupported == other.mtpSupported
}

/**
 * Port abstraction over [MtpFailureMemory] so unit tests can inject a
 * deterministic fake without subclassing the final class.
 */
internal interface MtpFailureMemoryPort {
    fun shouldSkipMtp(
        modelPath: String,
        backendName: String,
        mtpEnabled: Boolean,
        validationVersion: Int,
        kvCacheCapacityTokens: Int,
        enableVisionBackend: Boolean,
        fileSize: Long,
        modifiedAt: Long,
        mtpSupported: Boolean
    ): String?

    fun shouldSkipBackend(
        modelPath: String,
        backendName: String,
        validationVersion: Int,
        kvCacheCapacityTokens: Int,
        enableVisionBackend: Boolean,
        fileSize: Long,
        modifiedAt: Long,
        mtpSupported: Boolean
    ): String?

    fun recordFailure(
        modelPath: String,
        backendName: String,
        mtpEnabled: Boolean,
        validationVersion: Int,
        kvCacheCapacityTokens: Int,
        enableVisionBackend: Boolean,
        fileSize: Long,
        modifiedAt: Long,
        mtpSupported: Boolean,
        fallbackReason: String
    )
}

/**
 * Tracks MTP initialization failures to prevent repeated failed rebuilds.
 * Keyed by model fingerprint, actual backend, package capability
 * version, and engine settings. Entries are persisted through [storage].
 */
internal class MtpFailureMemory(
    private val storage: MtpFailureMemoryStorage = NoopMtpFailureMemoryStorage,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : MtpFailureMemoryPort {
    private data class FailureKey(
        val modelFingerprint: FailureModelFingerprint,
        val backendName: String,
        val mtpEnabled: Boolean,
        val kvCacheCapacityTokens: Int,
        val enableVisionBackend: Boolean
    )

    private data class FailureRecord(
        val failedAt: Long,
        val fallbackReason: String
    )

    private val failures = mutableMapOf<FailureKey, FailureRecord>()
    private val lock = Any()
    /** Last durability operation result: null=not attempted, true=success, false=failed */
    @Volatile private var lastDurabilityResult: Boolean? = null
    /** Last durability exception if any */
    @Volatile private var lastDurabilityException: Throwable? = null

    companion object {
        const val COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes
        private const val MAX_ENTRIES = 32
        private const val KEY_SEPARATOR = "\u001f"

        private fun serializeKey(key: FailureKey): String = listOf(
            key.modelFingerprint.canonicalPath,
            key.modelFingerprint.fileSize.toString(),
            key.modelFingerprint.modifiedAt.toString(),
            key.modelFingerprint.validationVersion.toString(),
            key.modelFingerprint.mtpSupported.toString(),
            key.backendName,
            key.mtpEnabled.toString(),
            key.kvCacheCapacityTokens.toString(),
            key.enableVisionBackend.toString()
        ).joinToString(KEY_SEPARATOR)

        private fun deserializeKey(serialized: String): FailureKey? {
            val parts = serialized.split(KEY_SEPARATOR)
            if (parts.size != 9) return null
            return FailureKey(
                modelFingerprint = FailureModelFingerprint(
                    canonicalPath = parts[0],
                    fileSize = parts[1].toLongOrNull() ?: return null,
                    modifiedAt = parts[2].toLongOrNull() ?: return null,
                    validationVersion = parts[3].toIntOrNull() ?: return null,
                    mtpSupported = parts[4].toBooleanStrictOrNull() ?: return null
                ),
                backendName = parts[5],
                mtpEnabled = parts[6].toBooleanStrictOrNull() ?: return null,
                kvCacheCapacityTokens = parts[7].toIntOrNull() ?: return null,
                enableVisionBackend = parts[8].toBooleanStrictOrNull() ?: return null
            )
        }
    }

    init {
        // Phase D: storage.load() may throw (e.g., disk full, I/O error,
        // corrupt SharedPreferences). Catch and record the durability
        // failure so Engine construction does not crash. In-memory state
        // starts empty in that case — cooldown cannot restore from disk
        // but new failures persist in memory within the current session.
        try {
            storage.load().forEach { (serializedKey, serializedValue) ->
                val key = deserializeKey(serializedKey) ?: return@forEach
                val parts = serializedValue.split("|", limit = 2)
                val failedAt = parts.getOrNull(0)?.toLongOrNull() ?: return@forEach
                failures[key] = FailureRecord(failedAt = failedAt, fallbackReason = parts.getOrNull(1).orEmpty())
            }
        } catch (t: Throwable) {
            lastDurabilityResult = false
            lastDurabilityException = t
            Log.w("FusionLiteRT", "MtpFailureMemory storage.load() threw — continuing with empty in-memory state", t)
        }
    }

    /**
     * Checks if an MTP attempt should be skipped due to recent failure.
     * Returns the fallback reason if MTP should be skipped, null otherwise.
     */
    override fun shouldSkipMtp(
        modelPath: String,
        backendName: String,
        mtpEnabled: Boolean,
        validationVersion: Int,
        kvCacheCapacityTokens: Int,
        enableVisionBackend: Boolean,
        fileSize: Long,
        modifiedAt: Long,
        mtpSupported: Boolean
    ): String? = synchronized(lock) {
        val key = FailureKey(
            modelFingerprint = FailureModelFingerprint(
                canonicalPath = modelPath,
                fileSize = fileSize,
                modifiedAt = modifiedAt,
                validationVersion = validationVersion,
                mtpSupported = mtpSupported
            ),
            backendName = backendName,
            mtpEnabled = mtpEnabled,
            kvCacheCapacityTokens = kvCacheCapacityTokens,
            enableVisionBackend = enableVisionBackend
        )
        val record = failures[key]
        if (record != null) {
            val elapsed = clock() - record.failedAt
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
     * Checks if a plain backend (non-MTP) attempt should be skipped due to recent failure.
     * Returns the fallback reason if the backend should be skipped, null otherwise.
     */
    override fun shouldSkipBackend(
        modelPath: String,
        backendName: String,
        validationVersion: Int,
        kvCacheCapacityTokens: Int,
        enableVisionBackend: Boolean,
        fileSize: Long,
        modifiedAt: Long,
        mtpSupported: Boolean
    ): String? = synchronized(lock) {
        val key = FailureKey(
            modelFingerprint = FailureModelFingerprint(
                canonicalPath = modelPath,
                fileSize = fileSize,
                modifiedAt = modifiedAt,
                validationVersion = validationVersion,
                mtpSupported = mtpSupported
            ),
            backendName = backendName,
            mtpEnabled = false,
            kvCacheCapacityTokens = kvCacheCapacityTokens,
            enableVisionBackend = enableVisionBackend
        )
        val record = failures[key]
        if (record != null) {
            val elapsed = clock() - record.failedAt
            if (elapsed < COOLDOWN_MS) {
                return record.fallbackReason
            } else {
                failures.remove(key)
                persist()
            }
        }
        return null
    }

    /**
     * Records an MTP failure for the given key.
     */
    override fun recordFailure(
        modelPath: String,
        backendName: String,
        mtpEnabled: Boolean,
        validationVersion: Int,
        kvCacheCapacityTokens: Int,
        enableVisionBackend: Boolean,
        fileSize: Long,
        modifiedAt: Long,
        mtpSupported: Boolean,
        fallbackReason: String
    ) = synchronized(lock) {
        val fingerprint = FailureModelFingerprint(
            canonicalPath = modelPath,
            fileSize = fileSize,
            modifiedAt = modifiedAt,
            validationVersion = validationVersion,
            mtpSupported = mtpSupported
        )
        val key = FailureKey(
            modelFingerprint = fingerprint,
            backendName = backendName,
            mtpEnabled = mtpEnabled,
            kvCacheCapacityTokens = kvCacheCapacityTokens,
            enableVisionBackend = enableVisionBackend
        )
        // Invalidate stale entries for the same model path when the
        // fingerprint changed (e.g. model replaced at same path).
        failures.keys
            .filter { it.modelFingerprint.canonicalPath == fingerprint.canonicalPath && !it.modelFingerprint.matches(fingerprint) }
            .forEach { failures.remove(it) }
        failures[key] = FailureRecord(
            failedAt = clock(),
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
        failures.keys.filter { it.modelFingerprint.canonicalPath == modelPath }.forEach { failures.remove(it) }
        persist()
    }

    /**
     * Clears all failure memory (e.g., on explicit manual retry).
     */
    fun clearAll() = synchronized(lock) {
        failures.clear()
        // Phase D: storage.clear() may throw OR return false. Both are
        // durability-failure outcomes; in-memory state is already cleared.
        try {
            val clearResult = storage.clear()
            lastDurabilityResult = clearResult
            if (!clearResult) {
                lastDurabilityException = IllegalStateException("Failed to clear failure memory storage")
            } else {
                lastDurabilityException = null
            }
        } catch (t: Throwable) {
            lastDurabilityResult = false
            lastDurabilityException = t
            Log.w("FusionLiteRT", "MtpFailureMemory storage.clear() threw — in-memory state still cleared", t)
        }
    }

    /**
     * Returns the result of the last durability operation.
     * null = no operation attempted yet
     * true = last save/clear succeeded
     * false = last save/clear failed
     */
    fun lastDurabilityResult(): Boolean? {
        return lastDurabilityResult
    }

    /**
     * Returns the exception from the last durability failure, if any.
     */
    fun lastDurabilityException(): Throwable? {
        return lastDurabilityException
    }

    /**
     * Returns true if the in-memory cooldown state is usable but the last
     * durability persistence failed, meaning the cooldown will not survive
     * process restart.
     */
    fun isDurabilityCompromised(): Boolean {
        return lastDurabilityResult == false
    }

    internal fun persistedEntryCount(): Int = synchronized(lock) {
        // Phase D: best-effort; a storage exception returns 0 so callers
        // can still verify in-memory operation in tests.
        try {
            storage.load().size
        } catch (t: Throwable) {
            0
        }
    }

    private fun persist() {
        // Phase D: storage.save() may throw (disk full, I/O error). Catch
        // and record the durability failure; in-memory state is still
        // authoritative for the current session so cooldown still works
        // for the lifetime of this Engine instance, but the app must
        // not claim it will survive restart.
        try {
            val result = storage.save(
                failures.entries.associate { (key, record) ->
                    serializeKey(key) to "${record.failedAt}|${record.fallbackReason}"
                }
            )
            val previousDurability = lastDurabilityResult?.let { 
                if (it) FailureMemoryDurability.Durable else FailureMemoryDurability.InMemoryOnly(IllegalStateException("Failure memory storage returned false")) 
            }
            
            lastDurabilityResult = result
            if (!result) {
                lastDurabilityException = IllegalStateException("Failed to save failure memory to storage")
            } else {
                lastDurabilityException = null
            }
        } catch (t: Throwable) {
            lastDurabilityResult = false
            lastDurabilityException = t
            Log.w("FusionLiteRT", "MtpFailureMemory storage.save() threw — in-memory state still usable", t)
        }
    }
}

/**
 * Phase 1: deterministic combination of stable Engine acquisition events
 * and request-local cooldown/skip events.
 */
internal fun combineFallbackEvents(
    acquisitionEvents: List<RuntimeFallbackEvent>,
    requestLocalEvents: List<RuntimeFallbackEvent>
): List<RuntimeFallbackEvent> {
    val merged = listOfNotNull(acquisitionEvents, requestLocalEvents).flatten()
    if (merged.isEmpty()) return emptyList()
    val result = mutableListOf<RuntimeFallbackEvent>()
    for (i in merged.indices) {
        val event = merged[i]
        // First two items always pass.
        if (i < 2) {
            result.add(event)
            continue
        }
        // At this point result.size >= 2.
        val prev = result[result.lastIndex]
        if (!isDuplicateFallback(prev, event)) {
            result.add(event)
            continue
        }
        val prevPrev = result[result.lastIndex - 1]
        if (!isDuplicateFallback(prevPrev, event)) {
            result.add(event)
        }
        // Otherwise this is the 3rd+ consecutive duplicate — skip
    }
    return result
}

private fun isDuplicateFallback(a: RuntimeFallbackEvent, b: RuntimeFallbackEvent): Boolean =
    a.reason == b.reason &&
        a.attemptedTextBackend == b.attemptedTextBackend &&
        a.attemptedMtpEnabled == b.attemptedMtpEnabled &&
        a.attemptedVisionBackend == b.attemptedVisionBackend
