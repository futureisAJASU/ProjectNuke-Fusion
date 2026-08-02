package com.projectnuke.fusion.modelzoo

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins `app/schema/litertlm_header_schema.fbs` to the exact official
 * google-ai-edge/LiteRT-LM v0.14.0 schema file. Any accidental drift in the
 * checked-in schema (or a dependency bump without re-copying) fails CI.
 */
class LiteRtLmSchemaFingerprintTest {

    private val schemaFile: File =
        File("schema/litertlm_header_schema.fbs").let { candidate ->
            if (candidate.isFile) candidate else File("app/schema/litertlm_header_schema.fbs")
        }

    /** SHA-256 of the verbatim upstream v0.14.0 litertlm_header_schema.fbs (LF). */
    private val expectedUpstreamSha256 = "d36b1e3a3aac59671a70a8a46d84c15e31879e5a90176d01caa8abda666c2e24"

    @Test
    fun `schema upstream block is byte-exact to official v0_14_0`() {
        assertTrue("missing schema file", schemaFile.isFile)
        val text = schemaFile.readText().replace("\r\n", "\n")
        val marker = "--- BEGIN verbatim upstream v0.14.0 schema/core/litertlm_header_schema.fbs ---"
        val begin = text.indexOf(marker)
        assertTrue("BEGIN marker not found; schema provenance was altered", begin >= 0)
        val upstream = text.substring(begin + marker.length + 1)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(upstream.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertEquals(expectedUpstreamSha256, digest)
    }

    @Test
    fun `provenance documents exact upstream tag and source path`() {
        val text = schemaFile.readText()
        assertTrue(text.contains("Tag:       v0.14.0"))
        assertTrue(text.contains("schema/core/litertlm_header_schema.fbs"))
        assertTrue(text.contains("litertlm-android:0.14.0"))
    }

    @Test
    fun `SectionDataType matches the pinned schema enum exactly`() {
        val text = schemaFile.readText()
        val enumBlock = text.substringAfter("enum AnySectionDataType:ubyte {")
        val members = Regex("""(?m)^\s*([A-Za-z_]+),\s*//""")
            .findAll(enumBlock)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(
            "AnySectionDataType members",
            listOf("NONE", "GenericBinaryData", "Deprecated", "TFLiteModel",
                "SP_Tokenizer", "LlmMetadataProto", "HF_Tokenizer_Zlib", "TFLiteWeights"),
            members,
        )
        assertEquals(
            "SectionDataType ids mirror AnySectionDataType exactly (0..7 only)",
            listOf(0, 1, 2, 3, 4, 5, 6, 7),
            LiteRtLmFileParser.SectionDataType.entries.map { it.id },
        )
        assertEquals(
            "no extra enum members beyond the pinned schema",
            8,
            LiteRtLmFileParser.SectionDataType.entries.size,
        )
    }
}
