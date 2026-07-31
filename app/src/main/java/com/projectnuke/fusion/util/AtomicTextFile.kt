package com.projectnuke.fusion.util

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun writeTextAtomically(
    target: File,
    text: String,
    replace: (File, File) -> Unit = { source, destination ->
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    },
) {
    target.parentFile?.mkdirs()
    val temporary = File(target.parentFile, "${target.name}.tmp")
    try {
        FileOutputStream(temporary).use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        replace(temporary, target)
    } finally {
        if (temporary.exists()) temporary.delete()
    }
}
