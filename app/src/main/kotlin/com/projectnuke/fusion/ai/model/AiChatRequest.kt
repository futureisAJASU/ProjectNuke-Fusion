package com.projectnuke.fusion.ai.model

data class AiChatRequest(
    val messages: List<AiMessage>,
    val temperature: Double? = null,
    val maxTokens: Int? = null
)

data class AiChatResponse(
    val id: String?,
    val model: String?,
    val content: String
)
