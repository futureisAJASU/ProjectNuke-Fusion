package com.projectnuke.fusion.ai

import com.projectnuke.fusion.ai.data.AiProviderRepository
import com.projectnuke.fusion.ai.model.AiChatRequest
import com.projectnuke.fusion.ai.network.OpenAiCompatibleClient
import com.projectnuke.fusion.model.ChatMessage

internal sealed interface ExternalAiChatResult {
    data class Success(val content: String, val providerDisplayName: String?) : ExternalAiChatResult
    data class BlockedAttachment(val message: String) : ExternalAiChatResult
    data class NoProvider(val message: String) : ExternalAiChatResult
}

internal class ExternalAiChatRunner(
    private val providerRepository: AiProviderRepository,
    private val client: OpenAiCompatibleClient
) {
    suspend fun generate(
        history: List<ChatMessage>,
        userInput: String,
        hasAttachments: Boolean,
        stripAttachments: (String) -> String
    ): ExternalAiChatResult {
        if (hasAttachments) {
            return ExternalAiChatResult.BlockedAttachment(
                "현재 외부 AI API 모드에서는 첨부 파일을 전송할 수 없습니다."
            )
        }

        val provider = providerRepository.getSelectedProvider()
        if (provider == null || !provider.isEnabled) {
            return ExternalAiChatResult.NoProvider(
                "사용 가능한 외부 AI API 제공자가 없습니다. AI API 설정을 확인해 주세요."
            )
        }

        val messages = buildExternalAiMessages(
            history = history,
            userInput = userInput,
            stripAttachments = stripAttachments
        )

        val response = client.chatCompletion(
            config = provider,
            request = AiChatRequest(
                messages = messages,
                temperature = provider.temperature,
                maxTokens = provider.maxTokens
            )
        )

        return ExternalAiChatResult.Success(
            content = response.content.ifBlank { "외부 AI API에서 빈 응답을 받았습니다." },
            providerDisplayName = provider.displayName
        )
    }
}
