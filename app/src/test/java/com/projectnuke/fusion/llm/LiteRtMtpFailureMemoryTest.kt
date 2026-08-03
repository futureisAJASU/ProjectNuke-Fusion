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
        reason: String = "MTP initialization failed, fell back to non-MTP"
    ) = memory.recordFailure(
        modelPath = modelPath,
        actualBackend = backend,
        validationVersion = 2,
        accelerator = "AUTO",
        kvCacheCapacityTokens = 4096,
        enableVisionBackend = false,
        fallbackReason = reason
    )

    @Test
    fun `recorded failure is skipped during cooldown and expired after`() {
        val memory = MtpFailureMemory()
        record(memory)
        assertTrue(memory.shouldSkipMtp("/data/models/gemma.litertlm", "GPU", 2, "AUTO", 4096, false) != null)
    }

    @Test
    fun `failure memory survives a new instance sharing the same storage`() {
        val storage = FakeStorage()
        val first = MtpFailureMemory(storage)
        record(first, reason = "MTP flag application failed")
        assertEquals(1, first.persistedEntryCount())

        val reloaded = MtpFailureMemory(storage)
        assertEquals(
            "MTP flag application failed",
            reloaded.shouldSkipMtp("/data/models/gemma.litertlm", "GPU", 2, "AUTO", 4096, false)
        )
    }

    @Test
    fun `different backend or capacity does not hit the persisted failure`() {
        val storage = FakeStorage()
        val first = MtpFailureMemory(storage)
        record(first, backend = "GPU")
        val reloaded = MtpFailureMemory(storage)

        assertNull(reloaded.shouldSkipMtp("/data/models/gemma.litertlm", "CPU", 2, "AUTO", 4096, false))
        assertNull(reloaded.shouldSkipMtp("/data/models/gemma.litertlm", "GPU", 2, "AUTO", 8192, false))
        assertNull(reloaded.shouldSkipMtp("/data/models/other.litertlm", "GPU", 2, "AUTO", 4096, false))
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
        assertNull(reloaded.shouldSkipMtp("/a/one.litertlm", "GPU", 2, "AUTO", 4096, false))
        assertTrue(reloaded.shouldSkipMtp("/b/two.litertlm", "GPU", 2, "AUTO", 4096, false) != null)
    }

    @Test
    fun `clearAll wipes the storage`() {
        val storage = FakeStorage()
        val memory = MtpFailureMemory(storage)
        record(memory)
        memory.clearAll()
        assertEquals(0, memory.persistedEntryCount())
        assertEquals(1, storage.clearCount)
        assertNull(
            MtpFailureMemory(storage).shouldSkipMtp("/data/models/gemma.litertlm", "GPU", 2, "AUTO", 4096, false)
        )
    }

    @Test
    fun `corrupt persisted entries are ignored`() {
        val storage = FakeStorage()
        storage.map["not-enough-fields"] = "12345|reason"
        storage.map["a\u001fb\u001f1\u001fc\u001f2\u001ftrue"] = "not-a-timestamp|reason"
        storage.map["a\u001fb\u001fwat\u001fc\u001f2\u001ftrue"] = "12345|reason"
        storage.map["a\u001fb\u001f1\u001fc\u001f2\u001ftrue"] = "12345" // valid key, value has one field only
        val memory = MtpFailureMemory(storage)
        assertFalse(memory.shouldSkipMtp("x", "GPU", 2, "AUTO", 4096, false) != null)
    }
}
