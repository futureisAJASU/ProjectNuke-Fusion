package com.projectnuke.fusion.ai

import com.projectnuke.fusion.ai.model.AiMessage
import com.projectnuke.fusion.ai.model.AiRole
import com.projectnuke.fusion.model.ChatMessage
import java.util.Locale

internal fun buildExternalAiMessages(
    history: List<ChatMessage>,
    userInput: String,
    stripAttachments: (String) -> String
): List<AiMessage> {
    return buildList {
        history.forEach { message ->
            val role = message.role.toAiRoleOrNull() ?: return@forEach
            val content = stripAttachments(message.content).trim()
            if (content.isNotBlank()) {
                add(AiMessage(role = role, content = content))
            }
        }
        if (userInput.isNotBlank()) {
            add(AiMessage(role = AiRole.USER, content = userInput))
        }
    }
}

internal fun buildExternalAiMessages(
    messages: List<ChatMessage>,
    stripAttachments: (String) -> String
): List<AiMessage> {
    return buildList {
        messages.forEach { message ->
            val role = message.role.toAiRoleOrNull() ?: return@forEach
            val content = stripAttachments(message.content).trim()
            if (content.isNotBlank()) {
                add(AiMessage(role = role, content = content))
            }
        }
    }
}

private fun String.toAiRoleOrNull(): AiRole? {
    return when (lowercase(Locale.US)) {
        "system" -> AiRole.SYSTEM
        "user" -> AiRole.USER
        "assistant" -> AiRole.ASSISTANT
        else -> null
    }
}
