package com.projectnuke.fusion.chat

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelLifecycleAndDraftTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `configuration recreation retains active Activity scoped registry session`() = runTest {
        val activityViewModel = ChatViewModel()
        val release = CompletableDeferred<Unit>()
        val session = activityViewModel.registry.start(
            scope = activityViewModel.scope,
            snapshot = snapshot(41L, "request-41"),
        ) {
            release.await()
        }

        // A configuration change retains the same instance in the Activity's ViewModelStore.
        val recreatedCompositionOwner = activityViewModel
        assertTrue(recreatedCompositionOwner.registry.isActive(41L, "request-41"))
        assertFalse(session.job.isCancelled)

        release.complete(Unit)
        session.job.join()
    }

    @Test
    fun `current conversation and serializable drafts survive process recreation`() {
        val handle = SavedStateHandle()
        val original = ChatViewModel(handle)
        original.selectConversation(72L)
        original.updateDraftText(72L, "persisted draft")
        val import = original.beginAttachmentImport(72L)
        assertTrue(original.beginAttachmentCopy(72L, import.token))
        assertTrue(
            original.completeAttachmentImport(
                72L,
                import.token,
                listOf(PendingAttachmentIdentity("a.txt", "text/plain", "/managed/a.txt")),
            )
        )
        original.beginAttachmentImport(72L)

        val restored = ChatViewModel(
            SavedStateHandle(
                mapOf(
                    "current_conversation_id" to handle.get<Long>("current_conversation_id"),
                    "composer_drafts_v1" to handle.get<String>("composer_drafts_v1"),
                )
            )
        )

        assertEquals(72L, restored.currentConversationId.value)
        assertEquals("persisted draft", restored.draft(72L).rawInput)
        assertEquals(listOf("/managed/a.txt"), restored.draft(72L).pendingAttachments.map { it.localPath })
        assertNull("process recreation must not restore invalid import ownership", restored.draft(72L).importOwnership)
        assertNull("process recreation must not restore invalid submission ownership", restored.draft(72L).activeSubmissionToken)
    }

    @Test
    fun `A and B retain independent text and attachment trays`() {
        val vm = ChatViewModel()
        vm.updateDraftText(1L, "draft A")
        vm.updateDraftText(2L, "draft B")
        addAttachment(vm, 1L, PendingAttachmentIdentity("a", "text/plain", "/managed/a"))
        addAttachment(vm, 2L, PendingAttachmentIdentity("b", "image/png", "/managed/b"))

        assertEquals("draft A", vm.draft(1L).rawInput)
        assertEquals(listOf("/managed/a"), vm.draft(1L).pendingAttachments.map { it.localPath })
        assertEquals("draft B", vm.draft(2L).rawInput)
        assertEquals(listOf("/managed/b"), vm.draft(2L).pendingAttachments.map { it.localPath })
    }

    @Test
    fun `stale settlement for A cannot clear visible B`() {
        val vm = ChatViewModel()
        vm.updateDraftText(1L, "send A")
        addAttachment(vm, 1L, PendingAttachmentIdentity("a", "text/plain", "/managed/a"))
        val submissionA = requireNotNull(vm.beginSubmission(1L))

        vm.updateDraftText(2L, "keep B")
        addAttachment(vm, 2L, PendingAttachmentIdentity("b", "text/plain", "/managed/b"))
        vm.selectConversation(2L)

        assertTrue(vm.reconcileCommittedSubmission(submissionA, listOf("/managed/a")))
        assertEquals(2L, vm.currentConversationId.value)
        assertEquals("keep B", vm.draft(2L).rawInput)
        assertEquals(listOf("/managed/b"), vm.draft(2L).pendingAttachments.map { it.localPath })
    }

    @Test
    fun `new chat draft is isolated from persisted conversations`() {
        val vm = ChatViewModel()
        vm.updateDraftText(ChatViewModel.NEW_CONVERSATION_ID, "new chat")
        vm.updateDraftText(9L, "persisted chat")

        assertEquals("new chat", vm.draft(ChatViewModel.NEW_CONVERSATION_ID).rawInput)
        assertEquals("persisted chat", vm.draft(9L).rawInput)
        vm.clearDraft(9L)
        assertEquals("new chat", vm.draft(ChatViewModel.NEW_CONVERSATION_ID).rawInput)
    }

    @Test
    fun `stale import owner cannot mutate a replacement draft owner`() {
        val vm = ChatViewModel()
        val old = vm.beginAttachmentImport(1L)
        val replacement = vm.beginAttachmentImport(1L)

        assertFalse(
            vm.completeAttachmentImport(
                1L,
                old.token,
                listOf(PendingAttachmentIdentity("stale", "text/plain", "/managed/stale")),
            )
        )
        assertEquals(replacement.token, vm.draft(1L).importOwnership?.token)
        assertTrue(vm.draft(1L).pendingAttachments.isEmpty())
    }

    @Test
    fun `terminal request removal preserves replacement identity guard`() {
        val vm = ChatViewModel()
        vm.update(1L) { it.copy(activeRequestId = "A", isGenerating = true) }
        vm.finishRequestState(1L, "A")
        assertFalse(vm.states.value.containsKey(1L))

        vm.update(1L) { it.copy(activeRequestId = "B", isGenerating = true) }
        vm.finishRequestState(1L, "A")
        assertEquals("B", vm.state(1L).activeRequestId)
    }

    private fun addAttachment(
        vm: ChatViewModel,
        conversationId: Long,
        attachment: PendingAttachmentIdentity,
    ) {
        val owner = vm.beginAttachmentImport(conversationId)
        assertTrue(vm.beginAttachmentCopy(conversationId, owner.token))
        assertTrue(vm.completeAttachmentImport(conversationId, owner.token, listOf(attachment)))
    }

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
}
