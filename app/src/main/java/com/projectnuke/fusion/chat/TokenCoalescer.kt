package com.projectnuke.fusion.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class CoalescerState { OPEN, FINISHING, FINISHED, ABORTED }

/**
 * A single-consumer streaming pipeline. Tokens are appended to an
 * append-only buffer and published via [onPublish] approximately every
 * [publishIntervalMs] milliseconds and once on final flush.
 *
 * Lifecycle: OPEN → FINISHING → FINISHED  (normal completion)
 *            OPEN → ABORTED                (cancellation / error)
 *
 * Properties:
 *   * no coroutine created per token
 *   * no StringBuilder.toString() on every token
 *   * conflated channel signalling (bounded, never blocks callers)
 *   * all lifecycle transitions and buffer writes protected by one lock
 *   * [shouldPublish] guard: if false, intermediate snapshots are skipped
 *   * final text created once after completion via [finish]
 *   * append calls after terminal state are silently ignored
 */
class TokenCoalescer(
    scope: CoroutineScope,
    private val publishIntervalMs: Long = 30L,
    private val shouldPublish: () -> Boolean = { true },
    private val onPublish: (String) -> Unit = {},
) {
    private val buffer = StringBuilder()
    private val tick = Channel<Unit>(Channel.CONFLATED)
    private val consumerJob: Job
    private val lock = Any()
    private var lifecycle = CoalescerState.OPEN

    init {
        consumerJob = scope.launch(Dispatchers.Default) {
            while (true) {
                val result = tick.receiveCatching()
                if (result.isClosed) break
                var snapshot = ""
                var shouldStop = false
                synchronized(lock) {
                    if (lifecycle != CoalescerState.OPEN) {
                        shouldStop = true
                    } else {
                        snapshot = buffer.toString()
                    }
                }
                if (shouldStop) break
                if (!shouldPublish()) continue
                onPublish(snapshot)
                delay(publishIntervalMs)
            }
        }
    }

    /**
     * Appends a token under the lifecycle lock so that no token is
     * written after the terminal snapshot has been captured.
     * Nonblocking apart from the short lock.
     */
    fun append(token: String) {
        if (token.isEmpty()) return
        synchronized(lock) {
            if (lifecycle != CoalescerState.OPEN) return
            buffer.append(token)
        }
        tick.trySend(Unit)
    }

    /**
     * Atomically transitions from OPEN to FINISHING, stops accepting
     * appends, waits for the consumer, creates the final string once,
     * publishes the final snapshot at most once, and returns the string.
     *
     * If the stream was already ABORTED, returns the current buffer
     * without publishing.
     *
     * Concurrent or repeated calls do not publish a second snapshot.
     */
    suspend fun finish(): String = withContext(Dispatchers.Default) {
        val captured: String
        val iAmFinisher: Boolean
        synchronized(lock) {
            if (lifecycle == CoalescerState.FINISHED || lifecycle == CoalescerState.ABORTED) {
                return@withContext buffer.toString()
            }
            if (lifecycle == CoalescerState.FINISHING) {
                return@withContext buffer.toString()
            }
            lifecycle = CoalescerState.FINISHING
            captured = buffer.toString()
            iAmFinisher = true
        }
        tick.close()
        consumerJob.join()
        if (iAmFinisher) {
            if (shouldPublish()) onPublish(captured)
            synchronized(lock) { lifecycle = CoalescerState.FINISHED }
        }
        captured
    }

    /**
     * Abandons the stream without publishing additional state.
     * Idempotent — safe to call multiple times.  Runs cleanup inside
     * [NonCancellable] so that it completes even when the calling
     * coroutine is already cancelled.
     */
    suspend fun abort() {
        val doCleanup: Boolean
        synchronized(lock) {
            if (lifecycle != CoalescerState.OPEN) {
                doCleanup = false
            } else {
                lifecycle = CoalescerState.ABORTED
                doCleanup = true
            }
        }
        if (!doCleanup) return
        withContext(NonCancellable) {
            tick.close()
            consumerJob.cancelAndJoin()
        }
    }
}
