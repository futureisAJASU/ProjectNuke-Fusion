package com.projectnuke.fusion.chat

import com.projectnuke.fusion.model.ChatMessage

internal data class FinalPromptBudget(
    val modelId: String?,
    val external: Boolean,
    val maxOutputTokens: Int,
)

internal object FinalPromptBudgeter {
    private const val MAX_MESSAGE_CHARS = 8_000

    fun fit(messages: List<ChatMessage>, budget: FinalPromptBudget): List<ChatMessage> {
        val limit = if (budget.external) 48_000 else when {
            budget.modelId.orEmpty().contains("4b", true) -> 24_000
            budget.modelId.orEmpty().contains("2b", true) -> 16_000
            else -> 20_000
        }
        val reserve = budget.maxOutputTokens.coerceAtLeast(0) * 4
        val available = (limit - reserve).coerceAtLeast(0)
        val bounded = messages.map { message ->
            if (message.content.length <= MAX_MESSAGE_CHARS) message
            else message.copy(content = message.content.take(MAX_MESSAGE_CHARS / 2) +
                "\n[…content truncated…]\n" + message.content.takeLast(MAX_MESSAGE_CHARS / 2))
        }
        if (bounded.sumOf { it.content.length + 32 } <= available) return bounded
        val mandatory = bounded.filter { it.role == "system" }.toMutableList()
        val current = bounded.lastOrNull { it.role == "user" }
        if (current != null) mandatory += current
        var remaining = (available - mandatory.sumOf { it.content.length + 32 }).coerceAtLeast(0)
        val turns = bounded.filter { it.role != "system" && it !== current }
            .chunked(2).asReversed()
        val selected = mutableListOf<List<ChatMessage>>()
        for (turn in turns) {
            val cost = turn.sumOf { it.content.length + 32 }
            if (cost > remaining) break
            selected += turn
            remaining -= cost
        }
        return buildList {
            addAll(bounded.filter { it.role == "system" })
            addAll(selected.asReversed().flatten())
            current?.let { add(it) }
        }
    }
}
