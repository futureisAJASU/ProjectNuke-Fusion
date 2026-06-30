package com.projectnuke.fusion.ai.model

enum class AiProviderType {
    OPENAI,
    NVIDIA_NIM,
    CUSTOM_OPENAI_COMPATIBLE
}

data class AiProviderConfig(
    val id: String,
    val type: AiProviderType,
    val displayName: String,
    val baseUrl: String,
    val modelId: String,
    val apiKeySecretId: String?,
    val isEnabled: Boolean = true,
    val temperature: Double = 0.7,
    val maxTokens: Int? = null
)
