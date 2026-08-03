package com.projectnuke.fusion.llm

import com.projectnuke.fusion.model.AcceleratorMode
import com.projectnuke.fusion.model.GenerationSettings
import com.projectnuke.fusion.model.RequestedEngineProfile
import com.projectnuke.fusion.model.toRequestedEngineProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtEngineMtpStateTest {
    private fun profile(
        modelPath: String = "m.part",
        accelerator: AcceleratorMode = AcceleratorMode.GPU,
        mtpRequested: Boolean = false,
        maxTokens: Int = 1024,
        enableVisionBackend: Boolean = false
    ) = RequestedEngineProfile(
        modelPath = modelPath,
        accelerator = accelerator,
        mtpRequested = mtpRequested,
        maxTokens = maxTokens,
        enableVisionBackend = enableVisionBackend
    )

    @Test
    fun `cache key records effective MTP state so fallback is never cached as MTP`() {
        val keyWithMtp = buildLiteRtEngineCacheKey(profile(mtpRequested = true))
        val keyWithoutMtp = buildLiteRtEngineCacheKey(profile(mtpRequested = false))
        assertNotEquals(keyWithMtp, keyWithoutMtp)
        assertTrue(keyWithMtp.contains("|true|"))
        assertTrue(keyWithoutMtp.contains("|false|"))
        assertEquals(
            buildLiteRtEngineCacheKey(profile(mtpRequested = false)),
            buildLiteRtEngineCacheKey(profile(mtpRequested = false))
        )
    }

    @Test
    fun `cache key distinguishes accelerator maxTokens and vision backend`() {
        assertEquals(
            buildLiteRtEngineCacheKey(profile(accelerator = AcceleratorMode.CPU, maxTokens = 512, mtpRequested = true)),
            buildLiteRtEngineCacheKey(profile(accelerator = AcceleratorMode.CPU, maxTokens = 512, mtpRequested = true))
        )
        assertNotEquals(
            buildLiteRtEngineCacheKey(profile(accelerator = AcceleratorMode.CPU, maxTokens = 512, mtpRequested = true)),
            buildLiteRtEngineCacheKey(profile(accelerator = AcceleratorMode.GPU, maxTokens = 512, mtpRequested = true))
        )
        assertNotEquals(
            buildLiteRtEngineCacheKey(profile(accelerator = AcceleratorMode.CPU, maxTokens = 512, mtpRequested = true)),
            buildLiteRtEngineCacheKey(profile(accelerator = AcceleratorMode.CPU, maxTokens = 1024, mtpRequested = true))
        )
        assertNotEquals(
            buildLiteRtEngineCacheKey(profile(accelerator = AcceleratorMode.CPU, maxTokens = 512, mtpRequested = true)),
            buildLiteRtEngineCacheKey(profile(accelerator = AcceleratorMode.CPU, maxTokens = 512, mtpRequested = true, enableVisionBackend = true))
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
    fun `GenerationSettings migration carries MTP backend KV capacity and vision flags into the profile`() {
        val settings = GenerationSettings(
            maxTokens = 4096,
            accelerator = AcceleratorMode.GPU,
            speculativeDecodingEnabled = true
        )
        val migrated = settings.toRequestedEngineProfile(modelPath = "model.litertlm", enableVisionBackend = true)
        assertEquals("model.litertlm", migrated.modelPath)
        assertEquals(AcceleratorMode.GPU, migrated.accelerator)
        assertTrue(migrated.mtpRequested)
        assertEquals(4096, migrated.maxTokens)
        assertTrue(migrated.enableVisionBackend)
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
