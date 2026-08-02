package com.projectnuke.fusion.modelzoo

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

internal data class LiteRtLmCapabilities(
    val hasDrafter: Boolean = false,
    val validationVersion: Int = 0,
    val validationTimestamp: Long = 0L,
)

internal object LiteRtLmPackageValidator {
    private val MAGIC = "LITERTLM".toByteArray(Charsets.US_ASCII)
    private const val HEADER_SIZE = 16
    private const val SECTION_ENTRY_SIZE = 16
    private const val MAX_SECTIONS = 64
    private const val SECTION_INDEX_TOKENIZER_MODEL = 1
    private const val SECTION_INDEX_DRAFTER = 3

    fun validate(file: File): Boolean {
        if (!file.isFile || file.length() < MAGIC.size + HEADER_SIZE + 2L * SECTION_ENTRY_SIZE) return false
        return runCatching { parseCapabilities(file).validationVersion >= 1 }.getOrDefault(false)
    }

    fun capabilities(file: File): LiteRtLmCapabilities =
        runCatching { parseCapabilities(file) }.getOrDefault(LiteRtLmCapabilities())

    private fun parseCapabilities(file: File): LiteRtLmCapabilities {
        RandomAccessFile(file, "r").use { raf ->
            val channel = raf.channel

            val magicBuf = ByteBuffer.allocate(MAGIC.size)
            channel.read(magicBuf)
            magicBuf.flip()
            val magicBytes = ByteArray(MAGIC.size)
            magicBuf.get(magicBytes)
            require(magicBytes.contentEquals(MAGIC)) { "invalid magic" }

            val hdrBuf = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            channel.read(hdrBuf)
            hdrBuf.flip()
            val version = hdrBuf.int
            val sectionCount = hdrBuf.int.coerceIn(0, MAX_SECTIONS)

            val fileSize = file.length()
            val sections = mutableListOf<SectionEntry>()
            for (i in 0 until sectionCount) {
                val entryBuf = ByteBuffer.allocate(SECTION_ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                channel.read(entryBuf)
                entryBuf.flip()
                val offset = entryBuf.long
                val length = entryBuf.long
                if (offset < 0L || length < 0L || offset > Long.MAX_VALUE - length || offset + length > fileSize) {
                    throw IllegalStateException("section overflow in entry $i")
                }
                if (i > 0) {
                    val prev = sections.last()
                    if (offset < prev.offset + prev.length) {
                        throw IllegalStateException("section $i overlaps with previous")
                    }
                }
                sections.add(SectionEntry(offset, length, i))
            }

            require(sectionCount > SECTION_INDEX_TOKENIZER_MODEL) { "missing tokenizer model section" }

            val hasDrafter = sectionCount > SECTION_INDEX_DRAFTER &&
                sections.any { it.index == SECTION_INDEX_DRAFTER && it.length > 0L }

            return LiteRtLmCapabilities(
                hasDrafter = hasDrafter,
                validationVersion = version,
                validationTimestamp = System.currentTimeMillis(),
            )
        }
    }

    private data class SectionEntry(val offset: Long, val length: Long, val index: Int)
}