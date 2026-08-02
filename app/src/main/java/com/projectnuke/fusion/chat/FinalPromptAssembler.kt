package com.projectnuke.fusion.chat

import com.projectnuke.fusion.model.ChatMessage

internal data class FinalPromptRequest(
    val messages: List<ChatMessage>,
    val budget: FinalPromptBudget,
)

internal sealed interface PromptAssemblyResult {
    data class Ready(
        val messages: List<ChatMessage>,
        val inputCost: Int,
        val configuredLimit: Int,
        val contextsRemoved: Int = 0,
    ) : PromptAssemblyResult

    data object TooLarge : PromptAssemblyResult
}

internal object FinalPromptAssembler {
    fun assemble(request: FinalPromptRequest): PromptAssemblyResult {
        val fitted = FinalPromptBudgeter.fit(request.messages, request.budget)
        if (fitted.isTooLarge) return PromptAssemblyResult.TooLarge
        val limit = FinalPromptBudgeter.computeLimit(request.budget)
        val cost = fitted.messages.sumOf { it.content.length + 32 }
        return PromptAssemblyResult.Ready(
            messages = fitted.messages,
            inputCost = cost,
            configuredLimit = limit,
            contextsRemoved = request.messages.size - fitted.messages.map { it.content }.size,
        )
    }

    fun isTooLarge(result: PromptAssemblyResult): Boolean =
        result is PromptAssemblyResult.TooLarge

    fun readyOrThrow(result: PromptAssemblyResult): List<ChatMessage> =
        (result as? PromptAssemblyResult.Ready)?.messages
            ?: throw IllegalStateException("Prompt assembly failed: result is not Ready")
}

internal data class FittedMessages(
    val messages: List<ChatMessage>,
    val isTooLarge: Boolean,
)