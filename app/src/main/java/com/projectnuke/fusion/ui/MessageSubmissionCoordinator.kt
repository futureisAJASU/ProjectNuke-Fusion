package com.projectnuke.fusion.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal data class SubmissionCommitState(
    var conversationId: Long,
    var conversationWasCreated: Boolean = false,
    var messageInserted: Boolean = false,
)

internal data class SubmissionCommitResult(
    val conversationId: Long,
    val conversationWasCreated: Boolean,
    val publicationFailure: Throwable?,
    val reconciliationFailed: Boolean,
)

/**
 * Owns the irreversible user-message commit boundary.
 *
 * Cancellation is observed before the commit starts. Once the block begins, the optional
 * conversation insert and user-message insert run inside the supplied database transaction, then
 * publication, composer reconciliation and timestamp update settle without a cancellable gap. The
 * transaction itself is the single rollback boundary, so no second best-effort row deletion is issued.
 */
internal suspend fun commitAndSettleUserSubmission(
    state: SubmissionCommitState,
    parentJob: Job?,
    runInTransaction: suspend (suspend () -> Unit) -> Unit,
    createConversation: suspend () -> Long,
    insertUserMessage: suspend (conversationId: Long) -> Unit,
    publishConversation: (conversationId: Long) -> Unit,
    reconcileCommittedDraft: suspend () -> Boolean,
    updateConversationTimestamp: suspend (conversationId: Long) -> Unit,
    onPublicationFailure: (Throwable) -> Unit = {},
    onTimestampFailure: (Throwable) -> Unit = {},
): SubmissionCommitResult = withContext(NonCancellable) {
    if (parentJob != null && !parentJob.isActive) {
        throw CancellationException("Submission was cancelled before the database commit started")
    }

    val originalConversationId = state.conversationId
    try {
        runInTransaction {
            if (parentJob != null && !parentJob.isActive) {
                throw CancellationException("Submission was cancelled before the database transaction began")
            }
            if (state.conversationId == 0L) {
                state.conversationId = createConversation()
                state.conversationWasCreated = true
            }

            insertUserMessage(state.conversationId)
        }
        // Mark the message committed only after the transaction itself commits successfully.
        state.messageInserted = true
    } catch (commitFailure: Exception) {
        // The conversation insert and first message insert are one atomic transaction. Do not issue
        // a second delete after rollback: a rolled-back row id may be reused by another insert.
        state.conversationId = originalConversationId
        state.conversationWasCreated = false
        state.messageInserted = false
        throw commitFailure
    }

    var publicationFailure: Throwable? = null
    if (state.conversationWasCreated) {
        try {
            publishConversation(state.conversationId)
        } catch (failure: Exception) {
            publicationFailure = failure
            onPublicationFailure(failure)
        }
    }

    // The message is committed at this point. Reconcile the exact captured composer identity
    // regardless of whether publishing the newly created conversation succeeded. The boolean
    // outcome is surfaced so the caller can record durable reconciliation debt for a retry.
    val reconciliationFailed = try {
        !reconcileCommittedDraft()
    } catch (_: Exception) {
        true
    }

    try {
        updateConversationTimestamp(state.conversationId)
    } catch (failure: Exception) {
        onTimestampFailure(failure)
    }

    SubmissionCommitResult(
        conversationId = state.conversationId,
        conversationWasCreated = state.conversationWasCreated,
        publicationFailure = publicationFailure,
        reconciliationFailed = reconciliationFailed,
    )
}

internal suspend fun <T> installGenerationRequestAndSettleOwner(
    owner: MessageSubmissionOwner,
    getActiveOwner: () -> MessageSubmissionOwner?,
    setActiveOwner: suspend (MessageSubmissionOwner?) -> Unit,
    install: suspend () -> T,
): T {
    return try {
        // Keep installation cancellable. GenerationSessionRegistry owns its own exact lifecycle
        // settlement; shielding start() could install a request after the submitting screen is gone.
        install()
    } finally {
        setActiveOwner(getActiveOwner().clearIfMatches(owner))
    }
}

internal data class CommittedDraftReconciliation(
    val shouldClearInput: Boolean,
    val remainingAttachments: List<LocalAttachment>,
    val committedPaths: List<String>,
)

internal fun reconcileCommittedDraft(
    currentInput: String,
    capturedRawInput: String,
    pendingAttachments: List<LocalAttachment>,
    capturedDraftPaths: List<String>,
): CommittedDraftReconciliation {
    val committedPaths = capturedDraftPaths.distinct()
    val committedSet = committedPaths.toSet()
    return CommittedDraftReconciliation(
        shouldClearInput = currentInput == capturedRawInput,
        remainingAttachments = pendingAttachments.filterNot { it.localPath in committedSet },
        committedPaths = committedPaths,
    )
}
