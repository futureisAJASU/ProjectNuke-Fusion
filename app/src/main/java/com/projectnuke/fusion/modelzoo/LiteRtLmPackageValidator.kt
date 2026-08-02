package com.projectnuke.fusion.modelzoo

import java.io.File

internal data class LiteRtLmCapabilities(
    val hasDrafter: Boolean = false,
    val validationVersion: Int = 0,
    val validationTimestamp: Long = 0L,
)

/**
 * Validates packages against the official LiteRT-LM file format
 * (schema pinned to litertlm-android 0.14.0, see `app/schema/litertlm_header_schema.fbs`).
 *
 * A package is valid when its header parses structurally ([LiteRtLmFileParser])
 * and it contains at least one TFLite model section and at least one tokenizer
 * section. The pre-0.14 invented header format that this validator previously
 * accepted is not recognized and is rejected as invalid.
 */
internal object LiteRtLmPackageValidator {

    internal const val VALIDATOR_IMPLEMENTATION_VERSION = 2

    fun validate(file: File): Boolean = runCatching {
        val parsed = LiteRtLmFileParser.parse(file)
        parsed.sections.any { it.dataType == LiteRtLmFileParser.SectionDataType.TFLITE_MODEL } &&
            parsed.sections.any {
                it.dataType == LiteRtLmFileParser.SectionDataType.SP_TOKENIZER ||
                    it.dataType == LiteRtLmFileParser.SectionDataType.HF_TOKENIZER_ZLIB
            }
    }.getOrDefault(false)

    fun capabilities(file: File): LiteRtLmCapabilities =
        runCatching {
            val parsed = parse(file)
            LiteRtLmCapabilities(
                hasDrafter = parsed.sections.any { section ->
                    section.dataType == LiteRtLmFileParser.SectionDataType.TFLITE_MODEL &&
                        section.items.any { item ->
                            item.key == LiteRtLmFileParser.KEY_MODEL_TYPE &&
                                item.stringValue == LiteRtLmFileParser.VALUE_MTP_DRAFTER
                        }
                },
                validationVersion = VALIDATOR_IMPLEMENTATION_VERSION,
                validationTimestamp = System.currentTimeMillis(),
            )
        }.getOrDefault(LiteRtLmCapabilities())

    internal fun parse(file: File): LiteRtLmFileParser.ParsedHeader = LiteRtLmFileParser.parse(file)
}
