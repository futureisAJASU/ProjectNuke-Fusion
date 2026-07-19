package com.projectnuke.fusion.chat

import com.projectnuke.fusion.model.GenerationSettings

/**
 * Immutable snapshot of a generation request captured exactly once when the
 * request starts. All later stages must verify [requestId] still matches the
 * active session before mutating visible UI state.
 *
 * [generationModeKey] is the string form of the caller's ChatGenerationMode
 * enum (kept as String here to avoid leaking the ui-package enum across
 * module boundaries).
 */
data class GenerationRequestSnapshot(
    val requestId: String,
    val conversationId: Long,
    val generationModeKey: String,
    val selectedModelId: String?,
    val selectedModelPath: String?,
    val settings: GenerationSettings,
    val reasoningEnabled: Boolean,
    val webSearchPolicy: WebSearchPolicy,
    val attachmentIds: List<String>,
    val multimodalImagePaths: List<String>,
    val promptText: String,
    val createdAt: Long,
) {
    enum class WebSearchPolicy { DISABLED, ENABLED, AUTO }
}
