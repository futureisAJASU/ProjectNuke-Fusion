package com.projectnuke.fusion.chat

import com.projectnuke.fusion.model.ChatMessage

internal data class PromptHistoryBudgetRequest(
    val history: List<ChatMessage>,
    val modelId: String?,
    val generationModeKey: String,
    val maxOutputTokens: Int,
    val currentRequestChars: Int,
    val attachmentCount: Int,
    val webSearchPlanned: Boolean,
    val summaryText: String? = null,
)

internal data class PromptHistorySelection(
    val messages: List<ChatMessage>,
    val omittedTurnCount: Int,
    val usedSummary: Boolean,
)

internal object PromptHistoryBudgeter {
    private const val MAX_MESSAGE_CHARS = 8_000
    private const val MAX_SUMMARY_CHARS = 1_500
    private const val MESSAGE_OVERHEAD_CHARS = 32

    fun select(request: PromptHistoryBudgetRequest): PromptHistorySelection {
        val contextChars = contextBudgetChars(request.modelId, request.generationModeKey)
        val reserved = request.currentRequestChars.coerceAtLeast(0) +
            request.maxOutputTokens.coerceAtLeast(0) * 4 +
            request.attachmentCount.coerceAtLeast(0) * 2_000 +
            if (request.webSearchPlanned) 6_000 else 0
        // Mandatory content may consume the entire context. Never invent history
        // capacity that would exceed the selected provider/model budget.
        var remaining = (contextChars - reserved).coerceAtLeast(0)

        val systemMessages = request.history
            .filter { it.role == "system" }
            .map(::boundedMessage)
        systemMessages.forEach { remaining -= cost(it) }

        val turns = coherentTurns(request.history.filter { it.role != "system" })
        val selectedReversed = mutableListOf<List<ChatMessage>>()
        var omitted = 0
        val newestFirst = turns.asReversed()
        for ((index, turn) in newestFirst.withIndex()) {
            val boundedTurn = turn.map(::boundedMessage)
            val turnCost = boundedTurn.sumOf(::cost)
            if (turnCost <= remaining) {
                selectedReversed += boundedTurn
                remaining -= turnCost
            } else {
                omitted += newestFirst.size - index
                break
            }
        }

        val selected = selectedReversed.asReversed().flatten()
        val summary = request.summaryText
            ?.trim()
            ?.take(MAX_SUMMARY_CHARS)
            ?.takeIf { it.isNotBlank() && omitted > 0 }
            ?.let { ChatMessage(role = "system", content = "OLDER_CONVERSATION_SUMMARY\n$it") }
            ?.takeIf { cost(it) <= remaining }

        return PromptHistorySelection(
            messages = buildList {
                addAll(systemMessages)
                if (summary != null) add(summary)
                addAll(selected)
            },
            omittedTurnCount = omitted,
            usedSummary = summary != null,
        )
    }

    private fun contextBudgetChars(modelId: String?, generationModeKey: String): Int {
        if (generationModeKey.contains("EXTERNAL", ignoreCase = true)) return 48_000
        val normalized = modelId.orEmpty().lowercase()
        return when {
            "e4b" in normalized || "4b" in normalized -> 24_000
            "e2b" in normalized || "2b" in normalized -> 16_000
            else -> 20_000
        }
    }

    private fun coherentTurns(messages: List<ChatMessage>): List<List<ChatMessage>> {
        val turns = mutableListOf<MutableList<ChatMessage>>()
        messages.forEach { message ->
            when (message.role) {
                "user" -> turns += mutableListOf(message)
                "assistant" -> {
                    val current = turns.lastOrNull()
                    if (current != null && current.firstOrNull()?.role == "user" &&
                        current.none { it.role == "assistant" }
                    ) {
                        current += message
                    }
                    // Orphan and duplicate assistant branches are omitted. Branch selection
                    // must happen before this budget boundary.
                }
            }
        }
        return turns
    }

    private fun boundedMessage(message: ChatMessage): ChatMessage {
        if (message.content.length <= MAX_MESSAGE_CHARS) return message
        val edge = (MAX_MESSAGE_CHARS - 32) / 2
        return message.copy(
            content = message.content.take(edge) +
                "\n[...oversized message truncated...]\n" +
                message.content.takeLast(edge)
        )
    }

    private fun cost(message: ChatMessage): Int = message.content.length + MESSAGE_OVERHEAD_CHARS
}
