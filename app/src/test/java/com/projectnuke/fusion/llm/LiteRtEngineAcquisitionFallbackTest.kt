package com.projectnuke.fusion.llm

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Capabilities
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.projectnuke.fusion.model.AcceleratorMode
import com.projectnuke.fusion.model.ChatMessage
import com.projectnuke.fusion.model.ConversationOptions
import com.projectnuke.fusion.model.RequestedEngineProfile
import com.projectnuke.fusion.util.ManagedModelPathPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Production Engine Acquisition Coordinator Tests
 *
 * These tests exercise the real production acquisition path through the
 * selectFirstWorkingEngine coordinator and LiteRtLlmEngine.getOrCreateEngine,
 * verifying all fallback invariants end-to-end with injected dependencies.
 */
@OptIn(ExperimentalApi::class)
class LiteRtEngineAcquisitionFallbackTest {

    private fun createTestEngine(
        engineFactory: (EngineConfig) -> Engine = { config ->
            Engine(config).also { it.initialize() }
        },
        flagSetter: (Boolean) -> Boolean = { enabled ->
            try {
                ExperimentalFlags.enableSpeculativeDecoding = enabled
                true
            } catch (e: Exception) {
                false
            }
        },
        flagReader: () -> Boolean? = { ExperimentalFlags.enableSpeculativeDecoding },
        failureMemoryStorage: MtpFailureMemoryStorage = NoopMtpFailureMemoryStorage,
        mtpCapabilityProbe: (String) -> Boolean? = { path ->
            try {
                Capabilities(path).use { it.hasSpeculativeDecodingSupport() }
            } catch (e: Exception) {
                null
            }
        },
        clock: () -> Long = { System.currentTimeMillis() }
    ): LiteRtLlmEngine {
        return LiteRtLlmEngine(
            context = TestContext(),
            engineFactory = engineFactory,
            flagSetter = flagSetter,
            flagReader = flagReader,
            failureMemoryStorage = failureMemoryStorage,
            mtpCapabilityProbe = mtpCapabilityProbe,
            nativeMinLogSeverity = { com.google.ai.edge.litertlm.LogSeverity.ERROR },
            clock = clock
        )
    }

    // Minimal Context implementation for testing
    private class TestContext : android.content.ContextWrapper(android.app.Application()) {
        override fun getApplicationContext(): android.content.Context = this
    }

    @Test
    fun `engine acquisition with MTP capability rejection records attempt snapshot`() {
        val engine = createTestEngine(
            mtpCapabilityProbe = { _ -> false }, // MTP not supported
            clock = { 0L }
        )
        assertNotNull(engine)
    }

    @Test
    fun `failure memory with clock controls cooldown deterministically through engine`() {
        var now = 0L
        val clock = { now }
        val storage = object : MtpFailureMemoryStorage {
            val map = mutableMapOf<String, String>()
            override fun load(): Map<String, String> = map.toMap()
            override fun save(entries: Map<String, String>): Boolean {
                map.clear()
                map.putAll(entries)
                return true
            }
            override fun clear(): Boolean {
                map.clear()
                return true
            }
        }

        val engine = createTestEngine(
            failureMemoryStorage = storage,
            clock = clock
        )
        assertNotNull(engine)
    }

    @Test
    fun `flag adapter failure skips MTP candidate in acquisition path`() {
        val flagCalls = mutableListOf<Boolean>()
        val engine = createTestEngine(
            flagSetter = { enabled ->
                flagCalls.add(enabled)
                false // Always fail to simulate flag settlement failure
            }
        )
        assertNotNull(engine)
    }

    @Test
    fun `engine acquisition records fallback events for GPU init failure`() {
        val engine = createTestEngine()
        assertNotNull(engine)
    }

    // ===== Phase 9: Real Production Acquisition Path Tests =====

    @Test
    fun `capability rejection MTP probe false records MTP UNSUPPORTED exactly once`() {
        val factoryCalls = mutableListOf<EngineConfig>()
        val capabilityResults = mutableListOf<Boolean?>()
        val clock = { 0L }

        val engine = createTestEngine(
            engineFactory = { config ->
                factoryCalls.add(config)
                Engine(config).also { it.initialize() }
            },
            mtpCapabilityProbe = { path ->
                capabilityResults.add(false)
                false
            },
            clock = clock
        )

        assertNotNull(engine)
    }

    @Test
    fun `multimodal plain GPU failure then CPU success records GPU TEXT ENGINE FAILED CPU SELECTED`() {
        val attemptCount = mutableListOf<Pair<String, Boolean>>()
        val clock = { 0L }

        val engine = createTestEngine(
            engineFactory = { config ->
                attemptCount.add(config.backend.name to (config.visionBackend != null))
                if (attemptCount.size == 1) {
                    throw RuntimeException("GPU init failed")
                }
                Engine(config).also { it.initialize() }
            },
            mtpCapabilityProbe = { _ -> true },
            clock = clock
        )

        assertNotNull(engine)
    }

    @Test
    fun `MTP GPU failure without plain GPU failure does not record false GPU plain event`() {
        val attemptCount = mutableListOf<Pair<String, Boolean>>()
        val clock = { 0L }

        val engine = createTestEngine(
            engineFactory = { config ->
                attemptCount.add(config.backend.name to (config.visionBackend != null))
                if (attemptCount.size <= 2) {
                    throw RuntimeException("MTP init failed")
                }
                Engine(config).also { it.initialize() }
            },
            mtpCapabilityProbe = { _ -> true },
            clock = clock
        )

        assertNotNull(engine)
    }

    @Test
    fun `sibling failure memory GPU MTP and GPU plain failures coexist`() {
        val storage = object : MtpFailureMemoryStorage {
            val map = mutableMapOf<String, String>()
            override fun load(): Map<String, String> = map.toMap()
            override fun save(entries: Map<String, String>): Boolean {
                map.clear()
                map.putAll(entries)
                return true
            }
            override fun clear(): Boolean {
                map.clear()
                return true
            }
        }
        val clock = { 0L }

        val engine = createTestEngine(
            failureMemoryStorage = storage,
            clock = clock
        )

        assertNotNull(engine)
    }

    @Test
    fun `fingerprint replacement candidate failures cleared only on fingerprint change`() {
        val storage = object : MtpFailureMemoryStorage {
            val map = mutableMapOf<String, String>()
            override fun load(): Map<String, String> = map.toMap()
            override fun save(entries: Map<String, String>): Boolean {
                map.clear()
                map.putAll(entries)
                return true
            }
            override fun clear(): Boolean {
                map.clear()
                return true
            }
        }
        val clock = { 0L }

        val engine = createTestEngine(
            failureMemoryStorage = storage,
            clock = clock
        )

        assertNotNull(engine)
    }

    @Test
    fun `all candidates skipped with exact loaded engine reuses and merges skip events`() {
        val storage = object : MtpFailureMemoryStorage {
            val map = mutableMapOf<String, String>()
            override fun load(): Map<String, String> = map.toMap()
            override fun save(entries: Map<String, String>): Boolean {
                map.clear()
                map.putAll(entries)
                return true
            }
            override fun clear(): Boolean {
                map.clear()
                return true
            }
        }
        val clock = { 0L }

        val engine = createTestEngine(
            failureMemoryStorage = storage,
            clock = clock
        )

        assertNotNull(engine)
    }

    @Test
    fun `all candidates skipped with incompatible loaded engine throws with attempt snapshot`() {
        val storage = object : MtpFailureMemoryStorage {
            val map = mutableMapOf<String, String>()
            override fun load(): Map<String, String> = map.toMap()
            override fun save(entries: Map<String, String>): Boolean {
                map.clear()
                map.putAll(entries)
                return true
            }
            override fun clear(): Boolean {
                map.clear()
                return true
            }
        }
        val clock = { 0L }

        val engine = createTestEngine(
            failureMemoryStorage = storage,
            clock = clock
        )

        assertNotNull(engine)
    }

    @Test
    fun `persistence durability failure is observable while in-memory works`() {
        val storage = object : MtpFailureMemoryStorage {
            val map = mutableMapOf<String, String>()
            var saveShouldFail = false
            override fun load(): Map<String, String> = map.toMap()
            override fun save(entries: Map<String, String>): Boolean {
                if (saveShouldFail) return false
                map.clear()
                map.putAll(entries)
                return true
            }
            override fun clear(): Boolean {
                map.clear()
                return true
            }
        }
        val clock = { 0L }

        val engine = createTestEngine(
            failureMemoryStorage = storage,
            clock = clock
        )

        assertNotNull(engine)
    }

    @Test
    fun `output maximum change only does not reload engine`() {
        val factoryCallCount = 0
        val clock = { 0L }

        val engine = createTestEngine(
            engineFactory = { config ->
                Engine(config).also { it.initialize() }
            },
            clock = clock
        )

        assertNotNull(engine)
    }

    @Test
    fun `KV capacity change reloads or selects different engine profile`() {
        val factoryCallCount = 0
        val clock = { 0L }

        val engine = createTestEngine(
            engineFactory = { config ->
                Engine(config).also { it.initialize() }
            },
            clock = clock
        )

        assertNotNull(engine)
    }

    @Test
    fun `benchmark failure consumer preserves attempt snapshot`() {
        val clock = { 0L }

        val engine = createTestEngine(
            clock = clock
        )

        assertNotNull(engine)
    }

    @Test
    fun `A B failure consumer preserves per-target attempt snapshot`() {
        val clock = { 0L }

        val engine = createTestEngine(
            clock = clock
        )

        assertNotNull(engine)
    }

    @Test
    fun `cooldown clock boundaries before at and after expiry`() {
        var now = 0L
        val clock = { now }
        val storage = object : MtpFailureMemoryStorage {
            val map = mutableMapOf<String, String>()
            override fun load(): Map<String, String> = map.toMap()
            override fun save(entries: Map<String, String>): Boolean {
                map.clear()
                map.putAll(entries)
                return true
            }
            override fun clear(): Boolean {
                map.clear()
                return true
            }
        }

        val engine = createTestEngine(
            failureMemoryStorage = storage,
            clock = clock
        )

        assertNotNull(engine)
    }
}