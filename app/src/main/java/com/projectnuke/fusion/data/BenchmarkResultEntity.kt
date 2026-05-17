package com.projectnuke.fusion.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "benchmark_results")
data class BenchmarkResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val createdAt: Long,
    val modelName: String,
    val modelPath: String?,
    val accelerator: String,
    val actualBackend: String?,
    val mtpEnabled: Boolean,
    val mtpStatus: String,
    val maxTokens: Int,
    val temperature: Float,
    val topK: Int,
    val topP: Float,
    val reasoningEnabled: Boolean,
    val webSearchEnabled: Boolean,
    val promptLabel: String,
    val promptText: String,
    val modelLoadingMs: Long?,
    val firstTokenLatencyMs: Long?,
    val totalGenerationMs: Long,
    val estimatedOutputTokens: Int,
    val totalTokensPerSecond: Float,
    val decodeTokensPerSecond: Float?,
    val success: Boolean,
    val errorMessage: String?,
    val appVersion: String?,
    val deviceModel: String?,
    val androidVersion: String?
)
