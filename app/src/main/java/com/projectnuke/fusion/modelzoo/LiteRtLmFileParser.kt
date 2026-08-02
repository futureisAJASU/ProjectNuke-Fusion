package com.projectnuke.fusion.modelzoo

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Structural parser for the official LiteRT-LM file header.
 *
 * Mirrors the LiteRT-LM file format defined by
 * `schema/core/litertlm_header_schema.fbs` pinned to the
 * google-ai-edge/LiteRT-LM v0.14.0 tag (see `app/schema/litertlm_header_schema.fbs`).
 * The schema compiles to a FlatBuffers root type `LiteRTLMMetaData` in
 * namespace `litert.lm.schema`; this parser walks that FlatBuffers tree with
 * the same semantics as flatc-generated accessors, with explicit bounds
 * checking so that adversarial input fails cleanly instead of throwing
 * out-of-bounds exceptions.
 *
 * Physical layout of a LiteRTLM file:
 *   [0, 8)         magic "LITERTLM"
 *   [8, 12)        major version (must equal 1)
 *   [12, 16)       minor version
 *   [16, 20)       patch version
 *   [20, 24)       reserved padding
 *   [24, 32)       header end offset (u64, little-endian): end of the header
 *                  block; the header block is FlatBuffers data with the root
 *                  table uoffset stored at file offset 32
 *   [32, headerEnd) FlatBuffers LiteRTLMMetaData tree
 *
 * Section ranges are global file byte ranges `[begin_offset, end_offset)`.
 * Each section begins on a 16 KiB block boundary, and section i+1 begins no
 * sooner than the block boundary following section i's end (BLOCK_SIZE
 * semantics from the pinned schema). Data of every section lies inside the
 * header block and is fully readable from the first `headerEnd` bytes.
 *
 * Versioning model: the file's own `major/minor/patch` (read from bytes 8-20)
 * describe the package schema/file version, separate from
 * `LiteRtLmPackageValidator.VALIDATOR_IMPLEMENTATION_VERSION` which describes
 * this application's validation logic. They are never conflated.
 */
internal object LiteRtLmFileParser {

    internal const val MAGIC = "LITERTLM"
    internal const val BLOCK_SIZE = 16 * 1024
    internal const val MAJOR_VERSION_REQUIRED = 1
    internal const val HEADER_LENGTH = 32

    /**
     * Official reader cap: `kLitertLmHeaderMaxSize = 16 * 1024` from
     * `runtime/util/litert_lm_loader.h` (v0.14.0). Both the streaming loader
     * and the C++ writer reject header blocks that exceed one block.
     */
    private const val MAX_HEADER_BYTES = 16 * 1024

    internal const val KEY_MODEL_TYPE = "model_type"
    internal const val VALUE_MTP_DRAFTER = "tf_lite_mtp_drafter"

    private const val VDATA_STRING_VALUE = 9
    private const val VDATA_MAX = 12

    /**
     * Mirrors `AnySectionDataType` from the pinned schema (v0.14.0), which
     * defines exactly eight members, ids 0..7. Any other id — including 8/9
     * which exist only in newer upstream schemas — is not defined by the
     * pinned schema and is rejected as unknown. Do not add members here
     * without also bumping the pinned schema copy.
     */
    internal enum class SectionDataType(val id: Int) {
        NONE(0),
        GENERIC_BINARY_DATA(1),
        DEPRECATED(2),
        TFLITE_MODEL(3),
        SP_TOKENIZER(4),
        LLM_METADATA_PROTO(5),
        HF_TOKENIZER_ZLIB(6),
        TFLITE_WEIGHTS(7);

        companion object {
            fun fromId(id: Int): SectionDataType? = entries.firstOrNull { it.id == id }
        }
    }

    internal data class KeyValue(
        val key: String,
        val valueType: Int,
        val stringValue: String?,
    )

    internal data class Section(
        val dataType: SectionDataType,
        val beginOffset: Long,
        val endOffset: Long,
        val items: List<KeyValue>,
    )

    internal data class ParsedHeader(
        val majorVersion: Int,
        val minorVersion: Int,
        val patchVersion: Int,
        val headerEnd: Long,
        val systemEntries: List<KeyValue>,
        val sections: List<Section>,
    )

    internal class ParseException(message: String) : Exception(message)

    fun parse(file: File): ParsedHeader {
        if (!file.isFile) throw ParseException("not a file")
        val fileSize = file.length()
        RandomAccessFile(file, "r").use { raf ->
            if (fileSize < HEADER_LENGTH + 4) throw ParseException("file too small")
            val head = ByteArray(HEADER_LENGTH + 4)
            raf.readFully(head)
            val headBuf = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
            for (i in MAGIC.indices) {
                if (headBuf.get(i).toInt() != MAGIC[i].code) throw ParseException("invalid magic")
            }
            val major = headBuf.getInt(8)
            val minor = headBuf.getInt(12)
            val patch = headBuf.getInt(16)
            if (major != MAJOR_VERSION_REQUIRED) throw ParseException("unsupported major version $major")
            val headerEnd = headBuf.getLong(24)
            if (headerEnd < HEADER_LENGTH + 4) throw ParseException("invalid header end $headerEnd")
            if (headerEnd > fileSize) throw ParseException("header extends past end of file")
            if (headerEnd % 4 != 0L) throw ParseException("header not 4-byte aligned")
            if (headerEnd > MAX_HEADER_BYTES) throw ParseException("header too large")
            val headerBytes = ByteArray(headerEnd.toInt())
            System.arraycopy(head, 0, headerBytes, 0, head.size)
            raf.readFully(headerBytes, head.size, headerBytes.size - head.size)
            return parseBuffer(
                ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN),
                headerEnd = headerEnd,
                major = major,
                minor = minor,
                patch = patch,
                fileSize = fileSize,
            )
        }
    }

    /** Parses the header block `buffer` (covering exactly `[0, headerEnd)`). */
    private fun parseBuffer(
        buffer: ByteBuffer,
        headerEnd: Long,
        major: Int,
        minor: Int,
        patch: Int,
        fileSize: Long,
    ): ParsedHeader {
        val fb = Fb(buffer)
        val rootU = fb.u32(HEADER_LENGTH)
        val rootPos = HEADER_LENGTH.toLong() + rootU
        if (rootPos < 0L || rootPos + 4 > fb.limit) throw ParseException("root offset out of bounds")
        val root = fb.table(rootPos.toInt())

        val systemField = fb.field(root, 0)
        val systemEntries = if (systemField == 0) {
            emptyList()
        } else {
            val systemTable = fb.table(fb.uoffset(systemField))
            val entriesField = fb.field(systemTable, 0)
            if (entriesField == 0) emptyList() else parseKeyValues(fb, entriesField)
        }

        val sectionField = fb.field(root, 1)
        if (sectionField == 0) throw ParseException("missing section metadata")
        val sectionMeta = fb.table(fb.uoffset(sectionField))
        val objectsField = fb.field(sectionMeta, 0)
        if (objectsField == 0) throw ParseException("missing section objects")

        val vec = fb.vector(objectsField)
        val sections = ArrayList<Section>(vec.count)
        // Official writer behavior (python/litert_lm_builder/litertlm_builder.py,
        // _round_up_to_block_size): the first section begins at header_end
        // rounded UP to a block; an already-aligned header_end is kept as-is.
        var nextAllowedBegin = roundUpBlock(headerEnd)
        for (i in 0 until vec.count) {
            val obj = fb.table(fb.uoffset(vec.elemsPos + 4 * i))
            val beginField = fb.field(obj, 1)
            val endField = fb.field(obj, 2)
            val typeField = fb.field(obj, 3)
            if (beginField == 0 || endField == 0 || typeField == 0) {
                throw ParseException("section $i missing begin/end/type")
            }
            val begin = fb.u64(beginField)
            val end = fb.u64(endField)
            if (begin < 0L || end < 0L) throw ParseException("section $i range exceeds 2^63")
            if (begin > end) throw ParseException("section $i begins after its end")
            if (begin == end) throw ParseException("section $i has zero size")
            if (end > fileSize) throw ParseException("section $i extends past end of file")
            if (begin % BLOCK_SIZE.toLong() != 0L) throw ParseException("section $i begin not block-aligned")
            // Strict next-block rule from the pinned schema: section i+1 begins
            // no sooner than the smallest K * BLOCK_SIZE strictly greater than
            // section i's end_offset. This also enforces ordering.
            if (begin < nextAllowedBegin) throw ParseException("section $i begins before the block boundary required after the previous section")
            val dataType = SectionDataType.fromId(fb.u8(typeField))
                ?: throw ParseException("section $i unknown data type ${fb.u8(typeField)}")
            val itemsField = fb.field(obj, 0)
            val items = if (itemsField == 0) emptyList() else parseKeyValues(fb, itemsField)
            sections += Section(dataType, begin, end, items)
            nextAllowedBegin = strictNextBlock(end)
        }
        if (sections.isEmpty()) throw ParseException("no sections")

        return ParsedHeader(
            majorVersion = major,
            minorVersion = minor,
            patchVersion = patch,
            headerEnd = headerEnd,
            systemEntries = systemEntries,
            sections = sections,
        )
    }

    private fun parseKeyValues(fb: Fb, fieldPos: Int): List<KeyValue> {
        val vec = fb.vector(fieldPos)
        val out = ArrayList<KeyValue>(vec.count)
        for (i in 0 until vec.count) {
            val kv = fb.table(fb.uoffset(vec.elemsPos + 4 * i))
            val keyField = fb.field(kv, 0)
            if (keyField == 0) throw ParseException("key missing")
            val key = fb.string(fb.uoffset(keyField))
            val valueTypeField = fb.field(kv, 1)
            val valueField = fb.field(kv, 2)
            val valueType = if (valueTypeField == 0) 0 else fb.u8(valueTypeField)
            if (valueType !in 0..VDATA_MAX) throw ParseException("unknown value type $valueType")
            if ((valueType == 0) != (valueField == 0)) {
                throw ParseException("union type/value mismatch for key $key")
            }
            val stringValue = if (valueField == 0) {
                null
            } else {
                val valueTable = fb.table(fb.uoffset(valueField))
                val innerField = fb.field(valueTable, 0)
                if (innerField == 0) throw ParseException("union value missing for key $key")
                if (valueType == VDATA_STRING_VALUE) {
                    fb.string(fb.uoffset(innerField))
                } else {
                    null
                }
            }
            out += KeyValue(key, valueType, stringValue)
        }
        return out
    }

    /**
     * Official writer rule: rounds [value] up to the next multiple of
     * BLOCK_SIZE; an already-aligned value is kept as-is (>= semantics, as in
     * `litertlm_builder.py::_round_up_to_block_size`).
     */
    internal fun roundUpBlock(value: Long): Long {
        val rem = value % BLOCK_SIZE.toLong()
        return if (rem == 0L) value else value + (BLOCK_SIZE - rem)
    }

    /**
     * Strict next-block rule from the pinned schema: the smallest multiple of
     * BLOCK_SIZE strictly greater than [end]. Overflow-safe: an end_offset so
     * large that the next boundary is not representable throws instead of
     * wrapping.
     */
    internal fun strictNextBlock(end: Long): Long {
        val rem = end % BLOCK_SIZE.toLong()
        val next = if (rem == 0L) end + BLOCK_SIZE else end + (BLOCK_SIZE - rem)
        if (next <= end) throw ParseException("next block boundary after $end overflows")
        return next
    }

    /**
     * Bounds-checked reader over a FlatBuffers buffer with the layout produced
     * by the flatc-compatible builder used by the LiteRT-LM exporter.
     */
    private class Fb(private val buf: ByteBuffer) {
        internal val limit: Int get() = buf.limit()

        private fun check(pos: Int, size: Int) {
            if (pos < 0 || size < 0 || pos.toLong() + size > limit) {
                throw ParseException("read out of bounds at $pos")
            }
        }

        fun u8(pos: Int): Int {
            check(pos, 1)
            return buf.get(pos).toInt() and 0xFF
        }

        fun u16(pos: Int): Int {
            check(pos, 2)
            return buf.getShort(pos).toInt() and 0xFFFF
        }

        fun u32(pos: Int): Long {
            check(pos, 4)
            return buf.getInt(pos).toLong() and 0xFFFFFFFFL
        }

        fun u64(pos: Int): Long {
            check(pos, 8)
            return buf.getLong(pos)
        }

        fun i32(pos: Int): Int {
            check(pos, 4)
            return buf.getInt(pos)
        }

        class Table(val pos: Int, val vt: Int, val vs: Int, val ts: Int)

        fun table(pos: Int): Table {
            check(pos, 4)
            val soff = i32(pos)
            val vt = pos - soff
            check(vt, 4)
            val vs = u16(vt)
            val ts = u16(vt + 2)
            // Note: no ts <= vs invariant — the official format routinely emits
            // vtable size 8 / table size 12 (e.g. for LiteRTLMMetaData).
            if (vs < 4 || ts < 4) throw ParseException("invalid vtable at $vt")
            check(vt, vs)
            return Table(pos, vt, vs, ts)
        }

        /** Absolute position of field [index], or 0 when absent. */
        fun field(t: Table, index: Int): Int {
            val entryPos = t.vt + 4 + 2 * index
            if (entryPos + 2 > t.vt + t.vs) return 0
            val off = u16(entryPos)
            if (off == 0) return 0
            if (off >= t.ts) throw ParseException("field $index outside table")
            val fp = t.pos + off
            check(fp, 1)
            return fp
        }

        /** Resolves the uoffset stored at [pos] into an absolute position. */
        fun uoffset(pos: Int): Int {
            val target = pos.toLong() + u32(pos)
            if (target < 0L || target + 4 > limit) throw ParseException("uoffset out of bounds at $pos")
            return target.toInt()
        }

        class Vec(val elemsPos: Int, val count: Int)

        /** Resolves the vector stored at [fieldPos]; returns element base and count. */
        fun vector(fieldPos: Int): Vec {
            val vp = uoffset(fieldPos)
            val count = u32(vp)
            val elems = vp + 4
            if (count * 4 + elems > limit) throw ParseException("vector out of bounds at $fieldPos")
            return Vec(elems.toInt(), count.toInt())
        }

        /** Reads a FlatBuffers string: u32 length (excluding NUL) + bytes + NUL. */
        fun string(pos: Int): String {
            val len = u32(pos)
            check(pos + 4, len.toInt())
            val bytes = ByteArray(len.toInt())
            val saved = buf.position()
            buf.position(pos + 4)
            buf.get(bytes)
            buf.position(saved)
            // flatc-compatible writers store the length without the NUL terminator;
            // tolerate writers that include it.
            var end = bytes.size
            if (end > 0 && bytes[end - 1] == 0.toByte()) end--
            return String(bytes, 0, end, Charsets.UTF_8)
        }
    }
}
