package com.projectnuke.fusion.ui

import com.projectnuke.fusion.search.stripSearchSourcesMetadata

internal fun visibleAssistantHistoryText(content: String): String {
    val withoutSources = stripSearchSourcesMetadata(content)
    val withoutMetrics = Regex("""(?is)<fusion_metrics>.*?</fusion_metrics>""").replace(withoutSources, "")
    val withoutThinking = Regex("""(?is)<fusion_thinking>.*?</fusion_thinking>""").replace(withoutMetrics, "")
    return withoutThinking
        .replace(Regex("""</?fusion_(?:thinking|answer|metrics)>""", RegexOption.IGNORE_CASE), "")
        .trim()
}
