package com.projectnuke.fusion.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class GenerationSession(
    val conversationId: Long,
    val requestId: String,
    val job: Job,
)

class GenerationSessionRegistry {
    private val sessions = ConcurrentHashMap<Long, GenerationSession>()

    suspend fun start(
        scope: CoroutineScope,
        snapshot: GenerationRequestSnapshot,
        block: suspend (GenerationSession) -> Unit
    ): GenerationSession {
        cancelAndJoin(snapshot.conversationId, "superseded-by-${snapshot.requestId}")

        val conversationId = snapshot.conversationId
        val requestId = snapshot.requestId
        val deferred = CompletableDeferred<GenerationSession>()

        scope.launch(start = CoroutineStart.DEFAULT) {
            val job = coroutineContext[Job] ?: error("No Job in coroutine context")
            val gs = GenerationSession(
                conversationId = conversationId,
                requestId = requestId,
                job = job,
            )
            sessions[conversationId] = gs
            deferred.complete(gs)

            job.invokeOnCompletion {
                clearMatching(conversationId, requestId)
            }

            block(gs)
        }

        return deferred.await()
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
        val session = sessions[conversationId] ?: return false
        if (!session.job.isActive) return false
        session.job.cancel(CancellationException(reason))
        session.job.join()
        return true
    }

    fun clearMatching(conversationId: Long, requestId: String): Boolean {
        val session = sessions[conversationId] ?: return false
        if (session.requestId == requestId && !session.job.isActive) {
            return sessions.remove(conversationId, session)
        }
        return false
    }

    fun hasActiveSession(conversationId: Long): Boolean =
        sessions[conversationId]?.job?.isActive == true

    fun activeConversationIds(): Set<Long> =
        sessions.filterValues { it.job.isActive }.keys.toSet()
}
