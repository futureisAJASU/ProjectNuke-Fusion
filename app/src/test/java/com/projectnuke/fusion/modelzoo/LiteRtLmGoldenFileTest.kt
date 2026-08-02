package com.projectnuke.fusion.modelzoo

import com.projectnuke.fusion.modelzoo.LiteRtLmFileParser.SectionDataType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * Optional integration test against a real production .litertlm package.
 *
 * Skipped (JUnit assumption) unless a local fixture is configured via:
 *  - environment variable FUSION_LITERTLM_GOLDEN_PATH, or
 *  - Gradle property -Plitertlm.golden.path=<path> (wired into test JVMs by
 *    app/build.gradle.kts), or
 *  - JVM system property -Dlitertlm.golden.path=<path>.
 *
 * The real model bytes are never committed to the repository; this test only
 * runs on machines that point it at their own fixture.
 */
class LiteRtLmGoldenFileTest {

    private fun goldenPath(): String? =
        System.getenv("FUSION_LITERTLM_GOLDEN_PATH")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("litertlm.golden.path")?.takeIf { it.isNotBlank() }

    @Test
    fun `real litertlm package validates and reports MTP capability`() {
        val path = goldenPath()
        Assume.assumeTrue(
            "set FUSION_LITERTLM_GOLDEN_PATH or -Plitertlm.golden.path to run",
            path != null,
        )
        val file = File(path!!)
        Assume.assumeTrue("golden fixture not found: $file", file.isFile)

        assertTrue("real package must validate", LiteRtLmPackageValidator.validate(file))

        val capabilities = LiteRtLmPackageValidator.capabilities(file)
        assertEquals(LiteRtLmPackageValidator.VALIDATOR_IMPLEMENTATION_VERSION, capabilities.validationVersion)
        assertTrue("real E2B package must carry MTP drafter metadata", capabilities.hasDrafter)

        val parsed = LiteRtLmPackageValidator.parse(file)
        assertEquals(1, parsed.majorVersion)
        assertTrue(parsed.sections.any { it.dataType == SectionDataType.TFLITE_MODEL })
        assertTrue(
            parsed.sections.any {
                it.dataType == SectionDataType.SP_TOKENIZER || it.dataType == SectionDataType.HF_TOKENIZER_ZLIB
            },
        )
        assertTrue(
            parsed.sections.any {
                it.items.any { item -> item.key == "model_type" && item.stringValue == "tf_lite_mtp_drafter" }
            },
        )
    }
}
