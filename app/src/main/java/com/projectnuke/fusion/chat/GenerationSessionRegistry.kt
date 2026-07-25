package com.projectnuke.fusion.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class GenerationSession(
    val conversationId: Long,
    val requestId: String,
    val job: Job,
)

class GenerationSessionRegistry {
    private val sessions = ConcurrentHashMap<Long, GenerationSession>()
    private val pending = ConcurrentHashMap<Long, String>()
    private val cancelledRequests = HashSet<String>()
    private val lockStripes = Array(16) { Mutex() }

    private fun registerCancelled(requestId: String) = synchronized(cancelledRequests) {
        cancelledRequests.add(requestId)
    }

    private fun isCancelled(requestId: String): Boolean = synchronized(cancelledRequests) {
        cancelledRequests.remove(requestId)
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

        pending[conversationId] = requestId

        if (isCancelled(requestId)) {
            pending.remove(conversationId, requestId)
            throw CancellationException("Request $requestId was cancelled before start")
        }

        return lock.withLock {
            if (isCancelled(requestId)) {
                pending.remove(conversationId, requestId)
                throw CancellationException("Request $requestId was cancelled before start")
            }

            val previous = sessions[conversationId]
            if (previous != null && !previous.job.isCompleted) {
                previous.job.cancel(CancellationException("superseded-by-${requestId}"))
                previous.job.join()
            }

            if (isCancelled(requestId)) {
                pending.remove(conversationId, requestId)
                throw CancellationException("Request $requestId was cancelled before start")
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
            pending.remove(conversationId, requestId)

            gs.job.invokeOnCompletion {
                sessions.remove(conversationId, gs)
            }

            if (!gs.job.start()) {
                sessions.remove(conversationId, gs)
                pending.remove(conversationId, requestId)
                throw CancellationException(
                    "Scope cancelled before session could start for conversation $conversationId"
                )
            }

            gs
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
        val lock = stripeFor(conversationId)
        return lock.withLock {
            val session = sessions[conversationId] ?: return@withLock false
            if (session.job.isCompleted) return@withLock false
            session.job.cancel(CancellationException(reason))
            session.job.join()
            true
        }
    }

    suspend fun cancelAndJoin(
        conversationId: Long,
        requestId: String,
        reason: String
    ): Boolean {
        val pendingId = pending[conversationId]
        if (pendingId == requestId) {
            registerCancelled(requestId)
            pending.remove(conversationId, requestId)
            return true
        }

        val lock = stripeFor(conversationId)
        return lock.withLock {
            val session = sessions[conversationId]
            if (session != null) {
                if (session.requestId != requestId) return@withLock false
                if (session.job.isCompleted) return@withLock false
                session.job.cancel(CancellationException(reason))
                session.job.join()
                true
            } else {
                val pendingId2 = pending[conversationId]
                if (pendingId2 != requestId) return@withLock false
                registerCancelled(requestId)
                pending.remove(conversationId, requestId)
                true
            }
        }
    }

    fun hasCancelledRequest(requestId: String): Boolean = synchronized(cancelledRequests) {
        cancelledRequests.contains(requestId)
    }

    fun hasActiveSession(conversationId: Long): Boolean =
        sessions[conversationId]?.job?.isActive == true

    fun activeConversationIds(): Set<Long> =
        sessions.filterValues { it.job.isActive }.keys.toSet()
}
