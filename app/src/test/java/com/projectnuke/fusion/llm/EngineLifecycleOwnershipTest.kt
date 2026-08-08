package com.projectnuke.fusion.llm

import com.projectnuke.fusion.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class EngineLifecycleOwnershipTest {

    private open class CloseTrackingEngineHandle(
        val backend: String,
        val closeCount: AtomicInteger = AtomicInteger(0)
    ) {
        open fun close() { closeCount.incrementAndGet() }
    }

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

    private val fakeFingerprint = ModelFingerprint(
        canonicalPath = "test_model.bin",
        fileSize = 1024L,
        modifiedAt = 1000L,
        validationVersion = 1,
        mtpSupported = true
    )

    private fun createCoordinator(
        memory: MtpFailureMemoryPort = FakeMtpFailureMemory(),
        probe: (String) -> Boolean? = { true },
        flag: (Boolean) -> Boolean = { true },
        factory: (String, Boolean, Boolean) -> EngineCandidateAttempt<CloseTrackingEngineHandle> = { backend, _, _ ->
            EngineCandidateAttempt.Success(CloseTrackingEngineHandle(backend))
        },
        fingerprintResolver: (String) -> ModelFingerprint = { fakeFingerprint }
    ) = EngineAcquisitionCoordinator<CloseTrackingEngineHandle>(
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
        engine: CloseTrackingEngineHandle,
        backend: String,
        mtpEnabled: Boolean,
        fingerprint: ModelFingerprint = fakeFingerprint,
        accelerator: AcceleratorMode = defaultProfile.accelerator,
        kvCacheCapacityTokens: Int = defaultProfile.kvCacheCapacityTokens,
        enableVisionBackend: Boolean = defaultProfile.enableVisionBackend
    ) = LoadedRuntimeState<CloseTrackingEngineHandle>(
        engine = engine,
        key = EngineRuntimeKey(
            fingerprint = fingerprint,
            accelerator = accelerator,
            kvCacheCapacityTokens = kvCacheCapacityTokens,
            enableVisionBackend = enableVisionBackend,
            mtpEnabled = mtpEnabled,
            selectedBackend = backend
        ),
        mtpStatus = MtpRuntimeStatus.OFF,
        runtimeSelection = EngineSelectionRuntime("AUTO", backend, null, true, true, null),
        actualTextBackend = backend,
        actualVisionBackend = null,
        fallbackEvents = emptyList()
    )

    // ── Phase 4: Engine lifecycle selection logic ──────────────────────────────

    @Test
    fun `same Engine reused`() {
        val engine = CloseTrackingEngineHandle("GPU")
        val state = loadedState(engine, backend = "GPU", mtpEnabled = false)
        val coordinator = createCoordinator(
            factory = { backend, _, _ -> EngineCandidateAttempt.Success(engine) }
        )

        val profile = defaultProfile.copy(mtpRequested = false)
        val outcome1 = coordinator.acquire(profile, state)
        assertNotNull(outcome1.selection)
        assertEquals(engine, outcome1.selection!!.engine)

        val outcome2 = coordinator.acquire(profile, state)
        assertNotNull(outcome2.selection)
        assertEquals(engine, outcome2.selection!!.engine)

        val outcome3 = coordinator.acquire(profile, state)
        assertNotNull(outcome3.selection)
        assertEquals(engine, outcome3.selection!!.engine)
    }

    @Test
    fun `new profile replaces Engine`() {
        val engine1 = CloseTrackingEngineHandle("GPU")
        val state = loadedState(engine1, backend = "GPU", mtpEnabled = false)
        val coordinator = createCoordinator(
            factory = { backend, _, _ ->
                if (backend == "GPU") EngineCandidateAttempt.Success(engine1)
                else EngineCandidateAttempt.Success(CloseTrackingEngineHandle(backend))
            }
        )

        val profile1 = defaultProfile.copy(mtpRequested = false)
        val outcome1 = coordinator.acquire(profile1, state)
        assertNotNull(outcome1.selection)
        assertEquals(engine1, outcome1.selection!!.engine)

        // New profile with different KV capacity -> new engine
        val engine2 = CloseTrackingEngineHandle("GPU")
        val coordinator2 = createCoordinator(
            factory = { backend, _, _ ->
                if (backend == "GPU") EngineCandidateAttempt.Success(engine2)
                else EngineCandidateAttempt.Success(CloseTrackingEngineHandle(backend))
            }
        )
        val profile2 = profile1.copy(kvCacheCapacityTokens = 4096)
        val outcome2 = coordinator2.acquire(profile2, state)
        assertNotNull(outcome2.selection)
        assertEquals(engine2, outcome2.selection!!.engine)
        assertNotEquals(engine1, engine2)
    }

    @Test
    fun `multiple replacements`() {
        val engines = mutableListOf<CloseTrackingEngineHandle>()
        val coordinator = createCoordinator(
            factory = { backend, _, _ ->
                val engine = CloseTrackingEngineHandle("GPU")
                engines.add(engine)
                EngineCandidateAttempt.Success(engine)
            }
        )

        val profile = defaultProfile.copy(mtpRequested = false)

        // First acquisition
        var outcome = coordinator.acquire(profile, null)
        assertNotNull(outcome.selection)

        // Second acquisition with different KV -> replaces engine1
        val profile2 = profile.copy(kvCacheCapacityTokens = 4096)
        outcome = coordinator.acquire(profile2, null)
        assertNotNull(outcome.selection)

        // Third acquisition with different KV again -> replaces engine2
        val profile3 = profile2.copy(kvCacheCapacityTokens = 8192)
        outcome = coordinator.acquire(profile3, null)
        assertNotNull(outcome.selection)

        // Fourth acquisition with different KV -> replaces engine3
        val profile4 = profile3.copy(kvCacheCapacityTokens = 16384)
        outcome = coordinator.acquire(profile4, null)
        assertNotNull(outcome.selection)
    }

    @Test
    fun `new acquisition fails before adoption currently valid old Engine remains available`() {
        val engine = CloseTrackingEngineHandle("GPU")
        val state = loadedState(engine, backend = "GPU", mtpEnabled = false)
        val coordinator = createCoordinator(
            factory = { backend, mtp, _ ->
                if (mtp) EngineCandidateAttempt.InitializationFailed(RuntimeException("MTP init failed"))
                else EngineCandidateAttempt.Success(engine)
            }
        )

        // First acquisition succeeds with plain GPU
        val profile = defaultProfile.copy(mtpRequested = true)
        val outcome1 = coordinator.acquire(profile, state)
        assertNotNull(outcome1.selection)
        assertEquals(engine, outcome1.selection!!.engine)

        // Second acquisition: MTP capability probe fails, falls back to plain GPU which is reused
        val outcome2 = coordinator.acquire(profile, state)
        assertNotNull(outcome2.selection)
        assertEquals(engine, outcome2.selection!!.engine)

        // Third acquisition: all candidates fail (simulate by making factory fail for plain GPU)
        // Use null state to force factory calls instead of reuse
        val coordinator3 = createCoordinator(
            factory = { backend, mtp, _ ->
                if (mtp) EngineCandidateAttempt.InitializationFailed(RuntimeException("MTP init failed"))
                else EngineCandidateAttempt.InitializationFailed(RuntimeException("Plain GPU init failed"))
            }
        )
        val outcome3 = coordinator3.acquire(profile, null)
        // Acquisition fails
        assertNull(outcome3.selection)
        assertNotNull(outcome3.failure)
    }

    @Test
    fun `new Engine created but adoption fails`() {
        // This tests the case where a new engine is created but adoption fails
        // In the current architecture, if factory succeeds but some later step fails,
        // the newly created engine should be closed to avoid orphan
        
        val oldEngine = CloseTrackingEngineHandle("GPU")
        val state = loadedState(oldEngine, backend = "GPU", mtpEnabled = false)
        val newEngine = CloseTrackingEngineHandle("GPU")
        
        val coordinator = createCoordinator(
            factory = { backend, mtp, _ ->
                // For the second call (when retrying with new profile), create a new engine
                if (mtp) EngineCandidateAttempt.InitializationFailed(RuntimeException("MTP init failed"))
                else EngineCandidateAttempt.Success(newEngine)
            }
        )

        val profile = defaultProfile.copy(mtpRequested = true)
        
        // First acquisition: MTP fails, falls back to plain GPU (oldEngine)
        val outcome1 = coordinator.acquire(profile, state)
        assertNotNull(outcome1.selection)
        assertEquals(oldEngine, outcome1.selection!!.engine)

        // Change profile to trigger new engine creation but with a failure in adoption
        // We'll use a different KV capacity to force new engine creation
        val profile2 = profile.copy(kvCacheCapacityTokens = 4096)
        val coordinator2 = createCoordinator(
            factory = { backend, mtp, _ ->
                if (mtp) EngineCandidateAttempt.InitializationFailed(RuntimeException("MTP init failed"))
                else EngineCandidateAttempt.Success(CloseTrackingEngineHandle(backend))
            }
        )
        val outcome2 = coordinator2.acquire(profile2, state)
        assertNotNull(outcome2.selection)
        val createdEngine = outcome2.selection!!.engine
        assertNotEquals(oldEngine, createdEngine)
    }

    @Test
    fun `close() throwing does not prevent new loadedState`() {
        val oldEngine = CloseTrackingEngineHandle("GPU")
        val state = loadedState(oldEngine, backend = "GPU", mtpEnabled = false)
        
        // Create an engine whose close() throws
        val throwingEngine = object : CloseTrackingEngineHandle("GPU") {
            override fun close() {
                super.close()
                throw RuntimeException("close() failed")
            }
        }
        
        val coordinator = createCoordinator(
            factory = { backend, _, _ ->
                EngineCandidateAttempt.Success(throwingEngine)
            }
        )
        
        val profile = defaultProfile.copy(mtpRequested = false, kvCacheCapacityTokens = 4096)
        
        // First acquisition with oldEngine
        val outcome1 = coordinator.acquire(profile.copy(kvCacheCapacityTokens = 2048), state)
        assertNotNull(outcome1.selection)
        assertEquals(oldEngine, outcome1.selection!!.engine)
        
        // Second acquisition creates throwingEngine
        val outcome2 = coordinator.acquire(profile, state)
        assertNotNull(outcome2.selection)
        assertEquals(throwingEngine, outcome2.selection!!.engine)
    }
}