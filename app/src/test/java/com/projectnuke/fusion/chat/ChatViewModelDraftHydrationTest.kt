package com.projectnuke.fusion.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelDraftHydrationTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `clear during load tombstones cannot be resurrected by hydration`() = runTest {
        val storeWrapper = DelayedHydrationDraftStore()
        val vm = ChatViewModel.forTesting(storeWrapper)

        vm.updateDraftText(1L, "draft during load")

        assertTrue(vm.clearDraft(1L))
        assertTrue(vm.draft(1L).rawInput.isEmpty())

        storeWrapper.releaseLoad(mapOf(1L to ComposerDraftState(rawInput = "stale restored", version = 1)))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", vm.draft(1L).rawInput)
        assertTrue(vm.draft(1L).pendingAttachments.isEmpty())
    }

    @Test
    fun `update during load wins over restored data`() = runTest {
        val storeWrapper = DelayedHydrationDraftStore()
        val vm = ChatViewModel.forTesting(storeWrapper)

        vm.updateDraftText(1L, "local update")

        storeWrapper.releaseLoad(mapOf(1L to ComposerDraftState(rawInput = "stale restored", version = 1)))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("local update", vm.draft(1L).rawInput)
    }

    @Test
    fun `stale load completion cannot restore a deleted draft`() = runTest {
        val storeWrapper = DelayedHydrationDraftStore()
        val vm = ChatViewModel.forTesting(storeWrapper)

        vm.updateDraftText(1L, "draft")
        assertTrue(vm.clearDraft(1L))

        storeWrapper.releaseLoad(mapOf(1L to ComposerDraftState(rawInput = "stale", version = 1)))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", vm.draft(1L).rawInput)
    }

    @Test
    fun `deletedBeforeHydration blocks stale persistence of restored draft`() = runTest {
        val storeWrapper = DelayedHydrationDraftStore()
        val vm = ChatViewModel.forTesting(storeWrapper)

        assertTrue(vm.clearDraft(1L))

        storeWrapper.releaseLoad(mapOf(1L to ComposerDraftState(rawInput = "stale", version = 1)))
        testDispatcher.scheduler.advanceUntilIdle()

        val persisted = storeWrapper.latestWritten
        assertTrue(persisted == null || !persisted.containsKey(1L))
    }

    @Test
    fun `write failure after clear does not advance durable ownership`() = runTest {
        val storeWrapper = FailingWriteDraftStore()
        val vm = ChatViewModel.forTesting(storeWrapper)

        vm.updateDraftText(1L, "draft")

        val result = vm.clearDraft(1L)
        assertFalse(result)

        assertEquals("", vm.draft(1L).rawInput)
    }

    @Test
    fun `concurrent debounced and critical writes are serialized by writeId`() = runTest {
        val storeWrapper = DelayedHydrationDraftStore()
        storeWrapper.releaseLoad(emptyMap())
        val vm = ChatViewModel.forTesting(storeWrapper)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.updateDraftText(1L, "debounced A")
        vm.updateDraftText(2L, "debounced B")
        val addAttachmentJob = launch {
            val owner = vm.beginAttachmentImport(3L)
            vm.beginAttachmentCopy(3L, owner.token)
            vm.completeAttachmentImport(
                3L, owner.token,
                listOf(PendingAttachmentIdentity("a", "text/plain", "/managed/a"))
            )
        }
        vm.updateDraftText(4L, "debounced C")

        addAttachmentJob.join()
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
        return super.write(writeId, drafts)
    }
}

private class FailingWriteDraftStore : PersistentComposerDraftStore(
    file = Files.createTempFile("fusion-drafts", ".json").toFile(),
    resolveManagedAttachment = { null },
    registerPendingAttachment = { it.absolutePath },
) {
    override suspend fun write(writeId: Long, drafts: Map<Long, ComposerDraftState>): Boolean = false
}
