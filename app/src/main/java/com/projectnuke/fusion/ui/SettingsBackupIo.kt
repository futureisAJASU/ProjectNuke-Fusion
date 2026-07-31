package com.projectnuke.fusion.ui

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal sealed interface SettingsBackupReadResult {
    data class Success(val text: String) : SettingsBackupReadResult
    data object Missing : SettingsBackupReadResult
    data object TooLarge : SettingsBackupReadResult
    data object Cancelled : SettingsBackupReadResult
    data object Failed : SettingsBackupReadResult
}

internal suspend fun readSettingsBackup(
    context: Context,
    uri: Uri,
): SettingsBackupReadResult = withContext(Dispatchers.IO) {
    runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: return@runCatching SettingsBackupReadResult.Missing
        input.use { readBoundedSettingsStream(it) }
    }.getOrElse { error ->
        if (error is kotlinx.coroutines.CancellationException) SettingsBackupReadResult.Cancelled
        else SettingsBackupReadResult.Failed
    }
}

internal suspend fun readBoundedSettingsStream(input: InputStream): SettingsBackupReadResult {
    val output = StringBuilder()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    return try {
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MaxSettingsBackupBytes) return SettingsBackupReadResult.TooLarge
            output.append(String(buffer, 0, count, Charsets.UTF_8))
        }
        SettingsBackupReadResult.Success(output.toString())
    } catch (_: kotlinx.coroutines.CancellationException) {
        SettingsBackupReadResult.Cancelled
    }
}

internal suspend fun writeBoundedSettingsStream(
    output: OutputStream,
    payload: String,
): Boolean {
    val bytes = payload.toByteArray(Charsets.UTF_8)
    if (bytes.size > MaxSettingsBackupBytes) return false
    return try {
        var offset = 0
        while (offset < bytes.size) {
            currentCoroutineContext().ensureActive()
            val count = minOf(8 * 1024, bytes.size - offset)
            output.write(bytes, offset, count)
            offset += count
        }
        output.flush()
        true
    } catch (_: kotlinx.coroutines.CancellationException) {
        throw kotlinx.coroutines.CancellationException("settings backup cancelled")
    }
}
