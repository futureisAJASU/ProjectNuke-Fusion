package com.projectnuke.fusion.chat

import com.projectnuke.fusion.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalPromptBudgeterTest {
    @Test
    fun `final prompt preserves system and current request while trimming whole turns`() {
        val messages = buildList {
            add(ChatMessage("system", "policy"))
            repeat(12) { add(ChatMessage("user", "u".repeat(2_000))); add(ChatMessage("assistant", "a".repeat(2_000))) }
            add(ChatMessage("user", "current"))
        }
        val result = FinalPromptBudgeter.fit(messages, FinalPromptBudget(null, false, 512)).messages
        assertEquals("policy", result.first().content)
        assertEquals("current", result.last().content)
        assertTrue(result.size % 2 == 0)
    }

    @Test
    fun `mandatory context larger than total capacity returns TooLarge marker`() {
        val messages = buildList {
            add(ChatMessage("system", "s".repeat(8_000)))
            add(ChatMessage("system", "s2".repeat(8_000)))
            add(ChatMessage("system", "s3".repeat(8_000)))
            add(ChatMessage("system", "s4".repeat(8_000)))
            add(ChatMessage("user", "current"))
        }
        val result = FinalPromptBudgeter.fit(messages, FinalPromptBudget("2b", false, 512))
        assertTrue(result.isTooLarge)
    }

    @Test
    fun `uses externaModelContextChars when provided instead of model name heuristic`() {
        val messages = buildList {
            add(ChatMessage("system", "s"))
            add(ChatMessage("user", "u".repeat(10_000)))
            add(ChatMessage("assistant", "a"))
        }
        val result = FinalPromptBudgeter.fit(messages, FinalPromptBudget("4b", false, 512, externaModelContextChars = 128_000))
        assertEquals(3, result.messages.size)
    }

    @Test
    fun malformed_history_beginning_with_assistant_is_handled() {
        val messages = listOf(
            ChatMessage("assistant", "orphan"),
            ChatMessage("user", "current"),
        )
        val result = FinalPromptBudgeter.fit(messages, FinalPromptBudget(null, false, 512))
        assertTrue(result.messages.last().role == "user")
    }

    @Test
    fun five_attachments_plus_web_search_memory_fits_within_budget() {
        val messages = buildList {
            add(ChatMessage("system", "policy"))
            repeat(5) { add(ChatMessage("user", "u".repeat(500))); add(ChatMessage("assistant", "a".repeat(500))) }
            add(ChatMessage("user", "current"))
        }
        val result = FinalPromptBudgeter.fit(messages, FinalPromptBudget(null, false, 2048))
        assertTrue(result.messages.sumOf { it.content.length } <= 48_000)
    }

    @Test
    fun external_request_uses_48k_budget() {
        val messages = buildList {
            add(ChatMessage("system", "s"))
            repeat(15) { add(ChatMessage("user", "u".repeat(1_500))); add(ChatMessage("assistant", "a".repeat(1_500))) }
            add(ChatMessage("user", "current"))
        }
        val result = FinalPromptBudgeter.fit(messages, FinalPromptBudget(null, true, 4096))
        val totalChars = result.messages.sumOf { it.content.length }
        assertTrue(totalChars <= 40_000)
    }
}