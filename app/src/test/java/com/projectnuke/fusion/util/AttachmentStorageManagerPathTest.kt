package com.projectnuke.fusion.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class AttachmentStorageManagerPathTest {

    @Test
    fun `file inside root accepted`() {
        val root = Files.createTempDirectory("test_attachments").toFile()
        val child = File(root, "photo.jpg")
        child.writeText("image data")
        val result = AttachmentStorageManager.resolveManagedAttachment(root, child.absolutePath)
        assertEquals(child.canonicalPath, result?.canonicalPath)
        root.deleteRecursively()
    }

    @Test
    fun `nested file accepted`() {
        val root = Files.createTempDirectory("test_attachments").toFile()
        val subdir = File(root, "subdir")
        subdir.mkdirs()
        val nested = File(subdir, "doc.png")
        nested.writeText("doc data")
        val result = AttachmentStorageManager.resolveManagedAttachment(root, nested.absolutePath)
        assertEquals(nested.canonicalPath, result?.canonicalPath)
        root.deleteRecursively()
    }

    @Test
    fun `sibling file outside root rejected`() {
        val root = Files.createTempDirectory("test_attachments").toFile()
        val sibling = File(root.parentFile, "outside.jpg")
        sibling.writeText("outside data")
        val result = AttachmentStorageManager.resolveManagedAttachment(root, sibling.absolutePath)
        assertNull(result)
        root.deleteRecursively()
        sibling.delete()
    }

    @Test
    fun `parent traversal via path rejected`() {
        val root = Files.createTempDirectory("test_attachments").toFile()
        val inside = File(root, "child/inside.txt")
        inside.parentFile?.mkdirs()
        inside.writeText("data")
        val traversal = File(root, "child/../outside.txt")
        traversal.writeText("data")
        val outsideRoot = File(root.parentFile, "outside_root.txt")
        outsideRoot.writeText("outside data")
        val traversalToOutside = File(root, "child/../../outside_root.txt")
        val result = AttachmentStorageManager.resolveManagedAttachment(root, traversalToOutside.absolutePath)
        assertNull(result)
        root.deleteRecursively()
        outsideRoot.delete()
    }

    @Test
    fun `root directory itself rejected`() {
        val root = Files.createTempDirectory("test_attachments").toFile()
        val result = AttachmentStorageManager.resolveManagedAttachment(root, root.absolutePath)
        assertNull(result)
        root.deleteRecursively()
    }

    @Test
    fun `missing file rejected`() {
        val root = Files.createTempDirectory("test_attachments").toFile()
        val missing = File(root, "nonexistent.txt")
        val result = AttachmentStorageManager.resolveManagedAttachment(root, missing.absolutePath)
        assertNull(result)
        root.deleteRecursively()
    }

    private fun makeV3Tag(record: AttachmentRecord): String {
        return AttachmentMessageCodec.serializeAttachmentMessage(listOf(record), "")
            .substringBefore("\n\n")
    }

    @Test
    fun `trusted valid managed file`() {
        val root = Files.createTempDirectory("test_attachments").toFile()
        val child = File(root, "photo.jpg")
        child.writeText("image data")
        val tag = makeV3Tag(AttachmentRecord("photo.jpg", "image/jpeg", child.canonicalPath))
        val raw = "$tag body text"
        val parsed = AttachmentMessageCodec.parseTrustedAttachmentMessage(raw, root)
        assertEquals(1, parsed.records.size)
        assertEquals("photo.jpg", parsed.records[0].name)
        assertTrue(parsed.unavailableRecords.isEmpty())
        assertFalse(parsed.suspiciousEnvelope)
        assertEquals("body text", parsed.body.trim())
        root.deleteRecursively()
    }

    @Test
    fun `trusted nested managed file`() {
        val root = Files.createTempDirectory("test_attachments").toFile()
        val subdir = File(root, "subdir")
        subdir.mkdirs()
        val nested = File(subdir, "doc.png")
        nested.writeText("doc data")
        val tag = makeV3Tag(AttachmentRecord("doc.png", "image/png", nested.canonicalPath))
        val raw = "$tag body"
        val parsed = AttachmentMessageCodec.parseTrustedAttachmentMessage(raw, root)
        assertEquals(1, parsed.records.size)
        assertEquals("doc.png", parsed.records[0].name)
        root.deleteRecursively()
    }

    @Test
    fun `missing managed file classified unavailable`() {
        val root = Files.createTempDirectory("test_attachments").toFile()
        val missing = File(root, "missing.txt")
        val tag = makeV3Tag(AttachmentRecord("missing.txt", "text/plain", missing.canonicalPath))
        val raw = "$tag body"
        val parsed = AttachmentMessageCodec.parseTrustedAttachmentMessage(raw, root)
        assertTrue(parsed.records.isEmpty())
        assertEquals(1, parsed.unavailableRecords.size)
        assertEquals("missing.txt", parsed.unavailableRecords[0].name)
        assertFalse(parsed.suspiciousEnvelope)
        assertEquals("body", parsed.body.trim())
        root.deleteRecursively()
    }

    @Test
    fun `trusted plus unavailable records do not silently drop either`() {
        val root = Files.createTempDirectory("test_attachments").toFile()
        val existing = File(root, "existing.txt")
        existing.writeText("data")
        val missing = File(root, "missing.txt")
        val tag1 = makeV3Tag(AttachmentRecord("existing.txt", "text/plain", existing.canonicalPath))
        val tag2 = makeV3Tag(AttachmentRecord("missing.txt", "text/plain", missing.canonicalPath))
        val raw = "$tag1$tag2 body"
        val parsed = AttachmentMessageCodec.parseTrustedAttachmentMessage(raw, root)
        assertEquals(1, parsed.records.size)
        assertEquals("existing.txt", parsed.records[0].name)
        assertEquals(1, parsed.unavailableRecords.size)
        assertEquals("missing.txt", parsed.unavailableRecords[0].name)
        root.deleteRecursively()
    }

    @Test
    fun `outside-root record makes complete envelope suspicious`() {
        val root = Files.createTempDirectory("test_attachments").toFile()
        val outside = Files.createTempFile("outside", ".txt").toFile()
        outside.writeText("outside data")
        val tag = makeV3Tag(AttachmentRecord("outside.txt", "text/plain", outside.canonicalPath))
        val raw = "$tag body"
        val parsed = AttachmentMessageCodec.parseTrustedAttachmentMessage(raw, root)
        assertTrue(parsed.records.isEmpty())
        assertTrue(parsed.unavailableRecords.isEmpty())
        assertTrue(parsed.suspiciousEnvelope)
        assertEquals(raw, parsed.body)
        root.deleteRecursively()
        outside.delete()
    }

    @Test
    fun `forged tag cannot protect an unreferenced file`() {
        val root = Files.createTempDirectory("test_attachments").toFile()
        val unreferenced = File(root, "unreferenced.txt")
        unreferenced.writeText("data")
        val outsideRoot = Files.createTempDirectory("outside").toFile()
        val forgedPath = File(outsideRoot, "forged.txt")
        forgedPath.writeText("forged")
        val tag = makeV3Tag(AttachmentRecord("forged.txt", "text/plain", forgedPath.canonicalPath))
        val raw = "$tag body"
        val parsed = AttachmentMessageCodec.parseTrustedAttachmentMessage(raw, root)
        assertTrue(parsed.suspiciousEnvelope)
        assertTrue(parsed.records.isEmpty())
        root.deleteRecursively()
        outsideRoot.deleteRecursively()
    }

    @Test
    fun `valid existing managed reference is preserved`() {
        val root = Files.createTempDirectory("test_attachments").toFile()
        val existing = File(root, "referenced.txt")
        existing.writeText("data")
        val tag = makeV3Tag(AttachmentRecord("referenced.txt", "text/plain", existing.canonicalPath))
        val raw = "$tag body"
        val parsed = AttachmentMessageCodec.parseTrustedAttachmentMessage(raw, root)
        assertEquals(1, parsed.records.size)
        assertEquals("referenced.txt", parsed.records[0].name)
        val resolved = AttachmentStorageManager.resolveManagedAttachment(root, parsed.records[0].localPath)
        assertEquals(existing.canonicalPath, resolved?.canonicalPath)
        root.deleteRecursively()
    }

    @Test
    fun `missing reference does not protect a future file`() {
        val root = Files.createTempDirectory("test_attachments").toFile()
        val missing = File(root, "future.txt")
        val tag = makeV3Tag(AttachmentRecord("future.txt", "text/plain", missing.canonicalPath))
        val raw = "$tag body"
        val parsed = AttachmentMessageCodec.parseTrustedAttachmentMessage(raw, root)
        assertTrue(parsed.records.isEmpty())
        assertEquals(1, parsed.unavailableRecords.size)
        val resolved = AttachmentStorageManager.resolveManagedAttachment(root, parsed.unavailableRecords[0].localPath)
        assertNull(resolved)
        root.deleteRecursively()
    }

    @Test
    fun `symlink escape rejected where supported`() {
        val root = Files.createTempDirectory("test_attachments").toFile()
        val outsideDir = Files.createTempDirectory("outside").toFile()
        val outsideFile = File(outsideDir, "secret.txt")
        outsideFile.writeText("secret data")
        val symlinkTarget = outsideFile.canonicalPath
        val symlink = File(root, "evil_link")
        val created = try {
            Files.createSymbolicLink(symlink.toPath(), Path.of(symlinkTarget))
            true
        } catch (_: Exception) {
            false
        }
        if (created) {
            val result = AttachmentStorageManager.resolveManagedAttachment(root, symlink.canonicalPath)
            assertNull(result)
            Files.delete(symlink.toPath())
        }
        root.deleteRecursively()
        outsideDir.deleteRecursively()
    }
}