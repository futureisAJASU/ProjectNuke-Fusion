package com.projectnuke.fusion.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.io.File
import com.projectnuke.fusion.util.writeTextAtomically
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

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
private val ConversationMemoryCandidateWriteLock = Any()
private const val MaxCandidateRecords = 1_000
private const val MaxCandidateTextChars = 2_000
private const val MaxCandidateSourceChars = 512
private const val MaxCandidateFileBytes = 1 * 1024 * 1024

private fun candidateFile(context: Context): File = File(context.filesDir, "conversation_memory_candidates.json")

private fun readCandidateJson(context: Context): String {
    return runBlocking(Dispatchers.IO) {
        val file = candidateFile(context)
        if (file.isFile && file.length() <= MaxCandidateFileBytes) {
            return@runBlocking runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("[]")
        }
        val legacy = context.getSharedPreferences(ConversationMemoryCandidatePrefs, Context.MODE_PRIVATE)
            .getString(ConversationMemoryCandidateKey, "[]") ?: "[]"
        if (legacy != "[]" && runCatching { writeTextAtomically(file, legacy); true }.getOrDefault(false)) {
            legacy
        } else legacy
    }
}

private fun writeCandidateJson(context: Context, json: String): Boolean {
    return runBlocking(Dispatchers.IO) {
        runCatching {
            val bounded = JSONArray(json)
            while (bounded.length() > MaxCandidateRecords) bounded.remove(bounded.length() - 1)
            val encoded = bounded.toString()
            if (encoded.toByteArray(Charsets.UTF_8).size > MaxCandidateFileBytes) return@runCatching false
            writeTextAtomically(candidateFile(context), encoded)
            true
        }.getOrDefault(false)
    }
}

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
    val raw = readCandidateJson(context)
    val arr = runCatching { JSONArray(raw ?: "[]") }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until minOf(arr.length(), MaxCandidateRecords)) {
            val obj = arr.optJSONObject(index) ?: continue
            val text = obj.optString("text").trim().take(MaxCandidateTextChars)
            if (text.isBlank()) continue
            val enabled = obj.optBoolean("enabled", true)
            add(
                ConversationMemoryCandidate(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    text = text,
                    conversationId = obj.optLong("conversationId"),
                    createdAt = obj.optLong("createdAt"),
                    updatedAt = obj.optLong("updatedAt").takeIf { it > 0L },
                    conversationTitle = obj.optString("conversationTitle").take(MaxCandidateSourceChars).takeIf { it.isNotBlank() },
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
    return synchronized(ConversationMemoryCandidateWriteLock) {
        if (conversationId <= 0L) return@synchronized 0
        val cleanCandidates = candidates
            .map { normalizeMemoryCandidateText(it) }
            .map { it.take(MaxCandidateTextChars) }
            .filter { it.isNotBlank() }
            .distinct()
        if (cleanCandidates.isEmpty()) return@synchronized 0

        val prefs = context.getSharedPreferences(ConversationMemoryCandidatePrefs, Context.MODE_PRIVATE)
        val existing = runCatching {
            JSONArray(readCandidateJson(context))
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
        if (writeCandidateJson(context, updated.toString())) savedCount else 0
    }
}

fun updateConversationMemoryCandidate(
    context: Context,
    candidateId: String,
    newText: String
): Boolean {
    return synchronized(ConversationMemoryCandidateWriteLock) {
        val cleanText = normalizeMemoryCandidateText(newText)
        if (candidateId.isBlank() || cleanText.isBlank()) return@synchronized false
        val prefs = context.getSharedPreferences(ConversationMemoryCandidatePrefs, Context.MODE_PRIVATE)
        val existing = runCatching {
            JSONArray(readCandidateJson(context))
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
        if (!changed) return@synchronized false
        writeCandidateJson(context, existing.toString())
    }
}

fun setConversationMemoryCandidateEnabled(
    context: Context,
    candidateId: String,
    enabled: Boolean
): Boolean {
    return synchronized(ConversationMemoryCandidateWriteLock) {
        if (candidateId.isBlank()) return@synchronized false
        val prefs = context.getSharedPreferences(ConversationMemoryCandidatePrefs, Context.MODE_PRIVATE)
        val existing = runCatching {
            JSONArray(readCandidateJson(context))
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
        if (!changed) return@synchronized false
        writeCandidateJson(context, existing.toString())
    }
}

fun setConversationMemoryCandidateScope(
    context: Context,
    candidateId: String,
    scope: MemoryScope,
    modelId: String? = null
): Boolean {
    return synchronized(ConversationMemoryCandidateWriteLock) {
        if (candidateId.isBlank()) return@synchronized false
        if (scope == MemoryScope.MODEL_ONLY && modelId.isNullOrBlank()) return@synchronized false
        val prefs = context.getSharedPreferences(ConversationMemoryCandidatePrefs, Context.MODE_PRIVATE)
        val existing = runCatching {
            JSONArray(readCandidateJson(context))
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
        if (!changed) return@synchronized false
        writeCandidateJson(context, existing.toString())
    }
}

fun deleteConversationMemoryCandidate(
    context: Context,
    candidateId: String
): Boolean {
    return synchronized(ConversationMemoryCandidateWriteLock) {
        if (candidateId.isBlank()) return@synchronized false
        val prefs = context.getSharedPreferences(ConversationMemoryCandidatePrefs, Context.MODE_PRIVATE)
        val existing = runCatching {
            JSONArray(readCandidateJson(context))
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
        if (!removed) return@synchronized false
        writeCandidateJson(context, updated.toString())
    }
}

fun deleteConversationOnlyMemoryCandidates(
    context: Context,
    conversationId: Long,
): Boolean {
    if (conversationId <= 0L) return true
    return synchronized(ConversationMemoryCandidateWriteLock) {
        val prefs = context.getSharedPreferences(ConversationMemoryCandidatePrefs, Context.MODE_PRIVATE)
        val existing = runCatching {
            JSONArray(readCandidateJson(context))
        }.getOrElse { JSONArray() }
        val updated = JSONArray()
        for (index in 0 until existing.length()) {
            val item = existing.optJSONObject(index) ?: continue
            val belongsToConversation = item.optLong("conversationId") == conversationId
            val promoted = item.optBoolean("enabled", false) &&
                item.optString("scope") in setOf(MemoryScope.GLOBAL.name, MemoryScope.MODEL_ONLY.name)
            if (!belongsToConversation || promoted) updated.put(item)
        }
        writeCandidateJson(context, updated.toString())
    }
}

private fun normalizeMemoryCandidateText(value: String): String {
    return value
        .replace(Regex("""^(?:[\-\*\u2022\u25CF\u25E6]\s*|\d+[\.\)]\s*)"""), "")
        .trim()
}
