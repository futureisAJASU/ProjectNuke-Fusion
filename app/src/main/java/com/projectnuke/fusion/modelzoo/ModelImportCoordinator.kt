package com.projectnuke.fusion.modelzoo

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import com.projectnuke.fusion.util.ManagedModelPathPolicy

internal enum class ModelImportFailure {
    TOO_LARGE,
    STORAGE_FULL,
    SOURCE_UNAVAILABLE,
    CANCELLED,
    INVALID_MODEL,
    ADOPTION_FAILED,
    URI_PERMISSION_LOST,
}

internal sealed interface ModelImportResult {
    data class Success(val file: File, val bytes: Long, val token: String) : ModelImportResult
    data class Failure(val kind: ModelImportFailure, val token: String) : ModelImportResult
}

internal data class ModelImportRequest(
    val token: String = UUID.randomUUID().toString(),
    val sourceIdentity: String,
    val displayName: String,
)

internal fun interface LiteRtLmValidator {
    fun validate(file: File): Boolean
}

internal fun defaultValidator(): LiteRtLmValidator = LiteRtLmValidator { file ->
    file.length() >= 1024L * 1024L && LiteRtLmPackageValidator.validate(file).isValid
}

/**
 * Process-owned, single-adoption coordinator for imported local models.
 *
 * Ownership model:
 * - Each [sourceIdentity] can have at most one active import at a time.
 * - A second import for the same [sourceIdentity] is rejected (even with the same token).
 * - The coordinator is process-scoped (survives config changes) and tied to the app's filesDir.
 * - Stale completions are ignored by checking the current token against [activeBySource].
 * - Abandoned staging files are cleaned up on startup/foreground via [cleanupAbandoned].
 */
internal class ModelImportCoordinator(
    private val modelDirectory: File,
    private val openSource: (String) -> InputStream?,
    private val sourceLength: (String) -> Long? = { null },
    private val usableSpace: (File) -> Long = { it.usableSpace },
    private val maximumBytes: Long = 12L * 1024L * 1024L * 1024L,
    private val reserveBytes: Long = 512L * 1024L * 1024L,
    private val validator: LiteRtLmValidator = defaultValidator(),
) {
    /** Maps sourceIdentity -> active token. Only one import per sourceIdentity at a time. */
    private val activeBySource = ConcurrentHashMap<String, String>()

    /** Tokens that have been explicitly cancelled. */
    private val cancelledTokens = ConcurrentHashMap.newKeySet<String>()

    /** Generation counter to detect stale completions. Incremented on each new import for a source. */
    private val generationBySource = ConcurrentHashMap<String, Long>()

    companion object {
        private const val ORPHAN_GRACE_MS = 300_000L
    }

    suspend fun import(
        request: ModelImportRequest,
        onProgress: suspend (Int) -> Unit = {},
    ): ModelImportResult = withContext(Dispatchers.IO) {
        // Claim ownership for this sourceIdentity. Reject if already active (even with same token).
        val currentGen = generationBySource.getOrDefault(request.sourceIdentity, 0L)
        val nextGen = currentGen + 1
        val claimed = generationBySource.compute(request.sourceIdentity) { _, old ->
            if (old != null && activeBySource[request.sourceIdentity] != null) {
                // Another import is already active for this source - reject new one
                return@compute old
            }
            activeBySource[request.sourceIdentity] = request.token
            nextGen
        }
        if (claimed != nextGen) {
            return@withContext ModelImportResult.Failure(ModelImportFailure.ADOPTION_FAILED, request.token)
        }

        val root = modelDirectory.canonicalFile
        var part: File? = null
        var adopted: File? = null
        var copied = 0L
        try {
            if (!(root.exists() || root.mkdirs()) || !root.isDirectory) {
                return@withContext ModelImportResult.Failure(ModelImportFailure.STORAGE_FULL, request.token)
            }
            val declared = sourceLength(request.sourceIdentity)
            if (declared != null && (declared < 0L || declared > maximumBytes || !hasSpace(root, declared))) {
                return@withContext ModelImportResult.Failure(
                    if (declared > maximumBytes) ModelImportFailure.TOO_LARGE else ModelImportFailure.STORAGE_FULL,
                    request.token,
                )
            }
            val safeName = sanitize(request.displayName)
            val target = File(root, "${UUID.randomUUID()}_$safeName")
            val staging = File(root, ".${target.name}.${request.token}.part")
            if (!isDirectChild(root, target) || !isDirectChild(root, staging)) {
                return@withContext ModelImportResult.Failure(ModelImportFailure.ADOPTION_FAILED, request.token)
            }
            part = staging
            val input = openSource(request.sourceIdentity)
                ?: return@withContext ModelImportResult.Failure(ModelImportFailure.SOURCE_UNAVAILABLE, request.token)
            copied = 0L
            input.use { source ->
                FileOutputStream(staging).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var lastProgress = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (request.token in cancelledTokens) throw CancellationException("model import cancelled")
                        // Verify this import is still the current generation for its source
                        if (generationBySource[request.sourceIdentity] != nextGen) {
                            throw CancellationException("superseded by newer import for same source")
                        }
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        copied += read
                        if (copied > maximumBytes) {
                            return@withContext ModelImportResult.Failure(ModelImportFailure.TOO_LARGE, request.token)
                        }
                        // Reserve is added exactly once per hasSpace call; read is the incremental write
                        if (!hasSpace(root, read.toLong())) {
                            return@withContext ModelImportResult.Failure(ModelImportFailure.STORAGE_FULL, request.token)
                        }
                        output.write(buffer, 0, read)
                        val progress = if (sourceLength(request.sourceIdentity) != null) {
                            val total = sourceLength(request.sourceIdentity)!!
                            (copied * 100L / total).toInt().coerceIn(0, 99)
                        } else -1
                        if (progress >= 0 && progress != lastProgress) {
                            lastProgress = progress
                            onProgress(progress)
                        }
                    }
                    output.flush()
                    output.fd.sync()
                }
            }
            if (!validator.validate(staging)) {
                return@withContext ModelImportResult.Failure(ModelImportFailure.INVALID_MODEL, request.token)
            }
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                try {
                    Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                } catch (_: Exception) {
                    try {
                        Files.move(staging.toPath(), target.toPath())
                    } catch (_: Exception) {
                        return@withContext ModelImportResult.Failure(ModelImportFailure.ADOPTION_FAILED, request.token)
                    }
                }
                if (!target.isFile || target.length() != copied || !validator.validate(target)) {
                    target.delete()
                    return@withContext ModelImportResult.Failure(ModelImportFailure.INVALID_MODEL, request.token)
                }
                adopted = target
            }
            withContext(NonCancellable) {
                onProgress(100)
            }
            ModelImportResult.Success(adopted!!, copied, request.token)
        } catch (e: CancellationException) {
            if (adopted != null) {
                @Suppress("UNCHECKED_CAST")
                ModelImportResult.Success(adopted!!, copied, request.token)
            } else {
                ModelImportResult.Failure(ModelImportFailure.CANCELLED, request.token)
            }
        } catch (e: java.io.IOException) {
            // Distinguish URI permission loss from other I/O errors
            if (e.message?.contains("permission", true) == true ||
                e.message?.contains("Permission denied", true) == true) {
                ModelImportResult.Failure(ModelImportFailure.URI_PERMISSION_LOST, request.token)
            } else {
                ModelImportResult.Failure(ModelImportFailure.ADOPTION_FAILED, request.token)
            }
        } catch (_: Exception) {
            ModelImportResult.Failure(ModelImportFailure.ADOPTION_FAILED, request.token)
        } finally {
            // Only clear ownership if this is still the current generation
            val currentGen = generationBySource[request.sourceIdentity] ?: 0
            if (currentGen == nextGen) {
                activeBySource.remove(request.sourceIdentity, request.token)
                generationBySource.remove(request.sourceIdentity)
            }
            cancelledTokens.remove(request.token)
            withContext(NonCancellable) {
                if (adopted == null) part?.takeIf { it.exists() }?.delete()
            }
        }
    }

    fun cancel(token: String) {
        cancelledTokens += token
    }

    /**
     * Cleans up abandoned staging files (.part/.bak) that are older than the grace period
     * and not owned by an active import token. Call from app startup/foreground.
     */
    fun cleanupAbandoned() {
        val activeTokens = activeBySource.values.toSet() + cancelledTokens.toSet()
        cleanupAbandoned(modelDirectory, activeTokens)
    }

    private fun cleanupAbandoned(root: File, activeTokens: Set<String>) {
        val cutoff = System.currentTimeMillis() - ORPHAN_GRACE_MS
        root.listFiles()?.forEach { file ->
            val name = file.name
            if (name.startsWith(".") && (name.endsWith(".part") || name.endsWith(".bak"))) {
                // Ownership: token is embedded in filename as .<uuid>.part
                val ownedByActive = activeTokens.any { it in name }
                if (!ownedByActive && file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        }
    }

    /**
     * Checks if there is enough space for [incoming] bytes.
     * Reserve is added exactly once per check.
     */
    private fun hasSpace(root: File, incoming: Long): Boolean =
        usableSpace(root) > incoming + reserveBytes

    private fun sanitize(value: String): String = value
        .replace(Regex("[\\u0000-\\u001F\\u007F\\\\/:*?\"<>|]"), "_")
        .take(96)
        .ifBlank { "custom_model.litertlm" }

    private fun isDirectChild(root: File, child: File): Boolean =
        child.canonicalFile.parentFile?.canonicalPath == root.canonicalPath &&
            !Files.isSymbolicLink(child.toPath())
}

internal object ModelImportCoordinatorRegistry {
    private val coordinators = ConcurrentHashMap<String, ModelImportCoordinator>()

    fun forContext(context: Context): ModelImportCoordinator {
        val app = context.applicationContext
        val key = app.filesDir.canonicalPath
        return coordinators.computeIfAbsent(key) {
            ModelImportCoordinator(
                modelDirectory = ManagedModelPathPolicy.getModelDirectory(app),
                openSource = { source -> app.contentResolver.openInputStream(Uri.parse(source)) },
                sourceLength = { source ->
                    runCatching {
                        app.contentResolver.query(
                            Uri.parse(source), null, null, null, null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                                    cursor.getLong(sizeIndex)
                                } else null
                            } else null
                        }
                    }.getOrNull()
                },
            )
        }
    }
}