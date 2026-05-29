package com.projectnuke.fusion.ui

import android.content.Context

data class ConversationSummaryMemory(
    val conversationId: Long,
    val summary: String,
    val updatedAt: Long
)

private const val ConversationSummaryPrefs = "fusion_conversation_summaries"
private const val MaxSummaryContextChars = 1500

fun loadConversationSummary(
    context: Context,
    conversationId: Long
): ConversationSummaryMemory? {
    if (conversationId <= 0L) return null
    val prefs = context.getSharedPreferences(ConversationSummaryPrefs, Context.MODE_PRIVATE)
    val summary = prefs.getString(summaryKey(conversationId), null)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return ConversationSummaryMemory(
        conversationId = conversationId,
        summary = summary,
        updatedAt = prefs.getLong(updatedAtKey(conversationId), 0L)
    )
}

fun saveConversationSummary(
    context: Context,
    conversationId: Long,
    summary: String
): ConversationSummaryMemory? {
    val cleanSummary = summary.trim()
    if (conversationId <= 0L || cleanSummary.isBlank()) return null
    val updatedAt = System.currentTimeMillis()
    context.getSharedPreferences(ConversationSummaryPrefs, Context.MODE_PRIVATE)
        .edit()
        .putString(summaryKey(conversationId), cleanSummary)
        .putLong(updatedAtKey(conversationId), updatedAt)
        .apply()
    return ConversationSummaryMemory(conversationId, cleanSummary, updatedAt)
}

fun deleteConversationSummary(
    context: Context,
    conversationId: Long
) {
    if (conversationId <= 0L) return
    context.getSharedPreferences(ConversationSummaryPrefs, Context.MODE_PRIVATE)
        .edit()
        .remove(summaryKey(conversationId))
        .remove(updatedAtKey(conversationId))
        .apply()
}

fun buildConversationSummaryContextText(summary: ConversationSummaryMemory?): String? {
    val text = summary?.summary?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return "[대화 요약]\n${text.take(MaxSummaryContextChars)}"
}

private fun summaryKey(conversationId: Long): String = "summary_$conversationId"

private fun updatedAtKey(conversationId: Long): String = "summary_updated_at_$conversationId"
