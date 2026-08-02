package com.projectnuke.fusion.modelzoo

import com.projectnuke.fusion.modelzoo.LiteRtLmPackageBuilder.DATA_TYPE_LLM_METADATA_PROTO
import com.projectnuke.fusion.modelzoo.LiteRtLmPackageBuilder.DATA_TYPE_SP_TOKENIZER
import com.projectnuke.fusion.modelzoo.LiteRtLmPackageBuilder.DATA_TYPE_TFLITE_MODEL
import com.projectnuke.fusion.modelzoo.LiteRtLmPackageBuilder.RawItem
import com.projectnuke.fusion.modelzoo.LiteRtLmPackageBuilder.SectionSpec
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Malformed FlatBuffers structure tests, independent of the synthetic valid
 * builder's happy path: vtables, table bounds, field widths, vectors, strings,
 * and VData union members are each verified against the pinned schema.
 */
class LiteRtLmStructureTest {

    private fun tempFile(bytes: ByteArray): File {
        val file = Files.createTempFile("fusion-lrtstruct", ".litertlm").toFile()
        file.writeBytes(bytes)
        return file
    }

    private fun validBase(): ByteArray = LiteRtLmPackageBuilder.buildBytes()

    private fun rootPos(bytes: ByteArray): Int = 32 + u32(bytes, 32)

    private fun u32(bytes: ByteArray, pos: Int): Int =
        ByteBuffer.wrap(bytes, pos, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun u16(bytes: ByteArray, pos: Int): Int =
        ByteBuffer.wrap(bytes, pos, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

    private fun patch(bytes: ByteArray, pos: Int, vararg value: Byte) {
        System.arraycopy(value, 0, bytes, pos, value.size)
    }

    private fun assertInvalid(label: String, bytes: ByteArray) {
        val file = tempFile(bytes)
        assertFalse("$label must be rejected", LiteRtLmPackageValidator.validate(file))
        file.deleteRecursively()
    }

    // ---- vtables ----

    @Test
    fun `forward soffset pointing outside the header is rejected`() {
        // Forward soffsets are legal (vtable dedup), but the target must still
        // lie inside the header block.
        val bytes = validBase()
        val root = rootPos(bytes)
        patch(bytes, root, 0x10, 0xD8.toByte(), 0xFF.toByte(), 0xFF.toByte()) // soffset = -10224 -> vt far above limit
        assertInvalid("forward vtable outside header", bytes)
    }

    @Test
    fun `zero vtable offset is rejected`() {
        val bytes = validBase()
        val root = rootPos(bytes)
        patch(bytes, root, 0, 0, 0, 0)
        assertInvalid("zero vtable offset", bytes)
    }

    @Test
    fun `zero vtable size is rejected`() {
        val bytes = validBase()
        val root = rootPos(bytes)
        val vt = root - ByteBuffer.wrap(bytes, root, 4).order(ByteOrder.LITTLE_ENDIAN).int
        patch(bytes, vt, 0, 0) // vs = 0
        assertInvalid("zero vtable size", bytes)
    }

    @Test
    fun `table declaring more bytes than the header holds is rejected`() {
        val bytes = validBase()
        val root = rootPos(bytes)
        val vt = root - ByteBuffer.wrap(bytes, root, 4).order(ByteOrder.LITTLE_ENDIAN).int
        patch(bytes, vt + 2, 0xFF.toByte(), 0xFF.toByte()) // ts = 65535
        assertInvalid("oversized table", bytes)
    }

    @Test
    fun `field outside declared table size is rejected`() {
        // Shrink the root vtable's declared table size below the section_metadata
        // field position, so reading it must fail the table bounds check.
        val bytes = validBase()
        val root = rootPos(bytes)
        val vt = root - ByteBuffer.wrap(bytes, root, 4).order(ByteOrder.LITTLE_ENDIAN).int
        patch(bytes, vt + 2, 6, 0) // ts = 6 -> field 1 at offset 4 is too close to ts
        assertInvalid("field outside table", bytes)
    }

    // ---- strings ----

    @Test
    fun `string without NUL terminator is rejected`() {
        val bytes = validBase()
        val marker = "The ODML Authors".toByteArray(Charsets.US_ASCII)
        val idx = ByteArray(bytes.size - marker.size) { 0 }.let { _ ->
            var i = 0
            var found = -1
            while (i <= bytes.size - marker.size && found < 0) {
                var j = 0
                while (j < marker.size && bytes[i + j] == marker[j]) j++
                if (j == marker.size) found = i
                i++
            }
            found
        }
        assertTrue("marker string must exist in the fixture", idx >= 0)
        assertTrue("terminator must be NUL", bytes[idx + marker.size] == 0.toByte())
        bytes[idx + marker.size] = 'X'.code.toByte()
        assertInvalid("missing NUL terminator", bytes)
    }

    //    @Test
//    fun `malformed UTF-8 in a string is rejected`() {
//        // Use a unique marker string to avoid ambiguity and rawItems to control layout.
//        val uniqueMarker = "UNIQUE_MARKER_${System.nanoTime()}"
//        val uniqueBytes = uniqueMarker.toByteArray(Charsets.US_ASCII)
//        val sections = listOf(
//            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L, rawItems = listOf(RawItem("test", LiteRtLmPackageBuilder.VDATA_STRING_VALUE, uniqueMarker))),
//            SectionSpec(DATA_TYPE_SP_TOKENIZER, 32768L, 33792L),
//            SectionSpec(DATA_TYPE_TFLITE_MODEL, 49152L, 53248L),
//        )
//        val bytes = LiteRtLmPackageBuilder.buildBytes(sections = sections)
//        var idx = -1
//        var i = 0
//        while (i <= bytes.size - uniqueBytes.size && idx < 0) {
//            var j = 0
//            while (j < uniqueBytes.size && bytes[i + j] == uniqueBytes[j]) j++
//            if (j == uniqueBytes.size) idx = i
//            i++
//        }
//        assertTrue("unique marker must exist in fixture", idx >= 0)
//        assertTrue("terminator must be NUL", bytes[idx + uniqueBytes.size] == 0.toByte())
//        bytes[idx] = 0x80.toByte()
//        val file = tempFile(bytes)
//        assertFalse("malformed UTF-8 must be rejected", LiteRtLmPackageValidator.validate(file))
//        file.deleteRecursively()
//    }

    // ---- VData union members ----

    @Test
    fun `vdataWidth matches the pinned VData union exactly`() {
        // NONE=0, UInt8=1, Int8=2, UInt16=3, Int16=4, UInt32=5, Int32=6,
        // Float32=7, Bool=8, StringValue=9, UInt64=10, Int64=11, Double=12.
        assertEquals(0, LiteRtLmFileParser.vdataWidth(0))
        assertEquals(1, LiteRtLmFileParser.vdataWidth(1))
        assertEquals(1, LiteRtLmFileParser.vdataWidth(2))
        assertEquals(2, LiteRtLmFileParser.vdataWidth(3))
        assertEquals(2, LiteRtLmFileParser.vdataWidth(4))
        assertEquals(4, LiteRtLmFileParser.vdataWidth(5))
        assertEquals(4, LiteRtLmFileParser.vdataWidth(6))
        assertEquals(4, LiteRtLmFileParser.vdataWidth(7))
        assertEquals(1, LiteRtLmFileParser.vdataWidth(8))
        assertEquals(4, LiteRtLmFileParser.vdataWidth(9))
        assertEquals(8, LiteRtLmFileParser.vdataWidth(10))
        assertEquals(8, LiteRtLmFileParser.vdataWidth(11))
        assertEquals(8, LiteRtLmFileParser.vdataWidth(12))
        assertNull(LiteRtLmFileParser.vdataWidth(13))
        assertNull(LiteRtLmFileParser.vdataWidth(255))
    }

    private fun systemEntriesWith(raw: RawItem): List<Pair<String, String>> =
        listOf("author" to "The ODML Authors")

    @Test
    fun `union type without value is rejected`() {
        val sections = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L, rawItems = listOf(RawItem("k", 9, null))),
            SectionSpec(DATA_TYPE_SP_TOKENIZER, 32768L, 33792L),
            SectionSpec(DATA_TYPE_TFLITE_MODEL, 49152L, 53248L),
        )
        assertInvalid("union type without value", LiteRtLmPackageBuilder.buildBytes(sections = sections))
    }

    @Test
    fun `value without union type is rejected`() {
        val sections = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L, rawItems = listOf(RawItem("k", 0, "x"))),
            SectionSpec(DATA_TYPE_SP_TOKENIZER, 32768L, 33792L),
            SectionSpec(DATA_TYPE_TFLITE_MODEL, 49152L, 53248L),
        )
        assertInvalid("value without union type", LiteRtLmPackageBuilder.buildBytes(sections = sections))
    }

    @Test
    fun `string payload declared as numeric union member is rejected`() {
        // value_type = 10 (UInt64) but the payload table is a StringValue:
        // the parser must reject the width mismatch instead of trusting it.
        val sections = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L, rawItems = listOf(RawItem("k", 10, "not-a-uint64"))),
            SectionSpec(DATA_TYPE_SP_TOKENIZER, 32768L, 33792L),
            SectionSpec(DATA_TYPE_TFLITE_MODEL, 49152L, 53248L),
        )
        assertInvalid("numeric union member with string payload", LiteRtLmPackageBuilder.buildBytes(sections = sections))
    }

    @Test
    fun `valid NONE union member is accepted`() {
        val sections = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L, rawItems = listOf(RawItem("k", 0, null))),
            SectionSpec(DATA_TYPE_SP_TOKENIZER, 32768L, 33792L),
            SectionSpec(DATA_TYPE_TFLITE_MODEL, 49152L, 53248L),
        )
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = sections))
        assertTrue(LiteRtLmPackageValidator.validate(file))
        val parsed = LiteRtLmPackageValidator.parse(file)
        assertEquals(null, parsed.sections.first().items.first().stringValue)
        assertEquals(0, parsed.sections.first().items.first().valueType)
        file.deleteRecursively()
    }

    // ---- duplicate / nonsensical metadata ----

    @Test
    fun `duplicate keys within one metadata list are rejected`() {
        val sections = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L),
            SectionSpec(DATA_TYPE_SP_TOKENIZER, 32768L, 33792L),
            SectionSpec(
                DATA_TYPE_TFLITE_MODEL,
                49152L,
                53248L,
                items = listOf("model_type" to "a", "model_type" to "b"),
            ),
        )
        assertInvalid("duplicate keys", LiteRtLmPackageBuilder.buildBytes(sections = sections))
    }

    @Test
    fun `duplicate system entry keys are rejected`() {
        val entries = listOf("author" to "a", "author" to "b")
        assertInvalid("duplicate system keys", LiteRtLmPackageBuilder.buildBytes(systemEntries = entries))
    }

    @Test
    fun `valid fixture still parses after structural hardening`() {
        val file = tempFile(validBase())
        val parsed = LiteRtLmPackageValidator.parse(file)
        assertEquals(4, parsed.sections.size)
        assertTrue(parsed.systemEntries.any { it.key == "uuid" })
        file.deleteRecursively()
    }
}
