package com.projectnuke.fusion.util

import android.content.Context
import com.projectnuke.fusion.data.ChatDao
import com.projectnuke.fusion.util.AttachmentMessageCodec
import com.projectnuke.fusion.util.ParsedAttachments
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

fun resolveManagedAttachment(context: Context, path: String): File? {
        if (path.isBlank()) return null
        val targetCanonical = safeCanonicalPath(path) ?: return null
        val dirCanonical = safeCanonicalPath(getAttachmentDirectory(context).absolutePath)
            ?: return null
        if (targetCanonical == dirCanonical) return null
        val prefix = "$dirCanonical${File.separator}"
        if (!targetCanonical.startsWith(prefix)) return null
        val targetFile = File(targetCanonical)
        if (!targetFile.exists()) return null
        if (!targetFile.isFile) return null
        return targetFile
    }

    suspend fun calculateAttachmentStorageStats(
        context: Context,
        dao: ChatDao
    ): AttachmentStorageStats {
        val dir = getAttachmentDirectory(context)
        val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
        val allMessageContents = dao.getAllMessageContents()
        val referencedPaths = extractReferencedAttachmentPaths(context, allMessageContents)
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
            val referencedPaths = extractReferencedAttachmentPaths(context, allMessageContents)
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

    private fun extractReferencedAttachmentPaths(
        context: Context,
        contents: List<String>
    ): Set<String> {
        val dirCanonical = safeCanonicalPath(getAttachmentDirectory(context).absolutePath) ?: return emptySet()
        val result = mutableSetOf<String>()
        contents.forEach { raw ->
            val parsed = AttachmentMessageCodec.parseAttachmentMessage(raw)
            parsed.records.forEach { record ->
                val canonical = safeCanonicalPath(record.localPath) ?: return@forEach
                if (!isInAttachmentDirectory(canonical, dirCanonical)) return@forEach
                result.add(canonical)
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
