package com.projectnuke.fusion.modelzoo

import com.projectnuke.fusion.model.ChatMessage

interface FusionPromptAdapter {
    val family: ModelFamily
    fun buildMessages(messages: List<ChatMessage>): List<ChatMessage>
    fun supportsFusionReasoningTags(): Boolean
    fun sanitizeOutput(raw: String): String
}

data class PreparedMessages(
    val messages: List<ChatMessage>,
    val modelFamily: ModelFamily
)

object GemmaPromptAdapter : FusionPromptAdapter {
    override val family = ModelFamily.GEMMA
    override fun buildMessages(messages: List<ChatMessage>): List<ChatMessage> = messages
    override fun supportsFusionReasoningTags(): Boolean = true
    override fun sanitizeOutput(raw: String): String = raw.trim()
}

object QwenPromptAdapter : FusionPromptAdapter {
    override val family = ModelFamily.QWEN
    override fun buildMessages(messages: List<ChatMessage>): List<ChatMessage> = shortenSystemMessages(messages)
    override fun supportsFusionReasoningTags(): Boolean = false
    override fun sanitizeOutput(raw: String): String = raw
        .replace(Regex("(?is)<think>.*?</think>"), "")
        .trim()
}

object DeepSeekPromptAdapter : FusionPromptAdapter {
    override val family = ModelFamily.DEEPSEEK
    override fun buildMessages(messages: List<ChatMessage>): List<ChatMessage> = shortenSystemMessages(messages).map { message ->
        if (message.role == "system") {
            message.copy(content = "${message.content}\n추론 출력이 길어질 수 있으므로 최종 답변은 명확하게 구분해 주세요.")
        } else {
            message
        }
    }
    override fun supportsFusionReasoningTags(): Boolean = false
    override fun sanitizeOutput(raw: String): String = raw
        .replace(Regex("(?is)<think>.*?</think>"), "")
        .replace(Regex("(?is)<thinking>.*?</thinking>"), "")
        .trim()
}

object LlamaPromptAdapter : FusionPromptAdapter {
    override val family = ModelFamily.LLAMA
    override fun buildMessages(messages: List<ChatMessage>): List<ChatMessage> = messages.map { message ->
        if (message.role == "system") message.copy(content = message.content.trim()) else message
    }
    override fun supportsFusionReasoningTags(): Boolean = false
    override fun sanitizeOutput(raw: String): String = raw.trim()
}

object MistralPromptAdapter : FusionPromptAdapter {
    override val family = ModelFamily.MISTRAL
    override fun buildMessages(messages: List<ChatMessage>): List<ChatMessage> = messages.map { message ->
        if (message.role == "system") message.copy(content = message.content.trim()) else message
    }
    override fun supportsFusionReasoningTags(): Boolean = false
    override fun sanitizeOutput(raw: String): String = raw.trim()
}

object PhiPromptAdapter : FusionPromptAdapter {
    override val family = ModelFamily.PHI
    override fun buildMessages(messages: List<ChatMessage>): List<ChatMessage> = shortenSystemMessages(messages)
    override fun supportsFusionReasoningTags(): Boolean = false
    override fun sanitizeOutput(raw: String): String = raw.trim()
}

object CustomPromptAdapter : FusionPromptAdapter {
    override val family = ModelFamily.CUSTOM
    override fun buildMessages(messages: List<ChatMessage>): List<ChatMessage> = GemmaPromptAdapter.buildMessages(messages)
    override fun supportsFusionReasoningTags(): Boolean = true
    override fun sanitizeOutput(raw: String): String = raw.trim()
}

object KimiPromptAdapter : FusionPromptAdapter {
    override val family = ModelFamily.KIMI
    override fun buildMessages(messages: List<ChatMessage>): List<ChatMessage> = messages
    override fun supportsFusionReasoningTags(): Boolean = false
    override fun sanitizeOutput(raw: String): String = raw.trim()
}

object FusionPromptAdapters {
    fun forFamily(family: ModelFamily): FusionPromptAdapter = when (family) {
        ModelFamily.GEMMA -> GemmaPromptAdapter
        ModelFamily.CUSTOM -> CustomPromptAdapter
        ModelFamily.QWEN -> QwenPromptAdapter
        ModelFamily.DEEPSEEK -> DeepSeekPromptAdapter
        ModelFamily.LLAMA -> LlamaPromptAdapter
        ModelFamily.MISTRAL -> MistralPromptAdapter
        ModelFamily.PHI -> PhiPromptAdapter
        ModelFamily.KIMI -> KimiPromptAdapter
    }

    /**
     * Prepares messages for the model by extracting internal metadata and stripping
     * all Fusion-internal marker messages. This ensures runtime configuration never
     * alters the semantic model prompt.
     */
    fun prepareMessagesForModel(messages: List<ChatMessage>): PreparedMessages {
        // Extract model family from FUSION_MODEL_FAMILY marker for adapter selection
        val familyMarker = messages.firstOrNull { it.role == "system" && it.content.startsWith("FUSION_MODEL_FAMILY=") }
            ?.content
            ?.substringAfter("=")
            ?.trim()
        val family = runCatching { ModelFamily.valueOf(familyMarker.orEmpty()) }.getOrDefault(ModelFamily.GEMMA)

        // Strip all Fusion-internal marker messages
        val cleanedMessages = messages.filter { msg ->
            !(msg.role == "system" && (
                msg.content.startsWith("FUSION_GENERATION_SETTINGS") ||
                msg.content.startsWith("FUSION_SELECTED_MODEL_PATH=") ||
                msg.content.startsWith("FUSION_MODEL_FAMILY=")
            ))
        }

        return PreparedMessages(cleanedMessages, family)
    }

    @Deprecated("Use prepareMessagesForModel instead. This reads markers that should be stripped before model prompt construction.", ReplaceWith("forFamily(prepareMessagesForModel(messages).modelFamily)"))
    fun inferFromMessages(messages: List<ChatMessage>): FusionPromptAdapter {
        val marker = messages.firstOrNull { it.role == "system" && it.content.startsWith("FUSION_MODEL_FAMILY=") }
            ?.content
            ?.substringAfter("=")
            ?.trim()
        val family = runCatching { ModelFamily.valueOf(marker.orEmpty()) }.getOrDefault(ModelFamily.GEMMA)
        return forFamily(family)
    }
}

private fun shortenSystemMessages(messages: List<ChatMessage>): List<ChatMessage> {
    return messages.map { message ->
        if (message.role != "system") return@map message
        val short = message.content
            .lines()
            .filterNot { it.contains("<fusion_thinking>") || it.contains("<fusion_answer>") }
            .joinToString("\n")
            .trim()
        message.copy(content = short)
    }
}
