package com.projectnuke.fusion.llm

import com.projectnuke.fusion.model.ChatMessage
import com.projectnuke.fusion.model.GenerationSettings
import kotlinx.coroutines.delay

class FakeLlmEngine : LlmEngine {
    override suspend fun generate(
        messages: List<ChatMessage>,
        modelPath: String,
        settings: GenerationSettings
    ): String {
        delay(500)

        val lastUserMessage = messages
            .lastOrNull { it.role == "user" }
            ?.content
            ?: ""

        return buildString {
            appendLine("FakeLlmEngine 테스트 응답이야.")
            appendLine()
            appendLine("입력: $lastUserMessage")
            appendLine("모델 경로: $modelPath")
            appendLine("설정: max=${settings.maxTokens}, topK=${settings.topK}, topP=${settings.topP}, temp=${settings.temperature}, acc=${settings.accelerator}")
        }
    }

    override suspend fun generateStreaming(
        messages: List<ChatMessage>,
        modelPath: String,
        settings: GenerationSettings,
        onToken: (String) -> Unit
    ): String {
        val output = generate(messages, modelPath, settings)
        output.split(" ").forEach { token ->
            onToken("$token ")
            delay(35)
        }
        return output
    }

    override suspend fun generateMultimodalStreaming(
        messages: List<ChatMessage>,
        modelPath: String,
        settings: GenerationSettings,
        imagePaths: List<String>,
        onToken: (String) -> Unit
    ): String {
        val output = buildString {
            appendLine("FakeLlmEngine image test response.")
            appendLine("Images: ${imagePaths.joinToString()}")
            appendLine("Model: $modelPath")
        }
        output.split(" ").forEach { token ->
            onToken("$token ")
            delay(35)
        }
        return output
    }

    override fun unload() {}
}
