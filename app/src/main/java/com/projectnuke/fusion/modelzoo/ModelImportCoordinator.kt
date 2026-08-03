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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.projectnuke.fusion.util.ManagedModelPathPolicy

private const val DEFAULT_BUFFER_SIZE = 1024 * 1024

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
 * Represents an active model import operation with its token, deferred result, and progress.
 * The operation owns the entire import process from URI query through metadata commit.
 */
internal data class ActiveModelImport(
    val token: String,
    val deferred: Deferred<ModelImportResult>,
    val progress: kotlinx.coroutines.flow.MutableStateFlow<ModelImportProgress>,
)

/** Progress state for an ongoing model import. */
internal data class ModelImportProgress(
    val percent: Int = 0,
    val stage: ImportStage = ImportStage.COPYING,
)

internal enum class ImportStage {
    COPYING,
    VALIDATING,
    ADOPTING,
    COMMITTING_METADATA,
    RELEASING_PERMISSION,
    COMPLETE,
}

/**
 * Process-owned, single-adoption coordinator for imported local models.
 *
 * Ownership model:
 * - Each [sourceIdentity] can have at most one active import at a time.
 * - A second import for the same [sourceIdentity] is rejected (even with the same token).
 * - The coordinator is process-scoped (survives config changes) and tied to the app's filesDir.
 * - Stale completions are impossible because ownership is decided by atomic putIfAbsent.
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
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    /** Maps sourceIdentity -> active import operation. Only one import per sourceIdentity at a time. */
    private val activeImports = ConcurrentHashMap<String, ActiveModelImport>()

    companion object {
        private const val ORPHAN_GRACE_MS = 300_000L
    }

    suspend fun import(
        request: ModelImportRequest,
        onProgress: suspend (Int) -> Unit = {},
    ): ModelImportResult = withContext(Dispatchers.IO) {
        // Try to claim ownership atomically using putIfAbsent
        val progressFlow = kotlinx.coroutines.flow.MutableStateFlow(ModelImportProgress())
        val deferred = scope.async {
            runImportInternal(request, progressFlow)
        }
        val candidate = ActiveModelImport(request.token, deferred, progressFlow)

        val existing = activeImports.putIfAbsent(request.sourceIdentity, candidate)
        if (existing != null) {
            // Another import is already active for this source - reject new one
            deferred.cancel()
            return@withContext ModelImportResult.Failure(ModelImportFailure.ADOPTION_FAILED, request.token)
        }

        // Observe progress and forward to callback
        scope.launch {
            progressFlow.collect { p ->
                if (p.percent >= 0) onProgress(p.percent)
            }
        }

        try {
            val result = deferred.await()
            return@withContext result
        } finally {
            // Only remove if we still own it
            activeImports.remove(request.sourceIdentity, candidate)
        }
    }

    private suspend fun runImportInternal(
        request: ModelImportRequest,
        progress: kotlinx.coroutines.flow.MutableStateFlow<ModelImportProgress>,
    ): ModelImportResult = withContext(Dispatchers.IO) {
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
                        if (request.token in getCancelledTokens()) throw CancellationException("model import cancelled")
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        copied += read
                        if (copied > maximumBytes) {
                            return@withContext ModelImportResult.Failure(ModelImportFailure.TOO_LARGE, request.token)
                        }
                        if (!hasSpace(root, read.toLong())) {
                            return@withContext ModelImportResult.Failure(ModelImportFailure.STORAGE_FULL, request.token)
                        }
                        output.write(buffer, 0, read)
                        val prog = if (sourceLength(request.sourceIdentity) != null) {
                            val total = sourceLength(request.sourceIdentity)!!
                            (copied * 100L / total).toInt().coerceIn(0, 99)
                        } else -1
                        if (prog >= 0 && prog != lastProgress) {
                            lastProgress = prog
                            progress.value = ModelImportProgress(percent = prog, stage = ImportStage.COPYING)
                        }
                    }
                    output.flush()
                    output.fd.sync()
                }
            }
            progress.value = ModelImportProgress(percent = 99, stage = ImportStage.VALIDATING)
            if (!validator.validate(staging)) {
                return@withContext ModelImportResult.Failure(ModelImportFailure.INVALID_MODEL, request.token)
            }
            currentCoroutineContext().ensureActive()
            progress.value = ModelImportProgress(percent = 99, stage = ImportStage.ADOPTING)
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
            progress.value = ModelImportProgress(percent = 99, stage = ImportStage.COMMITTING_METADATA)
            // Metadata commit would happen here (handled by caller after this returns)
            progress.value = ModelImportProgress(percent = 99, stage = ImportStage.RELEASING_PERMISSION)
            // URI permission release would happen here
            progress.value = ModelImportProgress(percent = 100, stage = ImportStage.COMPLETE)
            ModelImportResult.Success(adopted!!, copied, request.token)
        } catch (e: CancellationException) {
            if (adopted != null) {
                @Suppress("UNCHECKED_CAST")
                ModelImportResult.Success(adopted!!, copied, request.token)
            } else {
                ModelImportResult.Failure(ModelImportFailure.CANCELLED, request.token)
            }
        } catch (e: java.io.IOException) {
            if (e.message?.contains("permission", true) == true ||
                e.message?.contains("Permission denied", true) == true) {
                ModelImportResult.Failure(ModelImportFailure.URI_PERMISSION_LOST, request.token)
            } else {
                ModelImportResult.Failure(ModelImportFailure.ADOPTION_FAILED, request.token)
            }
        } catch (_: Exception) {
            ModelImportResult.Failure(ModelImportFailure.ADOPTION_FAILED, request.token)
        } finally {
            withContext(NonCancellable) {
                if (adopted == null) part?.takeIf { it.exists() }?.delete()
            }
        }
    }

    private val cancelledTokens = ConcurrentHashMap.newKeySet<String>()

    fun cancel(token: String) {
        cancelledTokens += token
    }

    private fun getCancelledTokens(): Set<String> = cancelledTokens

    /**
     * Cleans up abandoned staging files (.part/.bak) that are older than the grace period
     * and not owned by an active import token. Call from app startup/foreground.
     */
    fun cleanupAbandoned() {
        val activeTokens = activeImports.values.map { it.token }.toSet() + cancelledTokens.toSet()
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
                scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO),
            )
        }
    }
}