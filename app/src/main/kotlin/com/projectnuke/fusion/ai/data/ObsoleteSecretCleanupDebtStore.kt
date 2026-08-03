package com.projectnuke.fusion.ai.data

import android.content.Context
import com.projectnuke.fusion.util.writeTextAtomically
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable record of an obsolete secret that could not be deleted.
 * The retry re-attempts the exact deletion through the secret store.
 */
internal data class ObsoleteSecretCleanupDebt(
    val secretId: String,
    val attempts: Int,
    val lastAttemptAt: Long,
)

internal data class ObsoleteSecretCleanupResult(
    val success: Boolean,
)

internal object ObsoleteSecretCleanupDebtStore {
    private const val MAX_ENTRIES = 32
    private const val MAX_SECRET_ID_CHARS = 128
    private const val MAX_ATTEMPTS = 100
    private const val FILE_NAME = "obsolete_secret_cleanup_debt.json"
    private val lock = Any()

    fun record(context: Context, secretId: String): Boolean =
        record(File(context.filesDir, FILE_NAME), secretId)

    fun record(file: File, secretId: String): Boolean = synchronized(lock) {
        val current = loadLocked(file).filterNot { it.secretId == secretId }
        val bounded = (current + ObsoleteSecretCleanupDebt(
            secretId = secretId.take(MAX_SECRET_ID_CHARS),
            attempts = 0,
            lastAttemptAt = System.currentTimeMillis(),
        )).takeLast(MAX_ENTRIES)
        persistLocked(file, bounded)
    }

    fun remove(context: Context, secretId: String): Boolean =
        remove(File(context.filesDir, FILE_NAME), secretId)

    fun remove(file: File, secretId: String): Boolean = synchronized(lock) {
        persistLocked(file, loadLocked(file).filterNot { it.secretId == secretId })
    }

    suspend fun retry(
        secretStore: com.projectnuke.fusion.ai.secure.SecretStore,
        file: File,
        limit: Int = 4,
    ): Int = withContext(Dispatchers.IO) {
        val entries = synchronized(lock) { loadLocked(file) }
        val pending = entries.filter { it.attempts < MAX_ATTEMPTS }.take(limit)
        val terminal = entries.filter { it.attempts >= MAX_ATTEMPTS }
        val remaining = entries.toMutableList()
        var cleaned = 0
        pending.forEach { entry ->
            val result = try {
                val success = secretStore.deleteSecret(entry.secretId)
                ObsoleteSecretCleanupResult(success)
            } catch (_: Throwable) {
                ObsoleteSecretCleanupResult(success = false)
            }
            remaining.remove(entry)
            if (result.success) {
                cleaned++
            } else {
                remaining += entry.copy(attempts = entry.attempts + 1, lastAttemptAt = System.currentTimeMillis())
            }
        }
        synchronized(lock) { persistLocked(file, (remaining + terminal).takeLast(MAX_ENTRIES)) }
        cleaned
    }

    fun getPendingCount(file: File): Int = synchronized(lock) {
        loadLocked(file).count { it.attempts < MAX_ATTEMPTS }
    }

    private fun loadLocked(file: File): List<ObsoleteSecretCleanupDebt> {
        val raw = runCatching { if (file.length() > 64 * 1024) return emptyList(); file.readText() }.getOrNull()
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until minOf(array.length(), MAX_ENTRIES)).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val secretId = item.optString("secretId").takeIf { it.length in 1..MAX_SECRET_ID_CHARS }
                    ?: return@mapNotNull null
                ObsoleteSecretCleanupDebt(
                    secretId = secretId,
                    attempts = item.optInt("attempts").coerceIn(0, MAX_ATTEMPTS),
                    lastAttemptAt = item.optLong("lastAttemptAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persistLocked(file: File, debts: List<ObsoleteSecretCleanupDebt>): Boolean {
        val array = JSONArray()
        debts.takeLast(MAX_ENTRIES).forEach { debt ->
            array.put(
                JSONObject()
                    .put("secretId", debt.secretId.take(MAX_SECRET_ID_CHARS))
                    .put("attempts", debt.attempts)
                    .put("lastAttemptAt", debt.lastAttemptAt),
            )
        }
        return runCatching { writeTextAtomically(file, array.toString()) }.isSuccess
    }
}