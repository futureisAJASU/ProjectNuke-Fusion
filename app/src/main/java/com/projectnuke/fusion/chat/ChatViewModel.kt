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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Per-conversation generation state. This state is deliberately not written to
 * [SavedStateHandle]: coroutine jobs and streaming state are valid only for the
 * live Activity-scoped ViewModel.
 */
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

/**
 * Activity-scoped runtime owner for chat generation and composer drafts.
 *
 * Android retains this ViewModel across configuration changes and clears it
 * only when the Activity is genuinely finished. [SavedStateHandle] contains
 * only navigation and serializable draft identities for process recreation.
 */
class ChatViewModel(
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    context: Context? = null,
) : ViewModel() {
    val scope: CoroutineScope = viewModelScope
    val registry = GenerationSessionRegistry()
    private val deletionCoordinator = ConversationDeletionCoordinator()

    private val _states = MutableStateFlow<Map<Long, ConversationGenerationState>>(emptyMap())
    val states: StateFlow<Map<Long, ConversationGenerationState>> = _states.asStateFlow()

    private val draftStore = context?.let(::PersistentComposerDraftStore)
    private var draftPersistJob: Job? = null
    private var draftWriteId = 0L
    private val _composerDrafts = MutableStateFlow(emptyMap<Long, ComposerDraftState>())
    val composerDrafts: StateFlow<Map<Long, ComposerDraftState>> = _composerDrafts.asStateFlow()

    private val _currentConversationId = MutableStateFlow(
        savedStateHandle[CURRENT_CONVERSATION_KEY] ?: NEW_CONVERSATION_ID
    )
    val currentConversationId: StateFlow<Long> = _currentConversationId.asStateFlow()

    init {
        draftStore?.let { store ->
            viewModelScope.launch(Dispatchers.IO) {
                val restored = store.load()
                _composerDrafts.update { current ->
                    restored + current.mapValues { (id, draft) ->
                        val stored = restored[id]
                        if (stored == null || draft.version >= stored.version) draft else stored
                    }
                }
            }
        }
    }

    fun selectConversation(conversationId: Long) {
        _currentConversationId.value = conversationId
        savedStateHandle[CURRENT_CONVERSATION_KEY] = conversationId
    }

    /** Snapshot of [conversationId], or a default if none has been recorded. */
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

    /** Remove the exact terminal request instead of retaining an empty map entry. */
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
    ): Deferred<ConversationDeletionResult> = viewModelScope.async {
        deletionCoordinator.delete(
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
    }

    fun draft(conversationId: Long): ComposerDraftState =
        _composerDrafts.value[conversationId] ?: ComposerDraftState()

    fun updateDraftText(conversationId: Long, rawInput: String) {
        updateDraft(conversationId, immediate = false) { current ->
            current.copy(rawInput = rawInput, version = current.version + 1L)
        }
    }

    fun appendQuickPrompt(conversationId: Long, prompt: String, append: (String, String) -> String) {
        updateDraft(conversationId, immediate = false) { current ->
            current.copy(
                rawInput = append(current.rawInput, prompt),
                version = current.version + 1L,
            )
        }
    }

    fun removePendingAttachment(conversationId: Long, localPath: String): Boolean {
        var removed = false
        updateDraft(conversationId, immediate = true) { current ->
            val remaining = current.pendingAttachments.filterNot {
                if (!removed && it.localPath == localPath) {
                    removed = true
                    true
                } else {
                    false
                }
            }
            if (!removed) current else current.copy(
                pendingAttachments = remaining,
                version = current.version + 1L,
            )
        }
        return removed
    }

    fun beginAttachmentImport(conversationId: Long): ComposerImportOwnership {
        val owner = ComposerImportOwnership(
            token = UUID.randomUUID().toString(),
            status = ComposerImportStatus.PICKER_OPEN,
        )
        updateDraft(conversationId, immediate = true) { current ->
            current.copy(importOwnership = owner, version = current.version + 1L)
        }
        return owner
    }

    fun pendingPickerImport(): Pair<Long, ComposerImportOwnership>? =
        _composerDrafts.value.entries.firstNotNullOfOrNull { (conversationId, draft) ->
            draft.importOwnership
                ?.takeIf { it.status == ComposerImportStatus.PICKER_OPEN }
                ?.let { conversationId to it }
        }

    fun beginAttachmentCopy(conversationId: Long, token: String): Boolean {
        var accepted = false
        updateDraft(conversationId, immediate = true) { current ->
            val owner = current.importOwnership
            if (owner?.token != token) return@updateDraft current
            accepted = true
            current.copy(
                importOwnership = owner.copy(status = ComposerImportStatus.COPYING),
                version = current.version + 1L,
            )
        }
        return accepted
    }

    fun completeAttachmentImport(
        conversationId: Long,
        token: String,
        attachments: List<PendingAttachmentIdentity>,
    ): Boolean {
        var accepted = false
        updateDraft(conversationId, immediate = true) { current ->
            if (current.importOwnership?.token != token) return@updateDraft current
            accepted = true
            current.copy(
                pendingAttachments = current.pendingAttachments + attachments,
                importOwnership = null,
                version = current.version + 1L,
            )
        }
        return accepted
    }

    fun settleAttachmentImport(conversationId: Long, token: String): Boolean {
        var accepted = false
        updateDraft(conversationId, immediate = true) { current ->
            if (current.importOwnership?.token != token) return@updateDraft current
            accepted = true
            current.copy(importOwnership = null, version = current.version + 1L)
        }
        return accepted
    }

    fun beginSubmission(conversationId: Long): ComposerSubmissionSnapshot? {
        var snapshot: ComposerSubmissionSnapshot? = null
        updateDraft(conversationId, immediate = true) { current ->
            if (current.activeSubmissionToken != null) return@updateDraft current
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
        return snapshot
    }

    fun settleSubmissionOwner(conversationId: Long, token: String) {
        updateDraft(conversationId, immediate = true) { current ->
            if (current.activeSubmissionToken != token) current
            else current.copy(activeSubmissionToken = null)
        }
    }

    /**
     * Reconcile only the captured draft key and exact submission owner. Later
     * edits survive; attachment paths committed by this submission are removed.
     */
    fun reconcileCommittedSubmission(
        snapshot: ComposerSubmissionSnapshot,
        committedPaths: List<String>,
    ): Boolean {
        var accepted = false
        updateDraft(snapshot.conversationId, immediate = true) { current ->
            if (current.activeSubmissionToken != snapshot.token) return@updateDraft current
            accepted = true
            val committed = committedPaths.toSet()
            current.copy(
                rawInput = if (current.rawInput == snapshot.rawInput) "" else current.rawInput,
                pendingAttachments = current.pendingAttachments.filterNot { it.localPath in committed },
                activeSubmissionToken = null,
                version = current.version + 1L,
            )
        }
        return accepted
    }

    fun clearDraft(conversationId: Long) {
        _composerDrafts.update { current ->
            if (conversationId !in current) current else current - conversationId
        }
        scheduleDraftPersist(immediate = true)
    }

    private inline fun updateDraft(
        conversationId: Long,
        immediate: Boolean,
        transform: (ComposerDraftState) -> ComposerDraftState,
    ) {
        _composerDrafts.update { current ->
            val existing = current[conversationId] ?: ComposerDraftState()
            val updated = transform(existing)
            if (updated == existing) current else current + (conversationId to updated)
        }
        scheduleDraftPersist(immediate)
    }

    private fun scheduleDraftPersist(immediate: Boolean) {
        val store = draftStore ?: return
        val snapshot = _composerDrafts.value
        val writeId = ++draftWriteId
        draftPersistJob?.cancel()
        draftPersistJob = viewModelScope.launch(Dispatchers.IO) {
            if (!immediate) delay(DRAFT_PERSIST_DEBOUNCE_MS)
            store.write(writeId, snapshot)
        }
    }

    companion object {
        const val NEW_CONVERSATION_ID = 0L
        private const val CURRENT_CONVERSATION_KEY = "current_conversation_id"
        private const val DRAFT_PERSIST_DEBOUNCE_MS = 300L

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
    }
}
