package com.projectnuke.fusion.ui

import android.content.Context
import com.projectnuke.fusion.data.MessageEntity
import org.json.JSONObject

internal data class ResponseVersionState(
    val groupByMessageId: Map<Long, Long> = emptyMap(),
    val activeMessageIdByGroup: Map<Long, Long> = emptyMap()
)

internal sealed interface ChatTimelineItem {
    data class User(val message: MessageEntity) : ChatTimelineItem
    data class AssistantVersions(
        val groupId: Long,
        val versions: List<MessageEntity>,
        val activeIndex: Int
    ) : ChatTimelineItem {
        val activeMessage: MessageEntity
            get() = versions[activeIndex]
    }
}

private const val ResponseVersionPrefsName = "fusion_response_versions"

internal fun loadResponseVersionState(context: Context, conversationId: Long): ResponseVersionState {
    val raw = context.getSharedPreferences(ResponseVersionPrefsName, Context.MODE_PRIVATE)
        .getString(conversationId.toString(), null)
        ?: return ResponseVersionState()
    return runCatching {
        val root = JSONObject(raw)
        ResponseVersionState(
            groupByMessageId = root.optJSONObject("groups").toLongMap(),
            activeMessageIdByGroup = root.optJSONObject("active").toLongMap()
        )
    }.getOrDefault(ResponseVersionState())
}

internal fun saveResponseVersionState(
    context: Context,
    conversationId: Long,
    state: ResponseVersionState
) {
    val root = JSONObject()
        .put("groups", state.groupByMessageId.toJsonObject())
        .put("active", state.activeMessageIdByGroup.toJsonObject())
    context.getSharedPreferences(ResponseVersionPrefsName, Context.MODE_PRIVATE)
        .edit()
        .putString(conversationId.toString(), root.toString())
        .apply()
}

internal fun buildChatTimeline(
    messages: List<MessageEntity>,
    state: ResponseVersionState
): List<ChatTimelineItem> {
    val items = mutableListOf<ChatTimelineItem>()
    val groups = linkedMapOf<Long, MutableList<MessageEntity>>()
    messages.forEach { message ->
        if (message.role == "user") {
            items += ChatTimelineItem.User(message)
        } else {
            val groupId = state.groupByMessageId[message.id] ?: -message.id
            val versions = groups[groupId]
            if (versions == null) {
                groups[groupId] = mutableListOf(message)
                items += ChatTimelineItem.AssistantVersions(groupId, emptyList(), 0)
            } else {
                versions += message
            }
        }
    }

    return items.map { item ->
        if (item !is ChatTimelineItem.AssistantVersions) return@map item
        val versions = groups[item.groupId].orEmpty()
        val selectedId = state.activeMessageIdByGroup[item.groupId]
        val activeIndex = versions.indexOfFirst { it.id == selectedId }
            .takeIf { it >= 0 }
            ?: versions.lastIndex
        item.copy(versions = versions, activeIndex = activeIndex.coerceAtLeast(0))
    }
}

internal fun activeTimelineMessages(
    messages: List<MessageEntity>,
    state: ResponseVersionState
): List<MessageEntity> = buildChatTimeline(messages, state).map {
    when (it) {
        is ChatTimelineItem.User -> it.message
        is ChatTimelineItem.AssistantVersions -> it.activeMessage
    }
}

private fun JSONObject?.toLongMap(): Map<Long, Long> {
    if (this == null) return emptyMap()
    return buildMap {
        keys().forEach { key ->
            val mapKey = key.toLongOrNull() ?: return@forEach
            val value = optLong(key, Long.MIN_VALUE)
            if (value != Long.MIN_VALUE) put(mapKey, value)
        }
    }
}

private fun Map<Long, Long>.toJsonObject(): JSONObject = JSONObject().also { json ->
    forEach { (key, value) -> json.put(key.toString(), value) }
}
