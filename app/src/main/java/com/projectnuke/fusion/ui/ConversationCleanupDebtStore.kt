package com.projectnuke.fusion.ui

import android.content.Context
import com.projectnuke.fusion.data.ChatDao
import com.projectnuke.fusion.util.AttachmentStorageManager
import com.projectnuke.fusion.util.FusionResponseRatings
import com.projectnuke.fusion.util.writeTextAtomically
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class ConversationCleanupDebt(
    val conversationId: Long,
    val messageIds: Set<Long>,
    val pendingPaths: Set<String>,
    val attempts: Int,
    val lastAttemptAt: Long,
)

internal object ConversationCleanupDebtStore {
    private const val MAX_ENTRIES = 64
    private const val MAX_MESSAGE_IDS = 512
    private const val MAX_PATHS = 64
    private const val MAX_PATH_CHARS = 4_096
    private const val MAX_ATTEMPTS = 100
    private const val RETRY_BASE_DELAY_MS = 5_000L
    private const val FILE_NAME = "conversation_cleanup_debt.json"
    private val lock = Any()

    fun record(context: Context, debt: ConversationCleanupDebt): Boolean = synchronized(lock) {
        val current = loadLocked(context).filterNot { it.conversationId == debt.conversationId }
        val bounded = (current + debt.copy(
            messageIds = debt.messageIds.take(MAX_MESSAGE_IDS).toSet(),
            pendingPaths = debt.pendingPaths.map { it.take(MAX_PATH_CHARS) }.take(MAX_PATHS).toSet(),
        )).takeLast(MAX_ENTRIES)
        persistLocked(context, bounded)
    }

    fun remove(context: Context, conversationId: Long): Boolean = synchronized(lock) {
        persistLocked(context, loadLocked(context).filterNot { it.conversationId == conversationId })
    }

    suspend fun retry(context: Context, dao: ChatDao, limit: Int = 4) = withContext(Dispatchers.IO) {
        val entries = synchronized(lock) { loadLocked(context) }
        val now = System.currentTimeMillis()
        val pending = entries.filter {
            it.attempts < MAX_ATTEMPTS &&
                (it.lastAttemptAt <= 0L || now - it.lastAttemptAt >= retryDelayMs(it.attempts))
        }.take(limit)
        val terminal = entries.filter { it.attempts >= MAX_ATTEMPTS }
        val remaining = entries.toMutableList()
        pending.forEach { entry ->
            val success = runCatching {
                deleteResponseVersionState(context, entry.conversationId)
                deleteConversationSummary(context, entry.conversationId)
                check(deleteConversationOnlyMemoryCandidates(context, entry.conversationId))
                check(FusionResponseRatings.deleteForMessages(context, entry.messageIds))
                entry.pendingPaths.forEach { path ->
                    check(AttachmentStorageManager.deletePendingAttachmentFile(context, path))
                }
                AttachmentStorageManager.cleanupUnreferencedAttachments(context, dao)
            }.isSuccess
            remaining.remove(entry)
            if (!success) {
                remaining += entry.copy(attempts = entry.attempts + 1, lastAttemptAt = System.currentTimeMillis())
            }
        }
        check(synchronized(lock) { persistLocked(context, (remaining + terminal).takeLast(MAX_ENTRIES)) })
    }

    private fun retryDelayMs(attempts: Int): Long =
        RETRY_BASE_DELAY_MS * (1L shl attempts.coerceIn(0, 10))

    private fun loadLocked(context: Context): List<ConversationCleanupDebt> {
        val file = File(context.filesDir, FILE_NAME)
        val raw = runCatching { if (file.length() > 512 * 1024) return emptyList(); file.readText() }.getOrNull()
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until minOf(array.length(), MAX_ENTRIES)).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optLong("id", -1L).takeIf { it > 0L } ?: return@mapNotNull null
                val messages = item.optJSONArray("messages")?.let { values ->
                    (0 until minOf(values.length(), MAX_MESSAGE_IDS)).mapNotNull { values.optLong(it, -1L).takeIf { value -> value > 0L } }.toSet()
                }.orEmpty()
                val paths = item.optJSONArray("paths")?.let { values ->
                    (0 until minOf(values.length(), MAX_PATHS)).mapNotNull { values.optString(it).takeIf { value -> value.length in 1..MAX_PATH_CHARS } }.toSet()
                }.orEmpty()
                ConversationCleanupDebt(id, messages, paths, item.optInt("attempts").coerceIn(0, MAX_ATTEMPTS), item.optLong("lastAttemptAt"))
            }
        }.getOrDefault(emptyList())
    }

    private fun persistLocked(context: Context, debts: List<ConversationCleanupDebt>): Boolean {
        val array = JSONArray()
        debts.takeLast(MAX_ENTRIES).forEach { debt ->
            array.put(JSONObject().put("id", debt.conversationId).put("attempts", debt.attempts).put("lastAttemptAt", debt.lastAttemptAt)
                .put("messages", JSONArray(debt.messageIds.take(MAX_MESSAGE_IDS).toList()))
                .put("paths", JSONArray(debt.pendingPaths.take(MAX_PATHS).map { it.take(MAX_PATH_CHARS) })))
        }
        return runCatching { writeTextAtomically(File(context.filesDir, FILE_NAME), array.toString()) }.isSuccess
    }
}
