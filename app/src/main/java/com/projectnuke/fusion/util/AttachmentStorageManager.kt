package com.projectnuke.fusion.util

import android.content.Context
import com.projectnuke.fusion.data.ChatDao
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private const val recentFileGracePeriodMs = 5 * 60 * 1000L
    private val pendingAttachmentPaths = ConcurrentHashMap.newKeySet<String>()

    fun getAttachmentDirectory(context: Context): File {
        return (context.getExternalFilesDir("attachments") ?: File(context.filesDir, "attachments")).apply {
            if (!exists()) mkdirs()
        }
    }

    fun registerPendingAttachment(file: File): String? {
        val canonical = safeCanonicalPath(file.absolutePath) ?: return null
        pendingAttachmentPaths.add(canonical)
        return canonical
    }

    fun unregisterPendingAttachment(path: String?) {
        if (path.isNullOrBlank()) return
        safeCanonicalPath(path)?.let { pendingAttachmentPaths.remove(it) }
    }

    suspend fun deletePendingAttachmentFile(
        context: Context,
        path: String?
    ): Boolean = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext false
        val attachmentDirCanonical = safeCanonicalPath(getAttachmentDirectory(context).absolutePath)
            ?: return@withContext false
        val targetFile = File(path)
        val targetCanonical = safeCanonicalPath(targetFile.absolutePath) ?: return@withContext false
        if (!isInAttachmentDirectory(targetCanonical, attachmentDirCanonical)) {
            return@withContext false
        }
        unregisterPendingAttachment(targetCanonical)
        targetFile.exists() && targetFile.delete()
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
        return withContext(Dispatchers.IO) {
            val dir = getAttachmentDirectory(context)
            val dirCanonical = safeCanonicalPath(dir.absolutePath)
                ?: return@withContext CleanupResult(deletedFiles = 0)
            val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
            val allMessageContents = dao.getAllMessageContents()
            val referencedPaths = extractReferencedAttachmentPaths(allMessageContents)
            val referencedCanonical = referencedPaths.mapNotNull { safeCanonicalPath(it) }.toSet()
            val pendingCanonical = pendingAttachmentPaths.toSet()
            val now = System.currentTimeMillis()

            var deleted = 0
            files.forEach { file ->
                val canonical = safeCanonicalPath(file.absolutePath) ?: return@forEach
                if (!isInAttachmentDirectory(canonical, dirCanonical)) return@forEach
                if (canonical in referencedCanonical) return@forEach
                if (canonical in pendingCanonical) return@forEach
                if ((now - file.lastModified()) <= recentFileGracePeriodMs) return@forEach
                if (file.delete()) {
                    deleted += 1
                }
            }

            CleanupResult(deletedFiles = deleted)
        }
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

    private fun isInAttachmentDirectory(path: String, attachmentDirCanonical: String): Boolean {
        return path == attachmentDirCanonical || path.startsWith("$attachmentDirCanonical${File.separator}")
    }
}
