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
    val trimmed: Boolean,
    val totalSavedCount: Int = 0,
    val enabledCount: Int = 0,
    val excludedByScopeCount: Int = 0
)

fun isSavedMemoryContextEnabled(prefs: SharedPreferences): Boolean {
    return prefs.getBoolean(PrefSavedMemoryContextEnabled, false)
}

fun buildSavedMemoryContext(
    context: Context,
    prefs: SharedPreferences,
    currentConversationId: Long?,
    currentModelId: String? = prefs.getString("selected_model", null),
    globalPreviewOnly: Boolean = false
): FusionSavedMemoryContext {
    val allCandidates = loadAllConversationMemoryCandidates(context)
    val enabledCandidates = allCandidates.filter { it.enabled && it.scope != MemoryScope.DISABLED }
    if (!isSavedMemoryContextEnabled(prefs)) {
        return FusionSavedMemoryContext(
            text = null,
            itemCount = 0,
            characterCount = 0,
            trimmed = false,
            totalSavedCount = allCandidates.size,
            enabledCount = enabledCandidates.size
        )
    }

    val matchingCandidates = enabledCandidates.filter { candidate ->
        when (candidate.scope) {
            MemoryScope.GLOBAL -> true
            MemoryScope.CONVERSATION_ONLY -> !globalPreviewOnly &&
                currentConversationId != null &&
                candidate.conversationId == currentConversationId
            MemoryScope.MODEL_ONLY -> !globalPreviewOnly &&
                !currentModelId.isNullOrBlank() &&
                candidate.modelId == currentModelId
            MemoryScope.DISABLED -> false
        }
    }
    val candidates = matchingCandidates
        .asSequence()
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
                .thenByDescending { (candidate, _) -> candidate.scope == MemoryScope.GLOBAL }
                .thenByDescending { (candidate, _) -> candidate.scope == MemoryScope.MODEL_ONLY }
                .thenByDescending { (candidate, _) -> candidate.updatedAt ?: candidate.createdAt }
                .thenBy { (_, cleanText) -> cleanText.length }
        )
        .take(MaxSavedMemoryItems)
        .toList()

    if (candidates.isEmpty()) {
        return FusionSavedMemoryContext(
            text = null,
            itemCount = 0,
            characterCount = 0,
            trimmed = false,
            totalSavedCount = allCandidates.size,
            enabledCount = enabledCandidates.size,
            excludedByScopeCount = enabledCandidates.size - matchingCandidates.size
        )
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
        return FusionSavedMemoryContext(
            text = null,
            itemCount = 0,
            characterCount = 0,
            trimmed = true,
            totalSavedCount = allCandidates.size,
            enabledCount = enabledCandidates.size,
            excludedByScopeCount = enabledCandidates.size - matchingCandidates.size
        )
    }

    val text = buildString {
        appendLine(header)
        append(lines.joinToString("\n"))
    }
    return FusionSavedMemoryContext(
        text = text,
        itemCount = lines.size,
        characterCount = text.length,
        trimmed = trimmed,
        totalSavedCount = allCandidates.size,
        enabledCount = enabledCandidates.size,
        excludedByScopeCount = enabledCandidates.size - matchingCandidates.size
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
