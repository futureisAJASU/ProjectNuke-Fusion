package com.projectnuke.fusion.modelzoo

import com.projectnuke.fusion.modelzoo.LiteRtLmFileParser.SectionDataType
import com.projectnuke.fusion.modelzoo.LiteRtLmPackageBuilder.DATA_TYPE_LLM_METADATA_PROTO
import com.projectnuke.fusion.modelzoo.LiteRtLmPackageBuilder.DATA_TYPE_SP_TOKENIZER
import com.projectnuke.fusion.modelzoo.LiteRtLmPackageBuilder.DATA_TYPE_TFLITE_MODEL
import com.projectnuke.fusion.modelzoo.LiteRtLmPackageBuilder.SectionSpec
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmPackageValidatorTest {

    private fun tempFile(bytes: ByteArray): File {
        val file = Files.createTempFile("fusion-lrtpkg", ".litertlm").toFile()
        file.writeBytes(bytes)
        return file
    }

    private fun delete(file: File) {
        file.deleteRecursively()
    }

    private fun headerEndOf(bytes: ByteArray): Long =
        ByteBuffer.wrap(bytes, 24, 8).order(ByteOrder.LITTLE_ENDIAN).long

    // ---- valid packages ----

    @Test
    fun `valid package with drafter has MTP capability`() {
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes())
        val capabilities = LiteRtLmPackageValidator.capabilities(file)
        assertTrue(capabilities.hasDrafter)
        assertEquals(LiteRtLmPackageValidator.VALIDATOR_IMPLEMENTATION_VERSION, capabilities.validationVersion)
        assertTrue(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    @Test
    fun `valid package without drafter has no MTP capability`() {
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = LiteRtLmPackageBuilder.defaultSections(hasDrafter = false)))
        val capabilities = LiteRtLmPackageValidator.capabilities(file)
        assertFalse(capabilities.hasDrafter)
        assertTrue(capabilities.validationVersion >= 1)
        assertTrue(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    @Test
    fun `parsed header exposes version, sections, and system entries`() {
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes())
        val parsed = LiteRtLmPackageValidator.parse(file)
        assertEquals(1, parsed.majorVersion)
        assertEquals(1, parsed.minorVersion)
        assertEquals(0, parsed.patchVersion)
        assertEquals(
            "The ODML Authors",
            parsed.systemEntries.firstOrNull { it.key == "author" }?.stringValue,
        )
        assertEquals(
            "2026-04-28T22:06:55.560103+00:00",
            parsed.systemEntries.firstOrNull { it.key == "creation_timestamp" }?.stringValue,
        )
        assertEquals(
            listOf(
                SectionDataType.LLM_METADATA_PROTO,
                SectionDataType.SP_TOKENIZER,
                SectionDataType.TFLITE_MODEL,
                SectionDataType.TFLITE_MODEL,
            ),
            parsed.sections.map { it.dataType },
        )
        val drafter = parsed.sections.last()
        assertEquals("tf_lite_mtp_drafter", drafter.items.first { it.key == "model_type" }.stringValue)
        assertTrue(parsed.sections.zipWithNext().all { (a, b) -> a.endOffset <= b.beginOffset })
        delete(file)
    }

    // ---- rejection of non-official content ----

    @Test
    fun `random bytes have no MTP capability and fail validation`() {
        val file = tempFile(ByteArray(4096) { 7 })
        val capabilities = LiteRtLmPackageValidator.capabilities(file)
        assertFalse(capabilities.hasDrafter)
        assertEquals(0, capabilities.validationVersion)
        assertFalse(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    @Test
    fun `empty and tiny files fail validation`() {
        assertFalse(LiteRtLmPackageValidator.validate(tempFile(ByteArray(0))))
        assertFalse(LiteRtLmPackageValidator.validate(tempFile(ByteArray(32))))
        assertFalse(LiteRtLmPackageValidator.validate(tempFile(ByteArray(35))))
    }

    @Test
    fun `legacy invented header format is rejected`() {
        val legacy = legacyInventedFormatBytes()
        val file = tempFile(legacy)
        assertFalse("legacy invented format must not be accepted", LiteRtLmPackageValidator.validate(file))
        val capabilities = LiteRtLmPackageValidator.capabilities(file)
        assertFalse(capabilities.hasDrafter)
        assertEquals(0, capabilities.validationVersion)
        delete(file)
    }

    @Test
    fun `unsupported major version is rejected`() {
        assertFalse(LiteRtLmPackageValidator.validate(tempFile(LiteRtLmPackageBuilder.buildBytes(major = 2))))
        assertFalse(LiteRtLmPackageValidator.validate(tempFile(LiteRtLmPackageBuilder.buildBytes(major = 0))))
    }

    @Test
    fun `minor and patch versions are accepted`() {
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(minor = 5, patch = 0))
        assertTrue(LiteRtLmPackageValidator.validate(file))
        assertEquals(5, LiteRtLmPackageValidator.parse(file).minorVersion)
        delete(file)
    }

    // ---- truncation ----

    @Test
    fun `truncated packages fail validation`() {
        val bytes = LiteRtLmPackageBuilder.buildBytes()
        val headerEnd = headerEndOf(bytes).toInt()
        for (cut in intArrayOf(1, 8, 24, 31, 32, 35, 36, headerEnd / 2, headerEnd - 1)) {
            val file = tempFile(bytes.copyOf(cut))
            assertFalse("truncated to $cut bytes must fail", LiteRtLmPackageValidator.validate(file))
            assertEquals(0, LiteRtLmPackageValidator.capabilities(file).validationVersion)
            delete(file)
        }
    }

    @Test
    fun `header extending past end of file fails validation`() {
        val bytes = LiteRtLmPackageBuilder.buildBytes()
        val headerEnd = headerEndOf(bytes).toInt()
        val file = tempFile(bytes.copyOf(headerEnd))
        assertFalse(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    @Test
    fun `header end not 4-byte aligned fails validation`() {
        val bytes = ByteArray(40)
        bytes[0] = 'L'.code.toByte(); bytes[1] = 'I'.code.toByte()
        bytes[2] = 'T'.code.toByte(); bytes[3] = 'E'.code.toByte()
        bytes[4] = 'R'.code.toByte(); bytes[5] = 'T'.code.toByte()
        bytes[6] = 'L'.code.toByte(); bytes[7] = 'M'.code.toByte()
        bytes[8] = 1 // major version 1
        ByteBuffer.wrap(bytes, 24, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(37) // headerEnd not 4-aligned
        val file = tempFile(bytes)
        assertFalse(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    // ---- invalid FlatBuffer roots ----

    @Test
    fun `root offset pointing outside the header fails validation`() {
        val bytes = LiteRtLmPackageBuilder.buildBytes()
        bytes[32] = 0xFF.toByte(); bytes[33] = 0xFF.toByte(); bytes[34] = 0xFF.toByte(); bytes[35] = 0xFF.toByte()
        val file = tempFile(bytes)
        assertFalse(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    @Test
    fun `root table with invalid vtable fails validation`() {
        val bytes = LiteRtLmPackageBuilder.buildBytes()
        // headerEnd already points past byte 39; craft root uoffset -> table at 36 whose soffset is 0.
        bytes[32] = 4; bytes[33] = 0; bytes[34] = 0; bytes[35] = 0
        bytes[36] = 0; bytes[37] = 0; bytes[38] = 0; bytes[39] = 0
        val file = tempFile(bytes)
        assertFalse(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    @Test
    fun `missing section metadata fails validation`() {
        val bytes = LiteRtLmPackageBuilder.buildBytes()
        bytes[32] = 0; bytes[33] = 0; bytes[34] = 0; bytes[35] = 0 // uoffset 0 -> no root at all
        val file = tempFile(bytes)
        assertFalse(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    // ---- section range validation ----

    @Test
    fun `section beginning after its end fails validation`() {
        val sections = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L),
            SectionSpec(DATA_TYPE_TFLITE_MODEL, 32768L, 16384L),
        )
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = sections))
        assertFalse(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    @Test
    fun `section extending past end of file fails validation`() {
        val sections = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L),
            SectionSpec(DATA_TYPE_TFLITE_MODEL, 32768L, 49152L),
        )
        val file = LiteRtLmPackageBuilder.buildFile(sections = sections, fileSize = 30000L)
        assertFalse(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    @Test
    fun `section range exceeding 2^63 fails validation`() {
        val sections = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, Long.MIN_VALUE, Long.MAX_VALUE),
        )
        val file = LiteRtLmPackageBuilder.buildFile(sections = sections, fileSize = 1024L * 1024)
        assertFalse(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    @Test
    fun `overlapping sections fail validation`() {
        val overlapping = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L),
            SectionSpec(DATA_TYPE_TFLITE_MODEL, 16384L, 32768L),
        )
        assertFalse(LiteRtLmPackageValidator.validate(tempFile(LiteRtLmPackageBuilder.buildBytes(sections = overlapping))))

        // Second section begins inside the first one's trailing block.
        val early = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L),
            SectionSpec(DATA_TYPE_TFLITE_MODEL, 20480L, 32768L),
        )
        assertFalse(LiteRtLmPackageValidator.validate(tempFile(LiteRtLmPackageBuilder.buildBytes(sections = early))))
    }

    @Test
    fun `section begin not block-aligned fails validation`() {
        val sections = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L),
            SectionSpec(DATA_TYPE_TFLITE_MODEL, 32772L, 49152L), // 32768L + 4
        )
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = sections))
        assertFalse(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    @Test
    fun `unknown section data type fails validation`() {
        val sections = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L),
            SectionSpec(10, 32768L, 49152L),
        )
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = sections))
        assertFalse(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    @Test
    fun `empty sections list fails validation`() {
        val sections = listOf<SectionSpec>()
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = sections))
        assertFalse(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    // ---- required sections ----

    @Test
    fun `package without model section fails validation`() {
        val sections = LiteRtLmPackageBuilder.defaultSections(hasDrafter = false).filter {
            it.dataType != DATA_TYPE_TFLITE_MODEL
        }
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = sections))
        assertFalse(LiteRtLmPackageValidator.validate(file))
        assertEquals(LiteRtLmPackageValidator.VALIDATOR_IMPLEMENTATION_VERSION, LiteRtLmPackageValidator.capabilities(file).validationVersion)
        delete(file)
    }

    @Test
    fun `package without tokenizer section fails validation`() {
        val sections = LiteRtLmPackageBuilder.defaultSections(hasDrafter = false).filter {
            it.dataType != DATA_TYPE_SP_TOKENIZER
        }
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = sections))
        assertFalse(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    // ---- MTP drafter capability metadata ----

    @Test
    fun `drafter section with other model type has no MTP capability`() {
        val sections = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L),
            SectionSpec(DATA_TYPE_SP_TOKENIZER, 32768L, 33792L),
            SectionSpec(
                DATA_TYPE_TFLITE_MODEL,
                49152L,
                53248L,
                listOf("model_type" to "tf_lite_prefill_decode"),
            ),
            SectionSpec(
                DATA_TYPE_TFLITE_MODEL,
                65536L,
                69632L,
                listOf("model_type" to "tf_lite_per_layer_embedder"),
            ),
        )
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = sections))
        assertTrue(LiteRtLmPackageValidator.validate(file))
        assertFalse(LiteRtLmPackageValidator.capabilities(file).hasDrafter)
        delete(file)
    }

    @Test
    fun `drafter model type on non-tflite section has no MTP capability`() {
        val sections = listOf(
            SectionSpec(
                DATA_TYPE_LLM_METADATA_PROTO,
                16384L,
                17408L,
                listOf("model_type" to "tf_lite_mtp_drafter"),
            ),
            SectionSpec(DATA_TYPE_SP_TOKENIZER, 32768L, 33792L),
            SectionSpec(
                DATA_TYPE_TFLITE_MODEL,
                49152L,
                53248L,
                listOf("model_type" to "tf_lite_prefill_decode"),
            ),
        )
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = sections))
        assertTrue(LiteRtLmPackageValidator.validate(file))
        assertFalse(LiteRtLmPackageValidator.capabilities(file).hasDrafter)
        delete(file)
    }

    @Test
    fun `huggingface tokenizer section counts as tokenizer`() {
        val sections = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L),
            SectionSpec(DATA_TYPE_SP_TOKENIZER, 32768L, 33792L),
            SectionSpec(com.projectnuke.fusion.modelzoo.LiteRtLmPackageBuilder.DATA_TYPE_HF_TOKENIZER_ZLIB, 49152L, 53248L),
            SectionSpec(DATA_TYPE_TFLITE_MODEL, 65536L, 69632L),
        )
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = sections))
        assertTrue(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    // ---- zero-length sections (official reader: litertlm_read.cc rejects them) ----

    @Test
    fun `zero-length tflite model section fails validation`() {
        val sections = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L),
            SectionSpec(DATA_TYPE_SP_TOKENIZER, 32768L, 33792L),
            SectionSpec(DATA_TYPE_TFLITE_MODEL, 49152L, 49152L),
        )
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = sections))
        assertFalse(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    @Test
    fun `zero-length sp tokenizer section fails validation`() {
        val sections = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L),
            SectionSpec(DATA_TYPE_SP_TOKENIZER, 32768L, 32768L),
            SectionSpec(DATA_TYPE_TFLITE_MODEL, 49152L, 53248L),
        )
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = sections))
        assertFalse(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    @Test
    fun `zero-length hf tokenizer section fails validation`() {
        val sections = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L),
            SectionSpec(com.projectnuke.fusion.modelzoo.LiteRtLmPackageBuilder.DATA_TYPE_HF_TOKENIZER_ZLIB, 32768L, 32768L),
            SectionSpec(DATA_TYPE_TFLITE_MODEL, 49152L, 53248L),
        )
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = sections))
        assertFalse(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    // ---- strict next-block rule ----

    @Test
    fun `section ending on a block boundary may not be followed by a section at the same offset`() {
        // Section 0 covers exactly [16384, 32768); the schema's strict rule
        // requires the next section to begin at 49152, not at 32768.
        val sections = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 32768L),
            SectionSpec(DATA_TYPE_SP_TOKENIZER, 32768L, 33792L),
            SectionSpec(DATA_TYPE_TFLITE_MODEL, 49152L, 53248L),
        )
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = sections))
        assertFalse(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    @Test
    fun `section ending on a block boundary may be followed by a section at the next block`() {
        val sections = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 32768L),
            SectionSpec(DATA_TYPE_SP_TOKENIZER, 49152L, 53248L),
            SectionSpec(DATA_TYPE_TFLITE_MODEL, 65536L, 73728L),
        )
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = sections))
        assertTrue(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    // ---- first-section boundary (official writer/reader behavior) ----

    @Test
    fun `first section may begin at a block-aligned header end`() {
        // Official writer keeps an already-aligned header_end as-is (>= semantics).
        val bytes = LiteRtLmPackageBuilder.buildBytes(
            sections = listOf(
                SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L),
                SectionSpec(DATA_TYPE_SP_TOKENIZER, 32768L, 33792L),
                SectionSpec(DATA_TYPE_TFLITE_MODEL, 49152L, 53248L),
            ),
        )
        ByteBuffer.wrap(bytes, 24, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(16384)
        val file = tempFile(bytes)
        assertTrue(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    @Test
    fun `first section inside the header block fails validation`() {
        val bytes = LiteRtLmPackageBuilder.buildBytes(
            sections = listOf(
                SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 8192L, 17408L),
                SectionSpec(DATA_TYPE_SP_TOKENIZER, 32768L, 33792L),
                SectionSpec(DATA_TYPE_TFLITE_MODEL, 49152L, 53248L),
            ),
        )
        // headerEnd set to 16384 so the header block covers [32, 16384), which
        // overlaps the first section.
        ByteBuffer.wrap(bytes, 24, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(16384)
        val file = tempFile(bytes)
        assertFalse(LiteRtLmPackageValidator.validate(file))
        delete(file)
    }

    // ---- block arithmetic (overflow-safe) ----

    @Test
    fun `roundUpBlock follows the official writer semantics`() {
        assertEquals(16384L, LiteRtLmFileParser.roundUpBlock(16384L))
        assertEquals(16384L, LiteRtLmFileParser.roundUpBlock(1872L))
        assertEquals(32768L, LiteRtLmFileParser.roundUpBlock(16385L))
        assertEquals(98304L, LiteRtLmFileParser.roundUpBlock(81924L))
    }

    @Test
    fun `strictNextBlock returns the smallest multiple strictly greater than the end`() {
        assertEquals(16384L, LiteRtLmFileParser.strictNextBlock(0L))
        assertEquals(32768L, LiteRtLmFileParser.strictNextBlock(16384L))
        assertEquals(32768L, LiteRtLmFileParser.strictNextBlock(17408L))
        assertEquals(98304L, LiteRtLmFileParser.strictNextBlock(98303L))
    }

    @Test
    fun `block arithmetic near Long MAX_VALUE does not wrap`() {
        try {
            LiteRtLmFileParser.strictNextBlock(Long.MAX_VALUE - 100)
            throw AssertionError("strictNextBlock must reject an unrepresentable next block")
        } catch (expected: LiteRtLmFileParser.ParseException) {
            // expected: next block boundary overflows
        }
        // roundUpBlock is only ever called with a validated headerEnd, which is
        // capped at MAX_HEADER_BYTES; still assert it stays sane near the cap.
        assertEquals(16384L, LiteRtLmFileParser.roundUpBlock(16384L))
    }

    // ---- the pre-0.14 invented format this validator previously accepted ----

    private fun legacyInventedFormatBytes(): ByteArray {
        val magic = "LITERTLM".toByteArray(Charsets.US_ASCII)
        val headerSize = 16
        val entrySize = 16
        val modelLength = 128
        val tokenizerLength = 64
        var dataOffset = (magic.size + headerSize + 2 * entrySize).toLong()
        val entries = buildList {
            add(Pair(dataOffset, modelLength.toLong())); dataOffset += modelLength.toLong()
            add(Pair(dataOffset, tokenizerLength.toLong())); dataOffset += tokenizerLength.toLong()
        }
        val out = java.io.ByteArrayOutputStream()
        out.write(magic)
        val header = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(1) // invented version
        header.putInt(2) // invented section count
        out.write(header.array())
        entries.forEach { (offset, length) ->
            val entry = ByteBuffer.allocate(entrySize).order(ByteOrder.LITTLE_ENDIAN)
            entry.putLong(offset)
            entry.putLong(length)
            out.write(entry.array())
        }
        out.write(ByteArray(modelLength) { 1 })
        out.write(ByteArray(tokenizerLength) { 2 })
        return out.toByteArray()
    }
}
