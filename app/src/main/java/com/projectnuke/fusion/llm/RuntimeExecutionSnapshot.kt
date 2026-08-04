package com.projectnuke.fusion.llm

import com.projectnuke.fusion.model.AcceleratorMode

/**
 * Backend actually selected for text inference by the runtime candidate
 * ladder. [UNKNOWN] covers the case where the runtime reported no backend or
 * the value could not be observed.
 */
enum class RuntimeBackend {
    CPU,
    GPU,
    UNKNOWN
}

/**
 * Backend selected for a runtime component (e.g. the sampler). Distinct from
 * [RuntimeBackend] because [Backend.GPU] success does not imply every component
 * executed on GPU; [UNKNOWN] is reported unless a stable LiteRT-LM API confirms
 * the component's placement.
 */
enum class RuntimeComponentBackend {
    CPU,
    GPU,
    UNKNOWN
}

/**
 * Stable, machine-readable model fingerprint summary carried on every
 * successful generation so callers (metrics, persistence, A/B results) can
 * observe the exact model identity without reading mutable engine state.
 */
data class ModelFingerprintSummary(
    val canonicalPath: String,
    val fileSize: Long,
    val modifiedAt: Long,
    val validationVersion: Int,
    val mtpSupported: Boolean
)

/**
 * Stable reason codes for app-level runtime fallbacks. Never derived from raw
 * native exception text. Phase 3 populates the full set; Phase 2 introduces
 * the stable surface so callers persist codes rather than English strings.
 */
enum class FallbackReason {
    MTP_UNSUPPORTED,
    MTP_SKIPPED_RECENT_FAILURE,
    BACKEND_SKIPPED_RECENT_FAILURE,
    SPECULATIVE_ENABLE_FLAG_SETTLEMENT_FAILED,
    SPECULATIVE_DISABLE_FLAG_SETTLEMENT_FAILED,
    MTP_ENGINE_INIT_FAILED,
    GPU_TEXT_ENGINE_FAILED_CPU_SELECTED,
    GPU_VISION_BACKEND_FAILED_CPU_VISION_SELECTED,
    ALL_CANDIDATES_EXHAUSTED
}

/**
 * One app-level runtime fallback captured during candidate selection. Records,
 * when applicable, the attempted text backend, the attempted vision backend,
 * the attempted MTP state, the selected replacement backend, and a stable
 * reason code. Native exception text is never exposed to users via this type.
 */
data class RuntimeFallbackEvent(
    val attemptedTextBackend: RuntimeBackend? = null,
    val attemptedVisionBackend: RuntimeBackend? = null,
    val attemptedMtpEnabled: Boolean? = null,
    val selectedReplacementBackend: RuntimeBackend? = null,
    val reason: FallbackReason
)

/**
 * One immutable snapshot of the runtime execution that produced a successful
 * generation. The single source of truth for visible metrics, benchmark
 * persistence, A/B result persistence, copied diagnostics and developer logs.
 * Callers must NOT reread [LiteRtLlmEngine.lastMtpStatus] or
 * [LiteRtLlmEngine.lastRuntimeSelection] after generation to reconstruct it.
 */
data class RuntimeExecutionSnapshot(
    val requestedAccelerator: AcceleratorMode,
    val selectedTextBackend: RuntimeBackend,
    val selectedVisionBackend: RuntimeBackend?,
    val samplerBackend: RuntimeComponentBackend,
    val mtpRequested: Boolean,
    val mtpStatus: MtpRuntimeStatus,
    val fallbackEvents: List<RuntimeFallbackEvent>,
    val modelFingerprint: ModelFingerprintSummary
)
