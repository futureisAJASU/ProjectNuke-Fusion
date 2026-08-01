package com.projectnuke.fusion.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val deletionOwners = ConcurrentHashMap<Long, String>()

    internal class PendingStart(
        val conversationId: Long,
        val requestId: String
    ) {
        private val state = AtomicReference(State.PENDING)
        val completed = CompletableDeferred<Unit>()
        val publishedSession = AtomicReference<GenerationSession?>()

        @Volatile
        var predecessor: PendingStart? = null

        /**
         * True when this start's token was claimed while a deletion owned the
         * conversation. The refusal survives the deletion's release so a start
         * that began during a deletion can never install afterwards.
         */
        @Volatile
        var claimedDuringDeletion: Boolean = false

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

    /**
     * Claims exclusive deferred ownership of a conversation for an in-flight
     * deletion. While owned, [start] refuses to install a new session for the
     * conversation so a generation can never outlive a deletion that already
     * began cancelling it. Must be paired with [releaseDeletionOwnership].
     */
    fun claimDeletionOwnership(conversationId: Long, reason: String): Boolean =
        deletionOwners.putIfAbsent(conversationId, reason) == null

    fun releaseDeletionOwnership(conversationId: Long, reason: String) {
        deletionOwners.remove(conversationId, reason)
    }

    internal fun deletionOwner(conversationId: Long): String? =
        deletionOwners[conversationId]

    suspend fun start(
        scope: CoroutineScope,
        snapshot: GenerationRequestSnapshot,
        block: suspend (GenerationSession) -> Unit
    ): GenerationSession {
        val conversationId = snapshot.conversationId
        val requestId = snapshot.requestId
        val lock = stripeFor(conversationId)

        val token = PendingStart(conversationId, requestId)

        try {
            latestTokens.compute(conversationId) { _, previous ->
                token.predecessor = previous
                previous?.cancel("superseded-by-$requestId")
                token.claimedDuringDeletion = deletionOwners.containsKey(conversationId)
                token
            }

            return lock.withLock {
                if (token.claimedDuringDeletion) {
                    throw CancellationException(
                        "Conversation $conversationId was being deleted when request $requestId began"
                    )
                }
                deletionOwners[conversationId]?.let { ownerReason ->
                    throw CancellationException(
                        "Conversation $conversationId is being deleted ($ownerReason)"
                    )
                }

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
                var owner = sessions.putIfAbsent(conversationId, gs)
                if (owner != null && owner.job.isCompleted) {
                    sessions.remove(conversationId, owner)
                    owner = sessions.putIfAbsent(conversationId, gs)
                }
                if (owner != null) {
                    sessions.remove(conversationId, gs)
                    if (!coroutineJob.isCompleted) {
                        coroutineJob.cancel(CancellationException(
                            "Request $requestId lost the conversation slot to ${owner.requestId}"
                        ))
                        coroutineJob.join()
                    }
                    throw CancellationException(
                        "Request $requestId superseded by ${owner.requestId} before install"
                    )
                }

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
 } catch (e: CancellationException) {
    withContext(NonCancellable) {
      if (token.state() != PendingStart.State.INSTALLED) {
        token.publishedSession.get()?.let { session ->
          sessions.remove(conversationId, session)
          if (!session.job.isCompleted) {
            session.job.cancel(CancellationException("start-cancelled"))
            session.job.join()
          }
        }
        val settled = mutableListOf<PendingStart>()
        settlePredecessorChain(token, "start-cancelled", settled)
        settled.forEach { it.completed.await() }
      }
    }
    throw e
  } finally {
            if (token.state() != PendingStart.State.INSTALLED) {
                latestTokens.remove(conversationId, token)
                token.publishedSession.get()?.let { session ->
                    sessions.remove(conversationId, session)
                    if (!session.job.isCompleted) {
                        session.job.cancel(CancellationException("start-aborted"))
                    }
                }
            }
            token.completed.complete(Unit)
        }
    }

    private fun GenerationSession.isBusy(): Boolean =
        job.isActive && !job.isCancelled

    fun activeSession(conversationId: Long): GenerationSession? =
        sessions[conversationId]?.takeIf { it.isBusy() }

    fun isActive(conversationId: Long, requestId: String): Boolean =
        sessions[conversationId]?.let {
            it.requestId == requestId && it.isBusy()
        } == true

    fun cancel(conversationId: Long, reason: String): Boolean {
        val session = sessions[conversationId] ?: return false
        if (!session.isBusy()) return false
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
            PendingStart.State.CANCELLED -> {
                if (!head.completed.isCompleted) {
                    settled.add(head)
                }
            }
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
            PendingStart.State.CANCELLED -> {
                if (head.completed.isCompleted) return false
                settled.add(head)
            }
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
                PendingStart.State.CANCELLED -> {
                    if (!pred.completed.isCompleted) {
                        if (out.none { it === pred }) {
                            out.add(pred)
                        }
                    }
                }
            }
            pred = pred.predecessor
        }
    }

    fun hasActiveSession(conversationId: Long): Boolean =
        sessions[conversationId]?.isBusy() == true

    fun activeConversationIds(): Set<Long> =
        sessions.filterValues { it.isBusy() }.keys.toSet()

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
