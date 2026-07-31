package com.projectnuke.fusion.chat

import com.projectnuke.fusion.data.MessageEntity
import com.projectnuke.fusion.model.ChatMessage
import com.projectnuke.fusion.ui.ResponseVersionState
import com.projectnuke.fusion.ui.activeTimelineMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptHistoryBudgeterTest {
    @Test
    fun `short coherent history is preserved`() {
        val history = listOf(
            message("user", "u1"),
            message("assistant", "a1"),
            message("user", "u2"),
            message("assistant", "a2"),
        )

        val selected = select(history)

        assertEquals(history, selected.messages)
        assertEquals(0, selected.omittedTurnCount)
    }

    @Test
    fun `long history keeps the most recent whole turns`() {
        val history = (1..20).flatMap { index ->
            listOf(
                message("user", "u$index-" + "x".repeat(600)),
                message("assistant", "a$index-" + "y".repeat(600)),
            )
        }

        val selected = select(history, maxOutputTokens = 2_000)

        assertTrue(selected.omittedTurnCount > 0)
        assertTrue(selected.messages.last().content.startsWith("a20-"))
        assertWholeTurns(selected.messages)
    }

    @Test
    fun `oversized individual messages are deterministically bounded`() {
        val history = listOf(
            message("user", "U".repeat(20_000)),
            message("assistant", "A".repeat(20_000)),
        )

        val selected = select(history, modelId = "Gemma E4B", maxOutputTokens = 128)

        assertEquals(2, selected.messages.size)
        assertTrue(selected.messages.all { it.content.length <= 8_100 })
        assertTrue(selected.messages.all { "oversized message truncated" in it.content })
    }

    @Test
    fun `attachment and web reservations reduce retained history without splitting pairs`() {
        val history = (1..8).flatMap { index ->
            listOf(
                message("user", "user-$index-" + "x".repeat(400)),
                message("assistant", "assistant-$index-" + "y".repeat(400)),
            )
        }

        val selected = select(
            history = history,
            attachmentCount = 5,
            webSearchPlanned = true,
            maxOutputTokens = 1_000,
        )

        assertTrue(selected.messages.size < history.size)
        assertWholeTurns(selected.messages)
    }

    @Test
    fun `response version branch is selected before budgeting`() {
        val entities = listOf(
            entity(1, "user", "question"),
            entity(11, "assistant", "old branch"),
            entity(12, "assistant", "new branch"),
        )
        val versionState = ResponseVersionState(
            groupByMessageId = mapOf(11L to 1L, 12L to 1L),
            activeMessageIdByGroup = mapOf(1L to 11L),
        )
        val activeBranch = activeTimelineMessages(entities, versionState)
            .map { message(it.role, it.content) }

        val selected = select(activeBranch)

        assertTrue(selected.messages.any { it.content == "old branch" })
        assertFalse(selected.messages.any { it.content == "new branch" })
    }

    @Test
    fun `summary is included only when older turns are omitted`() {
        val history = (1..20).flatMap { index ->
            listOf(
                message("user", "u$index-" + "x".repeat(600)),
                message("assistant", "a$index-" + "y".repeat(600)),
            )
        }

        val selected = select(
            history = history,
            maxOutputTokens = 2_000,
            summary = "deterministic older context",
        )

        assertTrue(selected.usedSummary)
        assertTrue(selected.messages.first().content.contains("deterministic older context"))
        assertWholeTurns(selected.messages.filter { it.role != "system" })
    }

    private fun select(
        history: List<ChatMessage>,
        modelId: String = "Gemma E2B",
        maxOutputTokens: Int = 512,
        attachmentCount: Int = 0,
        webSearchPlanned: Boolean = false,
        summary: String? = null,
    ) = PromptHistoryBudgeter.select(
        PromptHistoryBudgetRequest(
            history = history,
            modelId = modelId,
            generationModeKey = "LOCAL_MODEL",
            maxOutputTokens = maxOutputTokens,
            currentRequestChars = 128,
            attachmentCount = attachmentCount,
            webSearchPlanned = webSearchPlanned,
            summaryText = summary,
        )
    )

    private fun assertWholeTurns(messages: List<ChatMessage>) {
        assertEquals(0, messages.size % 2)
        messages.chunked(2).forEach { turn ->
            assertEquals("user", turn[0].role)
            assertEquals("assistant", turn[1].role)
        }
    }

    private fun message(role: String, content: String) = ChatMessage(role = role, content = content)

    private fun entity(id: Long, role: String, content: String) = MessageEntity(
        id = id,
        conversationId = 1L,
        role = role,
        content = content,
        createdAt = id,
    )
}
