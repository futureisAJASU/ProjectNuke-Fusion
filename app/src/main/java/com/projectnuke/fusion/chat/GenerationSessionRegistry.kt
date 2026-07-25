package com.projectnuke.fusion.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class GenerationSession(
    val conversationId: Long,
    val requestId: String,
    val job: Job,
)

class GenerationSessionRegistry {
    private val sessions = ConcurrentHashMap<Long, GenerationSession>()
    private val pendingTokens = ConcurrentHashMap<Long, PendingStart>()
    private val lockStripes = Array(16) { Mutex() }

    private class PendingStart(
        val conversationId: Long,
        val requestId: String
    ) {
        private val state = AtomicReference(State.PENDING)
        val completed = CompletableDeferred<Unit>()

        enum class State { PENDING, STARTING, CANCELLED }

        fun cancel(reason: String): Boolean {
            while (true) {
                val current = state.get()
                if (current != State.PENDING) return false
                if (state.compareAndSet(current, State.CANCELLED)) return true
            }
        }

        fun tryBeginStarting(): Boolean =
            state.compareAndSet(State.PENDING, State.STARTING)

        fun cancellationException(): CancellationException =
            CancellationException("Request $requestId was cancelled before start")

        fun isPendingState(): Boolean =
            state.get() == State.PENDING
    }

    private fun stripeFor(conversationId: Long): Mutex =
        lockStripes[(conversationId and 15L).toInt()]

    suspend fun start(
        scope: CoroutineScope,
        snapshot: GenerationRequestSnapshot,
        block: suspend (GenerationSession) -> Unit
    ): GenerationSession {
        val conversationId = snapshot.conversationId
        val requestId = snapshot.requestId
        val lock = stripeFor(conversationId)

        val token = PendingStart(conversationId, requestId)

        val replaced = pendingTokens.put(conversationId, token)
        if (replaced != null) {
            replaced.cancel("superseded-by-${requestId}")
        }

        try {
            return lock.withLock {
                if (!token.tryBeginStarting()) {
                    throw token.cancellationException()
                }

                val previous = sessions[conversationId]
                if (previous != null && !previous.job.isCompleted) {
                    previous.job.cancel(CancellationException("superseded-by-${requestId}"))
                    previous.job.join()
                }

                if (!scope.coroutineContext[Job]!!.isActive) {
                    throw CancellationException(
                        "Scope cancelled before session could start for conversation $conversationId"
                    )
                }

                lateinit var gs: GenerationSession

                val coroutineJob = scope.launch(start = CoroutineStart.LAZY) {
                    block(gs)
                }

                gs = GenerationSession(
                    conversationId = conversationId,
                    requestId = requestId,
                    job = coroutineJob,
                )
                sessions[conversationId] = gs
                pendingTokens.remove(conversationId, token)

                gs.job.invokeOnCompletion {
                    sessions.remove(conversationId, gs)
                }

                if (!gs.job.start()) {
                    sessions.remove(conversationId, gs)
                    pendingTokens.remove(conversationId, token)
                    throw CancellationException(
                        "Scope cancelled before session could start for conversation $conversationId"
                    )
                }

                gs
            }
        } finally {
            pendingTokens.remove(conversationId, token)
            token.completed.complete(Unit)
        }
    }

    fun activeSession(conversationId: Long): GenerationSession? =
        sessions[conversationId]?.takeIf { it.job.isActive }

    fun isActive(conversationId: Long, requestId: String): Boolean =
        sessions[conversationId]?.let {
            it.requestId == requestId && it.job.isActive
        } == true

    fun cancel(conversationId: Long, reason: String): Boolean {
        val session = sessions[conversationId] ?: return false
        if (!session.job.isActive) return false
        session.job.cancel(CancellationException(reason))
        return true
    }

    suspend fun cancelAndJoin(conversationId: Long, reason: String): Boolean {
        val captured = pendingTokens[conversationId]
        val capturedPending = if (captured != null && captured.cancel(reason)) captured else null

        val lock = stripeFor(conversationId)
        val joinedActive = lock.withLock {
            val session = sessions[conversationId]
            if (session != null && !session.job.isCompleted) {
                session.job.cancel(CancellationException(reason))
                session.job.join()
                true
            } else {
                false
            }
        }

        capturedPending?.completed?.await()

        return joinedActive || capturedPending != null
    }

    suspend fun cancelAndJoin(
        conversationId: Long,
        requestId: String,
        reason: String
    ): Boolean {
        val token = pendingTokens[conversationId]
        if (token != null && token.requestId == requestId) {
            if (token.cancel(reason)) {
                token.completed.await()
                return true
            }
        }

        val lock = stripeFor(conversationId)
        var pendingToAwait: PendingStart? = null
        val result = lock.withLock {
            val session = sessions[conversationId]
            if (session != null) {
                if (session.requestId != requestId) return@withLock false
                if (session.job.isCompleted) return@withLock false
                session.job.cancel(CancellationException(reason))
                session.job.join()
                true
            } else {
                val token2 = pendingTokens[conversationId]
                if (token2 != null && token2.requestId == requestId) {
                    if (token2.cancel(reason)) {
                        pendingToAwait = token2
                        true
                    } else false
                } else false
            }
        }

        pendingToAwait?.completed?.await()
        return result
    }

    fun hasActiveSession(conversationId: Long): Boolean =
        sessions[conversationId]?.job?.isActive == true

    fun activeConversationIds(): Set<Long> =
        sessions.filterValues { it.job.isActive }.keys.toSet()

    internal fun isPending(conversationId: Long, requestId: String): Boolean {
        val token = pendingTokens[conversationId] ?: return false
        return token.requestId == requestId && token.isPendingState()
    }
}
