package com.projectnuke.fusion.ui

import android.content.Context
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

enum class AbResultRating {
    NONE,
    PREFERRED,
    DISLIKED
}

data class StoredAbTestResult(
    val targetLabel: String,
    val modelName: String,
    val modelId: String?,
    val accelerator: String,
    val maxTokens: Int,
    val temperature: Float,
    val topK: Int,
    val topP: Float,
    val mtpEnabled: Boolean,
    val reasoningEnabled: Boolean,
    val memoryEnabled: Boolean,
    val answer: String?,
    val success: Boolean,
    val errorSummary: String?,
    val firstTokenLatencyMs: Long?,
    val totalGenerationTimeMs: Long,
    val estimatedTokens: Int,
    val totalTokensPerSecond: Double,
    val decodeTokensPerSecond: Double?,
    val rating: AbResultRating = AbResultRating.NONE
)

data class StoredAbTestSession(
    val id: String,
    val fullPrompt: String,
    val createdAt: Long,
    val results: List<StoredAbTestResult>
) {
    val promptPreview: String get() = fullPrompt.replace(Regex("""\s+"""), " ").trim().take(100)
    val targetCount: Int get() = results.size
    val failureCount: Int get() = results.count { !it.success }
}

object ModelAbTestHistoryStore {
    private const val MaxSessions = 20
    private const val FileName = "fusion_ab_test_history.json"
    private val writeMutex = Mutex()

    fun load(context: Context): List<StoredAbTestSession> {
        val file = historyFile(context)
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.toStoredSession()
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, session: StoredAbTestSession) {
        runBlocking {
            writeMutex.withLock {
                val next = (listOf(session) + load(context).filterNot { it.id == session.id })
                    .sortedByDescending { it.createdAt }
                    .take(MaxSessions)
                write(context, next)
            }
        }
    }

    fun delete(context: Context, sessionId: String) {
        runBlocking {
            writeMutex.withLock {
                write(context, load(context).filterNot { it.id == sessionId })
            }
        }
    }

    fun clear(context: Context) {
        runBlocking {
            writeMutex.withLock {
                historyFile(context).delete()
            }
        }
    }

    fun updateRating(context: Context, sessionId: String, targetLabel: String, rating: AbResultRating) {
        runBlocking {
            writeMutex.withLock {
                val updated = load(context).map { session ->
                    if (session.id != sessionId) {
                        session
                    } else {
                        session.copy(
                            results = session.results.map { result ->
                                if (result.targetLabel == targetLabel) result.copy(rating = rating) else result
                            }
                        )
                    }
                }
                write(context, updated)
            }
        }
    }

    private fun write(context: Context, sessions: List<StoredAbTestSession>) {
        val array = JSONArray()
        sessions.forEach { array.put(it.toJson()) }
        historyFile(context).writeText(array.toString(2), Charsets.UTF_8)
    }

    private fun historyFile(context: Context): File = File(context.filesDir, FileName)
}

private fun StoredAbTestSession.toJson(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("fullPrompt", fullPrompt)
        .put("createdAt", createdAt)
        .put("results", JSONArray().also { array -> results.forEach { array.put(it.toJson()) } })
}

private fun StoredAbTestResult.toJson(): JSONObject {
    return JSONObject()
        .put("targetLabel", targetLabel)
        .put("modelName", modelName)
        .put("modelId", modelId)
        .put("accelerator", accelerator)
        .put("maxTokens", maxTokens)
        .put("temperature", temperature.toDouble())
        .put("topK", topK)
        .put("topP", topP.toDouble())
        .put("mtpEnabled", mtpEnabled)
        .put("reasoningEnabled", reasoningEnabled)
        .put("memoryEnabled", memoryEnabled)
        .put("answer", answer)
        .put("success", success)
        .put("errorSummary", errorSummary)
        .put("firstTokenLatencyMs", firstTokenLatencyMs)
        .put("totalGenerationTimeMs", totalGenerationTimeMs)
        .put("estimatedTokens", estimatedTokens)
        .put("totalTokensPerSecond", totalTokensPerSecond)
        .put("decodeTokensPerSecond", decodeTokensPerSecond)
        .put("rating", rating.name)
}

private fun JSONObject.toStoredSession(): StoredAbTestSession? {
    val sessionId = optString("id").takeIf { it.isNotBlank() } ?: return null
    val resultArray = optJSONArray("results") ?: JSONArray()
    return StoredAbTestSession(
        id = sessionId,
        fullPrompt = optString("fullPrompt"),
        createdAt = optLong("createdAt"),
        results = (0 until resultArray.length()).mapNotNull { index ->
            resultArray.optJSONObject(index)?.toStoredResult()
        }
    )
}

private fun JSONObject.toStoredResult(): StoredAbTestResult? {
    val label = optString("targetLabel").takeIf { it.isNotBlank() } ?: return null
    return StoredAbTestResult(
        targetLabel = label,
        modelName = optString("modelName"),
        modelId = optString("modelId").takeIf { it.isNotBlank() },
        accelerator = optString("accelerator"),
        maxTokens = optInt("maxTokens"),
        temperature = optDouble("temperature").toFloat(),
        topK = optInt("topK"),
        topP = optDouble("topP").toFloat(),
        mtpEnabled = optBoolean("mtpEnabled"),
        reasoningEnabled = optBoolean("reasoningEnabled"),
        memoryEnabled = optBoolean("memoryEnabled"),
        answer = optString("answer").takeIf { it.isNotBlank() },
        success = optBoolean("success"),
        errorSummary = optString("errorSummary").takeIf { it.isNotBlank() },
        firstTokenLatencyMs = optLong("firstTokenLatencyMs").takeIf { has("firstTokenLatencyMs") && it > 0L },
        totalGenerationTimeMs = optLong("totalGenerationTimeMs"),
        estimatedTokens = optInt("estimatedTokens"),
        totalTokensPerSecond = optDouble("totalTokensPerSecond"),
        decodeTokensPerSecond = optDouble("decodeTokensPerSecond").takeIf { has("decodeTokensPerSecond") },
        rating = runCatching { AbResultRating.valueOf(optString("rating", AbResultRating.NONE.name)) }
            .getOrDefault(AbResultRating.NONE)
    )
}

fun StoredAbTestSession.toMarkdown(): String = buildString {
    appendLine("# Fusion A/B 테스트 결과")
    appendLine()
    appendLine("- 날짜: ${formatAbHistoryTime(createdAt)}")
    appendLine("- 프롬프트: $fullPrompt")
    appendLine("- 대상 수: $targetCount")
    results.forEach { result ->
        appendLine()
        appendLine("## ${result.targetLabel}")
        appendLine("- 모델: ${result.modelName}")
        appendLine("- 설정: ${result.settingsSummary()}")
        appendLine("- 속도: 첫 토큰 ${result.firstTokenLatencyMs?.let { "${it}ms" } ?: "측정 불가"} · 총 ${result.totalGenerationTimeMs}ms · 전체 ${result.totalTokensPerSecond.formatAbSpeed()} · 디코딩 ${result.decodeTokensPerSecond?.formatAbSpeed() ?: "측정 불가"}")
        appendLine("- 상태: ${if (result.success) "성공" else "실패"}")
        if (result.success) {
            appendLine()
            appendLine("답변:")
            appendLine(result.answer.orEmpty())
        } else {
            appendLine("- 오류: ${result.errorSummary ?: "이 대상의 실행에 실패했습니다."}")
        }
    }
    appendLine()
    appendLine("## 비교 요약")
    appendLine(buildStoredAbComparisonSummary(this@toMarkdown))
}.trim()

fun StoredAbTestResult.settingsSummary(): String {
    return "가속기=$accelerator · maxTokens=$maxTokens · temp=$temperature · topK=$topK · topP=$topP · MTP=${if (mtpEnabled) "켜짐" else "꺼짐"} · Reasoning=${if (reasoningEnabled) "켜짐" else "꺼짐"} · 메모리=${if (memoryEnabled) "사용" else "사용 안 함"}"
}

fun buildStoredAbComparisonSummary(session: StoredAbTestSession): String {
    val successful = session.results.filter { it.success }
    val fastestFirst = successful.filter { it.firstTokenLatencyMs != null }.minByOrNull { it.firstTokenLatencyMs ?: Long.MAX_VALUE }
    val fastestDecode = successful.filter { it.decodeTokensPerSecond != null }.maxByOrNull { it.decodeTokensPerSecond ?: 0.0 }
    val shortest = successful.minByOrNull { it.answer.orEmpty().length }
    val longest = successful.maxByOrNull { it.answer.orEmpty().length }
    return buildString {
        appendLine("- 가장 빠른 응답 시작: ${fastestFirst?.targetLabel ?: "측정 불가"}")
        appendLine("- 가장 높은 디코딩 속도: ${fastestDecode?.targetLabel ?: "측정 불가"}")
        appendLine("- 가장 짧은 답변: ${shortest?.targetLabel ?: "측정 불가"}")
        appendLine("- 가장 긴 답변: ${longest?.targetLabel ?: "측정 불가"}")
        append("- 실패한 대상: ${if (session.failureCount == 0) "없음" else "${session.failureCount}개"}")
    }
}

fun formatAbHistoryTime(timestamp: Long): String {
    return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))
}

private fun Double.formatAbSpeed(): String = String.format(java.util.Locale.US, "%.1f tok/s", this)
