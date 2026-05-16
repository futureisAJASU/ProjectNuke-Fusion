package com.projectnuke.fusion.model

enum class AcceleratorMode {
    AUTO,
    GPU,
    CPU
}

data class GenerationSettings(
    val maxTokens: Int = 4000,
    val topK: Int = 64,
    val topP: Float = 0.95f,
    val temperature: Float = 1.0f,
    val accelerator: AcceleratorMode = AcceleratorMode.GPU,
    val reasoningBudgetTokens: Int = 512,
    val speculativeDecodingEnabled: Boolean? = null
)
