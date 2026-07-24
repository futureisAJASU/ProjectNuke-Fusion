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

    @Test
    fun capturedProviderId_usedEvenIfSelectionChanges() = runBlocking {
        val multiSource = MultiProviderSource()
        multiSource.selectedId = "provider_b"
        val capturedProviderId = "provider_a"
        val fakeClient = object : ChatClient {
            override suspend fun chatCompletion(
                config: AiProviderConfig,
                request: AiChatRequest
            ): AiChatResponse {
                assertEquals("provider_a", config.id)
                assertEquals("https://a.example.com/", config.baseUrl)
                return AiChatResponse(id = "1", model = "a-model", content = "From A")
            }
        }
        val runner = ExternalAiChatRunner(multiSource, fakeClient)
        val result = runner.generateFromMessages(
            messages = listOf(AiMessage(AiRole.USER, "hi")),
            hasAttachments = false,
            providerId = capturedProviderId
        )
        assertTrue(result is ExternalAiChatResult.Success)
        assertEquals("From A", (result as ExternalAiChatResult.Success).content)
    }

    @Test
    fun capturedProviderDeleted_returnsNoProvider() = runBlocking {
        val multiSource = MultiProviderSource()
        multiSource.selectedId = "provider_b"
        val fakeClient = object : ChatClient {
            override suspend fun chatCompletion(
                config: AiProviderConfig,
                request: AiChatRequest
            ): AiChatResponse {
                throw AssertionError("Should not be called")
            }
        }
        val runner = ExternalAiChatRunner(multiSource, fakeClient)
        val result = runner.generateFromMessages(
            messages = listOf(AiMessage(AiRole.USER, "hi")),
            hasAttachments = false,
            providerId = "deleted_provider"
        )
        assertTrue(result is ExternalAiChatResult.NoProvider)
    }

    @Test
    fun capturedProviderDisabled_returnsNoProvider() = runBlocking {
        val source = object : ExternalAiProviderSource {
            override suspend fun getSelectedRunnableProvider(): AiProviderConfig? {
                return AiProviderConfig(
                    id = "enabled", type = AiProviderType.OPENAI, displayName = "Enabled",
                    baseUrl = "https://enabled.example.com/", modelId = "e-model", apiKeySecretId = "e-key"
                )
            }
            override suspend fun getRunnableProviderById(id: String): AiProviderConfig? {
                if (id == "disabled") return null
                return AiProviderConfig(
                    id = "enabled", type = AiProviderType.OPENAI, displayName = "Enabled",
                    baseUrl = "https://enabled.example.com/", modelId = "e-model", apiKeySecretId = "e-key"
                )
            }
        }
        val fakeClient = object : ChatClient {
            override suspend fun chatCompletion(
                config: AiProviderConfig,
                request: AiChatRequest
            ): AiChatResponse {
                throw AssertionError("Should not be called")
            }
        }
        val runner = ExternalAiChatRunner(source, fakeClient)
        val result = runner.generateFromMessages(
            messages = listOf(AiMessage(AiRole.USER, "hi")),
            hasAttachments = false,
            providerId = "disabled"
        )
        assertTrue(result is ExternalAiChatResult.NoProvider)
    }

    @Test
    fun nullProviderId_usesSelectedProvider() = runBlocking {
        val multiSource = MultiProviderSource()
        multiSource.selectedId = "provider_b"
        val fakeClient = object : ChatClient {
            override suspend fun chatCompletion(
                config: AiProviderConfig,
                request: AiChatRequest
            ): AiChatResponse {
                assertEquals("provider_b", config.id)
                return AiChatResponse(id = "1", model = "b-model", content = "From B")
            }
        }
        val runner = ExternalAiChatRunner(multiSource, fakeClient)
        val result = runner.generateFromMessages(
            messages = listOf(AiMessage(AiRole.USER, "hi")),
            hasAttachments = false,
            providerId = null
        )
        assertTrue(result is ExternalAiChatResult.Success)
        assertEquals("From B", (result as ExternalAiChatResult.Success).content)
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

        override suspend fun getRunnableProviderById(id: String): AiProviderConfig? {
            if (id != "test") return null
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
        override suspend fun getRunnableProviderById(id: String): AiProviderConfig? = null
    }

    private class MultiProviderSource : ExternalAiProviderSource {
        var selectedId: String? = "provider_b"

        private val providers = listOf(
            AiProviderConfig(
                id = "provider_a",
                type = AiProviderType.OPENAI,
                displayName = "Provider A",
                baseUrl = "https://a.example.com/",
                modelId = "a-model",
                apiKeySecretId = "a-key"
            ),
            AiProviderConfig(
                id = "provider_b",
                type = AiProviderType.OPENAI,
                displayName = "Provider B",
                baseUrl = "https://b.example.com/",
                modelId = "b-model",
                apiKeySecretId = "b-key"
            ),
        )

        override suspend fun getSelectedRunnableProvider(): AiProviderConfig? {
            return providers.firstOrNull { it.id == selectedId }
        }

        override suspend fun getRunnableProviderById(id: String): AiProviderConfig? {
            val p = providers.firstOrNull { it.id == id }
            if (p != null && p.isEnabled && !p.apiKeySecretId.isNullOrBlank() && p.baseUrl.isNotBlank() && p.modelId.isNotBlank()) return p
            return null
        }
    }
}
