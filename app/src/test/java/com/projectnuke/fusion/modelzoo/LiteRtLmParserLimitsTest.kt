package com.projectnuke.fusion.modelzoo

import com.projectnuke.fusion.modelzoo.LiteRtLmPackageBuilder.DATA_TYPE_LLM_METADATA_PROTO
import com.projectnuke.fusion.modelzoo.LiteRtLmPackageBuilder.DATA_TYPE_SP_TOKENIZER
import com.projectnuke.fusion.modelzoo.LiteRtLmPackageBuilder.DATA_TYPE_TFLITE_MODEL
import com.projectnuke.fusion.modelzoo.LiteRtLmPackageBuilder.SectionSpec
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves over-limit metadata is rejected before unbounded allocation:
 * large counts and strings fail cleanly (ParseException via the validator),
 * never with OOM, because every limit is checked before an ArrayList or
 * ByteArray proportional to the adversarial input is created.
 */
class LiteRtLmParserLimitsTest {

    private fun tempFile(bytes: ByteArray): File {
        val file = Files.createTempFile("fusion-lrtlimits", ".litertlm").toFile()
        file.writeBytes(bytes)
        return file
    }

    private fun invalid(file: File): Boolean = !LiteRtLmPackageValidator.validate(file)

    private fun sections(n: Int): List<SectionSpec> = (0 until n).map { i ->
        val begin = 16384L + i * 16_384L
        SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, begin, begin + 1024)
    }

    @Test
    fun `more than MAX_SECTION_COUNT sections fail without allocation blowup`() {
        val tooMany = LiteRtLmFileParser.MAX_SECTION_COUNT + 1
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = sections(tooMany)))
        assertTrue(invalid(file))
        assertEquals(0, LiteRtLmPackageValidator.capabilities(file).validationVersion)
        // The limit itself is generous relative to the real golden package (12 sections).
        assertTrue(LiteRtLmFileParser.MAX_SECTION_COUNT >= 10 * 12)
        file.deleteRecursively()
    }

    @Test
    fun `more than MAX_SYSTEM_ENTRIES fail`() {
        val entries = (0 until LiteRtLmFileParser.MAX_SYSTEM_ENTRIES + 1).map { "key$it" to "value$it" }
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(systemEntries = entries))
        assertTrue(invalid(file))
        file.deleteRecursively()
    }

    @Test
    fun `more than MAX_ITEMS_PER_SECTION fail`() {
        val items = (0 until LiteRtLmFileParser.MAX_ITEMS_PER_SECTION + 1).map { "k$it" to "v$it" }
        val sections = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L),
            SectionSpec(DATA_TYPE_SP_TOKENIZER, 32768L, 33792L),
            SectionSpec(DATA_TYPE_TFLITE_MODEL, 49152L, 53248L, items = items),
        )
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = sections))
        assertTrue(invalid(file))
        file.deleteRecursively()
    }

    @Test
    fun `key longer than MAX_KEY_BYTES fails`() {
        val longKey = "k".repeat(LiteRtLmFileParser.MAX_KEY_BYTES + 1)
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(systemEntries = listOf(longKey to "value")))
        assertTrue(invalid(file))
        file.deleteRecursively()
    }

    @Test
    fun `string value longer than MAX_STRING_VALUE_BYTES fails`() {
        val longValue = "v".repeat(LiteRtLmFileParser.MAX_STRING_VALUE_BYTES + 1)
        val sections = listOf(
            SectionSpec(DATA_TYPE_LLM_METADATA_PROTO, 16384L, 17408L),
            SectionSpec(DATA_TYPE_SP_TOKENIZER, 32768L, 33792L),
            SectionSpec(
                DATA_TYPE_TFLITE_MODEL,
                49152L,
                53248L,
                items = listOf("model_type" to longValue),
            ),
        )
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = sections))
        assertTrue(invalid(file))
        file.deleteRecursively()
    }

    @Test
    fun `metadata budget rejects cumulative overflow`() {
        val budget = LiteRtLmFileParser.MetadataBudget()
        budget.add(1024, "test")
        budget.add(1024, "test")
        assertEquals(2048L, budget.totalChars)
        // Pushing past MAX_TOTAL_METADATA_CHARS must throw, not wrap.
        assertThrows(LiteRtLmFileParser.ParseException::class.java) {
            budget.add(LiteRtLmFileParser.MAX_TOTAL_METADATA_CHARS - 2047, "test")
        }
        // Generous headroom over the measured real package (428 chars).
        assertTrue(LiteRtLmFileParser.MAX_TOTAL_METADATA_CHARS >= 64 * 428)
    }

    @Test
    fun `limits exceed the measured real Gemma 4 E2B package with headroom`() {
        // Measured on the real golden package: 12 sections, 3 system entries,
        // 2 items/section, 22-byte max key, 36-byte max value, 428 decoded chars.
        assertTrue(LiteRtLmFileParser.MAX_SECTION_COUNT >= 10 * 12)
        assertTrue(LiteRtLmFileParser.MAX_SYSTEM_ENTRIES >= 10 * 3)
        assertTrue(LiteRtLmFileParser.MAX_ITEMS_PER_SECTION >= 10 * 2)
        assertTrue(LiteRtLmFileParser.MAX_KEY_BYTES >= 10 * 22)
        assertTrue(LiteRtLmFileParser.MAX_STRING_VALUE_BYTES >= 10 * 36)
        assertTrue(LiteRtLmFileParser.MAX_TOTAL_METADATA_CHARS >= 10 * 428)
        assertTrue(LiteRtLmFileParser.MAX_VECTOR_ELEMENTS >= 10 * 12)
    }

    @Test
    fun `over-limit input is rejected even when mixed with valid structure`() {
        // A package that is otherwise valid must still fail when one section
        // carries too many items (checks precede any semantic use of the data).
        val items = (0 until LiteRtLmFileParser.MAX_ITEMS_PER_SECTION + 1).map { "k$it" to "v$it" }
        val sections = listOf(
            SectionSpec(
                DATA_TYPE_TFLITE_MODEL,
                16384L,
                17408L,
                items = listOf("model_type" to "tf_lite_prefill_decode"),
            ),
            SectionSpec(DATA_TYPE_SP_TOKENIZER, 32768L, 33792L),
            SectionSpec(DATA_TYPE_TFLITE_MODEL, 49152L, 53248L, items = items),
        )
        val file = tempFile(LiteRtLmPackageBuilder.buildBytes(sections = sections))
        assertFalse(LiteRtLmPackageValidator.validate(file))
        file.deleteRecursively()
    }
}
