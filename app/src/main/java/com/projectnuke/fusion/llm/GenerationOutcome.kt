package com.projectnuke.fusion.llm

/**
 * Typed result for every LLM generation call. Failures must never be encoded
 * as assistant text. Only [Success] may be stored as a normal answer.
 */
sealed interface GenerationOutcome {
    /**
     * A successful generation. [text] contains the model's reply (sanitized
     * by the prompt adapter). [actualBackend] reports the backend that the
     * runtime actually used, when reported by the engine.
     */
    data class Success(
        val text: String,
        val actualBackend: String? = null
    ) : GenerationOutcome

    /**
     * Generation was cancelled before completing. Must not be stored as an
     * assistant answer. Partial [partialText] is exposed for optional UI
     * feedback only.
     */
    data class Cancelled(
        val partialText: String = ""
    ) : GenerationOutcome

    /**
     * Generation completed with no usable output. Must not be stored as a
     * fabricated assistant answer.
     */
    data object Empty : GenerationOutcome

    /**
     * Generation failed with a recoverable error. [kind] is a stable
     * machine-readable category; [message] is a user-safe localized
     * explanation never populated from raw server/native output.
     */
    data class Failure(
        val kind: FailureKind,
        val message: String
    ) : GenerationOutcome
}

/**
 * Stable failure categories shared across engines and callers. Avoid leaking
 * raw vendor strings through the public API.
 */
enum class FailureKind {
    MODEL_NOT_FOUND,
    MODEL_LOAD_FAILED,
    MODEL_MULTIMODAL_UNSUPPORTED,
    IMAGE_NOT_FOUND,
    GENERATION_IO,
    GENERATION_INTERRUPTED,
    UNKNOWN
}
