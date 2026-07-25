package com.projectnuke.fusion.chat

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatViewModelRequestStateTest {

    @Test
    fun `finishRequestState(A) clears state only when A is still the stored request`() {
        val vm = ChatViewModel()
        val convId = 100L
        vm.update(convId) {
            it.copy(
                activeRequestId = "A",
                isGenerating = true,
                streamingText = "hello",
                streamingMetricsLine = "metrics",
                generationStatus = "gen",
                regeneratingMessageId = 42L,
                extractingMemoryCandidates = true,
                actualWebSearchUsed = true,
            )
        }
        assertEquals("A", vm.state(convId).activeRequestId)

        vm.finishRequestState(convId, "A")
        val state = vm.state(convId)
        assertNull(state.activeRequestId)
        assertFalse(state.isGenerating)
        assertNull(state.streamingText)
        assertNull(state.streamingMetricsLine)
        assertNull(state.generationStatus)
        assertNull(state.regeneratingMessageId)
        assertFalse(state.extractingMemoryCandidates)
        assertFalse(state.actualWebSearchUsed)
    }

    @Test
    fun `late finishRequestState(A) cannot clear replacement B`() {
        val vm = ChatViewModel()
        val convId = 101L
        vm.update(convId) { it.copy(activeRequestId = "A", isGenerating = true) }
        vm.update(convId) { it.copy(activeRequestId = "B", isGenerating = true) }

        vm.finishRequestState(convId, "A")
        assertEquals("B", vm.state(convId).activeRequestId)
        assertTrue(vm.state(convId).isGenerating)
    }

    @Test
    fun `updateRequestState(A) cannot mutate replacement B`() {
        val vm = ChatViewModel()
        val convId = 102L
        vm.update(convId) { it.copy(activeRequestId = "A", streamingText = "from-A") }
        vm.update(convId) { it.copy(activeRequestId = "B", streamingText = "from-B") }

        vm.updateRequestState(convId, "A") { it.copy(streamingText = "A-tries-update") }
        assertEquals("from-B", vm.state(convId).streamingText)
    }

    @Test
    fun `requireActiveSession rejects updates after registry session ends`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val vm = ChatViewModel()
            val convId = 103L
            vm.update(convId) { it.copy(activeRequestId = "R") }

            val blockRelease = CompletableDeferred<Unit>()
            val session = withTimeout(2000) {
                vm.registry.start(scope, snapshot(convId, "R")) {
                    blockRelease.await()
                }
            }
            blockRelease.complete(Unit)
            withTimeout(2000) { session.job.join() }

            vm.updateRequestState(convId, "R", requireActiveSession = true) {
                it.copy(streamingText = "should-not-appear")
            }
            assertNull(vm.state(convId).streamingText)
        } finally {
            scope.cancel()
            withTimeout(2000) { scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test
    fun `requireActiveSession false permits matching terminal reconciliation`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val vm = ChatViewModel()
            val convId = 104L
            vm.update(convId) { it.copy(activeRequestId = "R", isGenerating = true) }

            val blockRelease = CompletableDeferred<Unit>()
            val session = withTimeout(2000) {
                vm.registry.start(scope, snapshot(convId, "R")) {
                    blockRelease.await()
                }
            }
            blockRelease.complete(Unit)
            withTimeout(2000) { session.job.join() }

            vm.updateRequestState(convId, "R", requireActiveSession = false) {
                it.copy(isGenerating = false)
            }
            assertFalse(vm.state(convId).isGenerating)
        } finally {
            scope.cancel()
            withTimeout(2000) { scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test
    fun `state for conversation A cannot mutate conversation B`() {
        val vm = ChatViewModel()
        vm.update(100L) { it.copy(activeRequestId = "A1", streamingText = "text-A") }
        vm.update(200L) { it.copy(activeRequestId = "B1", streamingText = "text-B") }

        vm.finishRequestState(100L, "A1")
        assertNull(vm.state(100L).activeRequestId)
        assertEquals("B1", vm.state(200L).activeRequestId)
    }

    @Test
    fun `cancelGeneration joins the active session and clears generation fields`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val holdGate = CompletableDeferred<Unit>()
        val entered = CountDownLatch(1)
        try {
            val vm = ChatViewModel()
            val convId = 105L
            vm.update(convId) { it.copy(
                activeRequestId = "R",
                isGenerating = true,
                streamingText = "in-flight",
                streamingMetricsLine = "metrics",
                generationStatus = "generating",
                regeneratingMessageId = 42L,
                extractingMemoryCandidates = true,
                actualWebSearchUsed = true,
            ) }

            val session = withTimeout(2000) {
                vm.registry.start(scope, snapshot(convId, "R")) {
                    entered.countDown()
                    holdGate.await()
                }
            }

            awaitGate(entered)
            withTimeout(2000) { vm.cancelGeneration(convId, "test-stop") }

            val state = vm.state(convId)
            assertNull(state.activeRequestId)
            assertFalse(state.isGenerating)
            assertNull(state.streamingText)
            assertNull(state.streamingMetricsLine)
            assertNull(state.generationStatus)
            assertNull(state.regeneratingMessageId)
            assertFalse(state.extractingMemoryCandidates)
            assertFalse(state.actualWebSearchUsed)
        } finally {
            holdGate.complete(Unit)
            scope.cancel()
            withTimeout(2000) { scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test
    fun `cancelGeneration with no active session is safe`() = runBlocking {
        val vm = ChatViewModel()
        vm.cancelGeneration(999L, "nothing")
        assertTrue(true)
    }

    @Test
    fun `cancelGeneration of A cannot clear replacement B`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val aCleanupStarted = CountDownLatch(1)
        val aCleanupRelease = CompletableDeferred<Unit>()
        val aStarted = CountDownLatch(1)
        val bStarted = CountDownLatch(1)
        val bHold = CompletableDeferred<Unit>()
        try {
            val vm = ChatViewModel()
            val convId = 106L

            vm.update(convId) { it.copy(
                activeRequestId = "A",
                isGenerating = true,
                streamingText = "A-stream",
                streamingMetricsLine = "A-metrics",
                generationStatus = "A-status",
                regeneratingMessageId = 1L,
                extractingMemoryCandidates = true,
                actualWebSearchUsed = true,
            ) }

            val aSession = withTimeout(2000) {
                vm.registry.start(scope, snapshot(convId, "A")) {
                    aStarted.countDown()
                    try {
                        CompletableDeferred<Unit>().await()
                    } finally {
                        aCleanupStarted.countDown()
                        withContext(NonCancellable) {
                            aCleanupRelease.await()
                        }
                    }
                }
            }

            awaitGate(aStarted)

            val cancelDone = async(Dispatchers.Default) {
                vm.cancelGeneration(convId, "stop-A")
            }

            awaitGate(aCleanupStarted)

            vm.update(convId) { it.copy(
                activeRequestId = "B",
                isGenerating = true,
                streamingText = "B-stream",
                streamingMetricsLine = "B-metrics",
                generationStatus = "B-status",
                regeneratingMessageId = 2L,
                extractingMemoryCandidates = false,
                actualWebSearchUsed = false,
            ) }

            val bSessionDeferred = async(Dispatchers.Default) {
                vm.registry.start(scope, snapshot(convId, "B")) {
                    bStarted.countDown()
                    bHold.await()
                }
            }

            aCleanupRelease.complete(Unit)
            withTimeout(2000) { cancelDone.await() }
            awaitGate(bStarted)

            val stateB = vm.state(convId)
            assertEquals("B", stateB.activeRequestId)
            assertTrue(stateB.isGenerating)
            assertEquals("B-stream", stateB.streamingText)
            assertEquals("B-metrics", stateB.streamingMetricsLine)
            assertEquals("B-status", stateB.generationStatus)
            assertEquals(2L, stateB.regeneratingMessageId)
            assertFalse(stateB.extractingMemoryCandidates)
            assertFalse(stateB.actualWebSearchUsed)

            assertTrue("B should be active in registry", vm.registry.isActive(convId, "B"))

            val stateAfter = vm.state(convId)
            assertEquals("B", stateAfter.activeRequestId)
            assertTrue(stateAfter.isGenerating)
            assertEquals("B-stream", stateAfter.streamingText)
            assertEquals("B-metrics", stateAfter.streamingMetricsLine)
            assertEquals("B-status", stateAfter.generationStatus)
            assertEquals(2L, stateAfter.regeneratingMessageId)
            assertFalse(stateAfter.extractingMemoryCandidates)
            assertFalse(stateAfter.actualWebSearchUsed)

            assertTrue("B should still be active after A cleanup completes", vm.registry.isActive(convId, "B"))
        } finally {
            aCleanupRelease.complete(Unit)
            bHold.complete(Unit)
            scope.cancel()
            withTimeout(2000) { scope.coroutineContext[Job]!!.join() }
        }
    }

    // -- helpers --

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
