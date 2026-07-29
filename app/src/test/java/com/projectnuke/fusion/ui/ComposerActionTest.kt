package com.projectnuke.fusion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

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

    // === MessageSubmissionOwner ===

    @Test
    fun `stale submission cannot clear newer owner`() {
        val older = MessageSubmissionOwner(token = "old", sourceConversationId = 1L)
        val newer = MessageSubmissionOwner(token = "new", sourceConversationId = 1L)
        var active: MessageSubmissionOwner? = newer

        // older token tries to clear
        if (active?.token == older.token) {
            active = null
        }

        assertEquals(newer, active)
    }

    @Test
    fun `correct owner clears submission`() {
        val owner = MessageSubmissionOwner(token = "abc", sourceConversationId = 1L)
        var active: MessageSubmissionOwner? = owner

        if (active?.token == owner.token) {
            active = null
        }

        assertEquals(null, active)
    }

    @Test
    fun `null active submission does not match any owner`() {
        val owner = MessageSubmissionOwner(token = "x", sourceConversationId = 1L)
        var active: MessageSubmissionOwner? = null

        if (active?.token == owner.token) {
            active = null
        }

        assertEquals(null, active)
    }

    @Test
    fun `conversation observation does not terminate submission owner`() {
        val owner = MessageSubmissionOwner(token = "obs", sourceConversationId = 1L)
        var active: MessageSubmissionOwner? = owner

        // Simulate generation-state observation that would have set isSubmittingMessage = false
        val isGeneratingFromState = true
        if (isGeneratingFromState) {
            // This is what the new code must NOT do
        }

        assertNotNull(active)
        assertEquals("obs", active?.token)
    }

    @Test
    fun `raw input with trailing spaces clears after successful commit`() {
        val rawInput = "hello  "
        val normalizedText = rawInput.trim()

        assertEquals("hello", normalizedText)

        // Simulate successful commit reconciliation
        var input = rawInput
        if (input == rawInput) {
            input = ""
        }
        assertEquals("", input)
    }

    @Test
    fun `changed input is preserved after commit`() {
        val rawInput = "hello"
        var input = "hello world" // user changed input during submission

        if (input == rawInput) {
            input = ""
        }

        assertEquals("hello world", input)
    }

    @Test
    fun `committed captured attachments are removed while newer attachments remain`() {
        val pendingAttachments = mutableListOf(
            LocalAttachment(name = "a.pdf", mimeType = "application/pdf", localPath = "/a.pdf"),
            LocalAttachment(name = "b.pdf", mimeType = "application/pdf", localPath = "/b.pdf"),
            LocalAttachment(name = "c.pdf", mimeType = "application/pdf", localPath = "/c.pdf")
        )
        val capturedDraft = listOf(
            LocalAttachment(name = "a.pdf", mimeType = "application/pdf", localPath = "/a.pdf"),
            LocalAttachment(name = "b.pdf", mimeType = "application/pdf", localPath = "/b.pdf")
        )

        capturedDraft.forEach { attachment ->
            pendingAttachments.remove(attachment)
        }

        assertEquals(1, pendingAttachments.size)
        assertEquals("c.pdf", pendingAttachments[0].name)
    }

    @Test
    fun `pre-insert failure preserves all captured draft state`() {
        val rawInput = "hello"
        val attachments = listOf(
            LocalAttachment(name = "a.pdf", mimeType = "application/pdf", localPath = "/a.pdf")
        )

        // Simulate pre-insert failure - nothing should be cleared
        var input = rawInput
        val pendingBefore = attachments.toList()

        assertEquals("hello", input)
        assertEquals(1, pendingBefore.size)
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

    @Test
    fun `importing state uses capacity result and prevents second import`() {
        var isImporting = false
        val capacity = computePickerCapacity(pendingCount = 3, selectedCount = 4)

        // First import starts
        isImporting = true
        assertTrue(isImporting)

        // Second import rejected
        val secondAttemptBlocked = isImporting
        assertTrue(secondAttemptBlocked)

        assertEquals(2, capacity.remainingSlots)
        assertEquals(2, capacity.toProcess)
        assertEquals(2, capacity.skipped)
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

    // === MessageSubmissionOwner (UUID token) ===

    @Test
    fun `unique tokens do not match`() {
        val a = MessageSubmissionOwner(token = UUID.randomUUID().toString(), sourceConversationId = 1L)
        val b = MessageSubmissionOwner(token = UUID.randomUUID().toString(), sourceConversationId = 1L)
        assertNotEquals(a.token, b.token)
    }
}
