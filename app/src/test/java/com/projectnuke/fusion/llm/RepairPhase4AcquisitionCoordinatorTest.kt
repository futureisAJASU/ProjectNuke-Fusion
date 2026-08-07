package com.projectnuke.fusion.llm

import com.projectnuke.fusion.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class RepairPhase4AcquisitionCoordinatorTest {

    private class MockMtpFailureMemory : MtpFailureMemory(NoopMtpFailureMemoryStorage) {
        var shouldSkipMtpResult: String? = null
        var shouldSkipBackendResult: String? = null

        override fun shouldSkipMtp(modelPath: String, backendName: String, mtpEnabled: Boolean, validationVersion: Int, kvCacheCapacityTokens: Int, enableVisionBackend: Boolean, fileSize: Long, modifiedAt: Long, mtpSupported: Boolean): String? = shouldSkipMtpResult
        override fun shouldSkipBackend(modelPath: String, backendName: String, validationVersion: Int, kvCacheCapacityTokens: Int, enableVisionBackend: Boolean, fileSize: Long, modifiedAt: Long, mtpSupported: Boolean): String? = shouldSkipBackendResult
    }

    private fun createCoordinator(
        memory: MtpFailureMemory = MockMtpFailureMemory(),
        probe: (String) -> Boolean? = { true },
        flag: (Boolean) -> Boolean = { true },
        factory: (String, Boolean, Boolean) -> EngineCandidateAttempt<Any> = { _, _, _ -> 
            EngineCandidateAttempt.Success(Any()) 
        }
    ) = EngineAcquisitionCoordinator(memory, probe, flag, factory as (String, Boolean, Boolean) -> EngineCandidateAttempt<com.google.ai.edge.litertlm.Engine>, { 1000L })

    private val defaultProfile = RequestedEngineProfile(
        modelPath = "test_model.bin",
        accelerator = AcceleratorMode.AUTO,
        kvCacheCapacityTokens = 2048,
        mtpRequested = true,
        enableVisionBackend = false
    )

    @Test
    fun `capability rejection prevents factory calls and records MTP_UNSUPPORTED`() {
        var factoryCalls = 0
        val coordinator = EngineAcquisitionCoordinator(
            mtpFailureMemory = MockMtpFailureMemory(),
            mtpCapabilityProbe = { false },
            configureFlag = { true },
            engineFactory = { _, _, _ -> 
                factoryCalls++
                EngineCandidateAttempt.Success(mockEngine())
            }
        )

        val outcome = coordinator.acquire(defaultProfile, null)

        assertNotNull(outcome.selection)
        assertEquals(false, outcome.selection!!.selectedMtpEnabled)
        assertEquals(0, factoryCalls)
        assertTrue(outcome.fallbackEvents.any { it.reason == FallbackReason.MTP_UNSUPPORTED })
    }

    @Test
    fun `GPU plain failure falls back to CPU success`() {
        var callCount = 0
        val coordinator = EngineAcquisitionCoordinator(
            mtpFailureMemory = MockMtpFailureMemory(),
            mtpCapabilityProbe = { true },
            configureFlag = { true },
            engineFactory = { backend, _, _ -> 
                callCount++
                if (backend == "GPU") EngineCandidateAttempt.InitializationFailed(RuntimeException("GPU Fail"))
                else EngineCandidateAttempt.Success(mockEngine())
            }
        )

        val profile = defaultProfile.copy(mtpRequested = false)
        val outcome = coordinator.acquire(profile, null)

        assertNotNull(outcome.selection)
        assertEquals("CPU", outcome.selection!!.backendName)
        assertTrue(outcome.fallbackEvents.any { it.reason == FallbackReason.GPU_TEXT_ENGINE_FAILED_CPU_SELECTED })
    }

    @Test
    fun `MTP failure with plain GPU skipped by failure memory selects CPU`() {
        val memory = MockMtpFailureMemory().apply {
            shouldSkipBackendResult = FallbackReason.BACKEND_SKIPPED_RECENT_FAILURE
        }
        
        var gpuCalls = 0
        val coordinator = EngineAcquisitionCoordinator(
            mtpFailureMemory = memory,
            mtpCapabilityProbe = { true },
            configureFlag = { true },
            engineFactory = { backend, _, _ -> 
                if (backend == "GPU") gpuCalls++
                EngineCandidateAttempt.Success(mockEngine())
            }
        )

        val profile = defaultProfile.copy(mtpRequested = false)
        val outcome = coordinator.acquire(profile, null)

        assertNotNull(outcome.selection)
        assertEquals("CPU", outcome.selection!!.backendName)
        assertEquals(0, gpuCalls)
    }

    @Test
    fun `all candidates filtered by cooldown reuses compatible loaded engine`() {
        val memory = MockMtpFailureMemory().apply {
            shouldSkipMtpResult = FallbackReason.MTP_SKIPPED_RECENT_FAILURE
            shouldSkipBackendResult = FallbackReason.BACKEND_SKIPPED_RECENT_FAILURE
        }

        val engine = mockEngine()
        val loadedState = LoadedRuntimeState(
            engine = engine,
            key = EngineRuntimeKey(
                fingerprint = ModelFingerprint.of(defaultProfile.modelPath),
                accelerator = defaultProfile.accelerator,
                kvCacheCapacityTokens = defaultProfile.kvCacheCapacityTokens,
                enableVisionBackend = defaultProfile.enableVisionBackend,
                mtpEnabled = false,
                selectedBackend = "GPU"
            ),
            mtpStatus = MtpRuntimeStatus.OFF,
            runtimeSelection = mockRuntimeSelection(),
            actualTextBackend = "GPU",
            actualVisionBackend = null,
            fallbackEvents = emptyList()
        )

        var factoryCalls = 0
        val coordinator = EngineAcquisitionCoordinator(
            mtpFailureMemory = memory,
            mtpCapabilityProbe = { true },
            configureFlag = { true },
            engineFactory = { _, _, _ -> 
                factoryCalls++
                EngineCandidateAttempt.Success(mockEngine())
            }
        )
        val outcome = coordinator.acquire(defaultProfile, loadedState)

        assertNotNull(outcome.selection)
        assertEquals(engine, outcome.selection!!.engine)
        assertEquals(0, factoryCalls)
    }

    @Test
    fun `incompatible loaded engine is rejected when all candidates skipped`() {
        val memory = MockMtpFailureMemory().apply {
            shouldSkipMtpResult = FallbackReason.MTP_SKIPPED_RECENT_FAILURE
            shouldSkipBackendResult = FallbackReason.BACKEND_SKIPPED_RECENT_FAILURE
        }

        val engine = mockEngine()
        val loadedState = LoadedRuntimeState(
            engine = engine,
            key = EngineRuntimeKey(
                fingerprint = ModelFingerprint.of("different_model.bin"),
                accelerator = defaultProfile.accelerator,
                kvCacheCapacityTokens = defaultProfile.kvCacheCapacityTokens,
                enableVisionBackend = defaultProfile.enableVisionBackend,
                mtpEnabled = false,
                selectedBackend = "GPU"
            ),
            mtpStatus = MtpRuntimeStatus.OFF,
            runtimeSelection = mockRuntimeSelection(),
            actualTextBackend = "GPU",
            actualVisionBackend = null,
            fallbackEvents = emptyList()
        )

        val coordinator = EngineAcquisitionCoordinator(
            mtpFailureMemory = memory,
            mtpCapabilityProbe = { true },
            configureFlag = { true },
            engineFactory = { _, _, _ -> EngineCandidateAttempt.Success(mockEngine()) }
        )
        val outcome = coordinator.acquire(defaultProfile, loadedState)

        assertNull(outcome.selection)
        assertNotNull(outcome.failure)
        assertTrue(outcome.failure!!.attemptSnapshot.fallbackEvents.any { it.reason == FallbackReason.ALL_CANDIDATES_SKIPPED_RECENT_FAILURE })
    }

    @Test
    fun `request local event isolation`() {
        val memory = MockMtpFailureMemory().apply {
            shouldSkipBackendResult = FallbackReason.BACKEND_SKIPPED_RECENT_FAILURE
        }

        val engine = mockEngine()
        val loadedState = LoadedRuntimeState(
            engine = engine,
            key = EngineRuntimeKey(
                fingerprint = ModelFingerprint.of(defaultProfile.modelPath),
                accelerator = defaultProfile.accelerator,
                kvCacheCapacityTokens = defaultProfile.kvCacheCapacityTokens,
                enableVisionBackend = defaultProfile.enableVisionBackend,
                mtpEnabled = false,
                selectedBackend = "CPU"
            ),
            mtpStatus = MtpRuntimeStatus.OFF,
            runtimeSelection = mockRuntimeSelection(),
            actualTextBackend = "CPU",
            actualVisionBackend = null,
            fallbackEvents = emptyList()
        )

        val coordinator = EngineAcquisitionCoordinator(
            mtpFailureMemory = memory,
            mtpCapabilityProbe = { true },
            configureFlag = { true },
            engineFactory = { _, _, _ -> EngineCandidateAttempt.Success(mockEngine()) }
        )
        
        val outcomeA = coordinator.acquire(defaultProfile.copy(accelerator = AcceleratorMode.AUTO), loadedState)
        assertTrue(outcomeA.fallbackEvents.any { it.reason == FallbackReason.BACKEND_SKIPPED_RECENT_FAILURE })
        
        val outcomeB = coordinator.acquire(defaultProfile.copy(accelerator = AcceleratorMode.CPU), loadedState)
        assertFalse(outcomeB.fallbackEvents.any { it.reason == FallbackReason.BACKEND_SKIPPED_RECENT_FAILURE })
    }

    @Test
    fun `output only change does not recreate engine`() {
        var factoryCalls = 0
        val coordinator = EngineAcquisitionCoordinator(
            mtpFailureMemory = MockMtpFailureMemory(),
            mtpCapabilityProbe = { true },
            configureFlag = { true },
            engineFactory = { _, _, _ -> 
                factoryCalls++
                EngineCandidateAttempt.Success(mockEngine())
            }
        )
        
        val profile1 = defaultProfile.copy(kvCacheCapacityTokens = 2048)
        val outcome1 = coordinator.acquire(profile1, null)
        val engine1 = outcome1.selection!!.engine
        
        val loadedState = LoadedRuntimeState(
            engine = engine1,
            key = EngineRuntimeKey(
                fingerprint = ModelFingerprint.of(profile1.modelPath),
                accelerator = profile1.accelerator,
                kvCacheCapacityTokens = profile1.kvCacheCapacityTokens,
                enableVisionBackend = profile1.enableVisionBackend,
                mtpEnabled = outcome1.selection!!.selectedMtpEnabled,
                selectedBackend = outcome1.selection!!.backendName
            ),
            mtpStatus = MtpRuntimeStatus.OFF,
            runtimeSelection = mockRuntimeSelection(),
            actualTextBackend = outcome1.selection!!.backendName,
            actualVisionBackend = null,
            fallbackEvents = emptyList()
        )
        
        val profile2 = profile1.copy() 
        val outcome2 = coordinator.acquire(profile2, loadedState)
        
        assertEquals(engine1, outcome2.selection!!.engine)
        assertEquals(1, factoryCalls)
    }

    @Test
    fun `kv capacity change alters engine identity`() {
        var factoryCalls = 0
        val coordinator = EngineAcquisitionCoordinator(
            mtpFailureMemory = MockMtpFailureMemory(),
            mtpCapabilityProbe = { true },
            configureFlag = { true },
            engineFactory = { _, _, _ -> 
                factoryCalls++
                EngineCandidateAttempt.Success(mockEngine())
            }
        )
        
        val profile1 = defaultProfile.copy(kvCacheCapacityTokens = 2048)
        val outcome1 = coordinator.acquire(profile1, null)
        val engine1 = outcome1.selection!!.engine
        
        val loadedState = LoadedRuntimeState(
            engine = engine1,
            key = EngineRuntimeKey(
                fingerprint = ModelFingerprint.of(profile1.modelPath),
                accelerator = profile1.accelerator,
                kvCacheCapacityTokens = profile1.kvCacheCapacityTokens,
                enableVisionBackend = profile1.enableVisionBackend,
                mtpEnabled = outcome1.selection!!.selectedMtpEnabled,
                selectedBackend = outcome1.selection!!.backendName
            ),
            mtpStatus = MtpRuntimeStatus.OFF,
            runtimeSelection = mockRuntimeSelection(),
            actualTextBackend = outcome1.selection!!.backendName,
            actualVisionBackend = null,
            fallbackEvents = emptyList()
        )
        
        val profile2 = profile1.copy(kvCacheCapacityTokens = 4096)
        val outcome2 = coordinator.acquire(profile2, loadedState)
        
        assertNotEquals(engine1, outcome2.selection!!.engine)
        assertEquals(2, factoryCalls)
    }

    private fun mockEngine(): com.google.ai.edge.litertlm.Engine {
        // Use a real Engine object but with dummy config to avoid native crashes in JVM tests
        // Note: In a real Android environment this would need a proper mock or a mock-factory.
        // For JVM unit tests, we are using this to satisfy type constraints.
        return java.lang.reflect.Proxy.newProxyInstance(
            com.google.ai.edge.litertlm.Engine::class.java.classLoader,
            arrayOf(com.google.ai.edge.litertlm.Engine::class.java)
        ) as com.google.ai.edge.litertlm.Engine
    }

    private fun mockRuntimeSelection() = EngineSelectionRuntime("AUTO", "GPU", null, true, true, null)
}
