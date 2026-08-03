package com.projectnuke.fusion.llm

import com.google.ai.edge.litertlm.Engine
import com.projectnuke.fusion.model.AcceleratorMode
import com.projectnuke.fusion.modelzoo.LiteRtLmPackageValidator
import java.io.File

/**
 * Canonical identity of a model file at load time: the resolved path plus the
 * file attributes and package capabilities that determine whether a previously
 * loaded engine can be reused. Any change (file replaced, drafter capability
 * changed, validator version bumped) invalidates the loaded runtime.
 */
internal data class ModelFingerprint(
    val canonicalPath: String,
    val fileSize: Long,
    val modifiedAt: Long,
    val validationVersion: Int,
    val mtpSupported: Boolean
) {
    companion object {
        fun of(modelPath: String): ModelFingerprint {
            val file = File(modelPath)
            val canonicalPath = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
            val validation = LiteRtLmPackageValidator.validate(file).getOrNull()
            return ModelFingerprint(
                canonicalPath = canonicalPath,
                fileSize = runCatching { file.length() }.getOrDefault(0L),
                modifiedAt = runCatching { file.lastModified() }.getOrDefault(0L),
                validationVersion = validation?.validationVersion ?: 0,
                mtpSupported = validation?.hasDrafter == true
            )
        }
    }
}

/**
 * Typed engine identity: everything that requires an Engine (re)build. This is
 * the successor of the old concatenated string cache key, so a change in any
 * field means the loaded engine no longer matches the requested profile.
 */
internal data class EngineRuntimeKey(
    val fingerprint: ModelFingerprint,
    val accelerator: AcceleratorMode,
    val kvCacheCapacityTokens: Int,
    val enableVisionBackend: Boolean,
    val mtpEnabled: Boolean
)

/**
 * The one loaded native runtime plus the typed identity it was built for.
 * Replaces the scattered engine/backend/status fields so reuse decisions and
 * status reporting read from a single consistent snapshot.
 */
internal class LoadedRuntimeState(
    val engine: Engine,
    val key: EngineRuntimeKey,
    val mtpStatus: MtpRuntimeStatus,
    val runtimeSelection: EngineSelectionRuntime,
    val actualTextBackend: String,
    val actualVisionBackend: String?,
    val fallbackEvents: List<RuntimeFallbackEvent> = emptyList()
)
