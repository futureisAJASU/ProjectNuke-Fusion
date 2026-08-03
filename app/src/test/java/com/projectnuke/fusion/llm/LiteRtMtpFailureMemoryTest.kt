package com.projectnuke.fusion.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtMtpFailureMemoryTest {

    private class FakeStorage : MtpFailureMemoryStorage {
        val map = mutableMapOf<String, String>()
        var clearCount = 0
        override fun load(): Map<String, String> = map.toMap()
        override fun save(entries: Map<String, String>) {
            map.clear()
            map.putAll(entries)
        }

        override fun clear() {
            clearCount++
            map.clear()
        }
    }

    private fun record(
        memory: MtpFailureMemory,
        modelPath: String = "/data/models/gemma.litertlm",
        backend: String = "GPU",
        mtpEnabled: Boolean = true,
        reason: String = "MTP initialization failed, fell back to non-MTP"
    ) = memory.recordFailure(
        modelPath = modelPath,
        backendName = backend,
        mtpEnabled = mtpEnabled,
        validationVersion = 2,
        kvCacheCapacityTokens = 4096,
        enableVisionBackend = false,
        fallbackReason = reason
    )

    private fun shouldSkip(
        memory: MtpFailureMemory,
        modelPath: String = "/data/models/gemma.litertlm",
        backend: String = "GPU",
        mtpEnabled: Boolean = true
    ) = memory.shouldSkipMtp(
        modelPath = modelPath,
        backendName = backend,
        mtpEnabled = mtpEnabled,
        validationVersion = 2,
        kvCacheCapacityTokens = 4096,
        enableVisionBackend = false
    )

    @Test
    fun `recorded failure is skipped during cooldown`() {
        val memory = MtpFailureMemory()
        record(memory)
        assertTrue(shouldSkip(memory) != null)
    }

    @Test
    fun `failure memory survives a new instance sharing the same storage`() {
        val storage = FakeStorage()
        val first = MtpFailureMemory(storage)
        record(first, reason = "MTP flag application failed")
        assertEquals(1, first.persistedEntryCount())

        val reloaded = MtpFailureMemory(storage)
        assertEquals("MTP flag application failed", shouldSkip(reloaded))
    }

    @Test
    fun `failure is keyed by exact backend and MTP state`() {
        val storage = FakeStorage()
        val first = MtpFailureMemory(storage)
        record(first, backend = "GPU")
        val reloaded = MtpFailureMemory(storage)

        // A GPU+MTP failure must never poison a CPU request or a plain GPU request.
        assertNull(shouldSkip(reloaded, backend = "CPU"))
        assertNull(shouldSkip(reloaded, backend = "GPU", mtpEnabled = false))
        assertNull(shouldSkip(reloaded, modelPath = "/data/models/other.litertlm"))
        // Different KV capacity is a different engine configuration.
        assertNull(
            reloaded.shouldSkipMtp(
                modelPath = "/data/models/gemma.litertlm",
                backendName = "GPU",
                mtpEnabled = true,
                validationVersion = 2,
                kvCacheCapacityTokens = 8192,
                enableVisionBackend = false
            )
        )
    }

    @Test
    fun `clearForModel removes only that model and persists the removal`() {
        val storage = FakeStorage()
        val memory = MtpFailureMemory(storage)
        record(memory, modelPath = "/a/one.litertlm")
        record(memory, modelPath = "/b/two.litertlm")
        assertEquals(2, memory.persistedEntryCount())

        memory.clearForModel("/a/one.litertlm")
        val reloaded = MtpFailureMemory(storage)
        assertNull(shouldSkip(reloaded, modelPath = "/a/one.litertlm"))
        assertTrue(shouldSkip(reloaded, modelPath = "/b/two.litertlm") != null)
    }

    @Test
    fun `clearAll wipes the storage`() {
        val storage = FakeStorage()
        val memory = MtpFailureMemory(storage)
        record(memory)
        memory.clearAll()
        assertEquals(0, memory.persistedEntryCount())
        assertEquals(1, storage.clearCount)
        assertNull(MtpFailureMemory(storage).shouldSkipMtp("/data/models/gemma.litertlm", "GPU", true, 2, 4096, false))
    }

    @Test
    fun `corrupt persisted entries are ignored`() {
        val storage = FakeStorage()
        storage.map["not-enough-fields"] = "12345|reason"
        storage.map["a\u001fb\u001ftrue\u001f1\u001fc\u001f2"] = "not-a-timestamp|reason"
        storage.map["a\u001fb\u001fwat\u001f1\u001fc\u001f2"] = "12345|reason"
        storage.map["a\u001fb\u001ftrue\u001f1\u001fc\u001f2"] = "12345" // valid key, value has one field only
        val memory = MtpFailureMemory(storage)
        assertNull(shouldSkip(memory, modelPath = "x", backend = "b"))
    }
}
