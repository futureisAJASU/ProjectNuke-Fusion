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
    return chatViewModel.deleteConversation(
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
        },
        cleanupDerivedData = {
            withContext(Dispatchers.IO) {
                check(deleteResponseVersionStateSafely(appContext, conversationId))
                deleteConversationSummary(appContext, conversationId)
                check(deleteConversationOnlyMemoryCandidates(appContext, conversationId))
                check(FusionResponseRatings.deleteForMessages(appContext, deletedMessageIds))
                AttachmentStorageManager.cleanupUnreferencedAttachments(appContext, dao)
                removeConversationCleanupDebt(appContext, conversationId)
            }
        },
        recordCleanupDebt = {
            withContext(Dispatchers.IO) {
                recordConversationCleanupDebt(appContext, conversationId)
            }
        },
    ).await()
}

private fun deleteResponseVersionStateSafely(context: Context, conversationId: Long): Boolean =
    runCatching {
        deleteResponseVersionState(context, conversationId)
        true
    }.getOrDefault(false)

private const val CleanupDebtPrefs = "fusion_conversation_cleanup_debt"
private const val MaxCleanupDebt = 64

private fun recordConversationCleanupDebt(context: Context, conversationId: Long) {
    val prefs = context.getSharedPreferences(CleanupDebtPrefs, Context.MODE_PRIVATE)
    val ids = prefs.getStringSet("ids", emptySet()).orEmpty()
        .mapNotNull(String::toLongOrNull)
        .filter { it > 0L && it != conversationId }
        .takeLast(MaxCleanupDebt - 1)
        .plus(conversationId)
        .map(Long::toString)
        .toSet()
    prefs.edit().putStringSet("ids", ids).commit()
}

private fun removeConversationCleanupDebt(context: Context, conversationId: Long) {
    val prefs = context.getSharedPreferences(CleanupDebtPrefs, Context.MODE_PRIVATE)
    val updated = prefs.getStringSet("ids", emptySet()).orEmpty() - conversationId.toString()
    prefs.edit().putStringSet("ids", updated).commit()
}
