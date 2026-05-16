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

    fun unload()
}
