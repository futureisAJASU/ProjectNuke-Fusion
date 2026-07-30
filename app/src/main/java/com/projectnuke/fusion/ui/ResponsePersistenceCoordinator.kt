package com.projectnuke.fusion.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal data class PersistedResponseVersion<State>(
    val messageId: Long,
    val state: State,
)

/**
 * Persists an assistant message as the irreversible result. Conversation ordering metadata is
 * intentionally best-effort: a timestamp failure must never delete a successfully stored answer.
 */
internal suspend fun persistAssistantMessage(
    insertMessage: suspend () -> Long,
    updateConversationTimestamp: suspend () -> Unit,
    onTimestampFailure: (Throwable) -> Unit = {},
): Long = withContext(NonCancellable + Dispatchers.IO) {
    val messageId = insertMessage()
    try {
        updateConversationTimestamp()
    } catch (failure: Exception) {
        onTimestampFailure(failure)
    }
    messageId
}

/**
 * Persists a regenerated answer and its version metadata as one logical result. Only failures in
 * the message insert or version-state save roll the result back. Conversation ordering metadata is
 * best-effort and cannot invalidate an otherwise complete response.
 */
internal suspend fun <State> persistAssistantVersion(
    loadPreviousState: () -> State,
    insertMessage: suspend () -> Long,
    buildUpdatedState: (previous: State, messageId: Long) -> State,
    saveState: (State) -> Unit,
    restoreState: (State) -> Unit,
    deleteMessage: suspend (messageId: Long) -> Unit,
    updateConversationTimestamp: suspend () -> Unit,
    onTimestampFailure: (Throwable) -> Unit = {},
): PersistedResponseVersion<State> = withContext(NonCancellable + Dispatchers.IO) {
    val previousState = loadPreviousState()
    var insertedMessageId: Long? = null
    var stateSaveAttempted = false

    try {
        val messageId = insertMessage()
        insertedMessageId = messageId
        val updatedState = buildUpdatedState(previousState, messageId)
        stateSaveAttempted = true
        saveState(updatedState)

        try {
            updateConversationTimestamp()
        } catch (failure: Exception) {
            onTimestampFailure(failure)
        }

        PersistedResponseVersion(messageId = messageId, state = updatedState)
    } catch (failure: Exception) {
        insertedMessageId?.let { messageId ->
            try {
                deleteMessage(messageId)
            } catch (rollbackFailure: Exception) {
                failure.addSuppressed(rollbackFailure)
            }
        }
        if (stateSaveAttempted) {
            try {
                restoreState(previousState)
            } catch (rollbackFailure: Exception) {
                failure.addSuppressed(rollbackFailure)
            }
        }
        throw failure
    }
}
