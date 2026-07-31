package com.projectnuke.fusion.ui

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupIoTest {
    @Test
    fun `bounded streaming read rejects oversized input`() = runTest {
        val result = readBoundedSettingsStream(ByteArrayInputStream(ByteArray(MaxSettingsBackupBytes + 1)))
        assertEquals(SettingsBackupReadResult.TooLarge, result)
    }

    @Test
    fun `bounded streaming write flushes complete payload`() = runTest {
        val output = ByteArrayOutputStream()
        assertTrue(writeBoundedSettingsStream(output, "payload"))
        assertEquals("payload", output.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `utf8 characters split across read boundary remain intact`() = runTest {
        val prefix = "a".repeat(8 * 1024 - 1)
        val expected = prefix + "한🙂"
        val result = readBoundedSettingsStream(ByteArrayInputStream(expected.toByteArray(StandardCharsets.UTF_8)))
        assertEquals(SettingsBackupReadResult.Success(expected), result)
    }
}
