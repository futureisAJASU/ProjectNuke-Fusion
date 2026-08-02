package com.projectnuke.fusion.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLlmEngineMtpTest {
    private val mtpLadder = listOf(
        EngineCandidate("GPU", true),
        EngineCandidate("GPU", false),
        EngineCandidate("CPU", true),
        EngineCandidate("CPU", false)
    )

    private fun alwaysFlag(): (Boolean) -> Boolean = { true }

    @Test
    fun `selects first working candidate with MTP enabled`() {
        val selection = selectFirstWorkingEngine<String>(
            ladder = mtpLadder,
            enableVisionBackend = false,
            configureFlag = alwaysFlag(),
            tryCreate = { backendName, mtpEnabled, visionBackendIsCpu ->
                if (backendName == "GPU" && mtpEnabled) {
                    Result.success("gpu-mtp-engine")
                } else {
                    Result.failure(IllegalStateException("not reached"))
                }
            }
        )
        val result = selection.first
        assertNotNull(result)
        assertEquals("gpu-mtp-engine", result!!.engine)
        assertTrue(result.selectedMtpEnabled)
        assertTrue(result.mtpFlagAppliedForMtp)
        assertNull(selection.second)
    }

    @Test
    fun `MTP engine init failure falls back to non-MTP engine`() {
        val attempts = mutableListOf<String>()
        val selection = selectFirstWorkingEngine<String>(
            ladder = mtpLadder,
            enableVisionBackend = false,
            configureFlag = alwaysFlag(),
            tryCreate = { backendName, mtpEnabled, visionBackendIsCpu ->
                attempts += "$backendName-mtp=$mtpEnabled"
                if (backendName == "GPU" && !mtpEnabled) {
                    Result.success("gpu-plain-engine")
                } else {
                    Result.failure(IllegalStateException("init failed"))
                }
            }
        )
        val result = selection.first
        assertNotNull(result)
        assertEquals("gpu-plain-engine", result!!.engine)
        assertFalse(result.selectedMtpEnabled)
        assertTrue(result.mtpFlagAppliedForMtp)
        assertEquals(listOf("GPU-mtp=true", "GPU-mtp=false"), attempts)
    }

    @Test
    fun `flag apply failure is tracked even when later candidate succeeds`() {
        val selection = selectFirstWorkingEngine<String>(
            ladder = mtpLadder,
            enableVisionBackend = false,
            configureFlag = { false },
            tryCreate = { backendName, mtpEnabled, visionBackendIsCpu ->
                if (backendName == "GPU" && !mtpEnabled) {
                    Result.success("gpu-plain-engine")
                } else {
                    Result.failure(IllegalStateException("init failed"))
                }
            }
        )
        val result = selection.first
        assertNotNull(result)
        assertFalse(result!!.selectedMtpEnabled)
        assertFalse(result.mtpFlagAppliedForMtp)
    }

    @Test
    fun `GPU vision failure retries with CPU vision backend`() {
        var cpuVisionRetry = false
        val selection = selectFirstWorkingEngine<String>(
            ladder = mtpLadder,
            enableVisionBackend = true,
            configureFlag = alwaysFlag(),
            tryCreate = { backendName, mtpEnabled, visionBackendIsCpu ->
                if (visionBackendIsCpu) {
                    cpuVisionRetry = true
                    Result.success("gpu-engine-cpu-vision")
                } else {
                    Result.failure(IllegalStateException("gpu vision init failed"))
                }
            }
        )
        val result = selection.first
        assertNotNull(result)
        assertTrue(cpuVisionRetry)
        assertEquals("gpu-engine-cpu-vision", result!!.engine)
        assertTrue(result.selectedMtpEnabled)
    }

    @Test
    fun `all candidates failing returns null and last failure`() {
        val failure = IllegalStateException("all failed")
        val selection = selectFirstWorkingEngine<String>(
            ladder = mtpLadder,
            enableVisionBackend = false,
            configureFlag = alwaysFlag(),
            tryCreate = { _, _, _ -> Result.failure(failure) }
        )
        assertNull(selection.first)
        assertEquals(failure, selection.second)
    }

    @Test
    fun `flag is configured before each candidate attempt`() {
        val flagCalls = mutableListOf<Boolean>()
        val attempts = mutableListOf<Pair<String, Boolean>>()
        selectFirstWorkingEngine<String>(
            ladder = mtpLadder,
            enableVisionBackend = false,
            configureFlag = { enabled -> flagCalls += enabled; true },
            tryCreate = { backendName, mtpEnabled, visionBackendIsCpu ->
                attempts += (backendName to mtpEnabled)
                Result.failure(IllegalStateException("fail"))
            }
        )
        assertEquals(listOf(true, false, true, false), flagCalls)
        assertEquals(4, attempts.size)
    }
}
