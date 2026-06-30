package com.projectnuke.fusion.ai.model

enum class AiRole {
    SYSTEM,
    USER,
    ASSISTANT
}

data class AiMessage(
    val role: AiRole,
    val content: String
)
