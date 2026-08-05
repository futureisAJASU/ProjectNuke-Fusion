package com.projectnuke.fusion.model

/**
 * Engine-creation-time settings for a LiteRT-LM Engine.
 *
 * Every field here decides how the native Engine is built (backend selection,
 * KV cache capacity, speculative-decoding flag, vision backend). Changing any
 * field rebuilds the Engine; changing none of them reuses the loaded Engine.
 * Per-turn sampling/limits live in [ConversationOptions] instead.
 */
data class RequestedEngineProfile(
    val modelPath: String,
    val accelerator: AcceleratorMode,
    val mtpRequested: Boolean,
    /**
     * KV cache capacity in tokens, mapped to EngineConfig.maxNumTokens.
     * This is the combined input/output token capacity of the KV cache, NOT a
     * response length limit; the response cap lives in
     * [ConversationOptions.maxOutputToken]. Changing this value rebuilds the
     * Engine (the KV cache is allocated at engine creation).
     */
    val kvCacheCapacityTokens: Int,
    val enableVisionBackend: Boolean,
)

/**
 * Per-turn generation options for a single Conversation.
 *
 * Changing any field never rebuilds the Engine; each request creates a new
 * Conversation with these options applied as the SamplerConfig.
 */
data class ConversationOptions(
    /**
     * Explicit app-level streaming output limit in tokens. The pinned
     * litertlm-android 0.14.0 API has no ConversationConfig.maxOutputToken,
     * so enforcement is implemented by the caller when this is non-null.
     */
    val maxOutputToken: Int? = null,
    val temperature: Float = 1.0f,
    val topK: Int = 64,
    val topP: Float = 0.95f,
    val seed: Int? = null,
)

/**
 * Backward-compatible migration: builds the engine-creation profile from a
 * [GenerationSettings] value. [modelPath] is the path as passed by the caller;
 * the engine resolves the runnable model before building its cache identity.
 */
fun GenerationSettings.toRequestedEngineProfile(
    modelPath: String,
    enableVisionBackend: Boolean
): RequestedEngineProfile = RequestedEngineProfile(
    modelPath = modelPath,
    accelerator = accelerator,
    mtpRequested = speculativeDecodingEnabled == true,
    kvCacheCapacityTokens = kvCacheCapacityTokens.coerceAtLeast(KvCacheCapacityPolicy.MIN_CAPACITY).coerceAtMost(KvCacheCapacityPolicy.MAX_CAPACITY),
    enableVisionBackend = enableVisionBackend,
)

/**
 * Backward-compatible migration: builds the per-turn options from a
 * [GenerationSettings] value.
 * Validates that the requested output budget fits within the KV cache capacity.
 */
fun GenerationSettings.toConversationOptions(): ConversationOptions {
    // Ensure output budget doesn't exceed what KV cache can hold
    // Heuristic: assume prompt ≈ output budget, so 2 * output <= capacity
    val validatedMaxOutput = KvCacheCapacityPolicy.validateOutputBudget(
        requestedOutputTokens = maxTokens.coerceAtLeast(1),
        kvCacheCapacityTokens = kvCacheCapacityTokens.coerceAtLeast(KvCacheCapacityPolicy.MIN_CAPACITY),
        estimatedPromptTokens = maxTokens.coerceAtLeast(1) // heuristic: prompt ≈ output
    )
    return ConversationOptions(
        maxOutputToken = validatedMaxOutput,
        temperature = temperature,
        topK = topK,
        topP = topP,
        seed = null,
    )
}
