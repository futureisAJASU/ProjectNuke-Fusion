package com.projectnuke.fusion.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.SamplerConfig
import com.projectnuke.fusion.model.AcceleratorMode
import com.projectnuke.fusion.model.ChatMessage
import com.projectnuke.fusion.model.GenerationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext
import java.io.File

class LiteRtLlmEngine(
    private val context: Context
) : LlmEngine {

    private var engine: Engine? = null
    private var loadedKey: String? = null

    override suspend fun generate(
        messages: List<ChatMessage>,
        modelPath: String,
        settings: GenerationSettings
    ): String {
        return withContext(Dispatchers.IO) {
            val modelFile = File(modelPath)

            if (!modelFile.exists()) {
                return@withContext "모델 파일을 찾을 수 없어:\n$modelPath"
            }

            val engine = getOrCreateEngine(
                modelPath = modelFile.absolutePath,
                settings = settings
            )

            val systemText = buildSystemInstruction(messages, settings)
            val promptText = buildPrompt(messages)

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
                            output.append(
                                "\n\nLiteRT-LM 생성 중 오류:\n${throwable.message ?: throwable::class.java.simpleName}"
                            )
                        }
                        .collect { chunk ->
                            output.append(chunk.toString())
                        }
                }

                output.toString().ifBlank {
                    "모델 응답이 비어 있어."
                }
            } catch (e: Throwable) {
                "LiteRT-LM 실행 실패:\n${e.message ?: e::class.java.simpleName}"
            }
        }
    }

    private fun getOrCreateEngine(
        modelPath: String,
        settings: GenerationSettings
    ): Engine {
        val key = buildString {
            append(modelPath)
            append("|")
            append(settings.accelerator.name)
            append("|")
            append(settings.maxTokens)
        }

        val currentEngine = engine
        if (currentEngine != null && loadedKey == key) {
            return currentEngine
        }

        unload()

        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)

        val backend = when (settings.accelerator) {
            AcceleratorMode.CPU -> Backend.CPU()
            AcceleratorMode.GPU -> Backend.GPU()
            AcceleratorMode.AUTO -> Backend.GPU()
        }

        val config = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            maxNumTokens = settings.maxTokens.coerceAtLeast(512),
            cacheDir = context.cacheDir.absolutePath
        )

        val newEngine = Engine(config)
        newEngine.initialize()

        engine = newEngine
        loadedKey = key

        return newEngine
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

            if (systemMessages.isNotBlank()) {
                appendLine()
                appendLine(systemMessages)
            }
        }
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
        } catch (_: Throwable) {
        }

        engine = null
        loadedKey = null
    }
}