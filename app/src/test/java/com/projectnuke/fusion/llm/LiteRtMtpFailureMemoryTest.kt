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
        var saveCount = 0
        var saveSuccess = true
        override fun load(): Map<String, String> = map.toMap()
        override fun save(entries: Map<String, String>): Boolean {
            saveCount++
            if (saveSuccess) {
                map.clear()
                map.putAll(entries)
            }
            return saveSuccess
        }

        override fun clear(): Boolean {
            clearCount++
            map.clear()
            return true
        }
    }

    private fun record(
        memory: MtpFailureMemory,
        modelPath: String = "/data/models/gemma.litertlm",
        backend: String = "GPU",
        mtpEnabled: Boolean = true,
        reason: String = "MTP initialization failed, fell back to non-MTP",
        fileSize: Long = 100L,
        modifiedAt: Long = 1000L,
        mtpSupported: Boolean = true
    ) = memory.recordFailure(
        modelPath = modelPath,
        backendName = backend,
        mtpEnabled = mtpEnabled,
        validationVersion = 2,
        kvCacheCapacityTokens = 4096,
        enableVisionBackend = false,
        fileSize = fileSize,
        modifiedAt = modifiedAt,
        mtpSupported = mtpSupported,
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
        enableVisionBackend = false,
        fileSize = 100L,
        modifiedAt = 1000L,
        mtpSupported = true
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
                enableVisionBackend = false,
                fileSize = 100L,
                modifiedAt = 1000L,
                mtpSupported = true
            )
        )
    }

    @Test
    fun `failure is keyed by file identity so same-path replacement invalidates stale entries`() {
        val storage = FakeStorage()
        val first = MtpFailureMemory(storage)
        record(first, modelPath = "/data/models/gemma.litertlm")
        val reloaded = MtpFailureMemory(storage)
        assertTrue(shouldSkip(reloaded) != null)

        // Simulate model replacement at the same path (different file size / modified time).
        record(first, modelPath = "/data/models/gemma.litertlm", fileSize = 200L, modifiedAt = 2000L)
        val reloadedAfterReplacement = MtpFailureMemory(storage)
        // Old entry (size=100, modified=1000) should no longer match the new fingerprint.
        assertNull(
            reloadedAfterReplacement.shouldSkipMtp(
                modelPath = "/data/models/gemma.litertlm",
                backendName = "GPU",
                mtpEnabled = true,
                validationVersion = 2,
                kvCacheCapacityTokens = 4096,
                enableVisionBackend = false,
                fileSize = 100L,
                modifiedAt = 1000L,
                mtpSupported = true
            )
        )
        // New entry (size=200, modified=2000) should still match.
        assertTrue(
            reloadedAfterReplacement.shouldSkipMtp(
                modelPath = "/data/models/gemma.litertlm",
                backendName = "GPU",
                mtpEnabled = true,
                validationVersion = 2,
                kvCacheCapacityTokens = 4096,
                enableVisionBackend = false,
                fileSize = 200L,
                modifiedAt = 2000L,
                mtpSupported = true
            ) != null
        )
    }

    @Test
    fun `concurrent candidate failures for same fingerprint survive process recreation`() {
        val storage = FakeStorage()
        val first = MtpFailureMemory(storage)

        // Record GPU+MTP failure
        record(first, backend = "GPU", mtpEnabled = true, reason = "MTP init failed")
        // Record GPU plain failure
        record(first, backend = "GPU", mtpEnabled = false, reason = "Backend init failed")
        // Record CPU plain failure
        record(first, backend = "CPU", mtpEnabled = false, reason = "Backend init failed")

        assertEquals(3, first.persistedEntryCount())

        // Reload from storage — all three failures must survive
        val reloaded = MtpFailureMemory(storage)
        assertEquals(3, reloaded.persistedEntryCount())
        assertTrue(shouldSkip(reloaded, backend = "GPU", mtpEnabled = true) != null)
        assertTrue(shouldSkip(reloaded, backend = "GPU", mtpEnabled = false) != null)
        assertTrue(shouldSkip(reloaded, backend = "CPU", mtpEnabled = false) != null)
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
        assertNull(MtpFailureMemory(storage).shouldSkipMtp(
            modelPath = "/data/models/gemma.litertlm",
            backendName = "GPU",
            mtpEnabled = true,
            validationVersion = 2,
            kvCacheCapacityTokens = 4096,
            enableVisionBackend = false,
            fileSize = 100L,
            modifiedAt = 1000L,
            mtpSupported = true
        ))
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