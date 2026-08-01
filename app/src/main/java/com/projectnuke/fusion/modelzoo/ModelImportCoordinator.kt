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

internal object LiteRtLmPackageValidator : LiteRtLmValidator {
    private val magic = "LITERTLM".toByteArray(Charsets.US_ASCII)

    override fun validate(file: File): Boolean {
        if (!file.isFile || !file.extension.equals("litertlm", true) || file.length() < magic.size) return false
        return runCatching {
            RandomAccessFile(file, "r").use { input ->
                val header = ByteArray(magic.size)
                input.readFully(header)
                header.contentEquals(magic)
            }
        }.getOrDefault(false)
    }
}

/** Process-owned, single-adoption coordinator for imported local models. */
internal class ModelImportCoordinator(
    private val modelDirectory: File,
    private val openSource: (String) -> InputStream?,
    private val sourceLength: (String) -> Long? = { null },
    private val usableSpace: (File) -> Long = { it.usableSpace },
    private val maximumBytes: Long = 12L * 1024L * 1024L * 1024L,
    private val reserveBytes: Long = 512L * 1024L * 1024L,
    private val validator: LiteRtLmValidator = LiteRtLmValidator { file ->
        file.length() >= 1024L * 1024L && LiteRtLmPackageValidator.validate(file)
    },
) {
    private val activeBySource = ConcurrentHashMap<String, String>()
    private val cancelledTokens = ConcurrentHashMap.newKeySet<String>()

    companion object {
        private const val ORPHAN_GRACE_MS = 300_000L
    }

    suspend fun import(
        request: ModelImportRequest,
        onProgress: suspend (Int) -> Unit = {},
    ): ModelImportResult = withContext(Dispatchers.IO) {
        val existing = activeBySource.putIfAbsent(request.sourceIdentity, request.token)
        if (existing != null && existing != request.token) {
            return@withContext ModelImportResult.Failure(ModelImportFailure.ADOPTION_FAILED, request.token)
        }
        val root = modelDirectory.canonicalFile
        var part: File? = null
        var adopted: File? = null
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
            var copied = 0L
            input.use { source ->
                FileOutputStream(staging).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var lastProgress = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (request.token in cancelledTokens) throw CancellationException("model import cancelled")
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        copied += read
                        if (copied > maximumBytes) {
                            return@withContext ModelImportResult.Failure(ModelImportFailure.TOO_LARGE, request.token)
                        }
                        if (!hasSpace(root, read + reserveBytes)) {
                            return@withContext ModelImportResult.Failure(ModelImportFailure.STORAGE_FULL, request.token)
                        }
                        output.write(buffer, 0, read)
                        val progress = if (declared != null && declared > 0) {
                            (copied * 100L / declared).toInt().coerceIn(0, 99)
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
            onProgress(100)
            ModelImportResult.Success(adopted!!, copied, request.token)
        } catch (_: CancellationException) {
            ModelImportResult.Failure(ModelImportFailure.CANCELLED, request.token)
        } catch (_: Exception) {
            ModelImportResult.Failure(ModelImportFailure.ADOPTION_FAILED, request.token)
        } finally {
            activeBySource.remove(request.sourceIdentity, request.token)
            cancelledTokens.remove(request.token)
            withContext(NonCancellable) {
                part?.takeIf { it.exists() }?.delete()
                if (adopted != null) adopted?.delete()
            }
        }
    }

    fun cancel(token: String) {
        cancelledTokens += token
    }

    fun cleanupAbandoned() {
        val activeTokens = activeBySource.values.toSet() + cancelledTokens.toSet()
        cleanupAbandoned(modelDirectory, activeTokens)
    }

    private fun cleanupAbandoned(root: File, activeTokens: Set<String>) {
        val cutoff = System.currentTimeMillis() - ORPHAN_GRACE_MS
        root.listFiles()?.forEach { file ->
            val name = file.name
            if (name.startsWith(".") && (name.endsWith(".part") || name.endsWith(".bak"))) {
                val ownedByActive = activeTokens.any { it in name }
                if (!ownedByActive && file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        }
    }

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
