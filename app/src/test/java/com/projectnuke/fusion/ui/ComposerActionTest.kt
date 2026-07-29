package com.projectnuke.fusion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerActionTest {

    @Test
    fun `generating plus empty composer returns Stop`() {
        val result = resolveComposerPrimaryAction(
            isGenerating = true,
            isSubmittingMessage = false,
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
            composerText = "",
            hasAttachments = true
        )
        assertEquals(ComposerPrimaryAction.Send, result)
    }

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
