package com.projectnuke.fusion.llm

import com.projectnuke.fusion.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class RepairPhase4AcquisitionCoordinatorTest {

    private data class FakeEngineHandle(val backend: String)

    private class RecordingStorage : MtpFailureMemoryStorage {
        val saves = mutableListOf<Map<String, String>>()
        private var stored: Map<String, String> = emptyMap()
        override fun load(): Map<String, String> = stored
        override fun save(entries: Map<String, String>): Boolean {
            stored = entries
            saves += entries
            return true
        }

        override fun clear(): Boolean {
            stored = emptyMap()
            return true
        }
    }

    private data class RecordedFailure(
        val modelPath: String,
        val backendName: String,
        val mtpEnabled: Boolean,
        val fallbackReason: String
    )

    private class FakeMtpFailureMemory : MtpFailureMemoryPort {
        var shouldSkipMtpResult: FallbackReason? = null
        var shouldSkipBackendResult: FallbackReason? = null

        /** When set, backend skips apply only to this backend name. */
        var skipBackendName: String? = null

        val recordedFailures = mutableListOf<RecordedFailure>()

        override fun shouldSkipMtp(
            modelPath: String,
            backendName: String,
            mtpEnabled: Boolean,
            validationVersion: Int,
            kvCacheCapacityTokens: Int,
            enableVisionBackend: Boolean,
            fileSize: Long,
            modifiedAt: Long,
            mtpSupported: Boolean
        ): String? = shouldSkipMtpResult?.name

        override fun shouldSkipBackend(
            modelPath: String,
            backendName: String,
            validationVersion: Int,
            kvCacheCapacityTokens: Int,
            enableVisionBackend: Boolean,
            fileSize: Long,
            modifiedAt: Long,
            mtpSupported: Boolean
        ): String? =
            if (skipBackendName == null || backendName == skipBackendName) shouldSkipBackendResult?.name else null

        override fun recordFailure(
            modelPath: String,
            backendName: String,
            mtpEnabled: Boolean,
            validationVersion: Int,
            kvCacheCapacityTokens: Int,
            enableVisionBackend: Boolean,
            fileSize: Long,
            modifiedAt: Long,
            mtpSupported: Boolean,
            fallbackReason: String
        ) {
            recordedFailures += RecordedFailure(modelPath, backendName, mtpEnabled, fallbackReason)
        }
    }

    /**
     * Deterministic fingerprint (mtpSupported = true) so the MTP ladder is
     * exercised without touching the filesystem.
     */
    private val fakeFingerprint = ModelFingerprint(
        canonicalPath = "test_model.bin",
        fileSize = 1024L,
        modifiedAt = 1000L,
        validationVersion = 1,
        mtpSupported = true
    )

    private fun <T> createCoordinator(
        memory: MtpFailureMemoryPort = FakeMtpFailureMemory(),
        probe: (String) -> Boolean? = { true },
        flag: (Boolean) -> Boolean = { true },
        factory: (String, Boolean, Boolean) -> EngineCandidateAttempt<T> = { backend, _, _ ->
            EngineCandidateAttempt.Success(FakeEngineHandle(backend) as T)
        },
        fingerprintResolver: (String) -> ModelFingerprint = { fakeFingerprint }
    ) = EngineAcquisitionCoordinator(
        mtpFailureMemory = memory,
        mtpCapabilityProbe = probe,
        configureFlag = flag,
        engineFactory = factory,
        clock = { 1000L },
        fingerprintResolver = fingerprintResolver
    )

    private val defaultProfile = RequestedEngineProfile(
        modelPath = "test_model.bin",
        accelerator = AcceleratorMode.AUTO,
        kvCacheCapacityTokens = 2048,
        mtpRequested = true,
        enableVisionBackend = false
    )

    private fun loadedState(
        engine: FakeEngineHandle,
        backend: String,
        mtpEnabled: Boolean,
        fingerprint: ModelFingerprint = fakeFingerprint,
        accelerator: AcceleratorMode = defaultProfile.accelerator
    ) = LoadedRuntimeState(
        engine = engine,
        key = EngineRuntimeKey(
            fingerprint = fingerprint,
            accelerator = accelerator,
            kvCacheCapacityTokens = defaultProfile.kvCacheCapacityTokens,
            enableVisionBackend = defaultProfile.enableVisionBackend,
            mtpEnabled = mtpEnabled,
            selectedBackend = backend
        ),
        mtpStatus = MtpRuntimeStatus.OFF,
        runtimeSelection = EngineSelectionRuntime("AUTO", backend, null, true, true, null),
        actualTextBackend = backend,
        actualVisionBackend = null,
        fallbackEvents = emptyList()
    )

    // ── Phase 1: deterministic cooldown / capability / failure semantics ────

    @Test
    fun `MTP init failure enters cooldown and is retried only after the window`() {
        val now = AtomicLong(0L)
        val memory = MtpFailureMemory(storage = RecordingStorage(), clock = { now.get() })
        val mtpAttempts = mutableListOf<Boolean>()
        val coordinator = createCoordinator(
            memory = memory,
            factory = { backend, mtp, _ ->
                if (mtp) {
                    mtpAttempts += true
                    EngineCandidateAttempt.InitializationFailed(RuntimeException("MTP init failed"))
                } else {
                    EngineCandidateAttempt.Success(FakeEngineHandle(backend))
                }
            }
        )

        val outcome1 = coordinator.acquire(defaultProfile, null)
        assertNotNull(outcome1.selection)
        assertEquals(false, outcome1.selection!!.selectedMtpEnabled)
        assertTrue(outcome1.fallbackEvents.any { it.reason == FallbackReason.MTP_ENGINE_INIT_FAILED })
        assertEquals(1, mtpAttempts.size)

        now.set(60_000L)
        val outcome2 = coordinator.acquire(defaultProfile, null)
        assertEquals(false, outcome2.selection!!.selectedMtpEnabled)
        assertTrue(outcome2.fallbackEvents.any { it.reason == FallbackReason.MTP_SKIPPED_RECENT_FAILURE })
        assertEquals(1, mtpAttempts.size)

        now.set(MtpFailureMemory.COOLDOWN_MS + 1)
        val outcome3 = coordinator.acquire(defaultProfile, null)
        assertEquals(false, outcome3.selection!!.selectedMtpEnabled)
        assertEquals(2, mtpAttempts.size)
    }

    @Test
    fun `runtime capability rejection skips MTP without init attempts`() {
        val calls = mutableListOf<Pair<String, Boolean>>()
        val coordinator = createCoordinator(
            probe = { false },
            factory = { backend, mtp, _ ->
                calls += backend to mtp
                EngineCandidateAttempt.Success(FakeEngineHandle(backend))
            }
        )

        val outcome = coordinator.acquire(defaultProfile, null)

        assertEquals(false, outcome.selection!!.selectedMtpEnabled)
        assertFalse(calls.any { it.second })
        assertEquals(1, outcome.fallbackEvents.count { it.reason == FallbackReason.MTP_UNSUPPORTED })
        assertFalse(outcome.fallbackEvents.any { it.reason == FallbackReason.MTP_ENGINE_INIT_FAILED })
        assertEquals(false, outcome.mtpCapabilityResult)
    }

    @Test
    fun `MTP init failure is recorded with typed backend identity and exhausts once`() {
        val memory = FakeMtpFailureMemory()
        val coordinator = createCoordinator(
            memory = memory,
            factory = { _, _, _ -> EngineCandidateAttempt.InitializationFailed(RuntimeException("boom")) }
        )

        val outcome = coordinator.acquire(defaultProfile, null)

        assertNull(outcome.selection)
        assertNotNull(outcome.failure)
        assertTrue(memory.recordedFailures.any { it.mtpEnabled && it.backendName == "GPU" })
        assertEquals(
            listOf("GPU" to true, "GPU" to false, "CPU" to false),
            memory.recordedFailures.map { it.backendName to it.mtpEnabled }
        )
        assertEquals(1, outcome.fallbackEvents.count { it.reason == FallbackReason.ALL_CANDIDATES_EXHAUSTED })
    }

    @Test
    fun `cooldown skip does not refresh the recorded failure time`() {
        val now = AtomicLong(0L)
        val storage = RecordingStorage()
        val memory = MtpFailureMemory(storage = storage, clock = { now.get() })
        val coordinator = createCoordinator(
            memory = memory,
            factory = { backend, mtp, _ ->
                if (mtp) {
                    EngineCandidateAttempt.InitializationFailed(RuntimeException("MTP init failed"))
                } else {
                    EngineCandidateAttempt.Success(FakeEngineHandle(backend))
                }
            }
        )

        coordinator.acquire(defaultProfile, null)
        assertEquals(1, storage.saves.size)
        val firstFailedAt = storage.saves.last().values.first().substringBefore('|')

        now.set(30_000L)
        val outcome = coordinator.acquire(defaultProfile, null)
        assertTrue(outcome.fallbackEvents.any { it.reason == FallbackReason.MTP_SKIPPED_RECENT_FAILURE })
        assertEquals(1, storage.saves.size)
        assertEquals(firstFailedAt, storage.saves.last().values.first().substringBefore('|'))
    }

    // ── Existing acquisition semantics ──────────────────────────────────────

    @Test
    fun `GPU plain failure falls back to CPU success`() {
        var callCount = 0
        val coordinator = createCoordinator(
            factory = { backend, _, _ ->
                callCount++
                if (backend == "GPU") EngineCandidateAttempt.InitializationFailed(RuntimeException("GPU Fail"))
                else EngineCandidateAttempt.Success(FakeEngineHandle(backend))
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
        val memory = FakeMtpFailureMemory().apply {
            shouldSkipBackendResult = FallbackReason.BACKEND_SKIPPED_RECENT_FAILURE
            skipBackendName = "GPU"
        }

        var gpuCalls = 0
        val coordinator = createCoordinator(
            memory = memory,
            factory = { backend, _, _ ->
                if (backend == "GPU") gpuCalls++
                EngineCandidateAttempt.Success(FakeEngineHandle(backend))
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
        val memory = FakeMtpFailureMemory().apply {
            shouldSkipMtpResult = FallbackReason.MTP_SKIPPED_RECENT_FAILURE
            shouldSkipBackendResult = FallbackReason.BACKEND_SKIPPED_RECENT_FAILURE
        }

        val engine = FakeEngineHandle("GPU")
        val state = loadedState(engine, backend = "GPU", mtpEnabled = false)

        var factoryCalls = 0
        val coordinator = createCoordinator(
            memory = memory,
            factory = { backend, _, _ ->
                factoryCalls++
                EngineCandidateAttempt.Success(FakeEngineHandle(backend))
            }
        )
        val outcome = coordinator.acquire(defaultProfile, state)

        assertNotNull(outcome.selection)
        assertEquals(engine, outcome.selection!!.engine)
        assertEquals(0, factoryCalls)
    }

    @Test
    fun `incompatible loaded engine is rejected when all candidates skipped`() {
        val memory = FakeMtpFailureMemory().apply {
            shouldSkipMtpResult = FallbackReason.MTP_SKIPPED_RECENT_FAILURE
            shouldSkipBackendResult = FallbackReason.BACKEND_SKIPPED_RECENT_FAILURE
        }

        val state = loadedState(
            FakeEngineHandle("GPU"),
            backend = "GPU",
            mtpEnabled = false,
            fingerprint = fakeFingerprint.copy(canonicalPath = "different_model.bin")
        )

        val coordinator = createCoordinator<FakeEngineHandle>(memory = memory)
        val outcome = coordinator.acquire(defaultProfile, state)

        assertNull(outcome.selection)
        assertNotNull(outcome.failure)
        val failure = outcome.failure as? EngineSelectionFailedException
        assertNotNull(failure)
        assertTrue(failure!!.attemptSnapshot.fallbackEvents.any { it.reason == FallbackReason.ALL_CANDIDATES_SKIPPED_RECENT_FAILURE })
    }

    @Test
    fun `request local event isolation`() {
        val memory = FakeMtpFailureMemory().apply {
            shouldSkipBackendResult = FallbackReason.BACKEND_SKIPPED_RECENT_FAILURE
            skipBackendName = "GPU"
        }

        val state = loadedState(FakeEngineHandle("CPU"), backend = "CPU", mtpEnabled = false)
        val coordinator = createCoordinator<FakeEngineHandle>(memory = memory, probe = { false })

        val outcomeA = coordinator.acquire(defaultProfile.copy(accelerator = AcceleratorMode.AUTO), state)
        assertTrue(outcomeA.fallbackEvents.any { it.reason == FallbackReason.BACKEND_SKIPPED_RECENT_FAILURE })

        val outcomeB = coordinator.acquire(defaultProfile.copy(accelerator = AcceleratorMode.CPU), state)
        assertFalse(outcomeB.fallbackEvents.any { it.reason == FallbackReason.BACKEND_SKIPPED_RECENT_FAILURE })
    }

    @Test
    fun `output only change does not recreate engine`() {
        var factoryCalls = 0
        val coordinator = createCoordinator(
            factory = { backend, _, _ ->
                factoryCalls++
                EngineCandidateAttempt.Success(FakeEngineHandle(backend))
            }
        )

        val profile1 = defaultProfile.copy(kvCacheCapacityTokens = 2048)
        val outcome1 = coordinator.acquire(profile1, null)
        val engine1 = outcome1.selection!!.engine

        val state = loadedState(
            engine1,
            backend = outcome1.selection!!.backendName,
            mtpEnabled = outcome1.selection!!.selectedMtpEnabled
        )

        val profile2 = profile1.copy()
        val outcome2 = coordinator.acquire(profile2, state)

        assertEquals(engine1, outcome2.selection!!.engine)
        assertEquals(1, factoryCalls)
    }

    @Test
    fun `kv capacity change alters engine identity`() {
        var factoryCalls = 0
        val coordinator = createCoordinator(
            factory = { backend, _, _ ->
                factoryCalls++
                EngineCandidateAttempt.Success(FakeEngineHandle("$backend-$factoryCalls"))
            }
        )

        val profile1 = defaultProfile.copy(kvCacheCapacityTokens = 2048)
        val outcome1 = coordinator.acquire(profile1, null)
        val engine1 = outcome1.selection!!.engine

        val state = loadedState(
            engine1,
            backend = outcome1.selection!!.backendName,
            mtpEnabled = outcome1.selection!!.selectedMtpEnabled
        )

        val profile2 = profile1.copy(kvCacheCapacityTokens = 4096)
        val outcome2 = coordinator.acquire(profile2, state)

        assertNotEquals(engine1, outcome2.selection!!.engine)
        assertEquals(2, factoryCalls)
    }
}
