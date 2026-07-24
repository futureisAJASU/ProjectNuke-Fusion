package com.projectnuke.fusion.ai

import com.projectnuke.fusion.ai.model.AiChatRequest
import com.projectnuke.fusion.ai.model.AiMessage
import com.projectnuke.fusion.ai.model.AiProviderConfig
import com.projectnuke.fusion.ai.network.AiProviderClientException
import com.projectnuke.fusion.ai.network.ChatClient
import com.projectnuke.fusion.model.ChatMessage

internal interface ExternalAiProviderSource {
    suspend fun getSelectedRunnableProvider(): AiProviderConfig?
    suspend fun getRunnableProviderById(id: String): AiProviderConfig?
}

internal sealed interface ExternalAiChatResult {
    data class Success(val content: String, val providerDisplayName: String?) : ExternalAiChatResult
    data class BlockedAttachment(val message: String) : ExternalAiChatResult
    data class NoProvider(val message: String) : ExternalAiChatResult
    data class Empty(val providerDisplayName: String?) : ExternalAiChatResult
    data class Error(val message: String) : ExternalAiChatResult
}

internal class ExternalAiChatRunner(
    private val providerRepository: ExternalAiProviderSource,
    private val client: ChatClient
) {
    suspend fun generate(
        history: List<ChatMessage>,
        userInput: String,
        hasAttachments: Boolean,
        stripAttachments: (String) -> String
    ): ExternalAiChatResult {
        val messages = buildExternalAiMessages(
            history = history,
            userInput = userInput,
            stripAttachments = stripAttachments
        )

        return generateFromMessages(
            messages = messages,
            hasAttachments = hasAttachments
        )
    }

    suspend fun generateFromMessages(
        messages: List<AiMessage>,
        hasAttachments: Boolean = false,
        providerId: String? = null
    ): ExternalAiChatResult {
        if (hasAttachments) {
            return ExternalAiChatResult.BlockedAttachment(
                "현재 외부 AI API 모드에서는 첨부 파일을 전송할 수 없습니다."
            )
        }

        val provider = if (providerId != null) {
            providerRepository.getRunnableProviderById(providerId)
        } else {
            providerRepository.getSelectedRunnableProvider()
        } ?: return ExternalAiChatResult.NoProvider(
            "사용 가능한 외부 AI API 제공자가 없습니다. AI API 설정에서 필수 항목을 확인해 주세요."
        )

        return try {
            val response = client.chatCompletion(
                config = provider,
                request = AiChatRequest(
                    messages = messages,
                    temperature = provider.temperature,
                    maxTokens = provider.maxTokens
                )
            )
            if (response.content.isBlank()) {
                ExternalAiChatResult.Empty(providerDisplayName = provider.displayName)
            } else {
                ExternalAiChatResult.Success(
                    content = response.content,
                    providerDisplayName = provider.displayName
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: AiProviderClientException) {
            ExternalAiChatResult.Error(e.message ?: "외부 AI API 요청에 실패했습니다.")
        }
    }
}
