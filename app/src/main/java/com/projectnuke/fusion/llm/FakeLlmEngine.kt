package com.projectnuke.fusion.llm

import com.projectnuke.fusion.model.ChatMessage
import com.projectnuke.fusion.model.GenerationSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class FakeLlmEngine : LlmEngine {
    override suspend fun generate(
        messages: List<ChatMessage>,
        modelPath: String,
        settings: GenerationSettings
    ): GenerationOutcome {
        try {
            delay(500)
        } catch (e: CancellationException) {
            throw e
        }

        val lastUserMessage = messages
            .lastOrNull { it.role == "user" }
            ?.content
            ?: ""

        val text = buildString {
            appendLine("FakeLlmEngine 테스트 응답이야.")
            appendLine()
            appendLine("입력: $lastUserMessage")
            appendLine("모델 경로: $modelPath")
            appendLine("설정: max=${settings.maxTokens}, topK=${settings.topK}, topP=${settings.topP}, temp=${settings.temperature}, acc=${settings.accelerator}")
        }
        return GenerationOutcome.Success(text = text, actualBackend = "CPU")
    }

    override suspend fun generateStreaming(
        messages: List<ChatMessage>,
        modelPath: String,
        settings: GenerationSettings,
        onToken: (String) -> Unit
    ): GenerationOutcome {
        try {
            val outcome = generate(messages, modelPath, settings)
            if (outcome is GenerationOutcome.Success) {
                outcome.text.split(" ").forEach { token ->
                    onToken("$token ")
                    delay(35)
                }
            }
            return outcome
        } catch (e: CancellationException) {
            throw e
        }
    }

    override suspend fun generateMultimodalStreaming(
        messages: List<ChatMessage>,
        modelPath: String,
        settings: GenerationSettings,
        imagePaths: List<String>,
        onToken: (String) -> Unit
    ): GenerationOutcome {
        try {
            val text = buildString {
                appendLine("FakeLlmEngine image test response.")
                appendLine("Images: ${imagePaths.joinToString()}")
                appendLine("Model: $modelPath")
            }
            text.split(" ").forEach { token ->
                onToken("$token ")
                delay(35)
            }
            return GenerationOutcome.Success(text = text, actualBackend = "CPU")
        } catch (e: CancellationException) {
            throw e
        }
    }

    override fun unload() {}
}
