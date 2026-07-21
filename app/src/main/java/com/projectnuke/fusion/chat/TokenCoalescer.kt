package com.projectnuke.fusion.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class S { OPEN, FINISHING, ABORTING, FINISHED, ABORTED }

/**
 * A single-consumer streaming pipeline. Tokens are appended to an
 * append-only buffer and published via [onPublish] approximately every
 * [publishIntervalMs] milliseconds and once on final flush.
 *
 * Lifecycle:
 *   OPEN → FINISHING → FINISHED  (normal)
 *   OPEN → ABORTED                (immediate abort)
 *   OPEN → FINISHING → ABORTING → ABORTED  (abort during finish)
 *
 * All terminal callers coordinate through a shared [CompletableDeferred]
 * so that the first caller performs the work and later callers await the
 * same completion.  No caller returns while consumer shutdown is running.
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
    private var state = S.OPEN
    private val terminalCompleted = CompletableDeferred<Unit>()
    @Volatile private var finalizedText = ""

    init {
        consumerJob = scope.launch(Dispatchers.Default) {
            while (true) {
                val result = tick.receiveCatching()
                if (result.isClosed) break
                var snapshot = ""
                var stop = false
                synchronized(lock) {
                    if (state != S.OPEN) {
                        stop = true
                    } else {
                        snapshot = buffer.toString()
                    }
                }
                if (stop) break
                if (!shouldPublish()) continue
                onPublish(snapshot)
                delay(publishIntervalMs)
            }
        }
    }

    /** Appends a token under the lock so no write occurs after terminal
     *  transition begins.  Nonblocking apart from the short lock. */
    fun append(token: String) {
        if (token.isEmpty()) return
        synchronized(lock) {
            if (state != S.OPEN) return
            buffer.append(token)
        }
        tick.trySend(Unit)
    }

    /**
     * Finishes the stream.  The first caller performs consumer shutdown
     * and publishes the final snapshot at most once.  Later callers await
     * the same completion and return the same finalized text.
     *
     * If [abort] races and wins before final publication the snapshot is
     * suppressed and this method still waits for full cleanup.
     */
    suspend fun finish(): String {
        val primary: Boolean
        synchronized(lock) {
            when (state) {
                S.OPEN -> { state = S.FINISHING; primary = true }
                S.FINISHED -> return finalizedText
                S.ABORTED -> return finalizedText
                else -> { primary = false }
            }
        }
        if (!primary) {
            withContext(NonCancellable) { terminalCompleted.await() }
            return finalizedText
        }
        // --- primary finisher ---
        tick.close()
        consumerJob.join()
        val text: String
        val doPublish: Boolean
        synchronized(lock) {
            text = buffer.toString()
            finalizedText = text
            if (state == S.FINISHING) {
                state = S.FINISHED
                doPublish = true
            } else {
                doPublish = false
            }
        }
        if (doPublish) {
            if (shouldPublish()) onPublish(text)
            terminalCompleted.complete(Unit)
        } else {
            withContext(NonCancellable) { terminalCompleted.await() }
        }
        return text
    }

    /**
     * Aborts the stream without publishing additional state.
     * Idempotent and cancellation-safe.  The first caller performs
     * cleanup; later callers await the same completion.
     *
     * If called while [finish] is still processing, atomically
     * transitions to ABORTING so that the final publication is
     * suppressed, then takes over cleanup.
     */
    suspend fun abort() {
        val doCleanup: Boolean
        synchronized(lock) {
            when (state) {
                S.OPEN -> { state = S.ABORTED; doCleanup = true }
                S.FINISHING -> { state = S.ABORTING; doCleanup = true }
                else -> { doCleanup = false }
            }
        }
        if (doCleanup) {
            withContext(NonCancellable) {
                tick.close()
                consumerJob.cancelAndJoin()
            }
            synchronized(lock) { state = S.ABORTED }
            terminalCompleted.complete(Unit)
        } else {
            withContext(NonCancellable) { terminalCompleted.await() }
        }
    }
}
