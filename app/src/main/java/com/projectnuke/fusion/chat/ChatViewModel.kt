package com.projectnuke.fusion.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import android.content.Context
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

data class ConversationGenerationState(
    val isGenerating: Boolean = false,
    val activeRequestId: String? = null,
    val streamingText: String? = null,
    val streamingMetricsLine: String? = null,
    val generationStatus: String? = null,
    val regeneratingMessageId: Long? = null,
    val extractingMemoryCandidates: Boolean = false,
    val actualWebSearchUsed: Boolean = false,
)

data class PendingAttachmentIdentity(
    val name: String,
    val mimeType: String,
    val localPath: String,
)

enum class ComposerImportStatus {
    PICKER_OPEN,
    COPYING,
}

data class ComposerImportOwnership(
    val token: String,
    val status: ComposerImportStatus,
)

data class ComposerDraftState(
    val rawInput: String = "",
    val pendingAttachments: List<PendingAttachmentIdentity> = emptyList(),
    val version: Long = 0L,
    val importOwnership: ComposerImportOwnership? = null,
    val activeSubmissionToken: String? = null,
)

data class ComposerSubmissionSnapshot(
    val token: String,
    val conversationId: Long,
    val rawInput: String,
    val pendingAttachments: List<PendingAttachmentIdentity>,
    val version: Long,
)

class ChatViewModel(
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    context: Context? = null,
) : ViewModel() {
    val scope: CoroutineScope = viewModelScope
    val registry = GenerationSessionRegistry()
    private val deletionCoordinator = ConversationDeletionCoordinator()
    private val deletionDeferreds = ConcurrentHashMap<Long, Deferred<ConversationDeletionResult>>()

    private val _states = MutableStateFlow<Map<Long, ConversationGenerationState>>(emptyMap())
    val states: StateFlow<Map<Long, ConversationGenerationState>> = _states.asStateFlow()

    internal var draftMachine: ChatDraftStateMachine = ChatDraftStateMachine(
        store = context?.let(::PersistentComposerDraftStore),
        scope = viewModelScope,
    )

    val composerDrafts: StateFlow<Map<Long, ComposerDraftState>>
        get() = draftMachine.drafts

    private val _currentConversationId = MutableStateFlow(
        savedStateHandle[CURRENT_CONVERSATION_KEY] ?: NEW_CONVERSATION_ID
    )
    val currentConversationId: StateFlow<Long> = _currentConversationId.asStateFlow()

    fun selectConversation(conversationId: Long) {
        _currentConversationId.value = conversationId
        savedStateHandle[CURRENT_CONVERSATION_KEY] = conversationId
    }

    fun state(conversationId: Long): ConversationGenerationState =
        _states.value[conversationId] ?: ConversationGenerationState()

    fun update(conversationId: Long, transform: (ConversationGenerationState) -> ConversationGenerationState) {
        _states.update { current ->
            val existing = current[conversationId] ?: ConversationGenerationState()
            current + (conversationId to transform(existing))
        }
    }

    fun updateRequestState(
        conversationId: Long,
        requestId: String,
        requireActiveSession: Boolean = false,
        transform: (ConversationGenerationState) -> ConversationGenerationState,
    ) {
        _states.update { current ->
            val existing = current[conversationId] ?: return@update current
            if (existing.activeRequestId != requestId) return@update current
            if (requireActiveSession && !registry.isActive(conversationId, requestId)) return@update current
            current + (conversationId to transform(existing))
        }
    }

    fun finishRequestState(conversationId: Long, requestId: String) {
        _states.update { current ->
            val existing = current[conversationId] ?: return@update current
            if (existing.activeRequestId != requestId) return@update current
            current - conversationId
        }
    }

    fun clear(conversationId: Long) {
        _states.update { it - conversationId }
    }

    suspend fun cancelGeneration(conversationId: Long, reason: String = "user-stop") {
        val requestId = state(conversationId).activeRequestId ?: return
        registry.cancelAndJoin(conversationId, requestId, reason)
        finishRequestState(conversationId, requestId)
    }

    suspend fun cancelAndAwait(conversationId: Long, reason: String = "delete"): Boolean =
        registry.cancelAndJoin(conversationId, reason).also { clear(conversationId) }

    internal fun deleteConversation(
        conversationId: Long,
        exists: suspend () -> Boolean,
        commitDelete: suspend () -> Unit,
        settleTarget: suspend () -> Unit,
        cleanupDerivedData: suspend () -> Unit,
        recordCleanupDebt: suspend () -> Unit,
    ): Deferred<ConversationDeletionResult> {
        val existing = deletionDeferreds[conversationId]
        if (existing != null && !existing.isCompleted) return existing

        val deferred = viewModelScope.async {
            val deletionReason = "delete-conversation"
            val ownsDeletionOwnership =
                registry.claimDeletionOwnership(conversationId, deletionReason)
            try {
                val result = deletionCoordinator.delete(
                    conversationId = conversationId,
                    cancelAndJoin = {
                        cancelAndAwait(conversationId, reason = "delete-conversation")
                    },
                    exists = exists,
                    commitDelete = commitDelete,
                    settleTarget = settleTarget,
                    cleanupDerivedData = cleanupDerivedData,
                    recordCleanupDebt = recordCleanupDebt,
                )
                deletionDeferreds.remove(conversationId)
                result
            } finally {
                if (ownsDeletionOwnership) {
                    registry.releaseDeletionOwnership(conversationId, deletionReason)
                }
            }
        }
        val previous = deletionDeferreds.putIfAbsent(conversationId, deferred)
        if (previous != null) {
            deferred.cancel()
            return previous
        }
        return deferred
    }

    fun draft(conversationId: Long): ComposerDraftState =
        draftMachine.drafts.value[conversationId] ?: ComposerDraftState()

    fun updateDraftText(conversationId: Long, rawInput: String) {
        draftMachine.updateText(conversationId) { current ->
            current.copy(rawInput = rawInput, version = current.version + 1L)
        }
    }

    fun appendQuickPrompt(conversationId: Long, prompt: String, append: (String, String) -> String) {
        draftMachine.updateText(conversationId) { current ->
            current.copy(
                rawInput = append(current.rawInput, prompt),
                version = current.version + 1L,
            )
        }
    }

    suspend fun removePendingAttachment(conversationId: Long, localPath: String): Boolean {
        var removed = false
        return draftMachine.updateCritical(conversationId) { current ->
            val remaining = current.pendingAttachments.filterNot {
                if (!removed && it.localPath == localPath) {
                    removed = true
                    true
                } else {
                    false
                }
            }
            if (!removed) null else current.copy(
                pendingAttachments = remaining,
                version = current.version + 1L,
            )
        } && removed
    }

    fun beginAttachmentImport(conversationId: Long): ComposerImportOwnership {
        val owner = ComposerImportOwnership(
            token = UUID.randomUUID().toString(),
            status = ComposerImportStatus.PICKER_OPEN,
        )
        draftMachine.queueImmediate(conversationId) { current ->
            current.copy(importOwnership = owner, version = current.version + 1L)
        }
        return owner
    }

    fun pendingPickerImport(): Pair<Long, ComposerImportOwnership>? =
        draftMachine.drafts.value.entries.firstNotNullOfOrNull { (conversationId, draft) ->
            draft.importOwnership
                ?.takeIf { it.status == ComposerImportStatus.PICKER_OPEN }
                ?.let { conversationId to it }
        }

    suspend fun beginAttachmentCopy(conversationId: Long, token: String): Boolean {
        return draftMachine.updateImmediate(conversationId) { current ->
            val owner = current.importOwnership
            if (owner?.token != token) current
            else current.copy(
                importOwnership = owner.copy(status = ComposerImportStatus.COPYING),
                version = current.version + 1L,
            )
        }
    }

    suspend fun completeAttachmentImport(
        conversationId: Long,
        token: String,
        attachments: List<PendingAttachmentIdentity>,
    ): Boolean {
        var accepted = false
        val persisted = draftMachine.updateCritical(conversationId) { current ->
            if (current.importOwnership?.token != token) null
            else {
                accepted = true
                current.copy(
                    pendingAttachments = current.pendingAttachments + attachments,
                    importOwnership = null,
                    version = current.version + 1L,
                )
            }
        }
        return persisted && accepted
    }

    suspend fun settleAttachmentImport(conversationId: Long, token: String): Boolean {
        var accepted = false
        return draftMachine.updateCritical(conversationId) { current ->
            if (current.importOwnership?.token != token) null
            else {
                accepted = true
                current.copy(importOwnership = null, version = current.version + 1L)
            }
        } && accepted
    }

    suspend fun beginSubmission(conversationId: Long): ComposerSubmissionSnapshot? {
        var snapshot: ComposerSubmissionSnapshot? = null
        val persisted = draftMachine.updateCritical(conversationId) { current ->
            if (current.activeSubmissionToken != null) null
            else {
                val token = UUID.randomUUID().toString()
                snapshot = ComposerSubmissionSnapshot(
                    token = token,
                    conversationId = conversationId,
                    rawInput = current.rawInput,
                    pendingAttachments = current.pendingAttachments,
                    version = current.version,
                )
                current.copy(activeSubmissionToken = token)
            }
        }
        return if (persisted) snapshot else null
    }

    suspend fun settleSubmissionOwner(conversationId: Long, token: String): Boolean {
        return draftMachine.updateCritical(conversationId) { current ->
            if (current.activeSubmissionToken != token) null
            else current.copy(activeSubmissionToken = null)
        }
    }

    suspend fun reconcileCommittedSubmission(
        snapshot: ComposerSubmissionSnapshot,
        committedPaths: List<String>,
    ): Boolean {
        var accepted = false
        return draftMachine.updateCritical(snapshot.conversationId) { current ->
            if (current.activeSubmissionToken != snapshot.token) null
            else {
                accepted = true
                val committed = committedPaths.toSet()
                current.copy(
                    rawInput = if (current.rawInput == snapshot.rawInput) "" else current.rawInput,
                    pendingAttachments = current.pendingAttachments.filterNot { it.localPath in committed },
                    activeSubmissionToken = null,
                    version = current.version + 1L,
                )
            }
        } && accepted
    }

    suspend fun clearDraft(conversationId: Long): Boolean =
        draftMachine.clearDraft(conversationId)

    companion object {
        const val NEW_CONVERSATION_ID = 0L
        private const val CURRENT_CONVERSATION_KEY = "current_conversation_id"

        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    return ChatViewModel(
                        savedStateHandle = extras.createSavedStateHandle(),
                        context = context.applicationContext,
                    ) as T
                }
            }

        internal fun forTesting(store: PersistentComposerDraftStore): ChatViewModel =
            ChatViewModel().apply {
                val machine = ChatDraftStateMachine(
                    store = store,
                    scope = viewModelScope,
                )
                draftMachineField.set(this, machine)
            }

        private val draftMachineField by lazy {
            ChatViewModel::class.java.getDeclaredField("draftMachine").apply { isAccessible = true }
        }
    }
}
