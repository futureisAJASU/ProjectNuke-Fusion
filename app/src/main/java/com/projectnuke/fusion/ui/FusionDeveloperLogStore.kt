package com.projectnuke.fusion.ui

import android.content.Context

data class FusionDeveloperLogEvent(
    val timestamp: Long,
    val category: String,
    val message: String,
    val technicalSummary: String? = null
)

object FusionDeveloperLogStore {
    fun load(context: Context): List<FusionDeveloperLogEvent> {
        return DeveloperLogStore.load(context).map { event ->
            FusionDeveloperLogEvent(
                timestamp = event.timestamp,
                category = event.category,
                message = event.message,
                technicalSummary = event.technicalSummary
            )
        }
    }

    fun record(context: Context, category: String, message: String, technicalSummary: String? = null) {
        DeveloperLogStore.record(context, category, message, technicalSummary)
    }

    fun clear(context: Context) {
        DeveloperLogStore.clear(context)
    }
}
