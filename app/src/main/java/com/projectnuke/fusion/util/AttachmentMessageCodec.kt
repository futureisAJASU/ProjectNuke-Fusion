package com.projectnuke.fusion.util

import java.util.Base64
import java.io.File
import org.json.JSONObject

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
        val fields = splitV2Fields(content)
        if (fields == null || fields.size != 3) return null
        val record = AttachmentRecord(
            name = fields[0],
            mimeType = fields[1],
            localPath = fields[2]
        )
        val consumedLength = closeIdx + TAG_V2_CLOSE.length
        return ParsedTagResult(listOf(record), consumedLength)
    }

    private fun tryParseV1(text: String): ParsedTagResult? {
        if (!text.startsWith(TAG_V1_OPEN)) return null
        val closeIdx = text.indexOf(TAG_V1_CLOSE, TAG_V1_OPEN.length)
        if (closeIdx < 0) return null
        val content = text.substring(TAG_V1_OPEN.length, closeIdx)
        val lines = content.split("\n")
        if (lines.size < 3) return null
        var name: String? = null
        var mimeType: String? = null
        var path: String? = null
        for (line in lines) {
            val eqIdx = line.indexOf('=')
            if (eqIdx < 0) continue
            val key = line.substring(0, eqIdx).trim()
            val value = line.substring(eqIdx + 1).trim()
            when (key) {
                "name" -> { if (name != null) return null; name = value }
                "mime" -> { if (mimeType != null) return null; mimeType = value }
                "path" -> { if (path != null) return null; path = value }
            }
        }
        val resolvedPath = path ?: return null
        if (resolvedPath.isEmpty()) return null
        val record = AttachmentRecord(
            name = name ?: "",
            mimeType = mimeType ?: "",
            localPath = resolvedPath
        )
        val consumedLength = closeIdx + TAG_V1_CLOSE.length
        return ParsedTagResult(listOf(record), consumedLength)
    }

    private fun splitV2Fields(content: String): List<String>? {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var escaping = false
        for (i in 0 until content.length) {
            val c = content[i]
            if (escaping) {
                if (c != '\\' && c != '|') return null
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
        if (escaping) return null
        fields.add(current.toString())
        return fields
    }

    private fun serializeV3Attachment(record: AttachmentRecord): String {
        val jsonObj = JSONObject()
        jsonObj.put("n", record.name)
        jsonObj.put("t", record.mimeType)
        jsonObj.put("p", record.localPath)
        val json = jsonObj.toString()
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))
        return "$TAG_V3_OPEN$encoded$TAG_V3_CLOSE"
    }

    private fun deserializeV3Payload(payload: String): List<AttachmentRecord>? {
        val json = try {
            String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
        val jsonObj = try {
            JSONObject(json)
        } catch (_: Exception) {
            return null
        }
        if (jsonObj.length() != 3) return null
        val name = if (jsonObj.has("n")) jsonObj.getString("n") else return null
        val mimeType = if (jsonObj.has("t")) jsonObj.getString("t") else return null
        val localPath = if (jsonObj.has("p")) jsonObj.getString("p") else return null
        return listOf(AttachmentRecord(name = name, mimeType = mimeType, localPath = localPath))
    }

    fun parseTrustedAttachmentMessage(raw: String, attachmentRoot: File): ParsedAttachments {
        val parsed = parseAttachmentMessage(raw)
        if (parsed.records.isEmpty()) return parsed
        val rootCanonical = try {
            attachmentRoot.canonicalPath
        } catch (_: Exception) {
            return ParsedAttachments(emptyList(), raw)
        }
        val trustedRecords = mutableListOf<AttachmentRecord>()
        for (record in parsed.records) {
            val canonical = try {
                File(record.localPath).canonicalPath
            } catch (_: Exception) { continue }
            if (canonical == rootCanonical) continue
            val prefix = "$rootCanonical${File.separator}"
            if (!canonical.startsWith(prefix)) continue
            val file = File(canonical)
            if (!file.exists() || !file.isFile) continue
            trustedRecords.add(AttachmentRecord(
                name = record.name,
                mimeType = record.mimeType,
                localPath = canonical
            ))
        }
        if (trustedRecords.isEmpty() && parsed.records.isNotEmpty()) {
            return ParsedAttachments(emptyList(), raw)
        }
        return ParsedAttachments(trustedRecords, parsed.body)
    }

    private data class ParsedTagResult(
        val records: List<AttachmentRecord>,
        val consumedLength: Int
    )
}