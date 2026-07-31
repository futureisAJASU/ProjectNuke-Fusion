package com.projectnuke.fusion.chat

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal enum class ConversationDeletionResult {
    DELETED,
    ALREADY_ABSENT,
    BUSY,
}

/**
 * Owns the ordering boundary for conversation deletion. Cleanup runs after the
 * database commit and therefore must never be allowed to resurrect the row.
 */
internal class ConversationDeletionCoordinator {
    private val ownershipLock = Any()
    private val owners = mutableMapOf<Long, String>()

    suspend fun delete(
        conversationId: Long,
        cancelAndJoin: suspend () -> Unit,
        exists: suspend () -> Boolean,
        commitDelete: suspend () -> Unit,
        settleTarget: suspend () -> Unit,
        cleanupDerivedData: suspend () -> Unit,
        recordCleanupDebt: suspend () -> Unit,
    ): ConversationDeletionResult {
        require(conversationId > 0L)
        val owner = UUID.randomUUID().toString()
        synchronized(ownershipLock) {
            if (owners.putIfAbsent(conversationId, owner) != null) {
                return ConversationDeletionResult.BUSY
            }
        }

        var committed = false
        return try {
            cancelAndJoin()
            if (!exists()) {
                withContext(NonCancellable) {
                    settleTarget()
                    runCatching { cleanupDerivedData() }
                        .onFailure { runCatching { recordCleanupDebt() } }
                }
                ConversationDeletionResult.ALREADY_ABSENT
            } else {
                withContext(NonCancellable) {
                    commitDelete()
                    committed = true
                    runCatching { settleTarget() }
                        .onFailure { runCatching { recordCleanupDebt() } }
                    runCatching { cleanupDerivedData() }
                        .onFailure { runCatching { recordCleanupDebt() } }
                }
                ConversationDeletionResult.DELETED
            }
        } catch (cancelled: CancellationException) {
            if (!committed) throw cancelled
            ConversationDeletionResult.DELETED
        } finally {
            synchronized(ownershipLock) {
                if (owners[conversationId] == owner) owners.remove(conversationId)
            }
        }
    }
}
