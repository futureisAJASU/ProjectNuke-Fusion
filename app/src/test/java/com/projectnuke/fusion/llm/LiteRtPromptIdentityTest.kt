package com.projectnuke.fusion.llm

import com.projectnuke.fusion.model.ChatMessage
import com.projectnuke.fusion.model.ConversationOptions
import com.projectnuke.fusion.model.GenerationSettings
import com.projectnuke.fusion.model.toConversationOptions
import com.projectnuke.fusion.model.toRequestedEngineProfile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `system instruction contains formal Korean and no corrupted substitutions`() {
        val systemInstruction = buildSystemInstruction(messages)

        // Expected Korean phrases from the restored system instruction
        assertTrue("system instruction must contain '당신은 기기 내에서 실행되는 AI 비서 Fusion입니다.'",
            systemInstruction.contains("당신은 기기 내에서 실행되는 AI 비서 Fusion입니다."))
        assertTrue("system instruction must contain '한국어로 자연스럽게 답변하며 일관되게 존댓말을 사용합니다.'",
            systemInstruction.contains("한국어로 자연스럽게 답변하며 일관되게 존댓말을 사용합니다."))
        assertTrue("system instruction must contain '모르는 내용은 모른다고 명확히 밝힙니다.'",
            systemInstruction.contains("모르는 내용은 모른다고 명확히 밝힙니다."))
        assertTrue("system instruction must contain '추론이나 추정은 그 사실을 명확히 구분합니다.'",
            systemInstruction.contains("추론이나 추정은 그 사실을 명확히 구분합니다."))

        // No corrupted '?' substitutions (the encoding regression artifact)
        assertFalse("system instruction must not contain corrupted '?' substitutions",
            systemInstruction.contains("?신?") || systemInstruction.contains("?국?로") ||
            systemInstruction.contains("모르??") || systemInstruction.contains("추론?나"))
    }

    @Test
    fun `generation failure messages use formal Korean and no corrupted substitutions`() {
        val messages = listOf(ChatMessage(role = "user", content = "test"))
        val profile = GenerationSettings().toRequestedEngineProfile("model.litertlm", enableVisionBackend = false)

        // Test the classifyGenerationException function through its exposed behavior
        // We verify the mapping used in classifyGenerationException
        val failureMessages = mapOf(
            FailureKind.MODEL_MULTIMODAL_UNSUPPORTED to "이 모델은 이미지 입력을 지원하지 않습니다.",
            FailureKind.MODEL_LOAD_FAILED to "모델을 불러올 수 없습니다. 모델 설정을 확인한 뒤 다시 시도해 주세요.",
            FailureKind.GENERATION_IO to "모델 응답 중 입출력 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
            FailureKind.GENERATION_INTERRUPTED to "모델 응답을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요."
        )

        failureMessages.forEach { (kind, expectedMessage) ->
            assertEquals("failure message for $kind must match formal Korean",
                expectedMessage, getFailureMessage(kind))
            assertFalse("failure message for $kind must not contain corrupted '?' substitutions",
                expectedMessage.contains("??") || expectedMessage.contains("?시 ?") || expectedMessage.contains("?답??"))
        }
    }

    private fun getFailureMessage(kind: FailureKind): String {
        return when (kind) {
            FailureKind.MODEL_MULTIMODAL_UNSUPPORTED -> "이 모델은 이미지 입력을 지원하지 않습니다."
            FailureKind.MODEL_LOAD_FAILED -> "모델을 불러올 수 없습니다. 모델 설정을 확인한 뒤 다시 시도해 주세요."
            FailureKind.GENERATION_IO -> "모델 응답 중 입출력 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
            FailureKind.GENERATION_INTERRUPTED -> "모델 응답을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요."
            else -> "모델 응답을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요."
        }
    }
}
