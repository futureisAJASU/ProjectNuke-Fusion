package com.projectnuke.fusion.modelzoo

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmPackageValidatorTest {
    private val MAGIC = "LITERTLM".toByteArray(Charsets.US_ASCII)
    private val HEADER_SIZE = 16
    private val SECTION_ENTRY_SIZE = 16

    private fun buildPackage(
        version: Int = 1,
        drafterLength: Int = 0,
        tokenizerLength: Int = 64,
        modelLength: Int = 128,
        sectionCount: Int = if (drafterLength > 0) 4 else 2,
    ): File {
        val file = Files.createTempFile("fusion-lrtpkg", ".litertlm").toFile()
        val sectionCountFinal = sectionCount.coerceIn(1, 4)
        val entriesSize = sectionCountFinal * SECTION_ENTRY_SIZE
        var dataOffset = MAGIC.size + HEADER_SIZE + entriesSize.toLong()
        val entries = buildList {
            add(Pair(dataOffset, modelLength.toLong())); dataOffset += modelLength
            if (sectionCountFinal >= 2) {
                add(Pair(dataOffset, tokenizerLength.toLong())); dataOffset += tokenizerLength
            }
            if (sectionCountFinal == 4) {
                add(Pair(dataOffset, 0L))
                add(Pair(dataOffset, drafterLength.toLong()))
            }
        }
        RandomAccessFile(file, "rw").use { raf ->
            raf.write(MAGIC)
            val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(version)
            header.putInt(sectionCountFinal)
            raf.write(header.array())
            entries.forEach { (offset, length) ->
                val entry = ByteBuffer.allocate(SECTION_ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                entry.putLong(offset)
                entry.putLong(length)
                raf.write(entry.array())
            }
            raf.write(ByteArray(modelLength) { 1 })
            raf.write(ByteArray(tokenizerLength) { 2 })
            if (drafterLength > 0) raf.write(ByteArray(drafterLength) { 3 })
        }
        return file
    }

    @Test
    fun `package with drafter section has MTP capability`() {
        val file = buildPackage(drafterLength = 32)
        val capabilities = LiteRtLmPackageValidator.capabilities(file)
        assertTrue(capabilities.hasDrafter)
        assertTrue(capabilities.validationVersion >= 1)
        assertTrue(LiteRtLmPackageValidator.validate(file))
        file.deleteRecursively()
    }

    @Test
    fun `package without drafter section has no MTP capability`() {
        val file = buildPackage()
        val capabilities = LiteRtLmPackageValidator.capabilities(file)
        assertFalse(capabilities.hasDrafter)
        assertTrue(capabilities.validationVersion >= 1)
        assertTrue(LiteRtLmPackageValidator.validate(file))
        file.deleteRecursively()
    }

    @Test
    fun `random bytes have no MTP capability and fail validation`() {
        val file = Files.createTempFile("fusion-lrtpkg", ".litertlm").toFile()
        file.writeBytes(ByteArray(1024) { 7 })
        val capabilities = LiteRtLmPackageValidator.capabilities(file)
        assertFalse(capabilities.hasDrafter)
        assertEquals(0, capabilities.validationVersion)
        assertFalse(LiteRtLmPackageValidator.validate(file))
        file.deleteRecursively()
    }

    @Test
    fun `package without tokenizer section fails validation`() {
        val file = buildPackage(sectionCount = 1)
        assertFalse(LiteRtLmPackageValidator.validate(file))
        file.deleteRecursively()
    }
}
