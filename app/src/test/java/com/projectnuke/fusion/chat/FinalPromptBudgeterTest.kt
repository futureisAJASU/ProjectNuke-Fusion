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
        val result = FinalPromptBudgeter.fit(messages, FinalPromptBudget(null, false, 512))
        assertEquals("policy", result.first().content)
        assertEquals("current", result.last().content)
        assertTrue(result.size % 2 == 0)
    }
}
