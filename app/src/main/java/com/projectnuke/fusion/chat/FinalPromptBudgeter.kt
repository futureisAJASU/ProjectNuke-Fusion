package com.projectnuke.fusion.chat

import com.projectnuke.fusion.model.ChatMessage

internal data class FinalPromptBudget(
    val modelId: String?,
    val external: Boolean,
    val maxOutputTokens: Int,
    val externaModelContextChars: Int? = null,
)

internal object FinalPromptBudgeter {
    private const val MAX_MESSAGE_CHARS = 8_000

    fun fit(messages: List<ChatMessage>, budget: FinalPromptBudget): FittedMessages {
        val limit = if (budget.externaModelContextChars != null && budget.externaModelContextChars > 0) {
            budget.externaModelContextChars
        } else if (budget.external) {
            48_000
        } else when {
            budget.modelId.orEmpty().contains("4b", true) -> 24_000
            budget.modelId.orEmpty().contains("2b", true) -> 16_000
            else -> 20_000
        }
        val reserve = budget.maxOutputTokens.coerceAtLeast(0) * 4
        var available = (limit - reserve).coerceAtLeast(0)

        val bounded = messages.map { it.bounded() }

        if (bounded.sumOf { it.content.length + 32 } <= available) return FittedMessages(bounded, false)

        val mandatory = bounded.filter { it.role == "system" }.toMutableList()
        val current = bounded.lastOrNull { it.role == "user" }
        if (current != null) {
            mandatory += current
        }

        val mandatoryCost = mandatory.sumOf { it.content.length + 32 }
        if (mandatoryCost > limit) {
            available = (limit - reserve).coerceAtLeast(0)
            val lastUser = mandatory.lastOrNull { it.role == "user" }
            return FittedMessages(
                buildList {
                    add(ChatMessage("system", "Budget exceeded"))
                    lastUser?.let { add(it) }
                },
                true,
            )
        }

        available -= mandatoryCost

        val nonMandatory = bounded.filter { it.role != "system" && it !== current }
        val turns = nonMandatory.chunked(2).asReversed()
        val selected = mutableListOf<List<ChatMessage>>()

        for (turn in turns) {
            val cost = turn.sumOf { it.content.length + 32 }
            if (cost > available) break
            selected += turn
            available -= cost
        }

        return FittedMessages(
            buildList {
            addAll(bounded.filter { it.role == "system" })
            addAll(selected.asReversed().flatten())
            current?.let { add(it) }
            },
            false,
        )
    }

    fun computeLimit(budget: FinalPromptBudget): Int = if (budget.externaModelContextChars != null && budget.externaModelContextChars > 0) {
            budget.externaModelContextChars
        } else if (budget.external) {
            48_000
        } else when {
            budget.modelId.orEmpty().contains("4b", true) -> 24_000
            budget.modelId.orEmpty().contains("2b", true) -> 16_000
            else -> 20_000
        }

private fun ChatMessage.bounded(): ChatMessage {
        if (content.length <= MAX_MESSAGE_CHARS) return this
        return copy(
            content = content.take(MAX_MESSAGE_CHARS / 2) +
                "\n[\u2026content truncated\u2026]\n" + content.takeLast(MAX_MESSAGE_CHARS / 2)
        )
    }
}
