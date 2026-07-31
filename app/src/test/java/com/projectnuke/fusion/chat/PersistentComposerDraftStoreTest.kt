package com.projectnuke.fusion.chat

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentComposerDraftStoreTest {
    @Test
    fun `rapid writes are ordered and stale write cannot replace newer snapshot`() = runTest {
        val file = Files.createTempFile("fusion-drafts", ".json").toFile()
        val store = PersistentComposerDraftStore(file, { null }, { it.absolutePath })
        val newer = mapOf(1L to ComposerDraftState(rawInput = "new", version = 2))
        val older = mapOf(1L to ComposerDraftState(rawInput = "old", version = 1))

        assertTrue(store.write(2, newer))
        assertFalse(store.write(1, older))
        assertEquals("new", store.load()[1L]?.rawInput)
        file.delete()
    }

    @Test
    fun `corrupt and oversized stores fail closed`() = runTest {
        val file = Files.createTempFile("fusion-drafts", ".json").toFile()
        file.writeText("not-json")
        val store = PersistentComposerDraftStore(file, { null }, { it.absolutePath })
        assertTrue(store.load().isEmpty())
        assertTrue(store.write(1, mapOf(1L to ComposerDraftState(rawInput = "x".repeat(3_000_000)))))
        assertEquals(32_768, store.load()[1L]?.rawInput?.length)
        file.delete()
    }

    @Test
    fun `restoration keeps only managed regular attachments and re-registers them`() = runTest {
        val file = Files.createTempFile("fusion-drafts", ".json").toFile()
        val managed = file.parentFile.resolve("managed.txt").apply { writeText("ok") }
        val registered = mutableListOf<String>()
        val store = PersistentComposerDraftStore(
            file,
            resolveManagedAttachment = { path -> if (path == managed.absolutePath) managed else null },
            registerPendingAttachment = { registered += it.absolutePath; it.absolutePath },
        )
        store.write(1, mapOf(4L to ComposerDraftState(
            rawInput = "draft",
            pendingAttachments = listOf(
                PendingAttachmentIdentity("ok", "text/plain", managed.absolutePath),
                PendingAttachmentIdentity("outside", "text/plain", "C:/outside.txt"),
            ),
            version = 1,
        )))
        val restored = store.load()[4L]!!
        assertEquals(listOf(managed.absolutePath), restored.pendingAttachments.map { it.localPath })
        assertEquals(listOf(managed.absolutePath), registered)
        managed.delete()
        file.delete()
    }
}
