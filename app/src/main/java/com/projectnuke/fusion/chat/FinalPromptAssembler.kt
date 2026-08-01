package com.projectnuke.fusion.chat

import com.projectnuke.fusion.model.ChatMessage

internal data class FinalPromptRequest(
    val messages: List<ChatMessage>,
    val budget: FinalPromptBudget,
)

/** Single typed boundary used by every caller before model or provider invocation. */
internal object FinalPromptAssembler {
    fun assemble(request: FinalPromptRequest): List<ChatMessage> =
        FinalPromptBudgeter.fit(request.messages, request.budget)

    fun isTooLarge(prompt: List<ChatMessage>): Boolean =
        prompt.firstOrNull()?.content?.startsWith("FUSION_TOO_LARGE:") == true
}
