package com.projectnuke.fusion.util

import android.content.Context
import com.projectnuke.fusion.data.ChatDao
import java.io.File

data class AttachmentStorageStats(
    val totalBytes: Long,
    val totalFiles: Int,
    val referencedFiles: Int,
    val unreferencedFiles: Int
)

data class CleanupResult(
    val deletedFiles: Int
)

object AttachmentStorageManager {
    fun getAttachmentDirectory(context: Context): File {
        return (context.getExternalFilesDir("attachments") ?: File(context.filesDir, "attachments")).apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun calculateAttachmentStorageStats(
        context: Context,
        dao: ChatDao
    ): AttachmentStorageStats {
        val dir = getAttachmentDirectory(context)
        val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
        val allMessageContents = dao.getAllMessageContents()
        val referencedPaths = extractReferencedAttachmentPaths(allMessageContents)
        val referencedCanonical = referencedPaths.mapNotNull { safeCanonicalPath(it) }.toSet()

        val referencedCount = files.count { file ->
            safeCanonicalPath(file.absolutePath)?.let { it in referencedCanonical } == true
        }

        return AttachmentStorageStats(
            totalBytes = files.sumOf { it.length() },
            totalFiles = files.size,
            referencedFiles = referencedCount,
            unreferencedFiles = (files.size - referencedCount).coerceAtLeast(0)
        )
    }

    suspend fun cleanupUnreferencedAttachments(
        context: Context,
        dao: ChatDao
    ): CleanupResult {
        val dir = getAttachmentDirectory(context)
        val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
        val allMessageContents = dao.getAllMessageContents()
        val referencedPaths = extractReferencedAttachmentPaths(allMessageContents)
        val referencedCanonical = referencedPaths.mapNotNull { safeCanonicalPath(it) }.toSet()

        var deleted = 0
        files.forEach { file ->
            val canonical = safeCanonicalPath(file.absolutePath) ?: return@forEach
            if (canonical !in referencedCanonical) {
                if (file.delete()) {
                    deleted += 1
                }
            }
        }

        return CleanupResult(deletedFiles = deleted)
    }

    private fun extractReferencedAttachmentPaths(contents: List<String>): Set<String> {
        val v2Regex = Regex("""<fusion_attachment_v2>(.*?)\|(.*?)\|(.*?)</fusion_attachment_v2>""")
        val v1Regex = Regex("""(?s)<fusion_attachment>\s*name=(.*?)\nmime=(.*?)\npath=(.*?)\s*</fusion_attachment>""")
        val result = mutableSetOf<String>()

        contents.forEach { raw ->
            v2Regex.findAll(raw).forEach { match ->
                val path = match.groupValues.getOrNull(3)?.trim().orEmpty()
                if (path.isNotBlank()) result.add(path)
            }
            v1Regex.findAll(raw).forEach { match ->
                val path = match.groupValues.getOrNull(3)?.trim().orEmpty()
                if (path.isNotBlank()) result.add(path)
            }
        }

        return result
    }

    private fun safeCanonicalPath(path: String): String? {
        return runCatching { File(path).canonicalPath }.getOrNull()
    }
}
