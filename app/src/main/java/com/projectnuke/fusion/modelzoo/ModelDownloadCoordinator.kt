package com.projectnuke.fusion.modelzoo

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class ModelDownloadFailure {
    HTTP,
    REDIRECT,
    TIMEOUT,
    TOO_LARGE,
    STORAGE_FULL,
    INVALID_PAYLOAD,
    ATOMIC_ADOPTION,
    NETWORK,
}

internal sealed interface ModelDownloadResult {
    data class Success(val file: File, val bytes: Long) : ModelDownloadResult
    data class Failure(
        val kind: ModelDownloadFailure,
        val httpStatus: Int? = null,
    ) : ModelDownloadResult
}

internal fun interface ModelConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

internal fun interface AtomicModelAdopter {
    fun adopt(source: Path, target: Path)
}

internal class ModelDownloadCoordinator(
    private val connectionFactory: ModelConnectionFactory = ModelConnectionFactory { url ->
        url.openConnection() as HttpURLConnection
    },
    private val atomicAdopter: AtomicModelAdopter = AtomicModelAdopter { source, target ->
        Files.move(
            source,
            target,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    },
    private val usableSpace: (File) -> Long = { it.usableSpace },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val maximumBytes: Long = 12L * 1024L * 1024L * 1024L,
    private val reserveBytes: Long = 512L * 1024L * 1024L,
    private val minimumPlausibleBytes: Long = 1024L * 1024L,
    private val progressIntervalMs: Long = 500L,
) {
    private val mutex = Mutex()

    suspend fun download(
        sourceUrl: String,
        target: File,
        onProgress: suspend (Int) -> Unit = {},
    ): ModelDownloadResult = mutex.withLock {
        downloadLocked(sourceUrl, target, onProgress)
    }

    private suspend fun downloadLocked(
        sourceUrl: String,
        target: File,
        onProgress: suspend (Int) -> Unit,
    ): ModelDownloadResult {
        val parent = target.parentFile ?: return ModelDownloadResult.Failure(
            ModelDownloadFailure.ATOMIC_ADOPTION
        )
        if (!(parent.exists() || parent.mkdirs()) || !parent.isDirectory) {
            return ModelDownloadResult.Failure(ModelDownloadFailure.STORAGE_FULL)
        }

        val part = File(parent, ".${target.name}.part")
        runCatching { if (part.exists()) part.delete() }

        var connection: HttpURLConnection? = null
        try {
            val connected = openFinalConnection(sourceUrl)
            if (connected is OpenResult.Failure) {
                return connected.result
            }
            connection = (connected as OpenResult.Ready).connection

            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                readBoundedErrorBody(connection)
                return ModelDownloadResult.Failure(ModelDownloadFailure.HTTP, status)
            }

            val contentType = connection.contentType.orEmpty().lowercase()
            if (contentType.contains("text/html") || contentType.contains("application/json")) {
                return ModelDownloadResult.Failure(ModelDownloadFailure.INVALID_PAYLOAD, status)
            }

            val declaredLength = connection.contentLengthLong
            if (declaredLength > maximumBytes) {
                return ModelDownloadResult.Failure(ModelDownloadFailure.TOO_LARGE, status)
            }
            if (declaredLength > 0L && !hasSpace(parent, declaredLength)) {
                return ModelDownloadResult.Failure(ModelDownloadFailure.STORAGE_FULL, status)
            }

            var copied = 0L
            var lastPercent = -1
            var lastProgressAt = nowMillis()
            val header = ByteArray(PAYLOAD_SNIFF_BYTES)
            var headerCount = 0

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        copied += read
                        if (copied > maximumBytes) {
                            return ModelDownloadResult.Failure(ModelDownloadFailure.TOO_LARGE, status)
                        }
                        if (headerCount < header.size) {
                            val headerRead = minOf(read, header.size - headerCount)
                            buffer.copyInto(header, headerCount, 0, headerRead)
                            headerCount += headerRead
                        }
                        output.write(buffer, 0, read)

                        val percent = if (declaredLength > 0L) {
                            ((copied * 100L) / declaredLength).toInt().coerceIn(0, 99)
                        } else {
                            -1
                        }
                        val now = nowMillis()
                        if ((percent >= 0 && percent != lastPercent) || now - lastProgressAt >= progressIntervalMs) {
                            if (percent >= 0) onProgress(percent)
                            lastPercent = percent
                            lastProgressAt = now
                        }
                    }
                    output.flush()
                    output.fd.sync()
                }
            }

            if (declaredLength > 0L && copied != declaredLength) {
                return ModelDownloadResult.Failure(ModelDownloadFailure.INVALID_PAYLOAD, status)
            }
            if (copied < minimumPlausibleBytes || looksLikeHtml(header, headerCount)) {
                return ModelDownloadResult.Failure(ModelDownloadFailure.INVALID_PAYLOAD, status)
            }
            if (!hasSpace(parent, 0L)) {
                return ModelDownloadResult.Failure(ModelDownloadFailure.STORAGE_FULL, status)
            }

            try {
                atomicAdopter.adopt(part.toPath(), target.toPath())
            } catch (_: Exception) {
                return ModelDownloadResult.Failure(ModelDownloadFailure.ATOMIC_ADOPTION, status)
            }
            if (!target.isFile || target.length() != copied) {
                return ModelDownloadResult.Failure(ModelDownloadFailure.ATOMIC_ADOPTION, status)
            }

            onProgress(100)
            return ModelDownloadResult.Success(target, copied)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SocketTimeoutException) {
            return ModelDownloadResult.Failure(ModelDownloadFailure.TIMEOUT)
        } catch (_: Exception) {
            return ModelDownloadResult.Failure(ModelDownloadFailure.NETWORK)
        } finally {
            connection?.disconnect()
            if (part.exists() && !runCatching { part.delete() }.getOrDefault(false)) {
                part.deleteOnExit()
            }
        }
    }

    private fun hasSpace(parent: File, incomingBytes: Long): Boolean {
        val available = usableSpace(parent)
        if (available <= 0L) return false
        val required = incomingBytes.coerceAtLeast(0L) + reserveBytes
        return available >= required
    }

    private fun openFinalConnection(sourceUrl: String): OpenResult {
        var current = runCatching { URL(sourceUrl) }.getOrNull()
            ?: return OpenResult.Failure(ModelDownloadResult.Failure(ModelDownloadFailure.NETWORK))

        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val candidate = connectionFactory.open(current).apply {
                instanceFollowRedirects = false
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                useCaches = false
                requestMethod = "GET"
            }
            var handedOff = false
            try {
                candidate.connect()
                val status = candidate.responseCode
                if (status !in REDIRECT_STATUSES) {
                    handedOff = true
                    return OpenResult.Ready(candidate)
                }
                if (redirectCount >= MAX_REDIRECTS) {
                    return OpenResult.Failure(
                        ModelDownloadResult.Failure(ModelDownloadFailure.REDIRECT, status)
                    )
                }
                val location = candidate.getHeaderField("Location")
                    ?: return OpenResult.Failure(
                        ModelDownloadResult.Failure(ModelDownloadFailure.REDIRECT, status)
                    )
                val next = runCatching { URL(current, location) }.getOrNull()
                    ?: return OpenResult.Failure(
                        ModelDownloadResult.Failure(ModelDownloadFailure.REDIRECT, status)
                    )
                if (next.protocol !in setOf("https", "http") ||
                    (current.protocol == "https" && next.protocol != "https")
                ) {
                    return OpenResult.Failure(
                        ModelDownloadResult.Failure(ModelDownloadFailure.REDIRECT, status)
                    )
                }
                current = next
            } finally {
                if (!handedOff) {
                    candidate.disconnect()
                }
            }
        }
        return OpenResult.Failure(ModelDownloadResult.Failure(ModelDownloadFailure.REDIRECT))
    }

    private fun readBoundedErrorBody(connection: HttpURLConnection) {
        runCatching {
            connection.errorStream?.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var remaining = MAX_ERROR_BODY_BYTES
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                    if (read <= 0) break
                    remaining -= read
                }
            }
        }
    }

    private fun looksLikeHtml(header: ByteArray, count: Int): Boolean {
        if (count <= 0) return false
        val prefix = header.copyOf(count).toString(Charsets.UTF_8).trimStart().lowercase()
        return prefix.startsWith("<!doctype html") ||
            prefix.startsWith("<html") ||
            prefix.startsWith("<?xml") && "<html" in prefix
    }

    private sealed interface OpenResult {
        data class Ready(val connection: HttpURLConnection) : OpenResult
        data class Failure(val result: ModelDownloadResult.Failure) : OpenResult
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_ERROR_BODY_BYTES = 64 * 1024
        const val PAYLOAD_SNIFF_BYTES = 512
        val REDIRECT_STATUSES = setOf(
            HttpURLConnection.HTTP_MOVED_PERM,
            HttpURLConnection.HTTP_MOVED_TEMP,
            HttpURLConnection.HTTP_SEE_OTHER,
            307,
            308,
        )
    }
}
