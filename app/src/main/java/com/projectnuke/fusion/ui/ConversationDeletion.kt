package com.projectnuke.fusion.ui

import android.content.Context
import com.projectnuke.fusion.chat.ChatViewModel
import com.projectnuke.fusion.chat.ConversationDeletionResult
import com.projectnuke.fusion.data.ChatDao
import com.projectnuke.fusion.util.AttachmentStorageManager
import com.projectnuke.fusion.util.FusionResponseRatings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun deleteConversationProduction(
    context: Context,
    dao: ChatDao,
    chatViewModel: ChatViewModel,
    conversationId: Long,
): ConversationDeletionResult {
    val appContext = context.applicationContext
    var deletedMessageIds: Set<Long> = emptySet()
    val targetPendingPaths = chatViewModel.draft(conversationId)
        .pendingAttachments
        .mapTo(linkedSetOf()) { it.localPath }
    val deferred = chatViewModel.deleteConversation(
        conversationId = conversationId,
        exists = {
            val exists = dao.getConversationById(conversationId) != null
            if (exists) {
                deletedMessageIds = dao.getMessagesForConversation(conversationId)
                    .mapTo(linkedSetOf()) { it.id }
            }
            exists
        },
        commitDelete = { dao.deleteConversation(conversationId) },
        settleTarget = {
            chatViewModel.clear(conversationId)
            chatViewModel.clearDraft(conversationId)
            if (chatViewModel.currentConversationId.value == conversationId) {
                chatViewModel.selectConversation(
                    dao.getLatestConversation()?.id ?: ChatViewModel.NEW_CONVERSATION_ID
                )
            }
        },
        cleanupDerivedData = {
            withContext(Dispatchers.IO) {
                check(deleteResponseVersionStateSafely(appContext, conversationId))
                deleteConversationSummary(appContext, conversationId)
                check(deleteConversationOnlyMemoryCandidates(appContext, conversationId))
                check(FusionResponseRatings.deleteForMessages(appContext, deletedMessageIds))
                targetPendingPaths.forEach { path ->
                    check(AttachmentStorageManager.deletePendingAttachmentFile(appContext, path))
                }
                AttachmentStorageManager.cleanupUnreferencedAttachments(appContext, dao)
                removeConversationCleanupDebt(appContext, conversationId)
            }
        },
        recordCleanupDebt = {
            withContext(Dispatchers.IO) {
                ConversationCleanupDebtStore.record(
                    appContext,
                    ConversationCleanupDebt(conversationId, deletedMessageIds, targetPendingPaths, 0, 0L),
                )
            }
        },
    )
    return deferred.await()
}

private fun deleteResponseVersionStateSafely(context: Context, conversationId: Long): Boolean =
    runCatching {
        deleteResponseVersionState(context, conversationId)
        true
    }.getOrDefault(false)

private fun removeConversationCleanupDebt(context: Context, conversationId: Long) {
    ConversationCleanupDebtStore.remove(context, conversationId)
}
