package com.projectnuke.fusion.llm

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.projectnuke.fusion.model.AcceleratorMode
import com.projectnuke.fusion.model.RequestedEngineProfile

/**
 * Production acquisition coordinator extracted from [LiteRtLlmEngine.getOrCreateEngine].
 *
 * Encapsulates the full candidate selection and fallback logic so it can be
 * tested with injected dependencies without depending on the native Engine.
 * The coordinator is stateless (except for the failure memory which is
 * passed in) and deterministic given the same inputs.
 */
internal class EngineAcquisitionCoordinator(
    private val mtpFailureMemory: MtpFailureMemory,
    private val mtpCapabilityProbe: (String) -> Boolean?,
    private val configureFlag: (Boolean) -> Boolean,
    private val engineFactory: (backendName: String, mtpEnabled: Boolean, visionBackendIsCpu: Boolean) -> EngineCandidateAttempt<Engine>,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * Attempts to acquire an engine for the given profile.
     *
     * Returns the selected engine on success, or an [EngineSelectionOutcome]
     * with the failure and fallback events. The caller is responsible for
     * updating its own loaded state based on the outcome.
     */
    fun acquire(profile: RequestedEngineProfile, loadedState: LoadedRuntimeState?): EngineSelectionOutcome<Engine> {
        val mtpRequested = profile.mtpRequested
        val fingerprint = ModelFingerprint.of(profile.modelPath)
        val mtpSupported = fingerprint.mtpSupported
        val maxNumTokens = profile.kvCacheCapacityTokens.coerceAtLeast(1)

        var mtpSkippedByMemory = false
        val recordedFallbackEvents = mutableListOf<RuntimeFallbackEvent>()
        val plannedCandidates = buildEngineCandidateLadder(
            accelerator = profile.accelerator,
            mtpRequested = mtpRequested,
            mtpSupported = mtpSupported
        )

        // Filter candidates through failure memory
        val availableCandidates = plannedCandidates.filter { candidate ->
            if (candidate.mtpEnabled) {
                val skipReason = mtpFailureMemory.shouldSkipMtp(
                    modelPath = fingerprint.canonicalPath,
                    backendName = candidate.backend,
                    mtpEnabled = true,
                    validationVersion = fingerprint.validationVersion,
                    kvCacheCapacityTokens = profile.kvCacheCapacityTokens,
                    enableVisionBackend = profile.enableVisionBackend,
                    fileSize = fingerprint.fileSize,
                    modifiedAt = fingerprint.modifiedAt,
                    mtpSupported = fingerprint.mtpSupported
                )
                if (skipReason != null) {
                    mtpSkippedByMemory = true
                    recordedFallbackEvents += RuntimeFallbackEvent(
                        attemptedTextBackend = candidate.backend.toRuntimeBackend(),
                        attemptedMtpEnabled = true,
                        reason = FallbackReason.MTP_SKIPPED_RECENT_FAILURE
                    )
                    return@filter false
                }
            } else {
                val skipReason = mtpFailureMemory.shouldSkipBackend(
                    modelPath = fingerprint.canonicalPath,
                    backendName = candidate.backend,
                    validationVersion = fingerprint.validationVersion,
                    kvCacheCapacityTokens = profile.kvCacheCapacityTokens,
                    enableVisionBackend = profile.enableVisionBackend,
                    fileSize = fingerprint.fileSize,
                    modifiedAt = fingerprint.modifiedAt,
                    mtpSupported = fingerprint.mtpSupported
                )
                if (skipReason != null) {
                    recordedFallbackEvents += RuntimeFallbackEvent(
                        attemptedTextBackend = candidate.backend.toRuntimeBackend(),
                        attemptedMtpEnabled = false,
                        reason = FallbackReason.BACKEND_SKIPPED_RECENT_FAILURE
                    )
                    return@filter false
                }
            }
            true
        }

        // All candidates skipped by failure memory — try exact-match reuse
        val ladder = availableCandidates
        val preferredBackendName = ladder.firstOrNull()?.backend ?: run {
            // All candidates were skipped by failure memory. The only escape is
            // an exact-matchEMPTY reuse: a currently live, successfully
            // initialized Engine whose stored EngineRuntimeKey corresponds to
            // a candidate still in the *original* planned candidates. A different
            // model / profile / backend / MTP / vision / KV Engine must never be
            // reused, and the loaded engine must never be defeated by the same
            // failure-memory skip that triggered this branch (because failure
            // memory can only veto *new* init attempts, not already-live success).
            val currentLoadedState = loadedState
            if (currentLoadedState != null) {
                val loadedKey = currentLoadedState.key
                val isPlannedCandidate = plannedCandidates.any { candidate ->
                    candidate.backend == loadedKey.selectedBackend &&
                        candidate.mtpEnabled == loadedKey.mtpEnabled &&
                        loadedKey.fingerprint == fingerprint &&
                        loadedKey.accelerator == profile.accelerator &&
                        loadedKey.kvCacheCapacityTokens == profile.kvCacheCapacityTokens &&
                        loadedKey.enableVisionBackend == profile.enableVisionBackend
                }
                if (isPlannedCandidate) {
                    Log.i("FusionLiteRT", "All candidates skipped; reusing exactly matching live engine for plan")
                    return EngineSelectionOutcome(
                        selection = EngineSelectionResult(
                            engine = currentLoadedState.engine,
                            selectedMtpEnabled = currentLoadedState.key.mtpEnabled,
                            mtpFlagAppliedForMtp = currentLoadedState.mtpStatus == MtpRuntimeStatus.INITIALIZED_WITH_MTP_REQUEST,
                            backendName = loadedKey.selectedBackend,
                            visionBackend = if (profile.enableVisionBackend) loadedKey.selectedBackend else null
                        ),
                        failure = null,
                        fallbackEvents = recordedFallbackEvents
                    )
                }
            }
            recordedFallbackEvents += RuntimeFallbackEvent(
                reason = FallbackReason.ALL_CANDIDATES_SKIPPED_RECENT_FAILURE
            )
            return EngineSelectionOutcome(
                selection = null,
                failure = EngineSelectionFailedException(
                    RuntimeAttemptSnapshot(
                        requestedAccelerator = profile.accelerator,
                        fallbackEvents = recordedFallbackEvents,
                        modelFingerprint = ModelFingerprintSummary(
                            canonicalPath = fingerprint.canonicalPath,
                            fileSize = fingerprint.fileSize,
                            modifiedAt = fingerprint.modifiedAt,
                            validationVersion = fingerprint.validationVersion,
                            mtpSupported = fingerprint.mtpSupported
                        ),
                        mtpRequested = mtpRequested
                    ),
                    IllegalStateException("All engine candidates skipped due to recent failures")
                ),
                fallbackEvents = recordedFallbackEvents
            )
        }

        // Cached engine exact-match reuse
        val requestedKey = EngineRuntimeKey(
            fingerprint = fingerprint,
            accelerator = profile.accelerator,
            kvCacheCapacityTokens = profile.kvCacheCapacityTokens,
            enableVisionBackend = profile.enableVisionBackend,
            mtpEnabled = ladder.firstOrNull()?.mtpEnabled ?: false,
            selectedBackend = preferredBackendName
        )

        val currentState = loadedState
        if (currentState != null && currentState.key == requestedKey) {
            return EngineSelectionOutcome(
                selection = EngineSelectionResult(
                    engine = currentState.engine,
                    selectedMtpEnabled = currentState.key.mtpEnabled,
                    mtpFlagAppliedForMtp = currentState.mtpStatus == MtpRuntimeStatus.INITIALIZED_WITH_MTP_REQUEST,
                    backendName = currentState.key.selectedBackend,
                    visionBackend = if (profile.enableVisionBackend) currentState.key.selectedBackend else null
                ),
                failure = null,
                fallbackEvents = recordedFallbackEvents
            )
        }

        // Delegate to the selector
        val selection = selectFirstWorkingEngine(
            ladder = ladder,
            enableVisionBackend = profile.enableVisionBackend,
            configureFlag = configureFlag,
            tryCreate = { backend, mtp, visionCpu ->
                engineFactory(backend, mtp, visionCpu)
            }
        )

        // Merge selector's fallback events with our recorded events
        val allEvents = recordedFallbackEvents + selection.fallbackEvents

        // Record failures into memory
        val mtpAttempted = mtpRequested && mtpSupported
        val selectedMtpEnabled = selection.selection?.selectedMtpEnabled ?: false
        val mtpFlagAppliedForMtp = selection.selection?.mtpFlagAppliedForMtp ?: false

        // Record MTP failure if requested but not enabled
        if (mtpRequested && mtpSupported && !selectedMtpEnabled && mtpAttempted) {
            // Find the MTP backend that was attempted
            val mtpBackend = plannedCandidates.firstOrNull { it.mtpEnabled && it.backend == "GPU" }?.backend ?: "GPU"
            mtpFailureMemory.recordFailure(
                modelPath = fingerprint.canonicalPath,
                backendName = mtpBackend,
                mtpEnabled = true,
                validationVersion = fingerprint.validationVersion,
                kvCacheCapacityTokens = profile.kvCacheCapacityTokens,
                enableVisionBackend = profile.enableVisionBackend,
                fileSize = fingerprint.fileSize,
                modifiedAt = fingerprint.modifiedAt,
                mtpSupported = fingerprint.mtpSupported,
                fallbackReason = "MTP initialization failed"
            )
        }

        // Record plain backend failures
        for (ev in selection.fallbackEvents) {
            if (ev.reason == FallbackReason.BACKEND_ENGINE_INIT_FAILED &&
                ev.attemptedTextBackend != null &&
                !ev.attemptedMtpEnabled!!
            ) {
                mtpFailureMemory.recordFailure(
                    modelPath = fingerprint.canonicalPath,
                    backendName = ev.attemptedTextBackend!!.name,
                    mtpEnabled = false,
                    validationVersion = fingerprint.validationVersion,
                    kvCacheCapacityTokens = profile.kvCacheCapacityTokens,
                    enableVisionBackend = profile.enableVisionBackend,
                    fileSize = fingerprint.fileSize,
                    modifiedAt = fingerprint.modifiedAt,
                    mtpSupported = fingerprint.mtpSupported,
                    fallbackReason = "Backend initialization failed"
                )
            }
        }

        return EngineSelectionOutcome(
            selection = selection.selection,
            failure = selection.failure,
            fallbackEvents = allEvents
        )
    }
}