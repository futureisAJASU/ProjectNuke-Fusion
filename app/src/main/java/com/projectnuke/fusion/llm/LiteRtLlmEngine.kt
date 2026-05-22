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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext
import java.io.File

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
    ): String {
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
    ): String {
        return withContext(Dispatchers.IO) {
            val modelFile = File(modelPath)

            if (!modelFile.exists()) {
                Log.e("FusionEngine", "Selected model file does not exist: $modelPath")
                return@withContext "선택한 모델 파일을 찾을 수 없습니다. 모델을 다시 선택해 주세요."
            }

            val engine = getOrCreateEngine(
                modelPath = modelFile.absolutePath,
                settings = settings,
                enableVisionBackend = false
            )
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
                        .catch { throwable ->
                            Log.e("FusionEngine", "LiteRT-LM generation stream failed", throwable)
                            output.append("\n\n모델을 불러올 수 없습니다. 모델 설정을 확인한 뒤 다시 시도해 주세요.")
                        }
                        .collect { chunk ->
                            val token = chunk.toString()
                            output.append(token)
                            onToken(token)
                        }
                }

                promptAdapter.sanitizeOutput(output.toString()).ifBlank {
                    "모델 응답이 비어 있습니다."
                }
            } catch (e: Throwable) {
                Log.e("FusionEngine", "LiteRT-LM generation failed", e)
                "모델을 불러올 수 없습니다. 모델 설정을 확인한 뒤 다시 시도해 주세요."
            }
        }
    }

    override suspend fun generateMultimodalStreaming(
        messages: List<ChatMessage>,
        modelPath: String,
        settings: GenerationSettings,
        imagePaths: List<String>,
        onToken: (String) -> Unit
    ): String {
        return withContext(Dispatchers.IO) {
            val modelFile = File(modelPath)

            if (!modelFile.exists()) {
                Log.e("FusionEngine", "Selected model file does not exist for multimodal request: $modelPath")
                return@withContext "선택한 모델 파일을 찾을 수 없습니다. 모델을 다시 선택해 주세요."
            }

            val missingImage = imagePaths.firstOrNull { !File(it).exists() }
            if (missingImage != null) {
                return@withContext "이미지 입력 처리 실패: 이미지 파일을 찾을 수 없습니다.\n$missingImage"
            }

            try {
                val engine = getOrCreateEngine(
                    modelPath = modelFile.absolutePath,
                    settings = settings,
                    enableVisionBackend = true
                )
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

                engine.createConversation(conversationConfig).use { conversation ->
                    conversation
                        .sendMessageAsync(Contents.of(contentParts))
                        .catch { throwable ->
                            Log.e("FusionEngine", "LiteRT-LM multimodal generation stream failed", throwable)
                            output.append("\n\n이미지 입력 처리 실패: 모델 설정을 확인한 뒤 다시 시도해 주세요.")
                        }
                        .collect { chunk ->
                            val token = chunk.toString()
                            output.append(token)
                            onToken(token)
                        }
                }

                promptAdapter.sanitizeOutput(output.toString()).ifBlank {
                    "이미지 입력 처리 실패: 모델 응답이 비어 있습니다."
                }
            } catch (e: Throwable) {
                Log.e("FusionEngine", "LiteRT-LM multimodal generation failed", e)
                "이미지 입력 처리 실패: 모델 설정을 확인한 뒤 다시 시도해 주세요."
            }
        }
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
            Log.i("FusionLiteRT", "Model path: $modelPath")
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
        Log.i("FusionLiteRT", "Model path: $modelPath")
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
            appendLine("너는 Fusion이라는 온디바이스 AI 어시스턴트야.")
            appendLine("답변은 한국어로 자연스럽게 하고, 모르면 모른다고 말해.")
            appendLine("추론이나 추정은 추론이라고 명시해.")
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
                appendLine("modelPath=$modelPath")
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
