package com.projectnuke.fusion.util

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AtomicTextFileTest {
    @Test
    fun `failed replacement preserves old valid file and removes temp`() {
        val directory = Files.createTempDirectory("fusion-atomic").toFile()
        val target = directory.resolve("records.json").apply { writeText("old-valid") }

        runCatching {
            writeTextAtomically(target, "new") { _, _ -> error("simulated replacement failure") }
        }

        assertEquals("old-valid", target.readText())
        assertFalse(directory.resolve("records.json.tmp").exists())
        directory.deleteRecursively()
    }

    @Test
    fun `successful replacement adopts complete new content`() {
        val directory = Files.createTempDirectory("fusion-atomic").toFile()
        val target = directory.resolve("records.json").apply { writeText("old") }

        writeTextAtomically(target, "new-complete")

        assertEquals("new-complete", target.readText())
        assertFalse(directory.resolve("records.json.tmp").exists())
        directory.deleteRecursively()
    }
}
