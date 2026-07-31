package com.projectnuke.fusion.ui

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
}
