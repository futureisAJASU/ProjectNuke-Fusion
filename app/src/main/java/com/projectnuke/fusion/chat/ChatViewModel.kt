package com.projectnuke.fusion.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Per-conversation generation state owned by the [ChatViewModel]. Persisted
 * only long enough to restore streams across conversation switches; not the
 * source of truth for the persisted message body.
 */
data class ConversationGenerationState(
    val isGenerating: Boolean = false,
    val activeRequestId: String? = null,
    val streamingText: String? = null,
    val streamingMetricsLine: String? = null,
    val generationStatus: String? = null,
    val regeneratingMessageId: Long? = null,
    val extractingMemoryCandidates: Boolean = false,
    val actualWebSearchUsed: Boolean = false,
)

/**
 * ViewModel-equivalent holder. Owns the [registry] of generation sessions and
 * a per-conversation state map so a conversation switch does not leak another
 * conversation's stream. Lifecycles are decoupled from any single composable.
 */
class ChatViewModel {
    private val supervisor = SupervisorJob()
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + supervisor)

    val registry = GenerationSessionRegistry()

    private val _states = MutableStateFlow<Map<Long, ConversationGenerationState>>(emptyMap())
    val states: StateFlow<Map<Long, ConversationGenerationState>> = _states.asStateFlow()

    /** Snapshot of [conversationId], or a default if none recorded yet. */
    fun state(conversationId: Long): ConversationGenerationState =
        _states.value[conversationId] ?: ConversationGenerationState()

    fun update(conversationId: Long, transform: (ConversationGenerationState) -> ConversationGenerationState) {
        _states.update { current ->
            val existing = current[conversationId] ?: ConversationGenerationState()
            current + (conversationId to transform(existing))
        }
    }

    fun clear(conversationId: Long) {
        _states.update { it - conversationId }
    }

    /** Cancels any active session for [conversationId]. */
    suspend fun cancelGeneration(conversationId: Long, reason: String = "user-stop") {
        registry.cancelAndJoin(conversationId, reason)
        update(conversationId) { it.copy(
            isGenerating = false,
            activeRequestId = null,
            streamingText = null,
            streamingMetricsLine = null,
            generationStatus = null,
            regeneratingMessageId = null,
            extractingMemoryCandidates = false,
        ) }
    }

    /**
     * Cancels any active session for [conversationId] and joins it. Use before
     * deleting a conversation to guarantee no late DB writes occur after the
     * conversation is removed.
     */
    suspend fun cancelAndAwait(conversationId: Long, reason: String = "delete"): Boolean =
        registry.cancelAndJoin(conversationId, reason).also { clear(conversationId) }

    fun dispose() {
        scope.cancel()
    }
}

@Composable
fun rememberChatViewModel(): ChatViewModel {
    return remember { ChatViewModel() }
}
