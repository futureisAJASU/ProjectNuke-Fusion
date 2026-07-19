package com.projectnuke.fusion.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * A single-consumer streaming pipeline. Tokens are appended to an
 * append-only buffer, then published as a single StateFlow snapshot
 * approximately every [publishIntervalMs] milliseconds and once on final
 * flush.
 *
 * Properties:
 *   * no coroutine created per token
 *   * no StringBuilder.toString() on every token
 *   * bounded signalling (conflated publish tick)
 *   * stale-session guard: publishing invokes [shouldPublish]; if false, the
 *     publish is skipped entirely without recomposing the UI.
 *   * final text created once after completion via [finish].
 */
class TokenCoalescer(
    private val scope: CoroutineScope,
    private val publishIntervalMs: Long = 30L,
    private val shouldPublish: () -> Boolean = { true },
) {
    private val buffer = StringBuilder()
    private val tick = Channel<Unit>(Channel.CONFLATED)
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val consumerJob: kotlinx.coroutines.Job
    @Volatile private var stopped = false

    init {
        consumerJob = scope.launch(Dispatchers.Default) {
            while (true) {
                tick.receive()
                if (stopped) break
                if (!shouldPublish()) continue
                val snapshot = synchronizeBuffer()
                _text.value = snapshot
                delay(publishIntervalMs)
            }
        }
    }

    /** Appends a token. Thread-safe via a single-threaded serializer. */
    fun append(token: String) {
        if (token.isEmpty()) return
        synchronized(buffer) {
            buffer.append(token)
        }
        tick.trySend(Unit)
    }

    /** Returns the full text accumulated so far, without flushing. */
    fun currentText(): String = synchronized(buffer) { buffer.toString() }

    /**
     * Signals completion, flushes the final snapshot once after creation, and
     * tears down the consumer. Returns the finalized text.
     */
    suspend fun finish(): String = withContext(Dispatchers.Default) {
        stopped = true
        tick.close()
        consumerJob.join()
        val final = synchronizeBuffer()
        if (shouldPublish()) _text.value = final
        final
    }

    /** Abandons the stream without flushing additional state. */
    fun abort() {
        stopped = true
        tick.close()
        consumerJob.cancel()
    }

    private fun synchronizeBuffer(): String = synchronized(buffer) { buffer.toString() }
}
