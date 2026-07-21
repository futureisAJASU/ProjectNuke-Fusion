package com.projectnuke.fusion.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A single-consumer streaming pipeline. Tokens are appended to an
 * append-only buffer and published via [onPublish] approximately every
 * [publishIntervalMs] milliseconds and once on final flush.
 *
 * Properties:
 *   * no coroutine created per token
 *   * no StringBuilder.toString() on every token
 *   * conflated channel signalling (bounded, never blocks callers)
 *   * [shouldPublish] guard: if false, the intermediate snapshot is
 *     skipped entirely without invoking [onPublish].
 *   * final text created once after completion via [finish].
 *   * append calls after [finish] or [abort] are silently ignored.
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

    @Volatile private var stopped = false

    init {
        consumerJob = scope.launch(Dispatchers.Default) {
            while (true) {
                val result = tick.receiveCatching()
                if (result.isClosed || stopped) break
                if (!shouldPublish()) continue
                val snapshot = synchronized(buffer) { buffer.toString() }
                if (stopped) break
                onPublish(snapshot)
                delay(publishIntervalMs)
            }
        }
    }

    /**
     * Appends a token. Thread-safe and never blocks the caller.
     * Ignored after [finish] or [abort] has been called.
     */
    fun append(token: String) {
        if (stopped || token.isEmpty()) return
        synchronized(buffer) { buffer.append(token) }
        tick.trySend(Unit)
    }

    /**
     * Signals completion, flushes the final snapshot once, and tears
     * down the consumer. Returns the finalized text.
     */
    suspend fun finish(): String = withContext(Dispatchers.Default) {
        if (stopped) return@withContext synchronized(buffer) { buffer.toString() }
        stopped = true
        tick.trySend(Unit)
        tick.close()
        consumerJob.join()
        val final = synchronized(buffer) { buffer.toString() }
        if (shouldPublish()) onPublish(final)
        final
    }

    /**
     * Abandons the stream without publishing additional state.
     * Idempotent — safe to call multiple times.
     */
    suspend fun abort() {
        if (stopped) return
        stopped = true
        tick.close()
        consumerJob.cancelAndJoin()
    }
}
