package com.projectnuke.fusion.llm

import com.projectnuke.fusion.model.ChatMessage
import com.projectnuke.fusion.model.GenerationSettings

interface LlmEngine {
    suspend fun generate(
        messages: List<ChatMessage>,
        modelPath: String,
        settings: GenerationSettings
    ): GenerationOutcome

    suspend fun generateStreaming(
        messages: List<ChatMessage>,
        modelPath: String,
        settings: GenerationSettings,
        onToken: (String) -> Unit
    ): GenerationOutcome {
        val output = generate(
            messages = messages,
            modelPath = modelPath,
            settings = settings
        )
        if (output is GenerationOutcome.Success) {
            onToken(output.text)
        }
        return output
    }

    suspend fun generateMultimodalStreaming(
        messages: List<ChatMessage>,
        modelPath: String,
        settings: GenerationSettings,
        imagePaths: List<String>,
        onToken: (String) -> Unit
    ): GenerationOutcome {
        val outcome = generate(
            messages = messages,
            modelPath = modelPath,
            settings = settings
        )
        if (outcome is GenerationOutcome.Success) {
            onToken(outcome.text)
        }
        return outcome
    }

    fun unload()
}
