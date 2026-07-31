package com.projectnuke.fusion.ui

internal const val ConversationSearchDebounceMs = 300L
internal const val ConversationSearchResultLimit = 100

internal fun boundedConversationSearchQuery(raw: String): String? =
    raw.trim().take(100).takeIf { it.length >= 2 }
