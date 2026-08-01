package com.projectnuke.fusion.ui

import android.content.Context
import com.projectnuke.fusion.util.writeTextAtomically
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

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
private const val MaxCandidateRecords = 1_000
private const val MaxCandidateTextChars = 2_000
private const val MaxCandidateSourceChars = 512
private const val MaxCandidateFileBytes = 1 * 1024 * 1024

private val storeMutex = Mutex()
private var migrationDone = false

private fun candidateFile(context: Context): File =
    File(context.filesDir, "conversation_memory_candidates.json")

private suspend fun readCandidateJson(context: Context): String = withContext(Dispatchers.IO) {
    val file = candidateFile(context)
    if (file.isFile && file.length() <= MaxCandidateFileBytes) {
        runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("[]")
    } else {
        val legacy = context.getSharedPreferences(ConversationMemoryCandidatePrefs, Context.MODE_PRIVATE)
            .getString(ConversationMemoryCandidateKey, "[]") ?: "[]"
        if (legacy != "[]" && !migrationDone) {
            if (runCatching { writeTextAtomically(file, legacy); true }.getOrDefault(false)) {
                context.getSharedPreferences(ConversationMemoryCandidatePrefs, Context.MODE_PRIVATE)
                    .edit().remove(ConversationMemoryCandidateKey).apply()
            }
            migrationDone = true
        }
        legacy
    }
}

private suspend fun writeCandidateJson(context: Context, json: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val bounded = JSONArray(json)
            while (bounded.length() > MaxCandidateRecords) bounded.remove(bounded.length() - 1)
            val encoded = bounded.toString()
            if (encoded.toByteArray(Charsets.UTF_8).size > MaxCandidateFileBytes) return@runCatching false
            writeTextAtomically(candidateFile(context), encoded)
            true
        }.getOrDefault(false)
    }

suspend fun loadConversationMemoryCandidates(
    context: Context,
    conversationId: Long
): List<ConversationMemoryCandidate> = storeMutex.withLock {
    if (conversationId <= 0L) return@withLock emptyList()
    loadAllConversationMemoryCandidatesLocked(context)
        .filter { it.conversationId == conversationId }
}

suspend fun loadAllConversationMemoryCandidates(
    context: Context
): List<ConversationMemoryCandidate> = storeMutex.withLock {
    loadAllConversationMemoryCandidatesLocked(context)
}

private suspend fun loadAllConversationMemoryCandidatesLocked(
    context: Context,
): List<ConversationMemoryCandidate> {
    val raw = readCandidateJson(context)
    val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
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
                    conversationTitle = obj.optString("conversationTitle")
                        .take(MaxCandidateSourceChars).takeIf { it.isNotBlank() },
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

suspend fun saveConversationMemoryCandidates(
    context: Context,
    conversationId: Long,
    candidates: List<String>,
    conversationTitle: String? = null
): Int = storeMutex.withLock {
    if (conversationId <= 0L) return@withLock 0
    val cleanCandidates = candidates
        .map { normalizeMemoryCandidateText(it) }
        .map { it.take(MaxCandidateTextChars) }
        .filter { it.isNotBlank() }
        .distinct()
    if (cleanCandidates.isEmpty()) return@withLock 0

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
                .put("conversationTitle", conversationTitle?.take(MaxCandidateSourceChars))
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

suspend fun updateConversationMemoryCandidate(
    context: Context,
    candidateId: String,
    newText: String
): Boolean = storeMutex.withLock {
    val cleanText = normalizeMemoryCandidateText(newText)
    if (candidateId.isBlank() || cleanText.isBlank()) return@withLock false
    val existing = runCatching {
        JSONArray(readCandidateJson(context))
    }.getOrElse { JSONArray() }
    var changed = false
    for (index in 0 until existing.length()) {
        val obj = existing.optJSONObject(index) ?: continue
        if (obj.optString("id") != candidateId) continue
        obj.put("text", cleanText.take(MaxCandidateTextChars))
        obj.put("updatedAt", System.currentTimeMillis())
        changed = true
        break
    }
    if (!changed) return@withLock false
    writeCandidateJson(context, existing.toString())
}

suspend fun setConversationMemoryCandidateEnabled(
    context: Context,
    candidateId: String,
    enabled: Boolean
): Boolean = storeMutex.withLock {
    if (candidateId.isBlank()) return@withLock false
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
    if (!changed) return@withLock false
    writeCandidateJson(context, existing.toString())
}

suspend fun setConversationMemoryCandidateScope(
    context: Context,
    candidateId: String,
    scope: MemoryScope,
    modelId: String? = null
): Boolean = storeMutex.withLock {
    if (candidateId.isBlank()) return@withLock false
    if (scope == MemoryScope.MODEL_ONLY && modelId.isNullOrBlank()) return@withLock false
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
    if (!changed) return@withLock false
    writeCandidateJson(context, existing.toString())
}

suspend fun deleteConversationMemoryCandidate(
    context: Context,
    candidateId: String
): Boolean = storeMutex.withLock {
    if (candidateId.isBlank()) return@withLock false
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
    if (!removed) return@withLock false
    writeCandidateJson(context, updated.toString())
}

suspend fun deleteConversationOnlyMemoryCandidates(
    context: Context,
    conversationId: Long,
): Boolean = storeMutex.withLock {
    if (conversationId <= 0L) return@withLock true
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

private fun normalizeMemoryCandidateText(value: String): String {
    return value
        .replace(Regex("""^(?:[\-\*\u2022\u25CF\u25E6]\s*|\d+[\.\)]\s*)"""), "")
        .trim()
}
