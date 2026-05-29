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
    val updatedAt: Long? = null,
    val conversationTitle: String? = null,
    val enabled: Boolean = true,
    val savedByUser: Boolean = true
)

private const val ConversationMemoryCandidatePrefs = "fusion_memory_candidates"
private const val ConversationMemoryCandidateKey = "saved_candidates"

fun loadConversationMemoryCandidates(
    context: Context,
    conversationId: Long
): List<ConversationMemoryCandidate> {
    if (conversationId <= 0L) return emptyList()
    return loadAllConversationMemoryCandidates(context)
        .filter { it.conversationId == conversationId }
}

fun loadAllConversationMemoryCandidates(
    context: Context
): List<ConversationMemoryCandidate> {
    val raw = context.getSharedPreferences(ConversationMemoryCandidatePrefs, Context.MODE_PRIVATE)
        .getString(ConversationMemoryCandidateKey, null)
    val arr = runCatching { JSONArray(raw ?: "[]") }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until arr.length()) {
            val obj = arr.optJSONObject(index) ?: continue
            val text = obj.optString("text").trim()
            if (text.isBlank()) continue
            add(
                ConversationMemoryCandidate(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    text = text,
                    conversationId = obj.optLong("conversationId"),
                    createdAt = obj.optLong("createdAt"),
                    updatedAt = obj.optLong("updatedAt").takeIf { it > 0L },
                    conversationTitle = obj.optString("conversationTitle").takeIf { it.isNotBlank() },
                    enabled = obj.optBoolean("enabled", true),
                    savedByUser = obj.optBoolean("savedByUser", true)
                )
            )
        }
    }.sortedByDescending { it.updatedAt ?: it.createdAt }
}

fun saveConversationMemoryCandidates(
    context: Context,
    conversationId: Long,
    candidates: List<String>,
    conversationTitle: String? = null
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
                .put("updatedAt", now)
                .put("conversationTitle", conversationTitle)
                .put("enabled", true)
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

fun updateConversationMemoryCandidate(
    context: Context,
    candidateId: String,
    newText: String
): Boolean {
    val cleanText = normalizeMemoryCandidateText(newText)
    if (candidateId.isBlank() || cleanText.isBlank()) return false
    val prefs = context.getSharedPreferences(ConversationMemoryCandidatePrefs, Context.MODE_PRIVATE)
    val existing = runCatching {
        JSONArray(prefs.getString(ConversationMemoryCandidateKey, null) ?: "[]")
    }.getOrElse { JSONArray() }
    var changed = false
    for (index in 0 until existing.length()) {
        val obj = existing.optJSONObject(index) ?: continue
        if (obj.optString("id") != candidateId) continue
        obj.put("text", cleanText)
        obj.put("updatedAt", System.currentTimeMillis())
        changed = true
        break
    }
    if (!changed) return false
    prefs.edit().putString(ConversationMemoryCandidateKey, existing.toString()).apply()
    return true
}

fun setConversationMemoryCandidateEnabled(
    context: Context,
    candidateId: String,
    enabled: Boolean
): Boolean {
    if (candidateId.isBlank()) return false
    val prefs = context.getSharedPreferences(ConversationMemoryCandidatePrefs, Context.MODE_PRIVATE)
    val existing = runCatching {
        JSONArray(prefs.getString(ConversationMemoryCandidateKey, null) ?: "[]")
    }.getOrElse { JSONArray() }
    var changed = false
    for (index in 0 until existing.length()) {
        val obj = existing.optJSONObject(index) ?: continue
        if (obj.optString("id") != candidateId) continue
        obj.put("enabled", enabled)
        obj.put("updatedAt", System.currentTimeMillis())
        changed = true
        break
    }
    if (!changed) return false
    prefs.edit().putString(ConversationMemoryCandidateKey, existing.toString()).apply()
    return true
}

fun deleteConversationMemoryCandidate(
    context: Context,
    candidateId: String
): Boolean {
    if (candidateId.isBlank()) return false
    val prefs = context.getSharedPreferences(ConversationMemoryCandidatePrefs, Context.MODE_PRIVATE)
    val existing = runCatching {
        JSONArray(prefs.getString(ConversationMemoryCandidateKey, null) ?: "[]")
    }.getOrElse { JSONArray() }
    val updated = JSONArray()
    var removed = false
    for (index in 0 until existing.length()) {
        val obj = existing.optJSONObject(index) ?: continue
        if (obj.optString("id") == candidateId) {
            removed = true
            continue
        }
        updated.put(obj)
    }
    if (!removed) return false
    prefs.edit().putString(ConversationMemoryCandidateKey, updated.toString()).apply()
    return true
}

private fun normalizeMemoryCandidateText(value: String): String {
    return value
        .replace(Regex("""^(?:[\-\*\u2022\u25CF\u25E6]\s*|\d+[\.\)]\s*)"""), "")
        .trim()
}
