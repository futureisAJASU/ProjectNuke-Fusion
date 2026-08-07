package com.projectnuke.fusion.llm

import com.projectnuke.fusion.model.AcceleratorMode
import com.projectnuke.fusion.model.RequestedEngineProfile
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 4: real production acquisition coordinator tests.
 *
 * These tests exercise the `selectFirstWorkingEngine` coordinator directly with
 * synthetic ladders and injected `tryCreate` lambdas. Each tests covers one
 * of the 13 enumerated fallback cases without faking any production logic.
 * The coordinator under test is the exact same code path that
 * `LiteRtLlmEngine.getOrCreateEngine` uses at runtime.
 */
class RepairPhase4AcquisitionCoordinatorTest {

    private val gpuMtp  = EngineCandidate("GPU", mtpEnabled = true)
    private val gpuPlain = EngineCandidate("GPU", mtpEnabled = false)
    private val cpuPlain = EngineCandidate("CPU", mtpEnabled = false)

    private fun verifyEvent(
        events: List<RuntimeFallbackEvent>,
        index: Int,
        reason: FallbackReason,
        textBackend: RuntimeBackend? = null,
        mtp: Boolean? = null
    ) {
        assertTrue("expected at least ${index + 1} fallback events, got ${events.size}",
            events.size > index)
        val ev = events[index]
        assertEquals(reason, ev.reason)
        if (textBackend != null) assertEquals(textBackend, ev.attemptedTextBackend)
        if (mtp != null) assertEquals(mtp, ev.attemptedMtpEnabled)
    }

    // ── Case 1: MTP_UNSUPPORTED ──────────────────────────────────────────

    @Test
    fun `case1 CapabilityRejected drops MTP candidate and records MTP_UNSUPPORTED`() {
        val outcome = selectFirstWorkingEngine<DummyEngine>(
            ladder = listOf(EngineCandidate("GPU", true), EngineCandidate("GPU", false)),
            enableVisionBackend = false,
            configureFlag = { true },
            tryCreate = { backend, mtp, _ ->
                if (mtp) EngineCandidateAttempt.CapabilityRejected(backend, mtp)
                else EngineCandidateAttempt.Success(DummyEngine())
            }
        )
        assertNotNull(outcome.selection)
        assertEquals("GPU", outcome.selection!!.backendName)
        assertEquals(false, outcome.selection!!.selectedMtpEnabled)
        assertEquals(1, outcome.fallbackEvents.size)
        assertEquals(FallbackReason.MTP_UNSUPPORTED, outcome.fallbackEvents[0].reason)
        assertEquals(RuntimeBackend.GPU, outcome.fallbackEvents[0].attemptedTextBackend)
        assertTrue(outcome.fallbackEvents[0].attemptedMtpEnabled!!)
    }

    // ── Case 5: SPECULATIVE_ENABLE_FLAG_SETTLEMENT_FAILED ────────────────

    @Test
    fun `case5 speculative enable flag failure records event and falls through to plain`() {
        val outcome = selectFirstWorkingEngine<DummyEngine>(
            ladder = listOf(EngineCandidate("GPU", true), EngineCandidate("GPU", false)),
            enableVisionBackend = false,
            configureFlag = { mtp -> if (mtp) false else true },
            tryCreate = { _, _, _ -> EngineCandidateAttempt.Success(DummyEngine()) }
        )
        assertNotNull(outcome.selection)
        assertEquals("GPU", outcome.selection!!.backendName)
        assertEquals(false, outcome.selection!!.selectedMtpEnabled)
        assertTrue(outcome.fallbackEvents.any { it.reason == FallbackReason.SPECULATIVE_ENABLE_FLAG_SETTLEMENT_FAILED })
    }

    @Test
    fun `case6 disable flag failure records and candidate force skipped`() {
        val outcome = selectFirstWorkingEngine<DummyEngine>(
            ladder = listOf(EngineCandidate("GPU", false), EngineCandidate("CPU", false)),
            enableVisionBackend = false,
            configureFlag = { mtp -> if (mtp) true else false },
            tryCreate = { _, _, _ -> EngineCandidateAttempt.Success(DummyEngine()) }
        )
        assertNotNull(outcome.selection)
        assertEquals("CPU", outcome.selection!!.backendName)
        assertTrue(outcome.fallbackEvents.any { it.reason == FallbackReason.SPECULATIVE_DISABLE_FLAG_SETTLEMENT_FAILED })
    }

    // ── Case 7: ALL_CANDIDATES_EXHAUSTED ────────────────────────────────

    @Test
    fun `case 7 all candidates init-fail → selection null, 3 fallback events`() {
        val outcome = selectFirstWorkingEngine<DummyEngine>(
            ladder = listOf(EngineCandidate("GPU", true), EngineCandidate("GPU", false), EngineCandidate("CPU", false)),
            enableVisionBackend = false,
            configureFlag = { true },
            tryCreate = { _, _, _ -> EngineCandidateAttempt.InitializationFailed(RuntimeException("stop")) }
        )
        assertNull(outcome.selection)
        assertNotNull(outcome.failure)
        assertEquals(3, outcome.fallbackEvents.size)
        assertEquals(FallbackReason.MTP_ENGINE_INIT_FAILED, outcome.fallbackEvents[0].reason)
        assertEquals(FallbackReason.BACKEND_ENGINE_INIT_FAILED, outcome.fallbackEvents[1].reason)
        assertEquals(FallbackReason.BACKEND_ENGINE_INIT_FAILED, outcome.fallbackEvents[2].reason)
    }

    // ── Case 11: Empty ladder ─────────────────────────────────────────────

    @Test
    fun `all candidates skipped via empty ladder yields nil selection`() {
        val outcome = selectFirstWorkingEngine<DummyEngine>(
            ladder = emptyList(),
            enableVisionBackend = false,
            configureFlag = { true },
            tryCreate = { _, _, _ -> error("not called") }
        )
        assertNull(outcome.selection)
        assertEquals(0, outcome.fallbackEvents.size)
        assertNotNull(outcome.failure)
    }

    // ── Case 4: MTP_ENGINE_INIT_FAILED ─────────────────────────────────────

    @Test
    fun `MTP init failed then plain success records MTP engine init failed`() {
        val outcome = selectFirstWorkingEngine<DummyEngine>(
            ladder = listOf(EngineCandidate("GPU", true), EngineCandidate("GPU", false)),
            enableVisionBackend = false,
            configureFlag = { true },
            tryCreate = { _, mtp, _ ->
                if (mtp) EngineCandidateAttempt.InitializationFailed(RuntimeException("MTP down"))
                else EngineCandidateAttempt.Success(DummyEngine())
            }
        )
        assertNotNull(outcome.selection)
        assertEquals(false, outcome.selection!!.selectedMtpEnabled)
        assertTrue(outcome.fallbackEvents.any { it.reason == FallbackReason.MTP_ENGINE_INIT_FAILED })
    }

    // ── Case 8: GPU_TEXT_ENGINE_FAILED_CPU_SELECTED ──────────────────────

    @Test
    fun `GPU plain failure then CPU success records GPU_TEXT_ENGINE_FAILED_CPU_SELECTED`() {
        val outcome = selectFirstWorkingEngine<DummyEngine>(
            ladder = listOf(EngineCandidate("GPU", false), EngineCandidate("CPU", false)),
            enableVisionBackend = false,
            configureFlag = { true },
            tryCreate = { backend, _, _ ->
                if (backend == "GPU") EngineCandidateAttempt.InitializationFailed(RuntimeException("GPU fail"))
                else EngineCandidateAttempt.Success(DummyEngine())
            }
        )
        assertNotNull(outcome.selection)
        assertEquals("CPU", outcome.selection!!.backendName)
        assertEquals(2, outcome.fallbackEvents.size)
        assertEquals(FallbackReason.BACKEND_ENGINE_INIT_FAILED, outcome.fallbackEvents[0].reason)
        assertEquals(FallbackReason.GPU_TEXT_ENGINE_FAILED_CPU_SELECTED, outcome.fallbackEvents[1].reason)
        assertEquals(RuntimeBackend.CPU, outcome.fallbackEvents[1].selectedReplacementBackend)
    }

    // ── Case 9: GPU_VISION_BACKEND_FAILED_CPU_VISION_SELECTED ────────────

    @Test
    fun `GPU vision fails then CPU vision succeeds on a GPU-plain candidate`() {
        val outcome = selectFirstWorkingEngine<DummyEngine>(
            ladder = listOf(EngineCandidate("GPU", false)),
            enableVisionBackend = true,
            configureFlag = { true },
            tryCreate = { _, _, visionCpu ->
                if (!visionCpu) EngineCandidateAttempt.InitializationFailed(RuntimeException("GPU vision crash"))
                else EngineCandidateAttempt.Success(DummyEngine())
            }
        )
        assertNotNull(outcome.selection)
        assertEquals("GPU", outcome.selection!!.backendName)
        assertEquals("CPU", outcome.selection!!.visionBackend)
        assertEquals(false, outcome.selection!!.selectedMtpEnabled)
        assertTrue(outcome.fallbackEvents.any { it.reason == FallbackReason.GPU_VISION_BACKEND_FAILED_CPU_VISION_SELECTED })
        val ev = outcome.fallbackEvents.first { it.reason == FallbackReason.GPU_VISION_BACKEND_FAILED_CPU_VISION_SELECTED }
        assertEquals(RuntimeBackend.GPU, ev.attemptedTextBackend)
        assertEquals(RuntimeBackend.GPU, ev.attemptedVisionBackend)
        assertEquals(RuntimeBackend.CPU, ev.selectedReplacementBackend)
    }

    // ── Multi-fail chain: MTP → GPU plain → CPU all fail ─────────────────

    @Test
    fun `GPU MTP fails then GPU plain fails then CPU fails → no bridge events`() {
        val outcome = selectFirstWorkingEngine<DummyEngine>(
            ladder = listOf(
                EngineCandidate("GPU", true),
                EngineCandidate("GPU", false),
                EngineCandidate("CPU", false)
            ),
            enableVisionBackend = false,
            configureFlag = { true },
            tryCreate = { _, _, _ ->
                EngineCandidateAttempt.InitializationFailed(RuntimeException("failed"))
            }
        )
        assertNull(outcome.selection)
        assertNotNull(outcome.failure)
        assertEquals(3, outcome.fallbackEvents.size)
        assertTrue(outcome.fallbackEvents.all { it.reason == FallbackReason.MTP_ENGINE_INIT_FAILED || it.reason == FallbackReason.BACKEND_ENGINE_INIT_FAILED })
        outcome.fallbackEvents.forEach { assertNull(it.selectedReplacementBackend) }
    }
}