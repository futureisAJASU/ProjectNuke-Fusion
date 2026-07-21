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
import com.projectnuke.fusion.model.GenerationSettings
import com.projectnuke.fusion.modelzoo.FusionPromptAdapters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

@OptIn(ExperimentalApi::class)
class LiteRtLlmEngine(
    private val context: Context
) : LlmEngine {

    private var engine: Engine? = null
    private var loadedKey: String? = null
    @Volatile
    var lastMtpStatus: MtpRuntimeStatus = MtpRuntimeStatus.OFF
        private set

    override suspend fun generate(
        messages: List<ChatMessage>,
        modelPath: String,
        settings: GenerationSettings
    ): GenerationOutcome {
        return generateStreaming(
            messages = messages,
            modelPath = modelPath,
            settings = settings,
            onToken = {}
        )
    }

    override suspend fun generateStreaming(
        messages: List<ChatMessage>,
        modelPath: String,
        settings: GenerationSettings,
        onToken: (String) -> Unit
    ): GenerationOutcome {
        return withContext(Dispatchers.IO) {
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                Log.e("FusionEngine", "Selected model file does not exist: $modelPath")
                return@withContext GenerationOutcome.Failure(
                    kind = FailureKind.MODEL_NOT_FOUND,
                    message = "선택한 모델 파일을 찾을 수 없습니다. 모델을 다시 선택해 주세요."
                )
            }

            val engine = try {
                getOrCreateEngine(
                    modelPath = modelFile.absolutePath,
                    settings = settings,
                    enableVisionBackend = false
                )
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
                modelPath = modelFile.absolutePath,
                settings = settings,
                enableVisionBackend = false
            )

            val promptAdapter = FusionPromptAdapters.inferFromMessages(messages)
            val adaptedMessages = promptAdapter.buildMessages(messages)
            val systemText = buildSystemInstruction(adaptedMessages, settings)
            val promptText = buildPrompt(adaptedMessages)

            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(systemText),
                samplerConfig = SamplerConfig(
                    topK = settings.topK.coerceAtLeast(1),
                    topP = settings.topP.coerceIn(0f, 1f).toDouble(),
                    temperature = settings.temperature.coerceAtLeast(0f).toDouble()
                )
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
                GenerationOutcome.Success(text = sanitized, actualBackend = null)
            }
        }
    }

    override suspend fun generateMultimodalStreaming(
        messages: List<ChatMessage>,
        modelPath: String,
        settings: GenerationSettings,
        imagePaths: List<String>,
        onToken: (String) -> Unit
    ): GenerationOutcome {
        return withContext(Dispatchers.IO) {
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                Log.e("FusionEngine", "Selected model file does not exist for multimodal request: $modelPath")
                return@withContext GenerationOutcome.Failure(
                    kind = FailureKind.MODEL_NOT_FOUND,
                    message = "선택한 모델 파일을 찾을 수 없습니다. 모델을 다시 선택해 주세요."
                )
            }

            val missingImage = imagePaths.firstOrNull { !File(it).exists() }
            if (missingImage != null) {
                return@withContext GenerationOutcome.Failure(
                    kind = FailureKind.IMAGE_NOT_FOUND,
                    message = "이미지 입력 처리 실패: 이미지 파일을 찾을 수 없습니다."
                )
            }

            val engine = try {
                getOrCreateEngine(
                    modelPath = modelFile.absolutePath,
                    settings = settings,
                    enableVisionBackend = true
                )
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
                modelPath = modelFile.absolutePath,
                settings = settings,
                enableVisionBackend = true
            )

            val promptAdapter = FusionPromptAdapters.inferFromMessages(messages)
            val adaptedMessages = promptAdapter.buildMessages(messages)
            val systemText = buildSystemInstruction(adaptedMessages, settings)
            val promptText = buildPrompt(adaptedMessages)

            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(systemText),
                samplerConfig = SamplerConfig(
                    topK = settings.topK.coerceAtLeast(1),
                    topP = settings.topP.coerceIn(0f, 1f).toDouble(),
                    temperature = settings.temperature.coerceAtLeast(0f).toDouble()
                )
            )

            val contentParts = buildList<Content> {
                imagePaths.forEach { imagePath ->
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
                GenerationOutcome.Success(text = sanitized, actualBackend = null)
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

    private fun getOrCreateEngine(
        modelPath: String,
        settings: GenerationSettings,
        enableVisionBackend: Boolean
    ): Engine {
        val mtpRequested = settings.speculativeDecodingEnabled == true
        val mtpSupported = isSpeculativeDecodingSupportedModel(modelPath)
        val mtpEnabledForRuntime = mtpRequested && mtpSupported
        val backendName = when (settings.accelerator) {
            AcceleratorMode.CPU -> "CPU"
            AcceleratorMode.GPU,
            AcceleratorMode.AUTO -> "GPU"
        }
        val key = buildString {
            append(modelPath)
            append("|")
            append(settings.accelerator.name)
            append("|")
            append(settings.maxTokens)
            append("|")
            append(mtpEnabledForRuntime)
            append("|vision=")
            append(enableVisionBackend)
        }

        val currentEngine = engine
        if (currentEngine != null && loadedKey == key) {
            if (mtpRequested && !mtpSupported) {
                lastMtpStatus = MtpRuntimeStatus.UNSUPPORTED
            } else if (!mtpRequested) {
                lastMtpStatus = MtpRuntimeStatus.OFF
            }
            Log.i("FusionLiteRT", "MTP requested: $mtpRequested (cached engine reused)")
            Log.i("FusionLiteRT", "MTP status: $lastMtpStatus")
            Log.i("FusionLiteRT", "Backend: $backendName")
            Log.i("FusionLiteRT", "Vision backend requested: $enableVisionBackend")
            Log.i("FusionLiteRT", "Model path: ${File(modelPath).name}")
            return currentEngine
        }

        unload()

        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
        val backend = when (settings.accelerator) {
            AcceleratorMode.CPU -> Backend.CPU()
            AcceleratorMode.GPU -> Backend.GPU()
            AcceleratorMode.AUTO -> Backend.GPU()
        }

        Log.i("FusionLiteRT", "MTP requested: $mtpRequested")
        Log.i("FusionLiteRT", "Backend: $backendName")
        Log.i("FusionLiteRT", "Vision backend requested: $enableVisionBackend")
        Log.i("FusionLiteRT", "Model path: ${File(modelPath).name}")
        if (mtpRequested && !mtpSupported) {
            lastMtpStatus = MtpRuntimeStatus.UNSUPPORTED
            Log.i("FusionLiteRT", "MTP unsupported model/runtime")
        } else {
            lastMtpStatus = if (mtpEnabledForRuntime) MtpRuntimeStatus.REQUESTED else MtpRuntimeStatus.OFF
        }
        val mtpFlagApplied = configureSpeculativeDecodingFlag(mtpEnabledForRuntime)
        if (mtpEnabledForRuntime && !mtpFlagApplied) {
            lastMtpStatus = MtpRuntimeStatus.FAILED
        }
        Log.i(
            "FusionLiteRT",
            if (mtpEnabledForRuntime && mtpFlagApplied) {
                "MTP enabled before engine init"
            } else {
                "MTP disabled before engine init"
            }
        )

        val newEngine = createEngine(
            modelPath = modelPath,
            backend = backend,
            visionBackend = if (enableVisionBackend) backend else null,
            maxNumTokens = settings.maxTokens.coerceAtLeast(1)
        ).getOrElse { throwable ->
            if (mtpEnabledForRuntime && mtpFlagApplied) {
                Log.w("FusionLiteRT", "MTP engine initialization failed, retrying without MTP", throwable)
                lastMtpStatus = MtpRuntimeStatus.FAILED
                configureSpeculativeDecodingFlag(false)
                createEngine(
                    modelPath = modelPath,
                    backend = backend,
                    visionBackend = if (enableVisionBackend) backend else null,
                    maxNumTokens = settings.maxTokens.coerceAtLeast(1)
                ).getOrElse { retryThrowable ->
                    if (enableVisionBackend && backendName == "GPU") {
                        Log.w(
                            "FusionLiteRT",
                            "GPU vision backend failed after MTP fallback, retrying CPU vision backend",
                            retryThrowable
                        )
                        createEngine(
                            modelPath = modelPath,
                            backend = backend,
                            visionBackend = Backend.CPU(),
                            maxNumTokens = settings.maxTokens.coerceAtLeast(1)
                        ).getOrThrow()
                    } else {
                        throw retryThrowable
                    }
                }
            } else if (enableVisionBackend && backendName == "GPU") {
                Log.w(
                    "FusionLiteRT",
                    "GPU vision backend failed, retrying CPU vision backend",
                    throwable
                )
                createEngine(
                    modelPath = modelPath,
                    backend = backend,
                    visionBackend = Backend.CPU(),
                    maxNumTokens = settings.maxTokens.coerceAtLeast(1)
                ).getOrThrow()
            } else {
                throw throwable
            }
        }

        if (mtpEnabledForRuntime && mtpFlagApplied) {
            lastMtpStatus = MtpRuntimeStatus.APPLIED
        }
        engine = newEngine
        loadedKey = key

        return newEngine
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

            Engine(config).also { engine ->
                engine.initialize()
            }
        }
    }

    private fun buildSystemInstruction(
        messages: List<ChatMessage>,
        settings: GenerationSettings
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
            appendLine("maxTokens=${settings.maxTokens}")
            appendLine("topK=${settings.topK}")
            appendLine("topP=${settings.topP}")
            appendLine("temperature=${settings.temperature}")
            appendLine("accelerator=${settings.accelerator.name}")
            appendLine("speculativeDecoding=${settings.speculativeDecodingEnabled == true}")
            appendLine("reasoningBudgetTokens=${settings.reasoningBudgetTokens} (prompt-only; LiteRT-LM API does not expose a reasoning budget config here)")

            if (systemMessages.isNotBlank()) {
                appendLine()
                appendLine(systemMessages)
            }
        }
    }

    private fun logGenerationSettings(
        modelPath: String,
        settings: GenerationSettings,
        enableVisionBackend: Boolean
    ) {
        Log.i(
            "FusionLiteRT",
            buildString {
                appendLine("Generation settings before request")
                appendLine("modelPath=${File(modelPath).name}")
                appendLine("accelerator=${settings.accelerator.name} (runtime EngineConfig.backend)")
                appendLine("maxTokens=${settings.maxTokens} (runtime EngineConfig.maxNumTokens)")
                appendLine("topK=${settings.topK} (runtime SamplerConfig.topK)")
                appendLine("topP=${settings.topP} (runtime SamplerConfig.topP)")
                appendLine("temperature=${settings.temperature} (runtime SamplerConfig.temperature)")
                appendLine("reasoningBudgetTokens=${settings.reasoningBudgetTokens} (prompt-only unsupported by current LiteRT-LM API)")
                appendLine("MTP requested=${settings.speculativeDecodingEnabled == true} (runtime ExperimentalFlags.enableSpeculativeDecoding)")
                appendLine("visionBackend=$enableVisionBackend (runtime EngineConfig.visionBackend when true)")
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
        runCatching {
            configureSpeculativeDecodingFlag(false)
        }
    }

    private fun isSpeculativeDecodingSupportedModel(modelPath: String): Boolean {
        val lower = modelPath.lowercase()
        return "gemma-4" in lower || "gemma4" in lower
    }

    private fun configureSpeculativeDecodingFlag(enabled: Boolean): Boolean {
        return runCatching {
            ExperimentalFlags.enableSpeculativeDecoding = enabled
        }.onFailure { throwable ->
            Log.w(
                "FusionLiteRT",
                "Failed to ${if (enabled) "enable" else "disable"} MTP speculative decoding flag",
                throwable
            )
        }.isSuccess
    }
}

enum class MtpRuntimeStatus {
    OFF,
    REQUESTED,
    APPLIED,
    UNSUPPORTED,
    FAILED
}
