package com.projectnuke.fusion.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelDraftHydrationTest {
    private val testDispatcher = StandardTestDispatcher()
    private var vm: ChatViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        vm?.viewModelScope?.cancel()
        vm = null
        Dispatchers.resetMain()
    }

    @Test
    fun `clear during load tombstones cannot be resurrected by hydration`() = runTest {
        val storeWrapper = DelayedHydrationDraftStore()
        val vm = ChatViewModel.forTesting(storeWrapper).also { this@ChatViewModelDraftHydrationTest.vm = it }

        vm.updateDraftText(1L, "draft during load")
        val clear = async { vm.clearDraft(1L) }
        testDispatcher.scheduler.runCurrent()
        assertTrue(clear.isActive)

        storeWrapper.releaseLoad(mapOf(1L to ComposerDraftState(rawInput = "stale restored", version = 1)))
        assertTrue(clear.await())

        assertEquals("", vm.draft(1L).rawInput)
        assertTrue(vm.draft(1L).pendingAttachments.isEmpty())
    }

    @Test
    fun `update during load wins over restored data`() = runTest {
        val storeWrapper = DelayedHydrationDraftStore()
        val vm = ChatViewModel.forTesting(storeWrapper).also { this@ChatViewModelDraftHydrationTest.vm = it }

        vm.updateDraftText(1L, "local update")

        storeWrapper.releaseLoad(mapOf(1L to ComposerDraftState(rawInput = "stale restored", version = 1)))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("local update", vm.draft(1L).rawInput)
    }

    @Test
    fun `stale load completion cannot restore a deleted draft`() = runTest {
        val storeWrapper = DelayedHydrationDraftStore()
        val vm = ChatViewModel.forTesting(storeWrapper).also { this@ChatViewModelDraftHydrationTest.vm = it }

        vm.updateDraftText(1L, "draft")
        val clear = async { vm.clearDraft(1L) }
        testDispatcher.scheduler.runCurrent()

        storeWrapper.releaseLoad(mapOf(1L to ComposerDraftState(rawInput = "stale", version = 1)))
        assertTrue(clear.await())

        assertEquals("", vm.draft(1L).rawInput)
    }

    @Test
    fun `tombstone before hydration blocks stale persistence of restored draft`() = runTest {
        val storeWrapper = DelayedHydrationDraftStore()
        val vm = ChatViewModel.forTesting(storeWrapper).also { this@ChatViewModelDraftHydrationTest.vm = it }

        val clear = async { vm.clearDraft(1L) }
        testDispatcher.scheduler.runCurrent()

        storeWrapper.releaseLoad(mapOf(1L to ComposerDraftState(rawInput = "stale", version = 1)))
        assertTrue(clear.await())

        val persisted = storeWrapper.latestWritten
        assertTrue(persisted == null || !persisted.containsKey(1L))
    }

    @Test
    fun `mutation after tombstone before hydration is rejected`() = runTest {
        val storeWrapper = DelayedHydrationDraftStore()
        val vm = ChatViewModel.forTesting(storeWrapper).also { this@ChatViewModelDraftHydrationTest.vm = it }

        val clear = async { vm.clearDraft(1L) }
        testDispatcher.scheduler.runCurrent()
        val blocked = async { vm.completeAttachmentImport(1L, "token", emptyList()) }
        testDispatcher.scheduler.runCurrent()
        assertTrue(blocked.isActive)

        storeWrapper.releaseLoad(mapOf(1L to ComposerDraftState(rawInput = "stale", version = 1)))
        assertTrue(clear.await())
        assertFalse(blocked.await())

        assertEquals("", vm.draft(1L).rawInput)
    }

    @Test
    fun `beginSubmission cannot deadlock before hydration`() = runTest {
        val storeWrapper = DelayedHydrationDraftStore()
        val vm = ChatViewModel.forTesting(storeWrapper).also { this@ChatViewModelDraftHydrationTest.vm = it }
        vm.updateDraftText(1L, "hello")

        var snapshot: ComposerSubmissionSnapshot? = null
        val submission = async {
            snapshot = withTimeout(2_000) { vm.beginSubmission(1L) }
        }
        testDispatcher.scheduler.runCurrent()
        assertNull("must remain pending without deadlock", snapshot)
        assertTrue(submission.isActive)

        storeWrapper.releaseLoad(emptyMap())
        submission.await()
        assertNotNull(snapshot)
        assertEquals("hello", snapshot?.rawInput)
    }

    @Test
    fun `attachment completion cannot deadlock before hydration`() = runTest {
        val storeWrapper = DelayedHydrationDraftStore()
        val vm = ChatViewModel.forTesting(storeWrapper).also { this@ChatViewModelDraftHydrationTest.vm = it }
        val owner = vm.beginAttachmentImport(1L)

        var accepted: Boolean? = null
        val completion = async {
            accepted = withTimeout(2_000) {
                vm.completeAttachmentImport(
                    1L, owner.token,
                    listOf(PendingAttachmentIdentity("a", "text/plain", "/managed/a")),
                )
            }
        }
        testDispatcher.scheduler.runCurrent()
        assertTrue(completion.isActive)

        storeWrapper.releaseLoad(emptyMap())
        completion.await()
        assertEquals(true, accepted)
        assertEquals(listOf("/managed/a"), vm.draft(1L).pendingAttachments.map { it.localPath })
    }

    @Test
    fun `attachment removal cannot deadlock before hydration`() = runTest {
        val storeWrapper = DelayedHydrationDraftStore()
        val vm = ChatViewModel.forTesting(storeWrapper).also { this@ChatViewModelDraftHydrationTest.vm = it }
        val owner = vm.beginAttachmentImport(1L)
        val imported = async {
            vm.completeAttachmentImport(
                1L, owner.token,
                listOf(PendingAttachmentIdentity("a", "text/plain", "/managed/a")),
            )
        }
        testDispatcher.scheduler.runCurrent()
        assertTrue(imported.isActive)

        var removed: Boolean? = null
        val removal = async {
            imported.await()
            removed = withTimeout(2_000) { vm.removePendingAttachment(1L, "/managed/a") }
        }
        testDispatcher.scheduler.runCurrent()
        assertTrue(removal.isActive)

        storeWrapper.releaseLoad(emptyMap())
        removal.await()
        assertEquals(true, removed)
        assertTrue(vm.draft(1L).pendingAttachments.isEmpty())
    }

    @Test
    fun `committed reconciliation cannot deadlock before hydration`() = runTest {
        val storeWrapper = DelayedHydrationDraftStore()
        val vm = ChatViewModel.forTesting(storeWrapper).also { this@ChatViewModelDraftHydrationTest.vm = it }
        vm.updateDraftText(1L, "send me")

        var snapshot: ComposerSubmissionSnapshot? = null
        val submission = async {
            snapshot = withTimeout(2_000) { vm.beginSubmission(1L) }
        }
        testDispatcher.scheduler.runCurrent()
        assertTrue(submission.isActive)

        storeWrapper.releaseLoad(emptyMap())
        submission.await()

        var reconciled: Boolean? = null
        val reconcile = async {
            reconciled = withTimeout(2_000) {
                vm.reconcileCommittedSubmission(requireNotNull(snapshot), emptyList())
            }
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, reconciled)
        assertEquals("", vm.draft(1L).rawInput)
        assertNull(vm.draft(1L).activeSubmissionToken)
    }

    @Test
    fun `multiple pre-hydration mutations compose in order`() = runTest {
        val storeWrapper = DelayedHydrationDraftStore()
        val vm = ChatViewModel.forTesting(storeWrapper).also { this@ChatViewModelDraftHydrationTest.vm = it }

        vm.updateDraftText(1L, "one")
        vm.appendQuickPrompt(1L, " two") { base, prompt -> base + prompt }
        vm.appendQuickPrompt(1L, " three") { base, prompt -> base + prompt }

        storeWrapper.releaseLoad(mapOf(1L to ComposerDraftState(rawInput = "stale", version = 7)))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("one two three", vm.draft(1L).rawInput)
        assertEquals(3L, vm.draft(1L).version)
    }

    @Test
    fun `delayed noncritical mutation survives hydration completion`() = runTest {
        val storeWrapper = DelayedHydrationDraftStore()
        val vm = ChatViewModel.forTesting(storeWrapper).also { this@ChatViewModelDraftHydrationTest.vm = it }

        vm.updateDraftText(1L, "kept update")
        val owner = vm.beginAttachmentImport(2L)

        storeWrapper.releaseLoad(
            mapOf(
                2L to ComposerDraftState(rawInput = "restored B", version = 1),
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("kept update", vm.draft(1L).rawInput)
        assertEquals("restored B", vm.draft(2L).rawInput)
        assertEquals(owner.token, vm.draft(2L).importOwnership?.token)
    }

    @Test
    fun `write failure before hydration completes deferred replies with false`() = runTest {
        val storeWrapper = FailingWriteDraftStore()
        val vm = ChatViewModel.forTesting(storeWrapper).also { this@ChatViewModelDraftHydrationTest.vm = it }

        val clear = async { vm.clearDraft(1L) }
        testDispatcher.scheduler.runCurrent()
        assertTrue(clear.isActive)

        storeWrapper.releaseLoad(emptyMap())
        assertFalse(clear.await())
        assertEquals("", vm.draft(1L).rawInput)
    }

    @Test
    fun `concurrent debounced and critical writes are serialized by writeId`() = runTest {
        val storeWrapper = DelayedHydrationDraftStore()
        val vm = ChatViewModel.forTesting(storeWrapper).also { this@ChatViewModelDraftHydrationTest.vm = it }

        vm.updateDraftText(1L, "debounced A")
        vm.updateDraftText(2L, "debounced B")
        storeWrapper.releaseLoad(emptyMap())
        val addAttachmentJob = async {
            val owner = vm.beginAttachmentImport(3L)
            vm.beginAttachmentCopy(3L, owner.token)
            vm.completeAttachmentImport(
                3L, owner.token,
                listOf(PendingAttachmentIdentity("a", "text/plain", "/managed/a"))
            )
        }
        vm.updateDraftText(4L, "debounced C")

        addAttachmentJob.await()
        testDispatcher.scheduler.advanceUntilIdle()

        val sorted = storeWrapper.writeIds.sorted()
        assertEquals(sorted, storeWrapper.writeIds)
    }
}

private class DelayedHydrationDraftStore : PersistentComposerDraftStore(
    file = Files.createTempFile("fusion-drafts", ".json").toFile(),
    resolveManagedAttachment = { null },
    registerPendingAttachment = { it.absolutePath },
) {
    private val loadDeferred = CompletableDeferred<Map<Long, ComposerDraftState>>()
    val writeIds = mutableListOf<Long>()
    var latestWritten: Map<Long, ComposerDraftState>? = null
        private set

    fun releaseLoad(data: Map<Long, ComposerDraftState>) {
        loadDeferred.complete(data)
    }

    override suspend fun load(): Map<Long, ComposerDraftState> = loadDeferred.await()

    override suspend fun write(writeId: Long, drafts: Map<Long, ComposerDraftState>): Boolean {
        writeIds.add(writeId)
        latestWritten = drafts
        return true
    }
}

private class FailingWriteDraftStore : PersistentComposerDraftStore(
    file = Files.createTempFile("fusion-drafts", ".json").toFile(),
    resolveManagedAttachment = { null },
    registerPendingAttachment = { it.absolutePath },
) {
    private val loadDeferred = CompletableDeferred<Map<Long, ComposerDraftState>>()

    fun releaseLoad(data: Map<Long, ComposerDraftState>) {
        loadDeferred.complete(data)
    }

    override suspend fun load(): Map<Long, ComposerDraftState> = loadDeferred.await()

    override suspend fun write(writeId: Long, drafts: Map<Long, ComposerDraftState>): Boolean = false
}
