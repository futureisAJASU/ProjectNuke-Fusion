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

    fun fit(messages: List<ChatMessage>, budget: FinalPromptBudget): List<ChatMessage> {
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

        val bounded = messages.map { boundedMessage(it) }

        if (bounded.sumOf { it.content.length + 32 } <= available) return bounded

        val mandatory = bounded.filter { it.role == "system" }.toMutableList()
        val current = bounded.lastOrNull { it.role == "user" }
        if (current != null) {
            mandatory += current
        }

        val mandatoryCost = mandatory.sumOf { it.content.length + 32 }
        if (mandatoryCost > limit) {
            val truncatedSystem = mandatory.map { msg ->
                if (msg.role == "system") msg.copy(content = msg.content.take(MAX_MESSAGE_CHARS / 4) +
                    "\n[...mandatory content truncated: request exceeds model capacity...]\n") else msg
            }
            available = (limit - reserve).coerceAtLeast(0)
            val lastUser = truncatedSystem.lastOrNull { it.role == "user" }
            return buildList {
                add(ChatMessage("system", "FUSION_TOO_LARGE: Available context is not enough for the minimum required input"))
                lastUser?.let { add(it) }
            }
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

        return buildList {
            addAll(bounded.filter { it.role == "system" })
            addAll(selected.asReversed().flatten())
            current?.let { add(it) }
        }
    }

    private fun boundedMessage(message: ChatMessage): ChatMessage {
        if (message.content.length <= MAX_MESSAGE_CHARS) return message
        return message.copy(
            content = message.content.take(MAX_MESSAGE_CHARS / 2) +
                "\n[…content truncated…]\n" + message.content.takeLast(MAX_MESSAGE_CHARS / 2)
        )
    }
}
