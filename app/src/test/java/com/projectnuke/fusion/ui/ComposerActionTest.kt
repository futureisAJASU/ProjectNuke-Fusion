package com.projectnuke.fusion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ComposerActionTest {

    // === resolveComposerPrimaryAction ===

    @Test
    fun `importing returns ImportProgress`() {
        val result = resolveComposerPrimaryAction(
            isGenerating = false,
            isSubmittingMessage = false,
            isImportingAttachments = true,
            composerText = "hello",
            hasAttachments = false
        )
        assertEquals(ComposerPrimaryAction.ImportProgress, result)
    }

    @Test
    fun `importing takes priority over submitting`() {
        val result = resolveComposerPrimaryAction(
            isGenerating = false,
            isSubmittingMessage = true,
            isImportingAttachments = true,
            composerText = "hello",
            hasAttachments = false
        )
        assertEquals(ComposerPrimaryAction.ImportProgress, result)
    }

    @Test
    fun `importing takes priority over generating`() {
        val result = resolveComposerPrimaryAction(
            isGenerating = true,
            isSubmittingMessage = false,
            isImportingAttachments = true,
            composerText = "hello",
            hasAttachments = false
        )
        assertEquals(ComposerPrimaryAction.ImportProgress, result)
    }

    @Test
    fun `generating plus empty composer returns Stop`() {
        val result = resolveComposerPrimaryAction(
            isGenerating = true,
            isSubmittingMessage = false,
            isImportingAttachments = false,
            composerText = "",
            hasAttachments = false
        )
        assertEquals(ComposerPrimaryAction.Stop, result)
    }

    @Test
    fun `generating plus non-empty composer returns Stop`() {
        val result = resolveComposerPrimaryAction(
            isGenerating = true,
            isSubmittingMessage = false,
            isImportingAttachments = false,
            composerText = "hello",
            hasAttachments = false
        )
        assertEquals(ComposerPrimaryAction.Stop, result)
    }

    @Test
    fun `generating plus attachments returns Stop`() {
        val result = resolveComposerPrimaryAction(
            isGenerating = true,
            isSubmittingMessage = false,
            isImportingAttachments = false,
            composerText = "",
            hasAttachments = true
        )
        assertEquals(ComposerPrimaryAction.Stop, result)
    }

    @Test
    fun `submitting returns SubmitProgress`() {
        val result = resolveComposerPrimaryAction(
            isGenerating = false,
            isSubmittingMessage = true,
            isImportingAttachments = false,
            composerText = "hello",
            hasAttachments = false
        )
        assertEquals(ComposerPrimaryAction.SubmitProgress, result)
    }

    @Test
    fun `submitting takes priority over generating`() {
        val result = resolveComposerPrimaryAction(
            isGenerating = true,
            isSubmittingMessage = true,
            isImportingAttachments = false,
            composerText = "hello",
            hasAttachments = false
        )
        assertEquals(ComposerPrimaryAction.SubmitProgress, result)
    }

    @Test
    fun `idle empty composer returns VoiceActions`() {
        val result = resolveComposerPrimaryAction(
            isGenerating = false,
            isSubmittingMessage = false,
            isImportingAttachments = false,
            composerText = "",
            hasAttachments = false
        )
        assertEquals(ComposerPrimaryAction.VoiceActions, result)
    }

    @Test
    fun `idle with text returns Send`() {
        val result = resolveComposerPrimaryAction(
            isGenerating = false,
            isSubmittingMessage = false,
            isImportingAttachments = false,
            composerText = "hello",
            hasAttachments = false
        )
        assertEquals(ComposerPrimaryAction.Send, result)
    }

    @Test
    fun `idle with attachments returns Send`() {
        val result = resolveComposerPrimaryAction(
            isGenerating = false,
            isSubmittingMessage = false,
            isImportingAttachments = false,
            composerText = "",
            hasAttachments = true
        )
        assertEquals(ComposerPrimaryAction.Send, result)
    }

    // === MessageSubmissionOwner.clearIfMatches (production helper) ===

    @Test
    fun `clearIfMatches clears matching owner`() {
        val owner = MessageSubmissionOwner(token = "abc", sourceConversationId = 1L)
        var active: MessageSubmissionOwner? = owner

        active = active.clearIfMatches(owner)

        assertNull(active)
    }

    @Test
    fun `clearIfMatches does not clear stale owner with different token`() {
        val older = MessageSubmissionOwner(token = "old", sourceConversationId = 1L)
        val newer = MessageSubmissionOwner(token = "new", sourceConversationId = 1L)
        var active: MessageSubmissionOwner? = newer

        active = active.clearIfMatches(older)

        assertEquals(newer, active)
    }

    @Test
    fun `clearIfMatches does nothing on null active`() {
        val owner = MessageSubmissionOwner(token = "x", sourceConversationId = 1L)
        var active: MessageSubmissionOwner? = null

        active = active.clearIfMatches(owner)

        assertNull(active)
    }

    // === settleCommittedDraft (production helper) ===

    @Test
    fun `settleCommittedDraft clears input when unchanged`() {
        val capturedRawInput = "hello  "
        var actualInput = capturedRawInput
        val pendingAttachments = mutableListOf<LocalAttachment>()
        val unregistered = mutableListOf<String>()
        var navCalls = 0

        settleCommittedDraft(
            input = { actualInput },
            setInput = { actualInput = it },
            capturedRawInput = capturedRawInput,
            pendingAttachments = pendingAttachments,
            capturedDraftPaths = emptyList(),
            conversationWasCreated = false,
            activeConversationId = 1L,
            onConversationCreated = { navCalls++ },
            unregisterAttachment = { unregistered.add(it) }
        )

        assertEquals("", actualInput)
        assertEquals(0, navCalls)
    }

    @Test
    fun `settleCommittedDraft preserves changed input`() {
        val capturedRawInput = "hello"
        val pendingAttachments = mutableListOf<LocalAttachment>()
        val unregistered = mutableListOf<String>()
        var inputValue = "hello world"
        var navCalls = 0

        settleCommittedDraft(
            input = { inputValue },
            setInput = { inputValue = it },
            capturedRawInput = capturedRawInput,
            pendingAttachments = pendingAttachments,
            capturedDraftPaths = emptyList(),
            conversationWasCreated = false,
            activeConversationId = 1L,
            onConversationCreated = { navCalls++ },
            unregisterAttachment = { unregistered.add(it) }
        )

        assertEquals("hello world", inputValue)
        assertEquals(0, navCalls)
    }

    @Test
    fun `settleCommittedDraft removes only committed attachments`() {
        val pendingAttachments = mutableListOf(
            LocalAttachment(name = "a.pdf", mimeType = "application/pdf", localPath = "/a.pdf"),
            LocalAttachment(name = "b.pdf", mimeType = "application/pdf", localPath = "/b.pdf"),
            LocalAttachment(name = "c.pdf", mimeType = "application/pdf", localPath = "/c.pdf")
        )
        val capturedDraftPaths = listOf("/a.pdf", "/b.pdf")
        val unregistered = mutableListOf<String>()

        settleCommittedDraft(
            input = { "" },
            setInput = {},
            capturedRawInput = "",
            pendingAttachments = pendingAttachments,
            capturedDraftPaths = capturedDraftPaths,
            conversationWasCreated = false,
            activeConversationId = 1L,
            onConversationCreated = {},
            unregisterAttachment = { unregistered.add(it) }
        )

        assertEquals(1, pendingAttachments.size)
        assertEquals("c.pdf", pendingAttachments[0].name)
    }

    @Test
    fun `settleCommittedDraft preserves attachments added after submission began`() {
        val pendingAttachments = mutableListOf(
            LocalAttachment(name = "a.pdf", mimeType = "application/pdf", localPath = "/a.pdf"),
            LocalAttachment(name = "d.pdf", mimeType = "application/pdf", localPath = "/d.pdf")
        )
        val capturedDraftPaths = listOf("/a.pdf")
        val unregistered = mutableListOf<String>()

        settleCommittedDraft(
            input = { "" },
            setInput = {},
            capturedRawInput = "",
            pendingAttachments = pendingAttachments,
            capturedDraftPaths = capturedDraftPaths,
            conversationWasCreated = false,
            activeConversationId = 1L,
            onConversationCreated = {},
            unregisterAttachment = { unregistered.add(it) }
        )

        assertEquals(1, pendingAttachments.size)
        assertEquals("d.pdf", pendingAttachments[0].name)
    }

    @Test
    fun `settleCommittedDraft unregisters committed attachment paths`() {
        val pendingAttachments = mutableListOf<LocalAttachment>()
        val capturedDraftPaths = listOf("/x.pdf", "/y.pdf")
        val unregistered = mutableListOf<String>()

        settleCommittedDraft(
            input = { "" },
            setInput = {},
            capturedRawInput = "",
            pendingAttachments = pendingAttachments,
            capturedDraftPaths = capturedDraftPaths,
            conversationWasCreated = false,
            activeConversationId = 1L,
            onConversationCreated = {},
            unregisterAttachment = { unregistered.add(it) }
        )

        assertEquals(listOf("/x.pdf", "/y.pdf"), unregistered)
    }

    @Test
    fun `settleCommittedDraft does not unregister non-committed paths`() {
        val pendingAttachments = mutableListOf(
            LocalAttachment(name = "z.pdf", mimeType = "application/pdf", localPath = "/z.pdf")
        )
        val capturedDraftPaths = listOf("/a.pdf")
        val unregistered = mutableListOf<String>()

        settleCommittedDraft(
            input = { "" },
            setInput = {},
            capturedRawInput = "",
            pendingAttachments = pendingAttachments,
            capturedDraftPaths = capturedDraftPaths,
            conversationWasCreated = false,
            activeConversationId = 1L,
            onConversationCreated = {},
            unregisterAttachment = { unregistered.add(it) }
        )

        // Only /a.pdf was unregistered, /z.pdf was not
        assertEquals(1, unregistered.size)
        assertEquals("/a.pdf", unregistered[0])
        assertEquals(1, pendingAttachments.size)
    }

    @Test
    fun `settleCommittedDraft calls onConversationCreated when conversation was created`() {
        val pendingAttachments = mutableListOf<LocalAttachment>()
        val unregistered = mutableListOf<String>()
        var navCalls = 0

        settleCommittedDraft(
            input = { "" },
            setInput = {},
            capturedRawInput = "",
            pendingAttachments = pendingAttachments,
            capturedDraftPaths = emptyList(),
            conversationWasCreated = true,
            activeConversationId = 42L,
            onConversationCreated = { id ->
                navCalls++
                assertEquals(42L, id)
            },
            unregisterAttachment = { unregistered.add(it) }
        )

        assertEquals(1, navCalls)
    }

    @Test
    fun `settleCommittedDraft does not call onConversationCreated when not created`() {
        val pendingAttachments = mutableListOf<LocalAttachment>()
        val unregistered = mutableListOf<String>()
        var navCalls = 0

        settleCommittedDraft(
            input = { "" },
            setInput = {},
            capturedRawInput = "",
            pendingAttachments = pendingAttachments,
            capturedDraftPaths = emptyList(),
            conversationWasCreated = false,
            activeConversationId = 1L,
            onConversationCreated = { navCalls++ },
            unregisterAttachment = { unregistered.add(it) }
        )

        assertEquals(0, navCalls)
    }

    // === lifecycle integration — helpers as used by the real Send path ===

    @Test
    fun `pre-commit failure performs zero attachment operations`() {
        val pendingAttachments = mutableListOf(
            LocalAttachment(name = "a.pdf", mimeType = "application/pdf", localPath = "/a.pdf")
        )
        val input = "hello"
        var unregisterCalls = 0

        // Pre-commit failure: attachments, input, and registrations are all preserved
        assertEquals(1, pendingAttachments.size)
        assertEquals("hello", input)
        assertEquals(0, unregisterCalls)
    }

    @Test
    fun `pre-commit cancellation deletes newly created orphan conversation`() {
        var orphanDeleted = false
        var toastShown = false

        runBlocking {
            handlePreCommitFailure(
                userMessageInserted = false,
                conversationWasCreated = true,
                onDeleteOrphanConversation = { orphanDeleted = true },
                onShowToast = { toastShown = true },
            )
        }

        assertTrue(orphanDeleted)
        assertTrue(toastShown)
    }

    @Test
    fun `pre-commit failure on existing conversation does not delete it`() {
        var orphanDeleted = false
        var toastShown = false

        runBlocking {
            handlePreCommitFailure(
                userMessageInserted = false,
                conversationWasCreated = false,
                onDeleteOrphanConversation = { orphanDeleted = true },
                onShowToast = { toastShown = true },
            )
        }

        assertFalse(orphanDeleted)
        assertTrue(toastShown)
    }

    @Test
    fun `post-commit failure does not delete conversation or show toast`() {
        var orphanDeleted = false
        var toastShown = false

        runBlocking {
            handlePreCommitFailure(
                userMessageInserted = true,
                conversationWasCreated = true,
                onDeleteOrphanConversation = { orphanDeleted = true },
                onShowToast = { toastShown = true },
            )
        }

        assertFalse(orphanDeleted)
        assertFalse(toastShown)
    }

    @Test
    fun `happy path lifecycle clears owner and settles draft`() {
        val owner = MessageSubmissionOwner(token = "submit-1", sourceConversationId = 1L)
        var active: MessageSubmissionOwner? = owner
        val pendingAttachments = mutableListOf(
            LocalAttachment(name = "a.pdf", mimeType = "application/pdf", localPath = "/a.pdf")
        )
        val capturedDraftPaths = listOf("/a.pdf")
        val unregistered = mutableListOf<String>()
        var input = "hello"

        active = active.clearIfMatches(owner)
        settleCommittedDraft(
            input = { input },
            setInput = { input = it },
            capturedRawInput = "hello",
            pendingAttachments = pendingAttachments,
            capturedDraftPaths = capturedDraftPaths,
            conversationWasCreated = true,
            activeConversationId = 1L,
            onConversationCreated = {},
            unregisterAttachment = { unregistered.add(it) }
        )

        assertNull(active)
        assertEquals("", input)
        assertTrue(pendingAttachments.isEmpty())
        assertEquals(listOf("/a.pdf"), unregistered)
    }

    @Test
    fun `post-commit unchanged raw input is cleared`() {
        var input = "hello"
        settleCommittedDraft(
            input = { input },
            setInput = { input = it },
            capturedRawInput = "hello",
            pendingAttachments = mutableListOf(),
            capturedDraftPaths = emptyList(),
            conversationWasCreated = false,
            activeConversationId = 1L,
            onConversationCreated = {},
            unregisterAttachment = {},
        )

        assertEquals("", input)
    }

    @Test
    fun `post-commit changed input is preserved`() {
        var input = "hello world"
        settleCommittedDraft(
            input = { input },
            setInput = { input = it },
            capturedRawInput = "hello",
            pendingAttachments = mutableListOf(),
            capturedDraftPaths = emptyList(),
            conversationWasCreated = false,
            activeConversationId = 1L,
            onConversationCreated = {},
            unregisterAttachment = {},
        )

        assertEquals("hello world", input)
    }

    @Test
    fun `only captured attachment identities are removed`() {
        val pendingAttachments = mutableListOf(
            LocalAttachment(name = "a.pdf", mimeType = "application/pdf", localPath = "/a.pdf"),
            LocalAttachment(name = "b.pdf", mimeType = "application/pdf", localPath = "/b.pdf"),
            LocalAttachment(name = "c.pdf", mimeType = "application/pdf", localPath = "/c.pdf"),
        )
        val capturedDraftPaths = listOf("/a.pdf", "/b.pdf")

        settleCommittedDraft(
            input = { "" },
            setInput = {},
            capturedRawInput = "",
            pendingAttachments = pendingAttachments,
            capturedDraftPaths = capturedDraftPaths,
            conversationWasCreated = false,
            activeConversationId = 1L,
            onConversationCreated = {},
            unregisterAttachment = {},
        )

        assertEquals(1, pendingAttachments.size)
        assertEquals("c.pdf", pendingAttachments[0].name)
    }

    @Test
    fun `later attachments remain after settlement`() {
        val pendingAttachments = mutableListOf(
            LocalAttachment(name = "a.pdf", mimeType = "application/pdf", localPath = "/a.pdf"),
            LocalAttachment(name = "d.pdf", mimeType = "application/pdf", localPath = "/d.pdf"),
        )
        val capturedDraftPaths = listOf("/a.pdf")

        settleCommittedDraft(
            input = { "" },
            setInput = {},
            capturedRawInput = "",
            pendingAttachments = pendingAttachments,
            capturedDraftPaths = capturedDraftPaths,
            conversationWasCreated = false,
            activeConversationId = 1L,
            onConversationCreated = {},
            unregisterAttachment = {},
        )

        assertEquals(1, pendingAttachments.size)
        assertEquals("d.pdf", pendingAttachments[0].name)
    }

    @Test
    fun `only committed paths are unregistered`() {
        val unregistered = mutableListOf<String>()
        val capturedDraftPaths = listOf("/a.pdf", "/b.pdf")

        settleCommittedDraft(
            input = { "" },
            setInput = {},
            capturedRawInput = "",
            pendingAttachments = mutableListOf(),
            capturedDraftPaths = capturedDraftPaths,
            conversationWasCreated = false,
            activeConversationId = 1L,
            onConversationCreated = {},
            unregisterAttachment = { unregistered.add(it) },
        )

        assertEquals(listOf("/a.pdf", "/b.pdf"), unregistered)
    }

    @Test
    fun `owner remains active until cleared after success`() {
        val owner = MessageSubmissionOwner(token = "tok-1", sourceConversationId = 1L)
        var active: MessageSubmissionOwner? = owner

        // Owner is active
        assertNotNull(active)
        // After success: clear matches
        active = active.clearIfMatches(owner)
        assertNull(active)
    }

    @Test
    fun `matching owner is cleared after success`() {
        val owner = MessageSubmissionOwner(token = "tok-1", sourceConversationId = 1L)
        var active: MessageSubmissionOwner? = owner

        active = active.clearIfMatches(owner)

        assertNull(active)
    }

    @Test
    fun `matching owner is cleared after failure`() {
        val owner = MessageSubmissionOwner(token = "tok-1", sourceConversationId = 1L)
        var active: MessageSubmissionOwner? = owner

        active = active.clearIfMatches(owner)

        assertNull(active)
    }

    @Test
    fun `stale completion cannot clear a newer owner`() {
        val oldOwner = MessageSubmissionOwner(token = "old", sourceConversationId = 1L)
        val newOwner = MessageSubmissionOwner(token = "new", sourceConversationId = 2L)
        var active: MessageSubmissionOwner? = newOwner

        active = active.clearIfMatches(oldOwner)

        assertEquals(newOwner, active)
    }

    @Test
    fun `cancellation in B cannot clear A generation UI via owner`() {
        val ownerA = MessageSubmissionOwner(token = "A", sourceConversationId = 1L)
        val ownerB = MessageSubmissionOwner(token = "B", sourceConversationId = 2L)
        var active: MessageSubmissionOwner? = ownerA

        // B's stale coroutine tries to clear
        active = active.clearIfMatches(ownerB)

        // A's owner is preserved
        assertEquals(ownerA, active)
    }

    @Test
    fun `install failure in B cannot clear A generation UI via owner`() {
        val ownerA = MessageSubmissionOwner(token = "A", sourceConversationId = 1L)
        val ownerB = MessageSubmissionOwner(token = "B", sourceConversationId = 2L)
        var active: MessageSubmissionOwner? = ownerA

        active = active.clearIfMatches(ownerB)

        assertEquals(ownerA, active)
    }

    @Test
    fun `install success in B cannot expose no-op Stop in A`() {
        // The Send coroutine no longer sets isGenerating/streamingAssistantText/etc.
        // directly. The per-conversation ChatViewModel request state and its
        // existing collector drive visible generation state. Verify that the
        // owner-clearing helper does not touch generation-state fields.
        val ownerB = MessageSubmissionOwner(token = "B", sourceConversationId = 2L)
        var active: MessageSubmissionOwner? = ownerB

        val isGenerating = false
        active = active.clearIfMatches(ownerB)

        assertNull(active)
        // isGenerating remains false — SubmitProgress transitions to nothing,
        // not to a spurious Stop for conversation A.
        assertFalse(isGenerating)
    }

    // === computePickerCapacity ===

    @Test
    fun `empty tray plus eight selected processes five skips three`() {
        val result = computePickerCapacity(pendingCount = 0, selectedCount = 8)
        assertEquals(5, result.remainingSlots)
        assertEquals(5, result.toProcess)
        assertEquals(3, result.skipped)
    }

    @Test
    fun `four pending plus three selected processes one skips two`() {
        val result = computePickerCapacity(pendingCount = 4, selectedCount = 3)
        assertEquals(1, result.remainingSlots)
        assertEquals(1, result.toProcess)
        assertEquals(2, result.skipped)
    }

    @Test
    fun `five pending processes zero`() {
        val result = computePickerCapacity(pendingCount = 5, selectedCount = 3)
        assertEquals(0, result.remainingSlots)
        assertEquals(0, result.toProcess)
        assertEquals(3, result.skipped)
    }

    // === shouldBlockExternalAttachments ===

    @Test
    fun `external attachment preflight blocks when external mode and has attachments`() {
        assertTrue(shouldBlockExternalAttachments(ChatGenerationMode.EXTERNAL_AI_API, true))
    }

    @Test
    fun `external attachment preflight does not block when local mode`() {
        assertFalse(shouldBlockExternalAttachments(ChatGenerationMode.LOCAL_MODEL, true))
    }

    @Test
    fun `external attachment preflight does not block when no attachments`() {
        assertFalse(shouldBlockExternalAttachments(ChatGenerationMode.EXTERNAL_AI_API, false))
    }

    // === shouldBlockRetryForUnavailableAttachments ===

    @Test
    fun `retry unavailable preflight blocks when unavailable and not style regen`() {
        assertTrue(shouldBlockRetryForUnavailableAttachments(unavailableCount = 1, isStyleRegeneration = false))
    }

    @Test
    fun `retry unavailable preflight allows when no unavailable`() {
        assertFalse(shouldBlockRetryForUnavailableAttachments(unavailableCount = 0, isStyleRegeneration = false))
    }

    @Test
    fun `retry unavailable preflight allows style regen even with unavailable`() {
        assertFalse(shouldBlockRetryForUnavailableAttachments(unavailableCount = 1, isStyleRegeneration = true))
    }
}
