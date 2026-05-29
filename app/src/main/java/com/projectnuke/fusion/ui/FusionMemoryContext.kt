package com.projectnuke.fusion.ui

import android.content.Context
import android.content.SharedPreferences

const val PrefSavedMemoryContextEnabled = "saved_memory_context_enabled"

private const val MaxSavedMemoryItems = 10
private const val MaxSavedMemoryContextChars = 1500

data class FusionSavedMemoryContext(
    val text: String?,
    val itemCount: Int,
    val characterCount: Int,
    val trimmed: Boolean
)

fun isSavedMemoryContextEnabled(prefs: SharedPreferences): Boolean {
    return prefs.getBoolean(PrefSavedMemoryContextEnabled, false)
}

fun buildSavedMemoryContext(
    context: Context,
    prefs: SharedPreferences,
    currentConversationId: Long?
): FusionSavedMemoryContext {
    if (!isSavedMemoryContextEnabled(prefs)) {
        return FusionSavedMemoryContext(text = null, itemCount = 0, characterCount = 0, trimmed = false)
    }

    val candidates = loadAllConversationMemoryCandidates(context)
        .asSequence()
        .filter { it.enabled }
        .mapNotNull { candidate ->
            sanitizeFusionMemoryText(candidate.text)
                .takeIf { it.isNotBlank() }
                ?.let { cleanText -> candidate to cleanText }
        }
        .distinctBy { (_, cleanText) -> cleanText }
        .sortedWith(
            compareByDescending<Pair<ConversationMemoryCandidate, String>> { (candidate, _) ->
                currentConversationId != null && candidate.conversationId == currentConversationId
            }
                .thenByDescending { (candidate, _) -> candidate.updatedAt ?: candidate.createdAt }
                .thenBy { (_, cleanText) -> cleanText.length }
        )
        .take(MaxSavedMemoryItems)
        .toList()

    if (candidates.isEmpty()) {
        return FusionSavedMemoryContext(text = null, itemCount = 0, characterCount = 0, trimmed = false)
    }

    val header = "[저장된 메모리]"
    val lines = mutableListOf<String>()
    var usedChars = header.length
    var trimmed = false
    for ((_, cleanText) in candidates) {
        val line = "- $cleanText"
        val nextChars = usedChars + 1 + line.length
        if (nextChars > MaxSavedMemoryContextChars) {
            trimmed = true
            break
        }
        lines += line
        usedChars = nextChars
    }
    if (lines.size < candidates.size) trimmed = true
    if (lines.isEmpty()) {
        return FusionSavedMemoryContext(text = null, itemCount = 0, characterCount = 0, trimmed = true)
    }

    val text = buildString {
        appendLine(header)
        append(lines.joinToString("\n"))
    }
    return FusionSavedMemoryContext(
        text = text,
        itemCount = lines.size,
        characterCount = text.length,
        trimmed = trimmed
    )
}

fun sanitizeFusionMemoryText(raw: String): String {
    return raw
        .replace(Regex("""(?is)<fusion_metrics>.*?</fusion_metrics>"""), "")
        .replace(Regex("""(?is)<fusion_thinking>.*?</fusion_thinking>"""), "")
        .replace(Regex("""(?is)<fusion_answer>(.*?)</fusion_answer>"""), "$1")
        .replace(Regex("""(?is)<fusion_attachment_v2>.*?</fusion_attachment_v2>"""), "")
        .replace(Regex("""(?is)<fusion_attachment>.*?</fusion_attachment>"""), "")
        .replace(Regex("""(?is)</?fusion_(?:metrics|thinking|answer|attachment_v2|attachment)>"""), "")
        .lines()
        .filterNot { line ->
            line.contains("FUSION_", ignoreCase = true) ||
                line.contains("<fusion_", ignoreCase = true)
        }
        .joinToString("\n")
        .replace(Regex("""[ \t]+"""), " ")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}
