package com.projectnuke.fusion.chat

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenCoalescerTest {
    @Test fun concurrentFinishWaitsForOneTerminalOperationAndPublishesOnce() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val publishes = AtomicInteger()
        val shouldCalls = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val coalescer = TokenCoalescer(scope, shouldPublish = { shouldCalls.incrementAndGet() > 1 }, onPublish = {
                publishes.incrementAndGet(); entered.complete(Unit); runBlocking { release.await() }
            })
            coalescer.append("answer")
            val first = launch { coalescer.finish() }
            entered.await()
            val second = launch { coalescer.finish() }
            assertTrue(!second.isCompleted)
            release.complete(Unit)
            first.join(); second.join()
            assertEquals(1, publishes.get())
            assertEquals("answer", coalescer.finish())
        } finally { scope.cancel() }
    }

    @Test fun concurrentAbortWaitsForConsumerCleanupAndIsIdempotent() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val coalescer = TokenCoalescer(scope, onPublish = {
                entered.complete(Unit); runBlocking { release.await() }
            })
            coalescer.append("x"); entered.await()
            val a = launch { coalescer.abort() }; val b = launch { coalescer.abort() }
            assertTrue(!a.isCompleted || !b.isCompleted)
            release.complete(Unit); withTimeout(2_000) { a.join(); b.join() }
            coalescer.abort()
        } finally { scope.cancel() }
    }

    @Test fun abortDuringFinishingSuppressesFinalPublication() = runBlocking {
        val finishStarted = CompletableDeferred<Unit>(); val publishes = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val c = TokenCoalescer(scope, onPublish = { publishes.incrementAndGet(); finishStarted.complete(Unit) })
            c.append("x"); val finish = launch { c.finish() }; finishStarted.await()
            c.abort(); finish.join(); assertEquals(1, publishes.get())
        } finally { scope.cancel() }
    }

    @Test fun abortWaitsForRunningIntermediatePublicationAndBlocksLaterPublication() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val count = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val c = TokenCoalescer(scope, publishIntervalMs = 0, onPublish = { count.incrementAndGet(); entered.complete(Unit); runBlocking { release.await() } })
            c.append("a"); entered.await(); val abort = launch { c.abort() }
            assertTrue(!abort.isCompleted); release.complete(Unit); abort.join(); c.append("b"); assertEquals(1, count.get())
        } finally { scope.cancel() }
    }

    @Test fun appendsAfterTerminalBeginsAreIgnored() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try { val c = TokenCoalescer(scope); c.append("a"); val text = c.finish(); c.append("b"); assertEquals("a", text); c.abort() }
        finally { scope.cancel() }
    }

    @Test fun throwingShouldPublishSettlesFinishAndAbort() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val failure = IllegalStateException("should")
            val c = TokenCoalescer(scope, shouldPublish = { throw failure })
            try { c.finish(); assertTrue(false) } catch (e: IllegalStateException) { assertSame(failure, e) }
            withTimeout(2_000) { c.finish(); c.abort() }
        } finally { scope.cancel() }
    }

    @Test fun throwingOnPublishSettlesFinishAndAbort() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val failure = IllegalArgumentException("publish")
            val c = TokenCoalescer(scope, onPublish = { throw failure })
            try { c.finish(); assertTrue(false) } catch (e: IllegalArgumentException) { assertSame(failure, e) }
            withTimeout(2_000) { c.finish(); c.abort() }
        } finally { scope.cancel() }
    }

    @Test fun cancellationDuringFinishCleansUpAndPropagates() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val c = TokenCoalescer(scope, onPublish = { entered.complete(Unit); runBlocking { release.await() } })
            c.append("x"); val finish = async { c.finish() }; entered.await(); finish.cancel(); release.complete(Unit)
            try { finish.await(); assertTrue(false) } catch (_: CancellationException) { }
            withTimeout(2_000) { c.abort() }
        } finally { scope.cancel() }
    }

    @Test fun repeatedFinishReturnsSameFinalizedText() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try { val c = TokenCoalescer(scope); c.append("stable"); assertEquals("stable", c.finish()); assertEquals("stable", c.finish()) }
        finally { scope.cancel() }
    }

    @Test fun repeatedAbortDoesNotStartAnotherCleanup() = runBlocking {
        val count = AtomicInteger(); val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try { val c = TokenCoalescer(scope, onPublish = { count.incrementAndGet() }); c.abort(); c.abort(); c.append("ignored"); assertEquals(0, count.get()) }
        finally { scope.cancel() }
    }
}
