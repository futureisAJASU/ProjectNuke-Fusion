package com.projectnuke.fusion.llm

import com.projectnuke.fusion.model.ChatMessage
import com.projectnuke.fusion.model.ConversationOptions
import com.projectnuke.fusion.model.GenerationSettings
import com.projectnuke.fusion.model.toConversationOptions
import com.projectnuke.fusion.model.toRequestedEngineProfile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * MTP on/off (and any other runtime-engine setting change) must never change
 * the prompt bytes: the prompt is built from messages only, never from engine
 * settings. This guards against runtime settings leaking into prompts, which
 * would invalidate MTP on/off benchmark comparisons.
 */
class LiteRtPromptIdentityTest {

    private val messages = listOf(
        ChatMessage(role = "system", content = "사용자가 지정한 시스템 지시사항입니다."),
        ChatMessage(role = "user", content = "안녕하세요"),
        ChatMessage(role = "assistant", content = "안녕하세요! 무엇을 도와드릴까요?"),
        ChatMessage(role = "user", content = "오늘 날씨를 알려주세요")
    )

    @Test
    fun `prompt bytes are identical for MTP on and MTP off profiles`() {
        val onSettings = GenerationSettings(
            maxTokens = 4096,
            accelerator = com.projectnuke.fusion.model.AcceleratorMode.GPU,
            speculativeDecodingEnabled = true
        ).toRequestedEngineProfile("model.litertlm", enableVisionBackend = false)
        val offSettings = GenerationSettings(
            maxTokens = 1024,
            accelerator = com.projectnuke.fusion.model.AcceleratorMode.CPU,
            speculativeDecodingEnabled = false
        ).toRequestedEngineProfile("model.litertlm", enableVisionBackend = false)

        assertNotEquals(onSettings, offSettings)

        val systemOn = buildSystemInstruction(messages)
        val systemOff = buildSystemInstruction(messages)
        val promptOn = buildPrompt(messages)
        val promptOff = buildPrompt(messages)

        assertArrayEquals(systemOn.toByteArray(), systemOff.toByteArray())
        assertArrayEquals(promptOn.toByteArray(), promptOff.toByteArray())
    }

    @Test
    fun `prompt bytes are identical across sampling options`() {
        val lowTemp = GenerationSettings(
            maxTokens = 4000,
            temperature = 0.1f,
            topK = 1,
            topP = 0.1f
        ).toConversationOptions()
        val highTemp = ConversationOptions(
            maxOutputToken = 8192,
            temperature = 1.9f,
            topK = 100,
            topP = 1.0f,
            seed = 42
        )
        assertNotEquals(lowTemp, highTemp)

        assertArrayEquals(
            buildSystemInstruction(messages).toByteArray(),
            buildSystemInstruction(messages).toByteArray()
        )
        assertArrayEquals(
            buildPrompt(messages).toByteArray(),
            buildPrompt(messages).toByteArray()
        )
    }

    @Test
    fun `prompt never contains runtime settings text`() {
        val fullPrompt = buildSystemInstruction(messages) + "\n" + buildPrompt(messages)
        val forbiddenTokens = listOf(
            "GENERATION_SETTINGS",
            "speculativeDecoding",
            "MTP",
            "maxTokens",
            "maxNumTokens",
            "topK",
            "topP",
            "temperature",
            "accelerator",
            "GPU",
            "CPU",
            "backend",
            "kvCache",
            "reasoningBudgetTokens"
        )
        forbiddenTokens.forEach { token ->
            assertFalse("prompt must not contain '$token'", fullPrompt.contains(token))
        }
    }
}
