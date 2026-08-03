package com.projectnuke.fusion.llm

import com.projectnuke.fusion.model.AcceleratorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtEngineMtpStateTest {
    @Test
    fun `cache key records effective MTP state so fallback is never cached as MTP`() {
        val base = "m.part"
        val keyWithMtp = buildLiteRtEngineCacheKey(base, AcceleratorMode.GPU, 1024, mtpEnabled = true, enableVisionBackend = false)
        val keyWithoutMtp = buildLiteRtEngineCacheKey(base, AcceleratorMode.GPU, 1024, mtpEnabled = false, enableVisionBackend = false)
        assertNotEquals(keyWithMtp, keyWithoutMtp)
        assertTrue(keyWithMtp.contains("|true|"))
        assertTrue(keyWithoutMtp.contains("|false|"))
        assertEquals(
            buildLiteRtEngineCacheKey(base, AcceleratorMode.GPU, 1024, mtpEnabled = false, enableVisionBackend = false),
            buildLiteRtEngineCacheKey(base, AcceleratorMode.GPU, 1024, mtpEnabled = false, enableVisionBackend = false)
        )
    }

    @Test
    fun `cache key distinguishes accelerator maxTokens and vision backend`() {
        assertEquals(
            buildLiteRtEngineCacheKey("m", AcceleratorMode.CPU, 512, mtpEnabled = true, enableVisionBackend = false),
            buildLiteRtEngineCacheKey("m", AcceleratorMode.CPU, 512, mtpEnabled = true, enableVisionBackend = false)
        )
        assertNotEquals(
            buildLiteRtEngineCacheKey("m", AcceleratorMode.CPU, 512, mtpEnabled = true, enableVisionBackend = false),
            buildLiteRtEngineCacheKey("m", AcceleratorMode.GPU, 512, mtpEnabled = true, enableVisionBackend = false)
        )
        assertNotEquals(
            buildLiteRtEngineCacheKey("m", AcceleratorMode.CPU, 512, mtpEnabled = true, enableVisionBackend = false),
            buildLiteRtEngineCacheKey("m", AcceleratorMode.CPU, 1024, mtpEnabled = true, enableVisionBackend = false)
        )
        assertNotEquals(
            buildLiteRtEngineCacheKey("m", AcceleratorMode.CPU, 512, mtpEnabled = true, enableVisionBackend = false),
            buildLiteRtEngineCacheKey("m", AcceleratorMode.CPU, 512, mtpEnabled = true, enableVisionBackend = true)
        )
    }

    @Test
    fun `MTP runtime status has exact state set`() {
        val expected = setOf(
            MtpRuntimeStatus.OFF,
            MtpRuntimeStatus.UNSUPPORTED,
            MtpRuntimeStatus.REQUESTED,
            MtpRuntimeStatus.INITIALIZED_WITH_MTP_REQUEST,
            MtpRuntimeStatus.RUNTIME_CONFIRMED_ACTIVE,
            MtpRuntimeStatus.FALLBACK_DISABLED,
            MtpRuntimeStatus.FAILED
        )
        assertEquals(expected, MtpRuntimeStatus.entries.toSet())
    }

    @Test
    fun `AUTO ladder prefers GPU+MTP then GPU then CPU+MTP then CPU`() {
        val ladder = buildEngineCandidateLadder(AcceleratorMode.AUTO, mtpRequested = true, mtpSupported = true)
        assertEquals(
            listOf(
                EngineCandidate("GPU", true),
                EngineCandidate("GPU", false),
                EngineCandidate("CPU", true),
                EngineCandidate("CPU", false)
            ),
            ladder
        )
    }

    @Test
    fun `AUTO ladder without MTP capability skips MTP candidates`() {
        val ladder = buildEngineCandidateLadder(AcceleratorMode.AUTO, mtpRequested = true, mtpSupported = false)
        assertEquals(
            listOf(
                EngineCandidate("GPU", false),
                EngineCandidate("CPU", false)
            ),
            ladder
        )
    }

    @Test
    fun `AUTO ladder without MTP request never enables MTP`() {
        val ladder = buildEngineCandidateLadder(AcceleratorMode.AUTO, mtpRequested = false, mtpSupported = true)
        assertEquals(
            listOf(
                EngineCandidate("GPU", false),
                EngineCandidate("CPU", false)
            ),
            ladder
        )
    }

    @Test
    fun `GPU ladder retries without MTP when MTP init fails`() {
        val ladder = buildEngineCandidateLadder(AcceleratorMode.GPU, mtpRequested = true, mtpSupported = true)
        assertEquals(
            listOf(
                EngineCandidate("GPU", true),
                EngineCandidate("GPU", false)
            ),
            ladder
        )
    }

    @Test
    fun `CPU ladder keeps MTP fallback when MTP supported`() {
        val ladder = buildEngineCandidateLadder(AcceleratorMode.CPU, mtpRequested = true, mtpSupported = true)
        assertEquals(
            listOf(
                EngineCandidate("CPU", true),
                EngineCandidate("CPU", false)
            ),
            ladder
        )
        assertEquals("CPU", ladder.first().backend)
    }
}
