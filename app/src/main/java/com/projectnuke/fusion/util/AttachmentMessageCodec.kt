package com.projectnuke.fusion.util

import java.util.Base64
import java.io.File

data class AttachmentRecord(
    val name: String,
    val mimeType: String,
    val localPath: String
)

data class ParsedAttachments(
    val records: List<AttachmentRecord>,
    val body: String
)

object AttachmentMessageCodec {

    private const val TAG_V3 = "fusion_attachment_v3"
    private const val TAG_V2 = "fusion_attachment_v2"
    private const val TAG_V1 = "fusion_attachment"
    private const val TAG_V3_OPEN = "<$TAG_V3>"
    private const val TAG_V3_CLOSE = "</$TAG_V3>"
    private const val TAG_V2_OPEN = "<$TAG_V2>"
    private const val TAG_V2_CLOSE = "</$TAG_V2>"
    private const val TAG_V1_OPEN = "<$TAG_V1>"
    private const val TAG_V1_CLOSE = "</$TAG_V1>"

    fun serializeAttachmentMessage(
        attachments: List<AttachmentRecord>,
        body: String
    ): String {
        if (attachments.isEmpty()) return body
        val tags = attachments.joinToString("\n") { serializeV3Attachment(it) }
        return listOf(tags, body)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    fun parseAttachmentMessage(raw: String): ParsedAttachments {
        if (raw.isEmpty()) return ParsedAttachments(emptyList(), raw)
        val records = mutableListOf<AttachmentRecord>()
        var pos = 0
        val len = raw.length
        var consumedAny = false

        while (pos < len) {
            val remaining = raw.substring(pos)
            val v3Result = tryParseV3(remaining)
            if (v3Result != null) {
                records.addAll(v3Result.records)
                pos += v3Result.consumedLength
                while (pos < len && raw[pos].isWhitespace()) pos++
                consumedAny = true
                continue
            }
            val v2Result = tryParseV2(remaining)
            if (v2Result != null) {
                records.addAll(v2Result.records)
                pos += v2Result.consumedLength
                while (pos < len && raw[pos].isWhitespace()) pos++
                consumedAny = true
                continue
            }
            val v1Result = tryParseV1(remaining)
            if (v1Result != null) {
                records.addAll(v1Result.records)
                pos += v1Result.consumedLength
                while (pos < len && raw[pos].isWhitespace()) pos++
                consumedAny = true
                continue
            }
            break
        }

        val body = if (consumedAny) raw.substring(pos).trimStart() else raw
        return ParsedAttachments(records, body)
    }

    private fun tryParseV3(text: String): ParsedTagResult? {
        if (!text.startsWith(TAG_V3_OPEN)) return null
        val closeIdx = text.indexOf(TAG_V3_CLOSE, TAG_V3_OPEN.length)
        if (closeIdx < 0) return null
        val payload = text.substring(TAG_V3_OPEN.length, closeIdx)
        val records = deserializeV3Payload(payload) ?: return null
        val consumedLength = closeIdx + TAG_V3_CLOSE.length
        return ParsedTagResult(records, consumedLength)
    }

    private fun tryParseV2(text: String): ParsedTagResult? {
        if (!text.startsWith(TAG_V2_OPEN)) return null
        val closeIdx = text.indexOf(TAG_V2_CLOSE, TAG_V2_OPEN.length)
        if (closeIdx < 0) return null
        val content = text.substring(TAG_V2_OPEN.length, closeIdx)
        val records = parseV2FieldContent(content)
        val consumedLength = closeIdx + TAG_V2_CLOSE.length
        return ParsedTagResult(records, consumedLength)
    }

    private fun tryParseV1(text: String): ParsedTagResult? {
        if (!text.startsWith(TAG_V1_OPEN)) return null
        val closeIdx = text.indexOf(TAG_V1_CLOSE, TAG_V1_OPEN.length)
        if (closeIdx < 0) return null
        val content = text.substring(TAG_V1_OPEN.length, closeIdx)
        val records = parseV1FieldContent(content)
        val consumedLength = closeIdx + TAG_V1_CLOSE.length
        return ParsedTagResult(records, consumedLength)
    }

    private fun parseV2FieldContent(content: String): List<AttachmentRecord> {
        val fields = splitV2Fields(content)
        if (fields.size < 3) return emptyList()
        return listOf(AttachmentRecord(
            name = fields[0],
            mimeType = fields[1],
            localPath = fields[2]
        ))
    }

    private fun parseV1FieldContent(content: String): List<AttachmentRecord> {
        val lines = content.split("\n")
        var name = ""
        var mimeType = ""
        var path = ""
        for (line in lines) {
            val eqIdx = line.indexOf('=')
            if (eqIdx < 0) continue
            val key = line.substring(0, eqIdx).trim()
            val value = line.substring(eqIdx + 1).trim()
            when (key) {
                "name" -> name = value
                "mime" -> mimeType = value
                "path" -> path = value
            }
        }
        if (path.isEmpty()) return emptyList()
        return listOf(AttachmentRecord(name = name, mimeType = mimeType, localPath = path))
    }

    private fun splitV2Fields(content: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var escaping = false
        for (i in 0 until content.length) {
            val c = content[i]
            if (escaping) {
                current.append(c)
                escaping = false
            } else if (c == '\\') {
                escaping = true
            } else if (c == '|') {
                fields.add(current.toString())
                current.clear()
            } else {
                current.append(c)
            }
        }
        fields.add(current.toString())
        return fields
    }

    private fun serializeV3Attachment(record: AttachmentRecord): String {
        val json = compactJson(record)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))
        return "$TAG_V3_OPEN$encoded$TAG_V3_CLOSE"
    }

    private fun deserializeV3Payload(payload: String): List<AttachmentRecord>? {
        val json = try {
            String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
        return parseCompactV3Json(json)
    }

    private fun compactJson(record: AttachmentRecord): String {
        val sb = StringBuilder()
        sb.append("{\"n\":")
        sb.append(jsonString(record.name))
        sb.append(",\"t\":")
        sb.append(jsonString(record.mimeType))
        sb.append(",\"p\":")
        sb.append(jsonString(record.localPath))
        sb.append("}")
        return sb.toString()
    }

    private fun jsonString(s: String): String {
        val sb = StringBuilder()
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append(String.format("\\u%04x", ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private fun parseCompactV3Json(json: String): List<AttachmentRecord>? {
        val trimmed = json.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        val fields = mutableMapOf<String, String>()
        var pos = 0
        while (pos < inner.length) {
            while (pos < inner.length && inner[pos].isWhitespace()) pos++
            if (pos >= inner.length || inner[pos] != '"') break
            pos++
            val keyEnd = findStringEnd(inner, pos) ?: break
            val key = inner.substring(pos, keyEnd)
            pos = keyEnd + 1
            while (pos < inner.length && inner[pos] != ':') pos++
            if (pos >= inner.length) break
            pos++
            val value = parseJsonValue(inner, pos) ?: return null
            pos = value.second
            while (pos < inner.length && inner[pos] == ',') pos++
            fields[key] = value.first
        }
        val name = fields["n"] ?: return null
        val mimeType = fields["t"] ?: return null
        val path = fields["p"] ?: return null
        return listOf(AttachmentRecord(name = name, mimeType = mimeType, localPath = path))
    }

    private fun findStringEnd(s: String, start: Int): Int? {
        var i = start
        while (i < s.length) {
            when (s[i]) {
                '"' -> return i
                '\\' -> i += 2
                else -> i++
            }
        }
        return null
    }

    private fun parseJsonValue(s: String, start: Int): Pair<String, Int>? {
        var i = start
        while (i < s.length && s[i].isWhitespace()) i++
        if (i >= s.length) return null
        return when (s[i]) {
            '"' -> {
                val end = findStringEnd(s, i + 1) ?: return null
                val value = s.substring(i + 1, end)
                val unescaped = unescapeJsonStringValue(value)
                Pair(unescaped, end + 1)
            }
            else -> null
        }
    }

    private fun unescapeJsonStringValue(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (i + 5 < s.length) {
                            try {
                                sb.append(s.substring(i + 2, i + 6).toInt(16).toChar())
                                i += 5
                            } catch (_: NumberFormatException) {
                                sb.append(s[i])
                            }
                        } else {
                            sb.append(s[i])
                        }
                    }
                    else -> sb.append(s[i])
                }
                i += 2
            } else {
                sb.append(s[i])
                i++
            }
        }
        return sb.toString()
    }

    private data class ParsedTagResult(
        val records: List<AttachmentRecord>,
        val consumedLength: Int
    )
}
