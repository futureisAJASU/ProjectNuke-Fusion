package com.projectnuke.fusion.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single serialized owner of every draft mutation and of hydration.
 *
 * Hydration is a command in the same channel as every mutation, so hydration can never
 * deadlock against a mutation and no lock is ever held while awaiting hydration. Every
 * mutation is applied by the single consumer coroutine in queue order, which makes all
 * hydration, text updates, attachment import transitions, submission ownership
 * transitions, committed reconciliation, draft clears and persistence totally ordered.
 *
 * Durable mutations that arrive before hydration are applied in memory, deferred and
 * written once after hydration merges the restored state. A failed write completes every
 * deferred reply with `false` and rolls the in-memory map back to the exact state that
 * existed before the first deferred mutation.
 */
internal class ChatDraftStateMachine(
    private val store: PersistentComposerDraftStore?,
    private val scope: CoroutineScope,
) {
    private val commands = Channel<DraftCommand>(Channel.UNLIMITED)
    private val _drafts = MutableStateFlow(emptyMap<Long, ComposerDraftState>())
    val drafts: StateFlow<Map<Long, ComposerDraftState>> = _drafts.asStateFlow()
    private val hydrated = CompletableDeferred<Unit>()
    private var debounceJob: Job? = null
    private var hydrationApplied = false
    private val tombstoned = mutableSetOf<Long>()
    private val preHydrationBaseline = mutableMapOf<Long, ComposerDraftState>()
    private val deferredDurable = mutableListOf<Pair<CompletableDeferred<Boolean>, Map<Long, ComposerDraftState>>>()

    init {
        scope.launch {
            val restored = store?.load().orEmpty()
            commands.send(DraftCommand.Hydrate(restored))
            for (command in commands) {
                process(command)
            }
        }
    }

    fun updateText(conversationId: Long, transform: (ComposerDraftState) -> ComposerDraftState) {
        commands.trySend(
            DraftCommand.Mutate(
                conversationId = conversationId,
                durable = false,
                transform = transform,
                reply = null,
            )
        )
        scheduleDebouncedPersist(DRAFT_PERSIST_DEBOUNCE_MS)
    }

    suspend fun updateImmediate(conversationId: Long, transform: (ComposerDraftState) -> ComposerDraftState): Boolean {
        val reply = CompletableDeferred<Boolean>()
        commands.trySend(
            DraftCommand.Mutate(
                conversationId = conversationId,
                durable = false,
                transform = transform,
                reply = reply,
            )
        )
        scheduleDebouncedPersist(0L)
        return reply.await()
    }

    fun queueImmediate(conversationId: Long, transform: (ComposerDraftState) -> ComposerDraftState) {
        commands.trySend(
            DraftCommand.Mutate(
                conversationId = conversationId,
                durable = false,
                transform = transform,
                reply = null,
            )
        )
        scheduleDebouncedPersist(0L)
    }

    suspend fun updateCritical(
        conversationId: Long,
        transform: (ComposerDraftState) -> ComposerDraftState?,
    ): Boolean {
        val reply = CompletableDeferred<Boolean>()
        commands.trySend(
            DraftCommand.Mutate(
                conversationId = conversationId,
                durable = true,
                transform = transform,
                reply = reply,
            )
        )
        return reply.await()
    }

    suspend fun clearDraft(conversationId: Long): Boolean {
        val reply = CompletableDeferred<Boolean>()
        commands.trySend(DraftCommand.Clear(conversationId, reply))
        return reply.await()
    }

    suspend fun reconcileCommittedSubmission(
        draftKey: Long,
        token: String,
        capturedRawInput: String,
        committedPaths: Set<String>,
    ): Boolean {
        val reply = CompletableDeferred<Boolean>()
        commands.trySend(
            DraftCommand.ReconcileCommitted(
                draftKey = draftKey,
                token = token,
                capturedRawInput = capturedRawInput,
                committedPaths = committedPaths,
                reply = reply,
            )
        )
        return reply.await()
    }

    fun importOwnership(conversationId: Long): ComposerImportOwnership? =
        drafts.value[conversationId]?.importOwnership

    private suspend fun process(command: DraftCommand) {
        when (command) {
            is DraftCommand.Hydrate -> hydrate(command)
            is DraftCommand.Mutate -> mutate(command)
            is DraftCommand.Clear -> clear(command)
            is DraftCommand.ReconcileCommitted -> reconcileCommitted(command)
            is DraftCommand.PersistDebounced -> persistDebounced(command)
        }
    }

    private suspend fun hydrate(command: DraftCommand.Hydrate) {
        val tombstoneSnapshot = tombstoned.toSet()
        val merged = LinkedHashMap(_drafts.value)
        command.restored.forEach { (id, restored) ->
            if (id in tombstoneSnapshot) return@forEach
            val current = merged[id]
            if (current == null) {
                merged[id] = restored
            } else {
                merged[id] = mergeFields(preHydrationBaseline[id] ?: ComposerDraftState(), current, restored)
            }
        }
        tombstoned.clear()
        _drafts.value = merged
        hydrationApplied = true
        if (deferredDurable.isNotEmpty()) {
            val ok = durableWrite()
            deferredDurable.forEach { (reply, _) -> reply.complete(ok) }
            if (!ok) {
                val rollback = LinkedHashMap(deferredDurable.first().second)
                command.restored.forEach { (id, restored) ->
                    if (id in tombstoneSnapshot) return@forEach
                    val current = rollback[id]
                    if (current == null) {
                        rollback[id] = restored
                    } else {
                        rollback[id] = mergeFields(
                            preHydrationBaseline[id] ?: ComposerDraftState(),
                            current,
                            restored,
                        )
                    }
                }
                _drafts.value = rollback
            }
            deferredDurable.clear()
        }
        preHydrationBaseline.clear()
        hydrated.complete(Unit)
    }

    private fun mergeFields(
        baseline: ComposerDraftState,
        current: ComposerDraftState,
        restored: ComposerDraftState,
    ): ComposerDraftState = ComposerDraftState(
        rawInput = if (current.rawInput != baseline.rawInput) current.rawInput else restored.rawInput,
        pendingAttachments = if (current.pendingAttachments != baseline.pendingAttachments) {
            current.pendingAttachments
        } else {
            restored.pendingAttachments
        },
        version = if (current.version != baseline.version) current.version else restored.version,
        importOwnership = if (current.importOwnership != baseline.importOwnership) {
            current.importOwnership
        } else {
            restored.importOwnership
        },
        activeSubmissionToken = if (current.activeSubmissionToken != baseline.activeSubmissionToken) {
            current.activeSubmissionToken
        } else {
            restored.activeSubmissionToken
        },
    )

    private suspend fun mutate(command: DraftCommand.Mutate) {
        val conversationId = command.conversationId
        if (!hydrationApplied && conversationId in tombstoned) {
            command.reply?.complete(false)
            return
        }
        val before = _drafts.value
        if (!hydrationApplied && conversationId !in preHydrationBaseline) {
            preHydrationBaseline[conversationId] = before[conversationId] ?: ComposerDraftState()
        }
        val existing = before[conversationId] ?: ComposerDraftState()
        val result = command.transform(existing)
        if (result == null || result == existing) {
            command.reply?.complete(false)
            return
        }
        _drafts.value = before + (conversationId to result)
        if (!command.durable) {
            command.reply?.complete(true)
            return
        }
        if (!hydrationApplied) {
            deferredDurable += command.reply!! to before
            return
        }
        if (durableWrite()) {
            command.reply?.complete(true)
        } else {
            _drafts.value = before
            command.reply?.complete(false)
        }
    }

    private suspend fun clear(command: DraftCommand.Clear) {
        val before = _drafts.value
        if (!hydrationApplied && command.conversationId !in preHydrationBaseline) {
            preHydrationBaseline[command.conversationId] =
                before[command.conversationId] ?: ComposerDraftState()
        }
        val updated = if (before.containsKey(command.conversationId)) before - command.conversationId else before
        _drafts.value = updated
        if (!hydrationApplied) {
            tombstoned += command.conversationId
            deferredDurable += command.reply!! to before
            return
        }
        if (durableWrite()) {
            command.reply?.complete(true)
        } else {
            _drafts.value = before
            command.reply?.complete(false)
        }
    }

    private suspend fun persistDebounced(command: DraftCommand.PersistDebounced) {
        if (!hydrationApplied) return
        durableWrite()
    }

    private suspend fun reconcileCommitted(command: DraftCommand.ReconcileCommitted) {
        val before = _drafts.value
        val current = before[command.draftKey]
        if (current == null || current.activeSubmissionToken != command.token) {
            command.reply.complete(true)
            return
        }
        val updated = current.copy(
            rawInput = if (current.rawInput == command.capturedRawInput) "" else current.rawInput,
            pendingAttachments = current.pendingAttachments.filterNot {
                it.localPath in command.committedPaths
            },
            activeSubmissionToken = null,
            version = current.version + 1L,
        )
        _drafts.value = before + (command.draftKey to updated)
        if (durableWrite()) {
            command.reply.complete(true)
        } else {
            _drafts.value = before
            command.reply.complete(false)
        }
    }

    private suspend fun durableWrite(): Boolean {
        val store = store ?: return true
        return store.write(_drafts.value)
    }

    private fun scheduleDebouncedPersist(debounceMs: Long) {
        val store = store ?: return
        debounceJob?.cancel()
        debounceJob = scope.launch(Dispatchers.IO) {
            hydrated.await()
            if (debounceMs > 0L) delay(debounceMs)
            commands.trySend(DraftCommand.PersistDebounced(store))
        }
    }

    companion object {
        const val DRAFT_PERSIST_DEBOUNCE_MS = 300L
    }
}

internal sealed interface DraftCommand {
    data class Hydrate(val restored: Map<Long, ComposerDraftState>) : DraftCommand

    data class Mutate(
        val conversationId: Long,
        val durable: Boolean,
        val transform: (ComposerDraftState) -> ComposerDraftState?,
        val reply: CompletableDeferred<Boolean>?,
    ) : DraftCommand

    data class Clear(
        val conversationId: Long,
        val reply: CompletableDeferred<Boolean>?,
    ) : DraftCommand

    data class ReconcileCommitted(
        val draftKey: Long,
        val token: String,
        val capturedRawInput: String,
        val committedPaths: Set<String>,
        val reply: CompletableDeferred<Boolean>,
    ) : DraftCommand

    data class PersistDebounced(val store: PersistentComposerDraftStore) : DraftCommand
}
