package com.projectnuke.fusion.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-conversation generation session owner. A late callback from request A
 * must never update request B or another conversation: every mutation checks
 * that [requestId] is still the active id for [conversationId].
 */
class GenerationSession internal constructor(
    val conversationId: Long,
    val requestId: String,
    val job: Job,
)

/**
 * Registry of active generation Jobs keyed by conversationId. Operations:
 * start, cancel, cancelAndJoin, query active session, clear only the matching
 * completed session.
 */
class GenerationSessionRegistry {
    private data class MutableSession(val session: GenerationSession, val startedAt: Long)

    private val sessions = ConcurrentHashMap<Long, MutableSession>()

    /**
     * Starts [block] under a child Job of [scope] registered for
     * [snapshot.conversationId]. Cancels any prior active session for the same
     * conversation first.
     */
    suspend fun start(
        scope: CoroutineScope,
        snapshot: GenerationRequestSnapshot,
        block: suspend (GenerationSession) -> Unit
    ): GenerationSession {
        cancelAndJoin(snapshot.conversationId, "superseded-by-${snapshot.requestId}")
        val parentJob = scope.coroutineContext[Job]
        val sessionJob = Job(parentJob)
        val sessionScope = CoroutineScope(scope.coroutineContext + sessionJob)
        val session = GenerationSession(
            conversationId = snapshot.conversationId,
            requestId = snapshot.requestId,
            job = sessionJob,
        )
        sessions[snapshot.conversationId] = MutableSession(session, System.currentTimeMillis())
        sessionScope.launch(start = CoroutineStart.DEFAULT) {
            try {
                block(session)
            } finally {
                // Visible-state cleanup is owned by the caller; we only record
                // completion through the job becoming inactive.
            }
        }
        return session
    }

    /** Returns the active (non-completed) session for [conversationId] or null. */
    fun activeSession(conversationId: Long): GenerationSession? =
        sessions[conversationId]?.session?.takeIf { it.job.isActive }

    /**
     * Returns true only if the session for [conversationId] currently has
     * [requestId] and is still active. Test against the snapshot before
     * mutating any visible state from a callback.
     */
    fun isActive(conversationId: Long, requestId: String): Boolean =
        sessions[conversationId]?.session?.let {
            it.requestId == requestId && it.job.isActive
        } == true

    /** Cancels the current session for [conversationId] without joining. */
    fun cancel(conversationId: Long, reason: String): Boolean {
        val mutable = sessions[conversationId] ?: return false
        return runCatching {
            mutable.session.job.cancel()
            true
        }.getOrDefault(false)
    }

    /** Cancels and joins the current session for [conversationId]. */
    suspend fun cancelAndJoin(conversationId: Long, reason: String): Boolean {
        val mutable = sessions.remove(conversationId) ?: return false
        return runCatching {
            mutable.session.job.cancelAndJoin()
            true
        }.getOrDefault(false)
    }

    /**
     * Clears a completed session only if [requestId] still matches. Prevents a
     * late finisher wiping a newer session's record.
     */
    fun clearMatching(conversationId: Long, requestId: String): Boolean {
        val mutable = sessions[conversationId] ?: return false
        if (mutable.session.requestId == requestId && !mutable.session.job.isActive) {
            return sessions.remove(conversationId, mutable)
        }
        return false
    }

    /** True when there is any active (running) session for [conversationId]. */
    fun hasActiveSession(conversationId: Long): Boolean =
        sessions[conversationId]?.session?.job?.isActive == true

    fun activeConversationIds(): Set<Long> = sessions.keys.toSet()
}
