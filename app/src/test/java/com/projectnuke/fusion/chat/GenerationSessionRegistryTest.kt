package com.projectnuke.fusion.chat

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationSessionRegistryTest {

    @Test
    fun `started session becomes active with correct conversation and request ID`() = runBlocking {
        val scope = testScope()
        val blockEntered = CountDownLatch(1)
        val blockRelease = CompletableDeferred<Unit>()
        try {
            val registry = GenerationSessionRegistry()
            val session = withTimeout(2000) {
                registry.start(scope, snapshot(1L, "r1")) {
                    blockEntered.countDown()
                    blockRelease.await()
                }
            }
            awaitGate(blockEntered)
            assertEquals(1L, session.conversationId)
            assertEquals("r1", session.requestId)
            assertTrue(session.job.isActive)
            assertTrue(registry.isActive(1L, "r1"))
            assertNotNull(registry.activeSession(1L))
        } finally {
            blockRelease.complete(Unit)
            scope.cancel()
            withTimeout(2000) { scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test
    fun `normal block completion removes the session`() = runBlocking {
        val scope = testScope()
        val blockRelease = CompletableDeferred<Unit>()
        try {
            val registry = GenerationSessionRegistry()
            val session = withTimeout(2000) {
                registry.start(scope, snapshot(2L, "r2")) {
                    blockRelease.await()
                }
            }
            blockRelease.complete(Unit)
            withTimeout(2000) { session.job.join() }
            assertFalse(registry.isActive(2L, "r2"))
            assertNull(registry.activeSession(2L))
        } finally {
            blockRelease.complete(Unit)
            scope.cancel()
            withTimeout(2000) { scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test
    fun `block exception removes the session`() = runBlocking {
        val scope = testScope()
        val errors = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, e -> errors.add(e) }
        val errorScope = CoroutineScope(scope.coroutineContext[Job]!! + Dispatchers.Default + handler)
        try {
            val registry = GenerationSessionRegistry()
            val session = withTimeout(2000) {
                registry.start(errorScope, snapshot(3L, "r3")) {
                    throw IllegalStateException("boom")
                }
            }
            withTimeout(2000) { session.job.join() }
            assertTrue("expected IllegalStateException in errors",
                errors.any { it is IllegalStateException })
            assertFalse(registry.isActive(3L, "r3"))
            assertNull(registry.activeSession(3L))
        } finally {
            scope.cancel()
            withTimeout(2000) { scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test
    fun `replacing A cancels and fully joins A before B begins`() = runBlocking {
        val scope = testScope()
        val holdGate = CompletableDeferred<Unit>()
        val aCleanupStarted = CompletableDeferred<Unit>()
        val aCleanupRelease = CompletableDeferred<Unit>()
        val aStarted = CountDownLatch(1)
        val bEntered = AtomicBoolean(false)
        val bHold = CompletableDeferred<Unit>()
        try {
            val registry = GenerationSessionRegistry()

            val aDone = async(Dispatchers.Default) {
                registry.start(scope, snapshot(4L, "A")) {
                    aStarted.countDown()
                    try {
                        holdGate.await()
                    } finally {
                        aCleanupStarted.complete(Unit)
                        withContext(NonCancellable) {
                            aCleanupRelease.await()
                        }
                    }
                }
            }

            awaitGate(aStarted)

            val bDone = async(Dispatchers.Default) {
                registry.start(scope, snapshot(4L, "B")) {
                    bEntered.set(true)
                    bHold.await()
                }
            }

            withTimeout(2000) { aCleanupStarted.await() }
            assertFalse("B entered before A cleanup completed", bEntered.get())

            aCleanupRelease.complete(Unit)
            withTimeout(2000) { aDone.await(); bDone.await() }

            assertTrue("B should have entered after A cleanup", bEntered.get())
            assertTrue(registry.isActive(4L, "B"))
            assertFalse(registry.isActive(4L, "A"))
        } finally {
            holdGate.complete(Unit)
            aCleanupRelease.complete(Unit)
            bHold.complete(Unit)
            scope.cancel()
            withTimeout(2000) { scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test
    fun `A late completion callback cannot remove B`() = runBlocking {
        val scope = testScope()
        val holdGate = CompletableDeferred<Unit>()
        val aCleanupRelease = CompletableDeferred<Unit>()
        val aStarted = CountDownLatch(1)
        val bStarted = CountDownLatch(1)
        val bHold = CompletableDeferred<Unit>()
        try {
            val registry = GenerationSessionRegistry()

            val aDone = async(Dispatchers.Default) {
                registry.start(scope, snapshot(5L, "A")) {
                    aStarted.countDown()
                    try {
                        holdGate.await()
                    } finally {
                        withContext(NonCancellable) {
                            aCleanupRelease.await()
                        }
                    }
                }
            }

            awaitGate(aStarted)

            val bDone = async(Dispatchers.Default) {
                registry.start(scope, snapshot(5L, "B")) {
                    bStarted.countDown()
                    bHold.await()
                }
            }

            aCleanupRelease.complete(Unit)
            withTimeout(2000) { aDone.await() }
            awaitGate(bStarted)

            assertTrue("B should be the active session after replacement",
                registry.isActive(5L, "B"))
            assertEquals("B", registry.activeSession(5L)?.requestId)
        } finally {
            holdGate.complete(Unit)
            aCleanupRelease.complete(Unit)
            bHold.complete(Unit)
            scope.cancel()
            withTimeout(2000) { scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test
    fun `concurrent starts for one conversation leave only the newest active`() = runBlocking {
        val scope = testScope()
        val holdA = CompletableDeferred<Unit>()
        val aStarted = CountDownLatch(1)
        val bStarted = CountDownLatch(1)
        val bHold = CompletableDeferred<Unit>()
        val cStarted = CountDownLatch(1)
        val cHold = CompletableDeferred<Unit>()
        try {
            val registry = GenerationSessionRegistry()

            val aDone = async(Dispatchers.Default) {
                registry.start(scope, snapshot(6L, "A")) {
                    aStarted.countDown()
                    holdA.await()
                }
            }

            awaitGate(aStarted)

            val bDone = async(Dispatchers.Default) {
                registry.start(scope, snapshot(6L, "B")) {
                    bStarted.countDown()
                    bHold.await()
                }
            }

            holdA.complete(Unit)
            withTimeout(2000) { aDone.await(); bDone.await() }
            awaitGate(bStarted)

            assertTrue("B should be active after A cleanup", registry.isActive(6L, "B"))

            val cDone = async(Dispatchers.Default) {
                registry.start(scope, snapshot(6L, "C")) {
                    cStarted.countDown()
                    cHold.await()
                }
            }

            withTimeout(2000) { bDone.await() }
            awaitGate(cStarted)

            assertTrue("C should be active as the newest starting session",
                registry.isActive(6L, "C"))
            assertFalse("A should no longer be active", registry.isActive(6L, "A"))
            assertFalse("B should no longer be active", registry.isActive(6L, "B"))
        } finally {
            holdA.complete(Unit)
            bHold.complete(Unit)
            cHold.complete(Unit)
            scope.cancel()
            withTimeout(2000) { scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test
    fun `sessions for different conversations operate independently`() = runBlocking {
        val scope = testScope()
        val holdA = CompletableDeferred<Unit>()
        val holdB = CompletableDeferred<Unit>()
        val aStarted = CountDownLatch(1)
        val bStarted = CountDownLatch(1)
        try {
            val registry = GenerationSessionRegistry()
            val aDone = async(Dispatchers.Default) {
                registry.start(scope, snapshot(10L, "A")) {
                    aStarted.countDown()
                    holdA.await()
                }
            }
            val bDone = async(Dispatchers.Default) {
                registry.start(scope, snapshot(20L, "B")) {
                    bStarted.countDown()
                    holdB.await()
                }
            }

            awaitGate(aStarted)
            awaitGate(bStarted)

            assertTrue(registry.isActive(10L, "A"))
            assertTrue(registry.isActive(20L, "B"))

            holdA.complete(Unit)
            holdB.complete(Unit)
            withTimeout(2000) { aDone.await(); bDone.await() }

            assertFalse(registry.isActive(10L, "A"))
            assertFalse(registry.isActive(20L, "B"))
        } finally {
            holdA.complete(Unit)
            holdB.complete(Unit)
            scope.cancel()
            withTimeout(2000) { scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test
    fun `cancelAndJoin waits for NonCancellable cleanup before returning`() = runBlocking {
        val scope = testScope()
        val holdGate = CompletableDeferred<Unit>()
        val enteredBlock = CountDownLatch(1)
        val cleanupStarted = CompletableDeferred<Unit>()
        val cleanupRelease = CompletableDeferred<Unit>()
        try {
            val registry = GenerationSessionRegistry()
            val aDone = async(Dispatchers.Default) {
                registry.start(scope, snapshot(7L, "A")) {
                    enteredBlock.countDown()
                    try {
                        holdGate.await()
                    } finally {
                        withContext(NonCancellable) {
                            cleanupStarted.complete(Unit)
                            cleanupRelease.await()
                        }
                    }
                }
            }

            awaitGate(enteredBlock)

            val cancelDone = async(Dispatchers.Default) {
                registry.cancelAndJoin(7L, "test-cancel")
            }

            withTimeout(2000) { cleanupStarted.await() }
            assertFalse("cancelAndJoin should still be blocked on cleanup",
                cancelDone.isCompleted)

            cleanupRelease.complete(Unit)
            withTimeout(2000) { cancelDone.await(); aDone.await() }

            assertFalse(registry.isActive(7L, "A"))
        } finally {
            holdGate.complete(Unit)
            cleanupRelease.complete(Unit)
            scope.cancel()
            withTimeout(2000) { scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test
    fun `cancelAndJoin leaves no active session`() = runBlocking {
        val scope = testScope()
        val holdGate = CompletableDeferred<Unit>()
        val entered = CountDownLatch(1)
        try {
            val registry = GenerationSessionRegistry()
            async(Dispatchers.Default) {
                registry.start(scope, snapshot(8L, "r8")) {
                    entered.countDown()
                    holdGate.await()
                }
            }

            awaitGate(entered)
            withTimeout(2000) { registry.cancelAndJoin(8L, "stop") }

            assertNull(registry.activeSession(8L))
            assertFalse(registry.isActive(8L, "r8"))
            assertFalse(registry.hasActiveSession(8L))
        } finally {
            holdGate.complete(Unit)
            scope.cancel()
            withTimeout(2000) { scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test
    fun `starting with already-cancelled scope throws CancellationException`() {
        val deadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { it.cancel() }
        val registry = GenerationSessionRegistry()
        try {
            runBlocking {
                try {
                    registry.start(deadScope, snapshot(9L, "r9")) { }
                    assertTrue("expected CancellationException", false)
                } catch (_: CancellationException) {
                }
            }
            assertNull(registry.activeSession(9L))
            assertFalse(registry.isActive(9L, "r9"))
        } finally {
            deadScope.cancel()
        }
    }

    @Test
    fun `repeated cancel calls are harmless and do not target a replacement session`() = runBlocking {
        val scope = testScope()
        val holdGate = CompletableDeferred<Unit>()
        val aCleanupRelease = CompletableDeferred<Unit>()
        val aStarted = CountDownLatch(1)
        val bHold = CompletableDeferred<Unit>()
        try {
            val registry = GenerationSessionRegistry()
            val aDone = async(Dispatchers.Default) {
                registry.start(scope, snapshot(11L, "A")) {
                    aStarted.countDown()
                    try {
                        holdGate.await()
                    } finally {
                        withContext(NonCancellable) {
                            aCleanupRelease.await()
                        }
                    }
                }
            }

            awaitGate(aStarted)
            val bDone = async(Dispatchers.Default) {
                registry.start(scope, snapshot(11L, "B")) {
                    bHold.await()
                }
            }

            aCleanupRelease.complete(Unit)
            withTimeout(2000) { aDone.await(); bDone.await() }

            assertTrue("B should be active after replacement",
                registry.isActive(11L, "B"))

            assertTrue("cancelAndJoin on B should succeed and wait for completion",
                registry.cancelAndJoin(11L, "B", "cancel-B"))
            assertFalse("B should no longer be active", registry.isActive(11L, "B"))

            assertFalse("repeated cancel should be harmless",
                registry.cancel(11L, "again"))
            assertFalse("cancelAndJoin after no active session should return false",
                registry.cancelAndJoin(11L, "again"))
        } finally {
            holdGate.complete(Unit)
            aCleanupRelease.complete(Unit)
            bHold.complete(Unit)
            scope.cancel()
            withTimeout(2000) { scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test
    fun `cancelAndJoin called multiple times is harmless`() = runBlocking {
        val scope = testScope()
        val holdGate = CompletableDeferred<Unit>()
        val entered = CountDownLatch(1)
        try {
            val registry = GenerationSessionRegistry()
            async(Dispatchers.Default) {
                registry.start(scope, snapshot(12L, "r12")) {
                    entered.countDown()
                    holdGate.await()
                }
            }

            awaitGate(entered)
            assertTrue(withTimeout(2000) { registry.cancelAndJoin(12L, "first") })
            assertFalse("second cancelAndJoin should return false",
                withTimeout(2000) { registry.cancelAndJoin(12L, "second") })
            assertNull(registry.activeSession(12L))
        } finally {
            holdGate.complete(Unit)
            scope.cancel()
            withTimeout(2000) { scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test
    fun `cancel on non-existent conversation returns false`() = runBlocking {
        val registry = GenerationSessionRegistry()
        assertFalse(registry.cancel(99L, "nonexistent"))
        assertFalse(withTimeout(2000) { registry.cancelAndJoin(99L, "nonexistent") })
    }

    @Test
    fun `stale cancel after natural completion is harmless`() = runBlocking {
        val scope = testScope()
        val release = CompletableDeferred<Unit>()
        try {
            val registry = GenerationSessionRegistry()
            val session = withTimeout(2000) {
                registry.start(scope, snapshot(13L, "r13")) { release.await() }
            }
            release.complete(Unit)
            withTimeout(2000) { session.job.join() }
            assertFalse("cancel should return false for completed job",
                registry.cancel(13L, "late"))
            assertFalse("cancelAndJoin should return false for completed session",
                registry.cancelAndJoin(13L, "late"))
        } finally {
            release.complete(Unit)
            scope.cancel()
            withTimeout(2000) { scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test
    fun `request-aware cancelAndJoin returns false for stale request`() = runBlocking {
        val scope = testScope()
        val holdA = CompletableDeferred<Unit>()
        val aStarted = CountDownLatch(1)
        val bStarted = CountDownLatch(1)
        val bHold = CompletableDeferred<Unit>()
        try {
            val registry = GenerationSessionRegistry()

            val aDone = async(Dispatchers.Default) {
                registry.start(scope, snapshot(14L, "A")) {
                    aStarted.countDown()
                    holdA.await()
                }
            }

            awaitGate(aStarted)

            val bDone = async(Dispatchers.Default) {
                registry.start(scope, snapshot(14L, "B")) {
                    bStarted.countDown()
                    bHold.await()
                }
            }

            holdA.complete(Unit)
            withTimeout(2000) { aDone.await(); bDone.await() }
            awaitGate(bStarted)

            assertTrue("B should be active after replacement", registry.isActive(14L, "B"))

            assertFalse("stale request A cancellation should return false",
                registry.cancelAndJoin(14L, "A", "late-cancel-A"))
            assertTrue("B should still be active", registry.isActive(14L, "B"))

            assertTrue("cancel B with matching request should succeed",
                registry.cancelAndJoin(14L, "B", "cancel-B"))
            assertFalse("B should no longer be active", registry.isActive(14L, "B"))
        } finally {
            holdA.complete(Unit)
            bHold.complete(Unit)
            scope.cancel()
            withTimeout(2000) { scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test
    fun `isActive returns false for unknown conversation`() {
        val registry = GenerationSessionRegistry()
        assertFalse(registry.isActive(99L, "any"))
    }

    @Test
    fun `activeSession returns null for unknown conversation`() {
        val registry = GenerationSessionRegistry()
        assertNull(registry.activeSession(99L))
    }

    @Test
    fun `hasActiveSession returns false for unknown conversation`() {
        val registry = GenerationSessionRegistry()
        assertFalse(registry.hasActiveSession(99L))
    }

    // -- helpers --

    private fun testScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun snapshot(conversationId: Long, requestId: String): GenerationRequestSnapshot =
        GenerationRequestSnapshot(
            requestId = requestId,
            conversationId = conversationId,
            generationModeKey = "TEST",
            selectedModelId = null,
            selectedModelPath = null,
            settings = com.projectnuke.fusion.model.GenerationSettings(),
            reasoningEnabled = false,
            webSearchPolicy = GenerationRequestSnapshot.WebSearchPolicy.DISABLED,
            attachmentIds = emptyList(),
            multimodalImagePaths = emptyList(),
            promptText = "test",
            rawUserText = "test",
            createdAt = 0L,
        )

    companion object {
        private fun awaitGate(gate: CountDownLatch) {
            assertTrue("synchronization gate was not reached", gate.await(5, TimeUnit.SECONDS))
        }
    }
}
