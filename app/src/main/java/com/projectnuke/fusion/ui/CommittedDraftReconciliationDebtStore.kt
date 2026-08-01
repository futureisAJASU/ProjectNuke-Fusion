package com.projectnuke.fusion.ui

import android.content.Context
import com.projectnuke.fusion.util.writeTextAtomically
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class CommittedDraftReconciliationDebt(
    val draftKey: Long,
    val token: String,
    val capturedRawInput: String,
    val committedPaths: Set<String>,
    val attempts: Int,
    val lastAttemptAt: Long,
)

internal data class DraftReconciliationResult(
    val success: Boolean,
    val releasePaths: Boolean,
)

internal fun interface DraftReconciliationOwner {
    suspend fun reconcile(debt: CommittedDraftReconciliationDebt): DraftReconciliationResult
}

/**
 * Durable record of a composer reconciliation that could not settle when the message was
 * committed. The retry re-attempts the exact reconciliation through the draft store: it only
 * clears the committed draft when the captured submission token still owns the draft, and only
 * clears the captured input when the draft still matches the committed text, so a newer
 * submission or a user edit after commit is never wiped.
 */
internal object CommittedDraftReconciliationDebtStore {
    private const val MAX_ENTRIES = 64
    private const val MAX_PATHS = 64
    private const val MAX_PATH_CHARS = 4_096
    private const val MAX_TOKEN_CHARS = 128
    private const val MAX_RAW_INPUT_CHARS = 32_768
    private const val MAX_ATTEMPTS = 100
    private const val FILE_NAME = "committed_draft_reconciliation_debt.json"
    private val lock = Any()

    fun record(context: Context, debt: CommittedDraftReconciliationDebt): Boolean =
        record(File(context.filesDir, FILE_NAME), debt)

    fun record(file: File, debt: CommittedDraftReconciliationDebt): Boolean = synchronized(lock) {
        val current = loadLocked(file).filterNot {
            it.draftKey == debt.draftKey && it.token == debt.token
        }
        val bounded = (current + debt.copy(
            token = debt.token.take(MAX_TOKEN_CHARS),
            capturedRawInput = debt.capturedRawInput.take(MAX_RAW_INPUT_CHARS),
            committedPaths = debt.committedPaths.map { it.take(MAX_PATH_CHARS) }.take(MAX_PATHS).toSet(),
        )).takeLast(MAX_ENTRIES)
        persistLocked(file, bounded)
    }

    fun remove(context: Context, draftKey: Long, token: String): Boolean =
        remove(File(context.filesDir, FILE_NAME), draftKey, token)

    fun remove(file: File, draftKey: Long, token: String): Boolean = synchronized(lock) {
        persistLocked(file, loadLocked(file).filterNot {
            it.draftKey == draftKey && it.token == token
        })
    }

    suspend fun retry(
        owner: DraftReconciliationOwner,
        unregisterPath: (String) -> Unit,
        file: File,
        limit: Int = 4,
    ): Int = withContext(Dispatchers.IO) {
        val entries = synchronized(lock) { loadLocked(file) }
        val pending = entries.filter { it.attempts < MAX_ATTEMPTS }.take(limit)
        val terminal = entries.filter { it.attempts >= MAX_ATTEMPTS }
        val remaining = entries.toMutableList()
        var reconciled = 0
        pending.forEach { entry ->
            val result = try {
                owner.reconcile(entry)
            } catch (_: Throwable) {
                DraftReconciliationResult(success = false, releasePaths = false)
            }
            remaining.remove(entry)
            if (result.success) {
                if (result.releasePaths) entry.committedPaths.forEach(unregisterPath)
                reconciled++
            } else {
                remaining += entry.copy(attempts = entry.attempts + 1, lastAttemptAt = System.currentTimeMillis())
            }
        }
        synchronized(lock) { persistLocked(file, (remaining + terminal).takeLast(MAX_ENTRIES)) }
        reconciled
    }

    private fun loadLocked(file: File): List<CommittedDraftReconciliationDebt> {
        val raw = runCatching { if (file.length() > 512 * 1024) return emptyList(); file.readText() }.getOrNull()
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until minOf(array.length(), MAX_ENTRIES)).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optLong("id", Long.MIN_VALUE)
                    .takeIf { it >= 0L } ?: return@mapNotNull null
                val token = item.optString("token").takeIf { it.length in 1..MAX_TOKEN_CHARS }
                    ?: return@mapNotNull null
                val paths = item.optJSONArray("paths")?.let { values ->
                    (0 until minOf(values.length(), MAX_PATHS)).mapNotNull {
                        values.optString(it).takeIf { value -> value.length in 1..MAX_PATH_CHARS }
                    }.toSet()
                }.orEmpty()
                CommittedDraftReconciliationDebt(
                    draftKey = id,
                    token = token,
                    capturedRawInput = item.optString("rawInput").take(MAX_RAW_INPUT_CHARS),
                    committedPaths = paths,
                    attempts = item.optInt("attempts").coerceIn(0, MAX_ATTEMPTS),
                    lastAttemptAt = item.optLong("lastAttemptAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persistLocked(file: File, debts: List<CommittedDraftReconciliationDebt>): Boolean {
        val array = JSONArray()
        debts.takeLast(MAX_ENTRIES).forEach { debt ->
            array.put(
                JSONObject()
                    .put("id", debt.draftKey)
                    .put("token", debt.token.take(MAX_TOKEN_CHARS))
                    .put("rawInput", debt.capturedRawInput.take(MAX_RAW_INPUT_CHARS))
                    .put("attempts", debt.attempts)
                    .put("lastAttemptAt", debt.lastAttemptAt)
                    .put("paths", JSONArray(debt.committedPaths.take(MAX_PATHS).map { it.take(MAX_PATH_CHARS) })),
            )
        }
        return runCatching { writeTextAtomically(file, array.toString()) }.isSuccess
    }
}
