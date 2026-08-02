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
    fun `cache hit with MTP requested and supported reports ACTIVE`() {
        assertEquals(
            MtpRuntimeStatus.ACTIVE,
            resolveMtpCacheHitStatus(mtpRequested = true, mtpSupported = true)
        )
    }

    @Test
    fun `cache hit with MTP requested but unsupported reports UNSUPPORTED`() {
        assertEquals(
            MtpRuntimeStatus.UNSUPPORTED,
            resolveMtpCacheHitStatus(mtpRequested = true, mtpSupported = false)
        )
    }

    @Test
    fun `cache hit without MTP request reports OFF even when supported`() {
        assertEquals(
            MtpRuntimeStatus.OFF,
            resolveMtpCacheHitStatus(mtpRequested = false, mtpSupported = true)
        )
    }

    @Test
    fun `MTP runtime status has exact state set`() {
        val expected = setOf(
            MtpRuntimeStatus.OFF,
            MtpRuntimeStatus.REQUESTED,
            MtpRuntimeStatus.ACTIVE,
            MtpRuntimeStatus.UNSUPPORTED,
            MtpRuntimeStatus.FALLBACK_DISABLED,
            MtpRuntimeStatus.FAILED
        )
        assertEquals(expected, MtpRuntimeStatus.entries.toSet())
    }
}
