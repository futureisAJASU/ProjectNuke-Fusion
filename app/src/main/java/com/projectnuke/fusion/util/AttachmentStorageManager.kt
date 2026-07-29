package com.projectnuke.fusion.util

import android.content.Context
import com.projectnuke.fusion.data.ChatDao
import com.projectnuke.fusion.util.AttachmentMessageCodec
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class AttachmentClassification {
    data class Trusted(val file: File) : AttachmentClassification()
    data class Unavailable(val canonicalPath: String) : AttachmentClassification()
    data object Suspicious : AttachmentClassification()
}

data class AttachmentStorageStats(
    val totalBytes: Long,
    val totalFiles: Int,
    val referencedFiles: Int,
    val unreferencedFiles: Int
)

data class CleanupResult(
    val deletedFiles: Int
)

internal data class AttachmentCandidate(
    val name: String,
    val mimeType: String,
    val localPath: String
)

internal fun extractReferencedAttachmentPaths(
    contents: List<String>,
    attachmentRoot: File
): Set<String> {
    val result = mutableSetOf<String>()
    contents.forEach { raw ->
        val parsed = AttachmentMessageCodec.parseTrustedAttachmentMessage(raw, attachmentRoot)
        if (parsed.suspiciousEnvelope) return@forEach
        parsed.records.forEach { record ->
            result.add(record.localPath)
        }
    }
    return result
}

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

    internal fun classifyAttachment(attachmentRoot: File, path: String): AttachmentClassification {
        if (path.isBlank()) return AttachmentClassification.Suspicious
        val suppliedPath = runCatching { File(path).toPath() }.getOrNull()
            ?: return AttachmentClassification.Suspicious
        if (Files.isSymbolicLink(suppliedPath)) return AttachmentClassification.Suspicious
        val targetCanonical = safeCanonicalPath(path) ?: return AttachmentClassification.Suspicious
        val dirCanonical = safeCanonicalPath(attachmentRoot.absolutePath)
            ?: return AttachmentClassification.Suspicious
        if (targetCanonical == dirCanonical) return AttachmentClassification.Suspicious
        val prefix = "$dirCanonical${File.separator}"
        if (!targetCanonical.startsWith(prefix)) return AttachmentClassification.Suspicious
        val targetFile = File(targetCanonical)
        if (!targetFile.exists()) return AttachmentClassification.Unavailable(targetCanonical)
        if (!targetFile.isFile) return AttachmentClassification.Suspicious
        return AttachmentClassification.Trusted(targetFile)
    }

    internal fun resolveManagedAttachment(attachmentRoot: File, path: String): File? {
        return when (val result = classifyAttachment(attachmentRoot, path)) {
            is AttachmentClassification.Trusted -> result.file
            else -> null
        }
    }

    fun resolveManagedAttachment(context: Context, path: String): File? {
        return resolveManagedAttachment(getAttachmentDirectory(context), path)
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
        val attachmentRoot = getAttachmentDirectory(context)
        return extractReferencedAttachmentPaths(contents, attachmentRoot)
    }

    private fun safeCanonicalPath(path: String): String? {
        return runCatching { File(path).canonicalPath }.getOrNull()
    }

    private fun isInAttachmentDirectory(path: String, attachmentDirCanonical: String): Boolean {
        return path == attachmentDirCanonical || path.startsWith("$attachmentDirCanonical${File.separator}")
    }
}

internal fun validateAttachmentBatch(
    candidates: List<AttachmentCandidate>,
    attachmentRoot: File
): List<AttachmentRecord>? {
    val result = mutableListOf<AttachmentRecord>()
    for (candidate in candidates) {
        when (val classification = AttachmentStorageManager.classifyAttachment(attachmentRoot, candidate.localPath)) {
            is AttachmentClassification.Trusted -> {
                result.add(AttachmentRecord(
                    name = candidate.name,
                    mimeType = candidate.mimeType,
                    localPath = classification.file.canonicalPath
                ))
            }
            else -> return null
        }
    }
    return result
}
