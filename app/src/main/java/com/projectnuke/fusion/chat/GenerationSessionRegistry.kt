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
    private val lockStripes = Array(16) { Mutex() }

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

        return lock.withLock {
            val previous = sessions[conversationId]
            if (previous != null && !previous.job.isCompleted) {
                previous.job.cancel(CancellationException("superseded-by-${requestId}"))
                previous.job.join()
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

            gs.job.invokeOnCompletion {
                sessions.remove(conversationId, gs)
            }

            if (!gs.job.start()) {
                sessions.remove(conversationId, gs)
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

    fun hasActiveSession(conversationId: Long): Boolean =
        sessions[conversationId]?.job?.isActive == true

    fun activeConversationIds(): Set<Long> =
        sessions.filterValues { it.job.isActive }.keys.toSet()
}
