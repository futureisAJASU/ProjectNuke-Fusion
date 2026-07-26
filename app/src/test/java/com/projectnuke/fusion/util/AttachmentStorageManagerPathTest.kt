package com.projectnuke.fusion.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class AttachmentStorageManagerPathTest {

    private fun resolveAttachment(attachmentRoot: File, path: String): File? {
        if (path.isBlank()) return null
        val targetCanonical = runCatching { File(path).canonicalPath }.getOrNull() ?: return null
        val dirCanonical = runCatching { attachmentRoot.canonicalPath }.getOrNull() ?: return null
        if (targetCanonical == dirCanonical) return null
        val prefix = "$dirCanonical${File.separator}"
        if (!targetCanonical.startsWith(prefix)) return null
        val targetFile = File(targetCanonical)
        if (!targetFile.exists()) return null
        if (!targetFile.isFile) return null
        return targetFile
    }

    @Test
    fun `file inside root accepted`() {
        val root = Files.createTempDirectory("test_attachments").toFile()
        val child = File(root, "photo.jpg")
        child.writeText("image data")
        val result = resolveAttachment(root, child.absolutePath)
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
        val result = resolveAttachment(root, nested.absolutePath)
        assertEquals(nested.canonicalPath, result?.canonicalPath)
        root.deleteRecursively()
    }

    @Test
    fun `sibling file outside root rejected`() {
        val root = Files.createTempDirectory("test_attachments").toFile()
        val sibling = File(root.parentFile, "outside.jpg")
        sibling.writeText("outside data")
        val result = resolveAttachment(root, sibling.absolutePath)
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
        val result = resolveAttachment(root, traversalToOutside.absolutePath)
        assertNull(result)
        root.deleteRecursively()
        outsideRoot.delete()
    }

    @Test
    fun `root directory itself rejected`() {
        val root = Files.createTempDirectory("test_attachments").toFile()
        val result = resolveAttachment(root, root.absolutePath)
        assertNull(result)
        root.deleteRecursively()
    }

    @Test
    fun `missing file rejected`() {
        val root = Files.createTempDirectory("test_attachments").toFile()
        val missing = File(root, "nonexistent.txt")
        val result = resolveAttachment(root, missing.absolutePath)
        assertNull(result)
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
            val result = resolveAttachment(root, symlink.canonicalPath)
            assertNull(result)
            Files.delete(symlink.toPath())
        }
        root.deleteRecursively()
        outsideDir.deleteRecursively()
    }
}