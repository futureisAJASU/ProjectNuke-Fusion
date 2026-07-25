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
    private val latestTokens = ConcurrentHashMap<Long, PendingStart>()
    private val lockStripes = Array(16) { Mutex() }

    internal class PendingStart(
        val conversationId: Long,
        val requestId: String
    ) {
        private val state = AtomicReference(State.PENDING)
        val completed = CompletableDeferred<Unit>()
        val publishedSession = AtomicReference<GenerationSession?>()

        @Volatile
        var predecessor: PendingStart? = null

        enum class State { PENDING, STARTING, INSTALLED, CANCELLED }

        fun cancel(reason: String): Boolean {
            while (true) {
                val current = state.get()
                if (current == State.INSTALLED || current == State.CANCELLED) return false
                if (state.compareAndSet(current, State.CANCELLED)) return true
            }
        }

        fun tryBeginStarting(): Boolean =
            state.compareAndSet(State.PENDING, State.STARTING)

        fun tryInstall(): Boolean =
            state.compareAndSet(State.STARTING, State.INSTALLED)

        fun cancellationException(): CancellationException =
            CancellationException("Request $requestId was cancelled before start")

        fun state(): State = state.get()
    }

    internal var onBeforeInstall: suspend () -> Unit = {}

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

        val replaced = latestTokens.put(conversationId, token)
        token.predecessor = replaced
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

                token.publishedSession.set(gs)
                sessions[conversationId] = gs

                onBeforeInstall()

                if (!token.tryInstall()) {
                    sessions.remove(conversationId, gs)
                    if (!coroutineJob.isCompleted) {
                        coroutineJob.cancel(CancellationException(
                            "Request $requestId was cancelled before install"
                        ))
                        coroutineJob.join()
                    }
                    throw token.cancellationException()
                }

                gs.job.invokeOnCompletion {
                    latestTokens.remove(conversationId, token)
                    sessions.remove(conversationId, gs)
                }

                if (!gs.job.start()) {
                    sessions.remove(conversationId, gs)
                    latestTokens.remove(conversationId, token)
                    throw CancellationException(
                        "Scope cancelled before session could start for conversation $conversationId"
                    )
                }

                gs
            }
        } finally {
            if (token.state() != PendingStart.State.INSTALLED) {
                latestTokens.remove(conversationId, token)
            }
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
        val head = latestTokens[conversationId]
        if (head == null) {
            val lock = stripeFor(conversationId)
            return lock.withLock {
                val session = sessions[conversationId]
                if (session != null && !session.job.isCompleted) {
                    session.job.cancel(CancellationException(reason))
                    session.job.join()
                    true
                } else false
            }
        }

        val settled = mutableListOf<PendingStart>()

        when (head.state()) {
            PendingStart.State.INSTALLED -> {
                head.publishedSession.get()?.let { session ->
                    if (!session.job.isCompleted) {
                        session.job.cancel(CancellationException(reason))
                        session.job.join()
                    }
                }
            }
            PendingStart.State.CANCELLED -> {}
            else -> {
                head.cancel(reason)
                settled.add(head)
            }
        }

        settlePredecessorChain(head, reason, settled)

        settled.forEach { it.completed.await() }

        return true
    }

    suspend fun cancelAndJoin(
        conversationId: Long,
        requestId: String,
        reason: String
    ): Boolean {
        val head = latestTokens[conversationId]
        if (head == null) return false
        if (head.requestId != requestId) return false

        val settled = mutableListOf<PendingStart>()

        when (head.state()) {
            PendingStart.State.INSTALLED -> {
                head.publishedSession.get()?.let { session ->
                    if (!session.job.isCompleted) {
                        session.job.cancel(CancellationException(reason))
                        session.job.join()
                    }
                }
            }
            PendingStart.State.CANCELLED -> return false
            else -> {
                head.cancel(reason)
                settled.add(head)
            }
        }

        settlePredecessorChain(head, reason, settled)

        settled.forEach { it.completed.await() }

        return true
    }

    private suspend fun settlePredecessorChain(
        from: PendingStart,
        reason: String,
        out: MutableList<PendingStart>
    ) {
        var pred = from.predecessor
        while (pred != null) {
            when (pred.state()) {
                PendingStart.State.PENDING,
                PendingStart.State.STARTING -> {
                    pred.cancel(reason)
                    out.add(pred)
                }
                PendingStart.State.INSTALLED -> {
                    pred.publishedSession.get()?.let { session ->
                        if (!session.job.isCompleted) {
                            session.job.cancel(CancellationException(reason))
                            session.job.join()
                        }
                    }
                }
                PendingStart.State.CANCELLED -> {}
            }
            pred = pred.predecessor
        }
    }

    fun hasActiveSession(conversationId: Long): Boolean =
        sessions[conversationId]?.job?.isActive == true

    fun activeConversationIds(): Set<Long> =
        sessions.filterValues { it.job.isActive }.keys.toSet()

    internal fun isPending(conversationId: Long, requestId: String): Boolean {
        val token = latestTokens[conversationId] ?: return false
        return token.requestId == requestId && token.state() == PendingStart.State.PENDING
    }

    internal fun isStarting(conversationId: Long, requestId: String): Boolean {
        val token = latestTokens[conversationId] ?: return false
        return token.requestId == requestId && token.state() == PendingStart.State.STARTING
    }

    internal fun hasSessionPublished(conversationId: Long, requestId: String): Boolean {
        val token = latestTokens[conversationId] ?: return false
        if (token.requestId != requestId) return false
        return token.publishedSession.get() != null
    }

    internal fun latestToken(conversationId: Long): PendingStart? =
        latestTokens[conversationId]
}
