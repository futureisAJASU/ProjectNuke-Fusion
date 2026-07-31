package com.projectnuke.fusion.chat

import android.content.Context
import com.projectnuke.fusion.util.AttachmentStorageManager
import com.projectnuke.fusion.util.writeTextAtomically
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

internal open class PersistentComposerDraftStore(
    private val file: File,
    private val resolveManagedAttachment: (String) -> File?,
    private val registerPendingAttachment: (File) -> String?,
) {
    constructor(context: Context) : this(
        file = File(context.applicationContext.filesDir, FILE_NAME),
        resolveManagedAttachment = { path: String ->
            AttachmentStorageManager.resolveManagedAttachment(context.applicationContext, path)
        },
        registerPendingAttachment = AttachmentStorageManager::registerPendingAttachment,
    )

    private val writeMutex = Mutex()
    private var latestWriteId = 0L

    open suspend fun load(): Map<Long, ComposerDraftState> = writeMutex.withLock {
        val raw = runCatching {
            if (!file.isFile || file.length() > MAX_FILE_BYTES) return@runCatching null
            file.readText(Charsets.UTF_8)
        }.getOrNull() ?: return@withLock emptyMap()
        decode(raw)
    }

    open suspend fun write(writeId: Long, drafts: Map<Long, ComposerDraftState>): Boolean =
        writeMutex.withLock {
            if (writeId < latestWriteId) return@withLock false
            latestWriteId = writeId
            val encoded = encode(drafts)
            if (encoded.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES) return@withLock false
            runCatching { writeTextAtomically(file, encoded) }.isSuccess
        }

    private fun decode(raw: String): Map<Long, ComposerDraftState> = runCatching {
        val result = LinkedHashMap<Long, ComposerDraftState>()
        val array = JSONArray(raw)
        for (index in 0 until minOf(array.length(), MAX_DRAFT_COUNT)) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optLong("id", Long.MIN_VALUE)
            if (id < 0L || id == Long.MIN_VALUE) continue
            val input = item.optString("input").take(MAX_INPUT_CHARS)
            val attachmentArray = item.optJSONArray("attachments") ?: JSONArray()
            val attachments = buildList {
                for (attachmentIndex in 0 until minOf(attachmentArray.length(), MAX_ATTACHMENT_COUNT)) {
                    val attachment = attachmentArray.optJSONObject(attachmentIndex) ?: continue
                    val path = attachment.optString("path")
                    if (path.length !in 1..MAX_PATH_CHARS) continue
                    val resolved = resolveManagedAttachment(path)
                        ?: continue
                    registerPendingAttachment(resolved)
                    add(PendingAttachmentIdentity(
                        name = attachment.optString("name").take(MAX_FIELD_CHARS),
                        mimeType = attachment.optString("mime").take(MAX_FIELD_CHARS),
                        localPath = resolved.absolutePath,
                    ))
                }
            }
            result[id] = ComposerDraftState(
                rawInput = input,
                pendingAttachments = attachments,
                version = item.optLong("version").coerceAtLeast(0L),
            )
        }
        result
    }.getOrDefault(emptyMap())

    private fun encode(drafts: Map<Long, ComposerDraftState>): String {
        val array = JSONArray()
        drafts.entries.asSequence()
            .filter { (_, draft) -> draft.rawInput.isNotEmpty() || draft.pendingAttachments.isNotEmpty() }
            .take(MAX_DRAFT_COUNT)
            .forEach { (id, draft) ->
                val attachments = JSONArray()
                draft.pendingAttachments.take(MAX_ATTACHMENT_COUNT).forEach { attachment ->
                    attachments.put(JSONObject()
                        .put("name", attachment.name.take(MAX_FIELD_CHARS))
                        .put("mime", attachment.mimeType.take(MAX_FIELD_CHARS))
                        .put("path", attachment.localPath.take(MAX_PATH_CHARS)))
                }
                array.put(JSONObject()
                    .put("id", id)
                    .put("input", draft.rawInput.take(MAX_INPUT_CHARS))
                    .put("version", draft.version)
                    .put("attachments", attachments))
            }
        return array.toString()
    }

    companion object {
        const val FILE_NAME = "composer_drafts_v2.json"
        const val MAX_DRAFT_COUNT = 64
        const val MAX_ATTACHMENT_COUNT = 32
        const val MAX_INPUT_CHARS = 32_768
        const val MAX_FIELD_CHARS = 512
        const val MAX_PATH_CHARS = 4_096
        const val MAX_FILE_BYTES = 2 * 1024 * 1024
        fun durableAttachmentPaths(context: Context): Set<String> = runCatching {
            val file = File(context.applicationContext.filesDir, FILE_NAME)
            if (!file.isFile || file.length() > MAX_FILE_BYTES) return@runCatching emptySet()
            val root = AttachmentStorageManager.getAttachmentDirectory(context)
                .canonicalFile
            val array = JSONArray(file.readText(Charsets.UTF_8))
            buildSet {
                for (index in 0 until minOf(array.length(), MAX_DRAFT_COUNT)) {
                    val attachments = array.optJSONObject(index)?.optJSONArray("attachments") ?: continue
                    for (attachmentIndex in 0 until minOf(attachments.length(), MAX_ATTACHMENT_COUNT)) {
                        val path = attachments.optJSONObject(attachmentIndex)
                            ?.optString("path")
                            ?.takeIf { it.length in 1..MAX_PATH_CHARS }
                            ?: continue
                        val candidate = File(path)
                        val canonical = candidate.canonicalFile
                        if (canonical.parentFile?.canonicalPath == root.canonicalPath &&
                            canonical.isFile && !java.nio.file.Files.isSymbolicLink(candidate.toPath())
                        ) add(canonical.path)
                    }
                }
            }
        }.getOrDefault(emptySet())
    }
}
