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

    override fun unload() {}
}