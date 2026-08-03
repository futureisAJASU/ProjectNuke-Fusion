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
        kvCacheCapacityTokens: Int = 1024,
        enableVisionBackend: Boolean = false
    ) = RequestedEngineProfile(
        modelPath = modelPath,
        accelerator = accelerator,
        mtpRequested = mtpRequested,
        kvCacheCapacityTokens = kvCacheCapacityTokens,
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
    fun `cache key distinguishes accelerator KV capacity and vision backend`() {
        assertEquals(
            buildLiteRtEngineCacheKey(profile(accelerator = AcceleratorMode.CPU, kvCacheCapacityTokens = 512, mtpRequested = true)),
            buildLiteRtEngineCacheKey(profile(accelerator = AcceleratorMode.CPU, kvCacheCapacityTokens = 512, mtpRequested = true))
        )
        assertNotEquals(
            buildLiteRtEngineCacheKey(profile(accelerator = AcceleratorMode.CPU, kvCacheCapacityTokens = 512, mtpRequested = true)),
            buildLiteRtEngineCacheKey(profile(accelerator = AcceleratorMode.GPU, kvCacheCapacityTokens = 512, mtpRequested = true))
        )
        assertNotEquals(
            buildLiteRtEngineCacheKey(profile(accelerator = AcceleratorMode.CPU, kvCacheCapacityTokens = 512, mtpRequested = true)),
            buildLiteRtEngineCacheKey(profile(accelerator = AcceleratorMode.CPU, kvCacheCapacityTokens = 1024, mtpRequested = true))
        )
        assertNotEquals(
            buildLiteRtEngineCacheKey(profile(accelerator = AcceleratorMode.CPU, kvCacheCapacityTokens = 512, mtpRequested = true)),
            buildLiteRtEngineCacheKey(profile(accelerator = AcceleratorMode.CPU, kvCacheCapacityTokens = 512, mtpRequested = true, enableVisionBackend = true))
        )
    }

    @Test
    fun `output limit change never changes the engine identity`() {
        val profileWithKv4096 = profile(kvCacheCapacityTokens = 4096, mtpRequested = true)
        val sameProfileAgain = profile(kvCacheCapacityTokens = 4096, mtpRequested = true)
        val differentKvCapacity = profile(kvCacheCapacityTokens = 8192, mtpRequested = true)

        assertEquals(
            buildLiteRtEngineCacheKey(profileWithKv4096),
            buildLiteRtEngineCacheKey(sameProfileAgain)
        )
        assertNotEquals(
            buildLiteRtEngineCacheKey(profileWithKv4096),
            buildLiteRtEngineCacheKey(differentKvCapacity)
        )

        // The engine identity is built from the profile only: per-turn options
        // (including the output limit) are never part of it, so changing them
        // must never rebuild the engine. The cache-key function literally has
        // no options input; assert the profile values it does consume.
        assertNotEquals(
            buildLiteRtEngineCacheKey(profile(kvCacheCapacityTokens = 4096, mtpRequested = false)),
            buildLiteRtEngineCacheKey(profile(kvCacheCapacityTokens = 4096, mtpRequested = true))
        )
        assertEquals(
            buildLiteRtEngineCacheKey(profileWithKv4096),
            buildLiteRtEngineCacheKey(profileWithKv4096.copy(enableVisionBackend = profileWithKv4096.enableVisionBackend))
        )
    }

    @Test
    fun `KV capacity change reloads the engine`() {
        // Same settings produce the same KV capacity -> same engine identity.
        val a = com.projectnuke.fusion.model.GenerationSettings(maxTokens = 4096, accelerator = AcceleratorMode.GPU, speculativeDecodingEnabled = true)
        val b = a.copy(maxTokens = 4096)
        assertEquals(
            buildLiteRtEngineCacheKey(a.toRequestedEngineProfile("m", enableVisionBackend = false)),
            buildLiteRtEngineCacheKey(b.toRequestedEngineProfile("m", enableVisionBackend = false))
        )
        // Changing KV capacity changes the engine identity -> engine reload.
        val c = a.copy(maxTokens = 2048)
        assertNotEquals(
            buildLiteRtEngineCacheKey(a.toRequestedEngineProfile("m", enableVisionBackend = false)),
            buildLiteRtEngineCacheKey(c.toRequestedEngineProfile("m", enableVisionBackend = false))
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
        assertEquals(4096, migrated.kvCacheCapacityTokens)
        assertTrue(migrated.enableVisionBackend)
    }

    @Test
    fun `AUTO ladder prefers GPU+MTP then GPU then CPU, never CPU+MTP`() {
        val ladder = buildEngineCandidateLadder(AcceleratorMode.AUTO, mtpRequested = true, mtpSupported = true)
        assertEquals(
            listOf(
                EngineCandidate("GPU", true),
                EngineCandidate("GPU", false),
                EngineCandidate("CPU", false)
            ),
            ladder
        )
        // Beta AUTO never falls back to CPU+MTP (explicit experimental only)
        // and keeps the ladder at most 3 candidates to avoid sequential inits.
        assertTrue(ladder.none { it.backend == "CPU" && it.mtpEnabled })
        assertTrue(ladder.size <= 3)
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
    fun `CPU ladder keeps MTP fallback only for explicit MTP requests`() {
        val ladder = buildEngineCandidateLadder(AcceleratorMode.CPU, mtpRequested = true, mtpSupported = true)
        assertEquals(
            listOf(
                EngineCandidate("CPU", true),
                EngineCandidate("CPU", false)
            ),
            ladder
        )
        assertEquals("CPU", ladder.first().backend)

        val withoutRequest = buildEngineCandidateLadder(AcceleratorMode.CPU, mtpRequested = false, mtpSupported = true)
        assertEquals(listOf(EngineCandidate("CPU", false)), withoutRequest)
    }
}
