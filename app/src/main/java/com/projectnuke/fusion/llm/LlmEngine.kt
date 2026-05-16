package com.projectnuke.fusion.llm

import com.projectnuke.fusion.model.ChatMessage
import com.projectnuke.fusion.model.GenerationSettings

interface LlmEngine {
    suspend fun generate(
        messages: List<ChatMessage>,
        modelPath: String,
        settings: GenerationSettings
    ): String

    suspend fun generateStreaming(
        messages: List<ChatMessage>,
        modelPath: String,
        settings: GenerationSettings,
        onToken: (String) -> Unit
    ): String {
        val output = generate(
            messages = messages,
            modelPath = modelPath,
            settings = settings
        )
        onToken(output)
        return output
    }

    suspend fun generateMultimodalStreaming(
        messages: List<ChatMessage>,
        modelPath: String,
        settings: GenerationSettings,
        imagePaths: List<String>,
        onToken: (String) -> Unit
    ): String {
        return "이미지 입력 처리 실패: 현재 LiteRT-LM 엔진이 이미지 입력을 지원하지 않습니다."
    }

    fun unload()
}
