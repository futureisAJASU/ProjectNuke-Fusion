package com.projectnuke.fusion.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FusionDeveloperLogEvent(
    val timestamp: Long,
    val category: String,
    val message: String,
    val technicalSummary: String? = null
)

object FusionDeveloperLogStore {
    private const val PrefsName = "fusion_developer_log"
    private const val KeyEvents = "events"
    private const val MaxEvents = 50

    fun load(context: Context): List<FusionDeveloperLogEvent> {
        val raw = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE).getString(KeyEvents, null)
        val arr = runCatching { JSONArray(raw ?: "[]") }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { index ->
            val obj = arr.optJSONObject(index) ?: return@mapNotNull null
            FusionDeveloperLogEvent(
                timestamp = obj.optLong("timestamp"),
                category = obj.optString("category"),
                message = obj.optString("message"),
                technicalSummary = obj.optString("technicalSummary").takeIf { it.isNotBlank() }
            )
        }
    }

    fun record(context: Context, category: String, message: String, technicalSummary: String? = null) {
        val current = load(context)
        val next = listOf(
            FusionDeveloperLogEvent(
                timestamp = System.currentTimeMillis(),
                category = category.take(40),
                message = message.take(180),
                technicalSummary = technicalSummary?.take(240)
            )
        ) + current
        save(context, next.take(MaxEvents))
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE).edit().remove(KeyEvents).apply()
    }

    private fun save(context: Context, events: List<FusionDeveloperLogEvent>) {
        val arr = JSONArray()
        events.forEach { event ->
            arr.put(
                JSONObject()
                    .put("timestamp", event.timestamp)
                    .put("category", event.category)
                    .put("message", event.message)
                    .put("technicalSummary", event.technicalSummary)
            )
        }
        context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE).edit().putString(KeyEvents, arr.toString()).apply()
    }
}
