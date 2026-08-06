package com.projectnuke.fusion.chat

import com.projectnuke.fusion.model.ChatMessage

/**
 * Estimates prompt-side token count from a fitted message list.
 *
 * Used by the per-turn `toConversationOptions(estimatedPromptTokens)` path
 * so [com.projectnuke.fusion.model.KvCacheCapacityPolicy.validateOutputBudget]
 * is invoked with real fitted-prompt information rather than the
 * settings-time "prompt ≈ output" heuristic that previously clamped
 * `maxOutputToken` to ~96 tokens at default settings.
 *
 * Cost model matches [FinalPromptBudgeter] / [PromptHistoryBudgeter]:
 *   - `MESSAGE_OVERHEAD_CHARS = 32` per message (role, formatting, special tokens)
 *   - `4 chars per token` heuristic for the content itself.
 */
internal object PromptTokenEstimator {

    private const val MESSAGE_OVERHEAD_CHARS = 32

    /** Estimate tokens for a fitted-prompt message list. Never returns negative. */
    fun estimateTokens(messages: List<ChatMessage>): Int {
        if (messages.isEmpty()) return 0
        var chars = 0
        for (m in messages) {
            chars += m.content.length.coerceAtLeast(0) + MESSAGE_OVERHEAD_CHARS
        }
        return (chars / 4).coerceAtLeast(0)
    }

    /** Estimate tokens for a single user-prompt string (no role overhead). */
    fun estimateTokensForString(content: String): Int =
        ((content.length.coerceAtLeast(0)) / 4).coerceAtLeast(0)
}
