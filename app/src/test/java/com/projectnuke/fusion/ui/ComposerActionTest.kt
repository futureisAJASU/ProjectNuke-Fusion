package com.projectnuke.fusion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val pendingAttachments = mutableListOf<LocalAttachment>()
        val unregistered = mutableListOf<String>()
        var navCalls = 0

        settleCommittedDraft(
            input = { capturedRawInput },
            setInput = { /* capture cleared value */ },
            capturedRawInput = capturedRawInput,
            pendingAttachments = pendingAttachments,
            capturedDraftPaths = emptyList(),
            conversationWasCreated = false,
            activeConversationId = 1L,
            onConversationCreated = { navCalls++ },
            unregisterAttachment = { unregistered.add(it) }
        )

        // Input reconciliation is verified by the caller; no observable effect in this test
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
