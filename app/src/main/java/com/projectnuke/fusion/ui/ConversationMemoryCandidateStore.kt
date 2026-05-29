package com.projectnuke.fusion.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ConversationMemoryCandidate(
    val id: String,
    val text: String,
    val conversationId: Long,
    val createdAt: Long,
    val savedByUser: Boolean = true
)

private const val ConversationMemoryCandidatePrefs = "fusion_memory_candidates"
private const val ConversationMemoryCandidateKey = "saved_candidates"

fun loadConversationMemoryCandidates(
    context: Context,
    conversationId: Long
): List<ConversationMemoryCandidate> {
    if (conversationId <= 0L) return emptyList()
    val raw = context.getSharedPreferences(ConversationMemoryCandidatePrefs, Context.MODE_PRIVATE)
        .getString(ConversationMemoryCandidateKey, null)
    val arr = runCatching { JSONArray(raw ?: "[]") }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until arr.length()) {
            val obj = arr.optJSONObject(index) ?: continue
            if (obj.optLong("conversationId") != conversationId) continue
            val text = obj.optString("text").trim()
            if (text.isBlank()) continue
            add(
                ConversationMemoryCandidate(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    text = text,
                    conversationId = conversationId,
                    createdAt = obj.optLong("createdAt"),
                    savedByUser = obj.optBoolean("savedByUser", true)
                )
            )
        }
    }
}

fun saveConversationMemoryCandidates(
    context: Context,
    conversationId: Long,
    candidates: List<String>
): Int {
    if (conversationId <= 0L) return 0
    val cleanCandidates = candidates
        .map { normalizeMemoryCandidateText(it) }
        .filter { it.isNotBlank() }
        .distinct()
    if (cleanCandidates.isEmpty()) return 0

    val prefs = context.getSharedPreferences(ConversationMemoryCandidatePrefs, Context.MODE_PRIVATE)
    val existing = runCatching {
        JSONArray(prefs.getString(ConversationMemoryCandidateKey, null) ?: "[]")
    }.getOrElse { JSONArray() }

    val existingKeys = buildSet {
        for (index in 0 until existing.length()) {
            val obj = existing.optJSONObject(index) ?: continue
            add("${obj.optLong("conversationId")}:${normalizeMemoryCandidateText(obj.optString("text"))}")
        }
    }

    val now = System.currentTimeMillis()
    val updated = JSONArray()
    var savedCount = 0
    cleanCandidates.forEach { candidate ->
        val dedupeKey = "$conversationId:$candidate"
        if (existingKeys.contains(dedupeKey)) return@forEach
        updated.put(
            JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("text", candidate)
                .put("conversationId", conversationId)
                .put("createdAt", now)
                .put("savedByUser", true)
        )
        savedCount++
    }
    for (index in 0 until existing.length()) {
        updated.put(existing.get(index))
    }
    prefs.edit().putString(ConversationMemoryCandidateKey, updated.toString()).apply()
    return savedCount
}

private fun normalizeMemoryCandidateText(value: String): String {
    return value
        .replace(Regex("""^[\-\*\u2022\u25CF\u25E6\d\.\)\s]+"""), "")
        .trim()
}
