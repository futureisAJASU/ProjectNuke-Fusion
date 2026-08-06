package com.projectnuke.fusion.chat

import com.projectnuke.fusion.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairPhaseEPromptTokenEstimatorTest {

    @Test
    fun `estimateTokens returns 0 for an empty message list`() {
        assertEquals(0, PromptTokenEstimator.estimateTokens(emptyList()))
    }

    @Test
    fun `estimateTokens accounts for message overhead per message`() {
        // Two messages, each 8 chars of content + 32 overhead = 80 chars / 4 = 20 tokens.
        val messages = listOf(
            ChatMessage("user", "12345678"),
            ChatMessage("assistant", "abcdefgh"),
        )
        assertEquals(20, PromptTokenEstimator.estimateTokens(messages))
    }

    @Test
    fun `estimateTokensForString applies the 4-chars-per-token heuristic`() {
        assertEquals(0, PromptTokenEstimator.estimateTokensForString(""))
        assertEquals(0, PromptTokenEstimator.estimateTokensForString("abc"))
        assertEquals(1, PromptTokenEstimator.estimateTokensForString("abcd"))
        assertEquals(5, PromptTokenEstimator.estimateTokensForString("a".repeat(20)))
    }

    @Test
    fun `estimateTokensForString never returns negative for unusual inputs`() {
        assertTrue(PromptTokenEstimator.estimateTokensForString("") >= 0)
        assertTrue(PromptTokenEstimator.estimateTokensForString("가".repeat(400)) >= 0)
    }

    @Test
    fun `estimateTokens grows monotonically with message length`() {
        val short = PromptTokenEstimator.estimateTokensForString("a".repeat(40))
        val long = PromptTokenEstimator.estimateTokensForString("a".repeat(400))
        assertTrue("longer text must yield more tokens", long > short)
    }
}
