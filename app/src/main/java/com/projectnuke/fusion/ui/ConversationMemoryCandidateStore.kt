package com.projectnuke.fusion.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ConversationMemoryCandidate(
    val id: String,
    val text: String,
    val conversationId: Long,
    val createdAt: Long,
    val updatedAt: Long? = null,
    val conversationTitle: String? = null,
    val enabled: Boolean = true,
    val scope: MemoryScope = MemoryScope.GLOBAL,
    val modelId: String? = null,
    val savedByUser: Boolean = true
)

enum class MemoryScope {
    GLOBAL,
    CONVERSATION_ONLY,
    MODEL_ONLY,
    DISABLED
}

enum class MemoryManagerSortMode {
    UPDATED_DESC,
    CREATED_DESC,
    SHORTEST_FIRST,
    LONGEST_FIRST,
    ENABLED_FIRST
}

private const val ConversationMemoryCandidatePrefs = "fusion_memory_candidates"
private const val ConversationMemoryCandidateKey = "saved_candidates"
const val PrefMemoryManagerSortMode = "memory_manager_sort_mode"
private val ConversationMemoryCandidateWriteMutex = Mutex()

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
            val enabled = obj.optBoolean("enabled", true)
            add(
                ConversationMemoryCandidate(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    text = text,
                    conversationId = obj.optLong("conversationId"),
                    createdAt = obj.optLong("createdAt"),
                    updatedAt = obj.optLong("updatedAt").takeIf { it > 0L },
                    conversationTitle = obj.optString("conversationTitle").takeIf { it.isNotBlank() },
                    enabled = enabled,
                    scope = obj.optString("scope")
                        .takeIf { it.isNotBlank() }
                        ?.let { runCatching { MemoryScope.valueOf(it) }.getOrNull() }
                        ?: if (enabled) MemoryScope.GLOBAL else MemoryScope.DISABLED,
                    modelId = obj.optString("modelId").takeIf { it.isNotBlank() },
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
    return runBlocking {
        ConversationMemoryCandidateWriteMutex.withLock {
            if (conversationId <= 0L) return@withLock 0
            val cleanCandidates = candidates
                .map { normalizeMemoryCandidateText(it) }
                .filter { it.isNotBlank() }
                .distinct()
            if (cleanCandidates.isEmpty()) return@withLock 0

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
                        .put("scope", MemoryScope.CONVERSATION_ONLY.name)
                        .put("savedByUser", true)
                )
                savedCount++
            }
            for (index in 0 until existing.length()) {
                updated.put(existing.get(index))
            }
            prefs.edit().putString(ConversationMemoryCandidateKey, updated.toString()).apply()
            savedCount
        }
    }
}

fun updateConversationMemoryCandidate(
    context: Context,
    candidateId: String,
    newText: String
): Boolean {
    return runBlocking {
        ConversationMemoryCandidateWriteMutex.withLock {
            val cleanText = normalizeMemoryCandidateText(newText)
            if (candidateId.isBlank() || cleanText.isBlank()) return@withLock false
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
            if (!changed) return@withLock false
            prefs.edit().putString(ConversationMemoryCandidateKey, existing.toString()).apply()
            true
        }
    }
}

fun setConversationMemoryCandidateEnabled(
    context: Context,
    candidateId: String,
    enabled: Boolean
): Boolean {
    return runBlocking {
        ConversationMemoryCandidateWriteMutex.withLock {
            if (candidateId.isBlank()) return@withLock false
            val prefs = context.getSharedPreferences(ConversationMemoryCandidatePrefs, Context.MODE_PRIVATE)
            val existing = runCatching {
                JSONArray(prefs.getString(ConversationMemoryCandidateKey, null) ?: "[]")
            }.getOrElse { JSONArray() }
            var changed = false
            for (index in 0 until existing.length()) {
                val obj = existing.optJSONObject(index) ?: continue
                if (obj.optString("id") != candidateId) continue
                obj.put("enabled", enabled)
                obj.put("scope", if (enabled) MemoryScope.GLOBAL.name else MemoryScope.DISABLED.name)
                if (!enabled) obj.remove("modelId")
                obj.put("updatedAt", System.currentTimeMillis())
                changed = true
                break
            }
            if (!changed) return@withLock false
            prefs.edit().putString(ConversationMemoryCandidateKey, existing.toString()).apply()
            true
        }
    }
}

fun setConversationMemoryCandidateScope(
    context: Context,
    candidateId: String,
    scope: MemoryScope,
    modelId: String? = null
): Boolean {
    return runBlocking {
        ConversationMemoryCandidateWriteMutex.withLock {
            if (candidateId.isBlank()) return@withLock false
            if (scope == MemoryScope.MODEL_ONLY && modelId.isNullOrBlank()) return@withLock false
            val prefs = context.getSharedPreferences(ConversationMemoryCandidatePrefs, Context.MODE_PRIVATE)
            val existing = runCatching {
                JSONArray(prefs.getString(ConversationMemoryCandidateKey, null) ?: "[]")
            }.getOrElse { JSONArray() }
            var changed = false
            for (index in 0 until existing.length()) {
                val obj = existing.optJSONObject(index) ?: continue
                if (obj.optString("id") != candidateId) continue
                obj.put("enabled", scope != MemoryScope.DISABLED)
                obj.put("scope", scope.name)
                if (scope == MemoryScope.MODEL_ONLY) {
                    obj.put("modelId", modelId)
                } else {
                    obj.remove("modelId")
                }
                obj.put("updatedAt", System.currentTimeMillis())
                changed = true
                break
            }
            if (!changed) return@withLock false
            prefs.edit().putString(ConversationMemoryCandidateKey, existing.toString()).apply()
            true
        }
    }
}

fun deleteConversationMemoryCandidate(
    context: Context,
    candidateId: String
): Boolean {
    return runBlocking {
        ConversationMemoryCandidateWriteMutex.withLock {
            if (candidateId.isBlank()) return@withLock false
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
            if (!removed) return@withLock false
            prefs.edit().putString(ConversationMemoryCandidateKey, updated.toString()).apply()
            true
        }
    }
}

private fun normalizeMemoryCandidateText(value: String): String {
    return value
        .replace(Regex("""^(?:[\-\*\u2022\u25CF\u25E6]\s*|\d+[\.\)]\s*)"""), "")
        .trim()
}
