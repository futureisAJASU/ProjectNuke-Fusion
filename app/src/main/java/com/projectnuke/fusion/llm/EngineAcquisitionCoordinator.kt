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
 *
 * Generic over the engine handle type: production uses the native [Engine],
 * unit tests use a plain fake handle.
 */
internal class EngineAcquisitionCoordinator<T>(
    private val mtpFailureMemory: MtpFailureMemoryPort,
    private val mtpCapabilityProbe: (String) -> Boolean?,
    private val configureFlag: (Boolean) -> Boolean,
    private val engineFactory: (backendName: String, mtpEnabled: Boolean, visionBackendIsCpu: Boolean) -> EngineCandidateAttempt<T>,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val onPersistenceCompleted: () -> Unit = {},
    private val fingerprintResolver: (String) -> ModelFingerprint = { ModelFingerprint.of(it) }
) {

    /**
     * Attempts to acquire an engine for the given profile.
     *
     * Returns the selected engine on success, or an [EngineSelectionOutcome]
     * with the failure and fallback events. The caller is responsible for
     * updating its own loaded state based on the outcome.
     */
    fun acquire(profile: RequestedEngineProfile, loadedState: LoadedRuntimeState<T>?): EngineSelectionOutcome<T> {
        val mtpRequested = profile.mtpRequested
        val fingerprint = fingerprintResolver(profile.modelPath)
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
        // shouldSkipMtp/shouldSkipBackend may have expired entries and persisted;
        // notify the engine so durability transitions can be recorded
        onPersistenceCompleted()

        val ladder = availableCandidates

        // If all candidates were skipped by failure memory, try exact-match reuse
        // against the original planned candidates. Failure memory can only veto
        // *new* init attempts, not already-live successful engines.
        if (ladder.isEmpty()) {
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
                            visionBackend = if (profile.enableVisionBackend) loadedKey.selectedBackend else null,
                            fingerprint = fingerprint,
                            mtpCapabilityResult = null
                        ),
                        failure = null,
                        fallbackEvents = recordedFallbackEvents,
                        fingerprint = fingerprint,
                        mtpCapabilityResult = null
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
                fallbackEvents = recordedFallbackEvents,
                fingerprint = fingerprint,
                mtpCapabilityResult = null
            )
        }

        // Delegate to the selector with loaded-state reuse check at each candidate
        var mtpFactoryCalled = false
        val selection = selectFirstWorkingEngine(
            ladder = ladder,
            enableVisionBackend = profile.enableVisionBackend,
            configureFlag = configureFlag,
            tryCreate = { backend, mtp, visionCpu ->
                if (mtp) {
                    mtpFactoryCalled = true
                    val probeResult = mtpCapabilityProbe(profile.modelPath)
                    if (probeResult == false) {
                        EngineCandidateAttempt.CapabilityRejected(
                            backendName = backend,
                            mtpEnabled = true
                        )
                    } else {
                        engineFactory(backend, mtp, visionCpu)
                    }
                } else {
                    engineFactory(backend, mtp, visionCpu)
                }
            },
            loadedState = loadedState,
            fingerprint = fingerprint,
            accelerator = profile.accelerator,
            kvCacheCapacityTokens = profile.kvCacheCapacityTokens,
            enableVisionBackendProfile = profile.enableVisionBackend
        )
        val mtpCapabilityProbeCalled = selection.fallbackEvents.any {
            it.attemptedMtpEnabled == true && it.reason == FallbackReason.MTP_UNSUPPORTED
        } || selection.selection?.selectedMtpEnabled == true || (mtpFactoryCalled && selection.fallbackEvents.any {
            it.attemptedMtpEnabled == true && it.reason == FallbackReason.MTP_ENGINE_INIT_FAILED
        })
        val mtpCapabilityResult = when {
            !mtpRequested || !mtpSupported -> null
            selection.fallbackEvents.any { it.attemptedMtpEnabled == true && it.reason == FallbackReason.MTP_UNSUPPORTED } -> false
            mtpFactoryCalled && mtpCapabilityProbeCalled -> true
            else -> null
        }

        // Merge selector's fallback events with our recorded events
        val allEvents = recordedFallbackEvents + selection.fallbackEvents

        // Record failures into memory
        val mtpAttempted = mtpFactoryCalled || selection.selection?.selectedMtpEnabled == true
        val selectedMtpEnabled = selection.selection?.selectedMtpEnabled ?: false
        val mtpFlagAppliedForMtp = selection.selection?.mtpFlagAppliedForMtp ?: false

        // Record MTP failure only when MTP was actually attempted and produced a typed init failure
        val mtpInitFailedEvent = selection.fallbackEvents.filter {
            it.attemptedMtpEnabled == true && it.reason == FallbackReason.MTP_ENGINE_INIT_FAILED
        }.lastOrNull()
        if (mtpInitFailedEvent != null) {
            val mtpBackendName = mtpInitFailedEvent.attemptedTextBackend?.name ?: (plannedCandidates.firstOrNull { it.mtpEnabled && it.backend == "GPU" }?.backend ?: "GPU")
            mtpFailureMemory.recordFailure(
                modelPath = fingerprint.canonicalPath,
                backendName = mtpBackendName,
                mtpEnabled = true,
                validationVersion = fingerprint.validationVersion,
                kvCacheCapacityTokens = profile.kvCacheCapacityTokens,
                enableVisionBackend = profile.enableVisionBackend,
                fileSize = fingerprint.fileSize,
                modifiedAt = fingerprint.modifiedAt,
                mtpSupported = fingerprint.mtpSupported,
                fallbackReason = mtpInitFailedEvent.attemptedVisionBackend?.name?.let { "MTP initialization failed (vision=$it)" } ?: "MTP initialization failed"
            )
            onPersistenceCompleted()
        }

        // Record plain backend failures with accurate typed identity
        for (ev in selection.fallbackEvents) {
            if (ev.attemptedMtpEnabled == false &&
                ev.attemptedTextBackend != null &&
                ev.reason == FallbackReason.BACKEND_ENGINE_INIT_FAILED
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
                    fallbackReason = ev.attemptedVisionBackend?.name?.let { "Backend initialization failed (vision=$it)" } ?: "Backend initialization failed"
                )
                onPersistenceCompleted()
            }
        }

        // Add terminal all-candidates-exhausted event when candidates were attempted
        var finalEvents = allEvents
        val candidatesAttempted = selection.fallbackEvents.any {
            it.reason == FallbackReason.BACKEND_ENGINE_INIT_FAILED || it.reason == FallbackReason.MTP_ENGINE_INIT_FAILED
        }
        if (selection.selection == null && selection.failure != null && candidatesAttempted) {
            finalEvents = allEvents + RuntimeFallbackEvent(
                reason = FallbackReason.ALL_CANDIDATES_EXHAUSTED
            )
        }

        // Build selection results with the resolved fingerprint and capability result
        val selectionResultWithData = selection.selection?.let {
            EngineSelectionResult(
                engine = it.engine,
                selectedMtpEnabled = it.selectedMtpEnabled,
                mtpFlagAppliedForMtp = it.mtpFlagAppliedForMtp,
                backendName = it.backendName,
                visionBackend = it.visionBackend,
                fingerprint = fingerprint,
                mtpCapabilityResult = mtpCapabilityResult
            )
        }

        return EngineSelectionOutcome(
            selection = selectionResultWithData,
            failure = selection.failure,
            fallbackEvents = finalEvents,
            fingerprint = fingerprint,
            mtpCapabilityResult = mtpCapabilityResult
        )
    }
}