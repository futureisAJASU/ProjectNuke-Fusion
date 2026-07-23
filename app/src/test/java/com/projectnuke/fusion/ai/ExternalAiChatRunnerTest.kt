package com.projectnuke.fusion.ai

import com.projectnuke.fusion.ai.model.AiChatRequest
import com.projectnuke.fusion.ai.model.AiChatResponse
import com.projectnuke.fusion.ai.model.AiMessage
import com.projectnuke.fusion.ai.model.AiProviderConfig
import com.projectnuke.fusion.ai.model.AiProviderType
import com.projectnuke.fusion.ai.model.AiRole
import com.projectnuke.fusion.ai.network.AiProviderClientException
import com.projectnuke.fusion.ai.network.ChatClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalAiChatRunnerTest {

    @Test
    fun blankContent_mapsToEmpty() = runBlocking {
        val fakeClient = object : ChatClient {
            override suspend fun chatCompletion(
                config: AiProviderConfig,
                request: AiChatRequest
            ): AiChatResponse {
                return AiChatResponse(id = "1", model = "test", content = "")
            }
        }
        val runner = ExternalAiChatRunner(FakeProviderSource(), fakeClient)
        val result = runner.generateFromMessages(
            messages = listOf(AiMessage(AiRole.USER, "hi")),
            hasAttachments = false
        )
        assertTrue(result is ExternalAiChatResult.Empty)
    }

    @Test
    fun blankContentWithWhitespace_mapsToEmpty() = runBlocking {
        val fakeClient = object : ChatClient {
            override suspend fun chatCompletion(
                config: AiProviderConfig,
                request: AiChatRequest
            ): AiChatResponse {
                return AiChatResponse(id = "1", model = "test", content = "  \n\t  ")
            }
        }
        val runner = ExternalAiChatRunner(FakeProviderSource(), fakeClient)
        val result = runner.generateFromMessages(
            messages = listOf(AiMessage(AiRole.USER, "hi")),
            hasAttachments = false
        )
        assertTrue(result is ExternalAiChatResult.Empty)
    }

    @Test
    fun nonBlankContent_mapsToSuccess() = runBlocking {
        val fakeClient = object : ChatClient {
            override suspend fun chatCompletion(
                config: AiProviderConfig,
                request: AiChatRequest
            ): AiChatResponse {
                return AiChatResponse(id = "1", model = "test", content = "Hello!")
            }
        }
        val runner = ExternalAiChatRunner(FakeProviderSource(), fakeClient)
        val result = runner.generateFromMessages(
            messages = listOf(AiMessage(AiRole.USER, "hi")),
            hasAttachments = false
        )
        assertTrue(result is ExternalAiChatResult.Success)
        assertEquals("Hello!", (result as ExternalAiChatResult.Success).content)
    }

    @Test
    fun clientException_mapsToError() = runBlocking {
        val fakeClient = object : ChatClient {
            override suspend fun chatCompletion(
                config: AiProviderConfig,
                request: AiChatRequest
            ): AiChatResponse {
                throw AiProviderClientException("Test error message")
            }
        }
        val runner = ExternalAiChatRunner(FakeProviderSource(), fakeClient)
        val result = runner.generateFromMessages(
            messages = listOf(AiMessage(AiRole.USER, "hi")),
            hasAttachments = false
        )
        assertTrue(result is ExternalAiChatResult.Error)
        assertEquals("Test error message", (result as ExternalAiChatResult.Error).message)
    }

    @Test
    fun cancellationException_isRethrown() = runBlocking {
        val fakeClient = object : ChatClient {
            override suspend fun chatCompletion(
                config: AiProviderConfig,
                request: AiChatRequest
            ): AiChatResponse {
                throw CancellationException("test cancel")
            }
        }
        val runner = ExternalAiChatRunner(FakeProviderSource(), fakeClient)
        try {
            runner.generateFromMessages(
                messages = listOf(AiMessage(AiRole.USER, "hi")),
                hasAttachments = false
            )
            assertTrue("Expected CancellationException", false)
        } catch (e: CancellationException) {
            assertEquals("test cancel", e.message)
        }
    }

    @Test
    fun blockedAttachment_returnsBlockedAttachment() = runBlocking {
        val fakeClient = object : ChatClient {
            override suspend fun chatCompletion(
                config: AiProviderConfig,
                request: AiChatRequest
            ): AiChatResponse {
                throw AssertionError("Should not be called")
            }
        }
        val runner = ExternalAiChatRunner(FakeProviderSource(), fakeClient)
        val result = runner.generateFromMessages(
            messages = listOf(AiMessage(AiRole.USER, "hi")),
            hasAttachments = true
        )
        assertTrue(result is ExternalAiChatResult.BlockedAttachment)
    }

    @Test
    fun noProvider_returnsNoProvider() = runBlocking {
        val fakeClient = object : ChatClient {
            override suspend fun chatCompletion(
                config: AiProviderConfig,
                request: AiChatRequest
            ): AiChatResponse {
                throw AssertionError("Should not be called")
            }
        }
        val runner = ExternalAiChatRunner(NoProviderSource(), fakeClient)
        val result = runner.generateFromMessages(
            messages = listOf(AiMessage(AiRole.USER, "hi")),
            hasAttachments = false
        )
        assertTrue(result is ExternalAiChatResult.NoProvider)
    }

    private class FakeProviderSource : ExternalAiProviderSource {
        override suspend fun getSelectedRunnableProvider(): AiProviderConfig? {
            return AiProviderConfig(
                id = "test",
                type = AiProviderType.OPENAI,
                displayName = "Test",
                baseUrl = "https://example.com/",
                modelId = "test-model",
                apiKeySecretId = "test-key"
            )
        }
    }

    private class NoProviderSource : ExternalAiProviderSource {
        override suspend fun getSelectedRunnableProvider(): AiProviderConfig? = null
    }
}
