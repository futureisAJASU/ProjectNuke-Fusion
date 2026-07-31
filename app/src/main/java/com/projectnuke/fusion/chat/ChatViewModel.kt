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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val draftStore: PersistentComposerDraftStore? = context?.let(::PersistentComposerDraftStore)
    private var draftPersistJob: Job? = null
    private var draftWriteId = 0L
    private val draftHydrated = CompletableDeferred<Unit>()
    private val draftMutationLock = Mutex()
    private val pendingMutations = mutableMapOf<Long, (ComposerDraftState?) -> ComposerDraftState?>()
    private val deletedBeforeHydration = mutableSetOf<Long>()
    private val _composerDrafts = MutableStateFlow(emptyMap<Long, ComposerDraftState>())
    val composerDrafts: StateFlow<Map<Long, ComposerDraftState>> = _composerDrafts.asStateFlow()

    private val _currentConversationId = MutableStateFlow(
        savedStateHandle[CURRENT_CONVERSATION_KEY] ?: NEW_CONVERSATION_ID
    )
    val currentConversationId: StateFlow<Long> = _currentConversationId.asStateFlow()

    init {
        draftStore?.let { store ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val restored = store.load()
                    draftMutationLock.withLock {
                        val merged = restored.toMutableMap()
                        for ((id, mutation) in pendingMutations) {
                            val current = merged[id]
                            val result = mutation(current)
                            if (result != null) merged[id] = result
                            else merged.remove(id)
                        }
                        pendingMutations.clear()
                        val tombstones = deletedBeforeHydration.toSet()
                        _composerDrafts.update { current ->
                            val result = current.toMutableMap()
                            tombstones.forEach { result.remove(it) }
                            merged.forEach { (id, draft) -> result[id] = draft }
                            result
                        }
                    }
                } finally {
                    draftHydrated.complete(Unit)
                }
            }
        } ?: draftHydrated.complete(Unit)
    }

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
        }
        val previous = deletionDeferreds.putIfAbsent(conversationId, deferred)
        if (previous != null) {
            deferred.cancel()
            return previous
        }
        return deferred
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

    suspend fun removePendingAttachment(conversationId: Long, localPath: String): Boolean {
        var removed = false
        return updateDraftCritical(conversationId) { current ->
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

    suspend fun completeAttachmentImport(
        conversationId: Long,
        token: String,
        attachments: List<PendingAttachmentIdentity>,
    ): Boolean {
        var accepted = false
        val persisted = updateDraftCritical(conversationId) { current ->
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
        return updateDraftCritical(conversationId) { current ->
            if (current.importOwnership?.token != token) null
            else {
                accepted = true
                current.copy(importOwnership = null, version = current.version + 1L)
            }
        } && accepted
    }

    suspend fun beginSubmission(conversationId: Long): ComposerSubmissionSnapshot? {
        var snapshot: ComposerSubmissionSnapshot? = null
        val persisted = updateDraftCritical(conversationId) { current ->
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
        return updateDraftCritical(conversationId) { current ->
            if (current.activeSubmissionToken != token) null
            else current.copy(activeSubmissionToken = null)
        }
    }

    suspend fun reconcileCommittedSubmission(
        snapshot: ComposerSubmissionSnapshot,
        committedPaths: List<String>,
    ): Boolean {
        var accepted = false
        return updateDraftCritical(snapshot.conversationId) { current ->
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

    suspend fun clearDraft(conversationId: Long): Boolean {
        draftMutationLock.withLock {
            if (draftHydrated.isCompleted) {
                _composerDrafts.update { current ->
                    if (conversationId !in current) current else current - conversationId
                }
            } else {
                deletedBeforeHydration += conversationId
                pendingMutations.remove(conversationId)
                _composerDrafts.update { current ->
                    if (conversationId !in current) current else current - conversationId
                }
            }
        }
        return persistDraftCritical()
    }

    private inline fun updateDraft(
        conversationId: Long,
        immediate: Boolean,
        crossinline transform: (ComposerDraftState) -> ComposerDraftState,
    ) {
        val now = draftHydrated.isCompleted
        if (!now) {
            viewModelScope.launch {
                draftMutationLock.withLock {
                    pendingMutations[conversationId] = { current ->
                        val base = current ?: ComposerDraftState()
                        transform(base)
                    }
                }
            }
        }
        _composerDrafts.update { current ->
            val existing = current[conversationId] ?: ComposerDraftState()
            val updated = transform(existing)
            if (updated == existing) current else current + (conversationId to updated)
        }
        scheduleDraftPersist(immediate)
    }

    private suspend fun updateDraftCritical(
        conversationId: Long,
        transform: (ComposerDraftState) -> ComposerDraftState?,
    ): Boolean = draftMutationLock.withLock {
        if (!draftHydrated.isCompleted) {
            if (conversationId in deletedBeforeHydration) return@withLock false
            pendingMutations[conversationId] = { current ->
                val base = current ?: ComposerDraftState()
                transform(base)
            }
        }
        var changed = false
        _composerDrafts.update { current ->
            val existing = current[conversationId] ?: ComposerDraftState()
            val result = transform(existing) ?: return@update current
            if (result == existing) return@update current
            changed = true
            current + (conversationId to result)
        }
        if (!changed) return@withLock false
        val writeId = ++draftWriteId
        draftPersistJob?.cancel()
        val store = draftStore ?: return@withLock true
        draftHydrated.await()
        store.write(writeId, _composerDrafts.value)
    }

    private suspend fun persistDraftCritical(): Boolean {
        val store = draftStore ?: return true
        val writeId = ++draftWriteId
        draftPersistJob?.cancel()
        draftHydrated.await()
        return store.write(writeId, _composerDrafts.value)
    }

    private fun scheduleDraftPersist(immediate: Boolean) {
        val store = draftStore ?: return
        val snapshot = _composerDrafts.value
        val writeId = ++draftWriteId
        draftPersistJob?.cancel()
        if (immediate) {
            draftPersistJob = viewModelScope.launch(Dispatchers.IO) {
                draftHydrated.await()
                store.write(writeId, _composerDrafts.value)
            }
        } else {
            draftPersistJob = viewModelScope.launch(Dispatchers.IO) {
                draftHydrated.await()
                delay(DRAFT_PERSIST_DEBOUNCE_MS)
                store.write(writeId, _composerDrafts.value)
            }
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

        internal fun forTesting(store: PersistentComposerDraftStore): ChatViewModel =
            ChatViewModel().apply { draftStoreField.set(this, store) }

        private val draftStoreField by lazy {
            ChatViewModel::class.java.getDeclaredField("draftStore").apply { isAccessible = true }
        }
    }
}
