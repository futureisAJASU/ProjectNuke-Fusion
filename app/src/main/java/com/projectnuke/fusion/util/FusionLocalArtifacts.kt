package com.projectnuke.fusion.util

import android.content.Context
import com.projectnuke.fusion.model.GenerationSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class FusionResponseRating(
    val messageId: Long,
    val rating: String,
    val modelName: String,
    val settingsSnapshot: String,
    val createdAt: Long
)

object FusionConversationSummaries {
    private const val PrefName = "fusion_conversation_summaries"

    fun get(context: Context, conversationId: Long): String? {
        if (conversationId == 0L) return null
        return context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
            .getString(conversationId.toString(), null)
            ?.takeIf { it.isNotBlank() }
    }

    fun save(context: Context, conversationId: Long, summary: String) {
        if (conversationId == 0L || summary.isBlank()) return
        context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
            .edit()
            .putString(conversationId.toString(), summary.trim())
            .apply()
    }
}

object FusionMemoryCandidates {
    fun extract(userVisibleTexts: List<String>): List<String> {
        return userVisibleTexts
            .flatMap { it.lines() }
            .map { it.trim().removePrefix("-").trim() }
            .filter { line ->
                line.length in 12..120 &&
                    !line.contains("FUSION_", ignoreCase = true) &&
                    !line.contains("http", ignoreCase = true)
            }
            .distinct()
            .take(8)
            .map { "- $it" }
    }
}

object FusionResponseRatings {
    private fun file(context: Context): File = File(context.filesDir, "fusion_response_ratings.json")

    fun get(context: Context, messageId: Long): String? {
        val file = file(context)
        if (!file.exists()) return null
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length())
                .map { array.getJSONObject(it) }
                .firstOrNull { it.optLong("messageId") == messageId }
                ?.optString("rating")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun toggle(context: Context, rating: FusionResponseRating): Boolean {
        val file = file(context)
        val current = if (file.exists()) {
            runCatching { JSONArray(file.readText()) }.getOrDefault(JSONArray())
        } else {
            JSONArray()
        }
        val kept = JSONArray()
        var removedSame = false
        for (index in 0 until current.length()) {
            val item = current.getJSONObject(index)
            val sameMessage = item.optLong("messageId") == rating.messageId
            if (sameMessage && item.optString("rating") == rating.rating) {
                removedSame = true
            } else if (!sameMessage) {
                kept.put(item)
            }
        }
        if (!removedSame) {
            kept.put(JSONObject().apply {
                put("messageId", rating.messageId)
                put("rating", rating.rating)
                put("modelName", rating.modelName)
                put("settingsSnapshot", rating.settingsSnapshot)
                put("createdAt", rating.createdAt)
            })
        }
        file.writeText(kept.toString(2))
        return !removedSame
    }
}

fun GenerationSettings.toRatingSnapshot(): String {
    return "maxTokens=$maxTokens, temp=$temperature, topK=$topK, topP=$topP, accelerator=${accelerator.name}, mtp=${speculativeDecodingEnabled == true}"
}
