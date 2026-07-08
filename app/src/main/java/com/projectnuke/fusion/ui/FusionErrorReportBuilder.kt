package com.projectnuke.fusion.ui

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.projectnuke.fusion.data.BenchmarkResultEntity
import com.projectnuke.fusion.modelzoo.FusionModelCatalog
import com.projectnuke.fusion.util.collectFusionSocInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FusionDeveloperLogSnapshot(
    val fullLogText: String,
    val errorReportText: String,
    val memoryStatusText: String,
    val abTestStatusText: String
)

suspend fun buildFusionDeveloperLogSnapshotAsync(
    context: Context,
    prefs: SharedPreferences,
    benchmarkResults: List<BenchmarkResultEntity>,
    events: List<FusionDeveloperLogEvent>
): FusionDeveloperLogSnapshot {
    val abTestHistory = ModelAbTestHistoryStore.loadAsync(context)
    return buildFusionDeveloperLogSnapshotFromHistory(
        context = context,
        prefs = prefs,
        benchmarkResults = benchmarkResults,
        events = events,
        abTestHistory = abTestHistory
    )
}

fun buildFusionDeveloperLogSnapshotFromHistory(
    context: Context,
    prefs: SharedPreferences,
    benchmarkResults: List<BenchmarkResultEntity>,
    events: List<FusionDeveloperLogEvent>,
    abTestHistory: List<StoredAbTestSession>
): FusionDeveloperLogSnapshot {
    val selectedModel = prefs.getString("selected_model", "Gemma 4 E2B-it") ?: "Gemma 4 E2B-it"
    val selectedModelPath = prefs.getString("selected_model_path", null)
    val accelerator = prefs.getString("accelerator", "GPU") ?: "GPU"
    val mtpEnabled = prefs.getBoolean("speculative_decoding_enabled", false)
    val reasoningEnabled = prefs.getBoolean("reasoning_enabled", false)
    val webSearchEnabled = prefs.getBoolean("web_search_enabled", false)
    val maxTokens = prefs.getInt("max_tokens", 4000)
    val temperature = prefs.getFloat("temperature", 1.0f)
    val topK = prefs.getInt("top_k", 64)
    val topP = prefs.getFloat("top_p", 0.95f)
    val appInfo = getFusionAppInfoSummary(context)
    val socInfo = collectFusionSocInfo()
    val memoryInfo = ActivityManager.MemoryInfo().also {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(it)
    }
    val selectedSpec = FusionModelCatalog.all(context).firstOrNull {
        it.displayName == selectedModel || (!selectedModelPath.isNullOrBlank() && it.localPath == selectedModelPath)
    }
    val latestBenchmark = benchmarkResults.firstOrNull()
    val errorEvents = events.filter { it.category.contains("error", ignoreCase = true) || it.category.contains("오류") }
    val savedMemories = loadAllConversationMemoryCandidates(context)
    val savedSummaries = loadAllConversationSummaries(context)
    val memoryContext = buildSavedMemoryContext(context, prefs, currentConversationId = null)
    val latestAbTest = abTestHistory.firstOrNull()
    val abTestStatus = buildString {
        appendLine("A/B 테스트 기록 수: ${abTestHistory.size}개")
        appendLine("최근 A/B 테스트: ${latestAbTest?.let { formatTime(it.createdAt) } ?: "없음"}")
        appendLine("최근 대상 수: ${latestAbTest?.targetCount ?: 0}개")
        append("최근 실패 대상 수: ${latestAbTest?.failureCount ?: 0}개")
    }
    val memoryStatus = buildString {
        appendLine("메모리 사용: ${if (isSavedMemoryContextEnabled(prefs)) "켜짐" else "꺼짐"}")
        appendLine("저장된 메모리 수: ${savedMemories.size}개")
        appendLine("사용 중인 메모리 수: ${savedMemories.count { it.enabled && it.scope != MemoryScope.DISABLED }}개")
        appendLine("전체 대화 메모리 수: ${savedMemories.count { it.scope == MemoryScope.GLOBAL }}개")
        appendLine("대화 전용 메모리 수: ${savedMemories.count { it.scope == MemoryScope.CONVERSATION_ONLY }}개")
        appendLine("모델 전용 메모리 수: ${savedMemories.count { it.scope == MemoryScope.MODEL_ONLY }}개")
        appendLine("사용 안 함 메모리 수: ${savedMemories.count { !it.enabled || it.scope == MemoryScope.DISABLED }}개")
        appendLine("현재 미리보기 포함 메모리 수: ${memoryContext.itemCount}개")
        appendLine("대화 요약 수: ${savedSummaries.size}개")
        appendLine("메모리 컨텍스트 예상 문자 수: ${memoryContext.characterCount}")
        append("메모리 컨텍스트 잘림: ${memoryContext.trimmed}")
    }

    val full = buildString {
        appendLine("개발자 로그")
        appendLine("[현재 상태]")
        appendLine("앱 버전: ${appInfo.first}")
        appendLine("기기: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("SoC/AP: ${socInfo.detectedSocVendor.name}")
        appendLine()
        appendLine("[모델]")
        appendLine("선택 모델: $selectedModel")
        appendLine("모델 패밀리: ${selectedSpec?.family?.name ?: "정보 없음"}")
        appendLine("런타임 형식: ${selectedSpec?.runtimeFormat?.name ?: "정보 없음"}")
        appendLine("가속기: $accelerator")
        appendLine("MTP: ${if (mtpEnabled) "켜짐" else "꺼짐"}")
        appendLine()
        appendLine("[메모리]")
        appendLine("전체 RAM: ${formatBytes(memoryInfo.totalMem)}")
        appendLine("사용 가능 RAM: ${formatBytes(memoryInfo.availMem)}")
        appendLine("저메모리 상태: ${memoryInfo.lowMemory}")
        appendLine()
        appendLine("[벤치마크]")
        appendLine("기록 수: ${benchmarkResults.size}개")
        appendLine("최근 모델: ${latestBenchmark?.modelName ?: "없음"}")
        appendLine("최근 decode tok/s: ${latestBenchmark?.decodeTokensPerSecond?.let { String.format(Locale.US, "%.1f", it) } ?: "정보 없음"}")
        appendLine()
        appendLine("[설정]")
        appendLine("maxTokens=$maxTokens, temperature=$temperature, topK=$topK, topP=$topP")
        appendLine("Reasoning=${if (reasoningEnabled) "켜짐" else "꺼짐"}, Web Search=${if (webSearchEnabled) "켜짐" else "꺼짐"}")
        appendLine()
        appendLine("[저장된 메모리]")
        appendLine(memoryStatus)
        appendLine()
        appendLine("[A/B 테스트]")
        appendLine(abTestStatus)
        appendLine()
        appendLine("[최근 오류]")
        if (events.isEmpty()) {
            appendLine("최근 오류가 없습니다.")
        } else {
            events.take(20).forEach { event ->
                appendLine("${formatTime(event.timestamp)} | ${event.category} | ${event.message}${event.technicalSummary?.let { " | $it" } ?: ""}")
            }
        }
    }.trimEnd()

    val errorReport = buildString {
        appendLine("Fusion 오류 보고서")
        appendLine("[저장된 메모리]")
        appendLine(memoryStatus)
        appendLine()
        appendLine("[A/B 테스트]")
        appendLine(abTestStatus)
        appendLine()
        if (errorEvents.isEmpty()) {
            appendLine("최근 오류가 없습니다.")
        } else {
            errorEvents.take(20).forEach { event ->
                appendLine("${formatTime(event.timestamp)} | ${event.category} | ${event.message}${event.technicalSummary?.let { " | $it" } ?: ""}")
            }
        }
    }.trimEnd()

    return FusionDeveloperLogSnapshot(
        fullLogText = full,
        errorReportText = errorReport,
        memoryStatusText = memoryStatus,
        abTestStatusText = abTestStatus
    )
}

private fun getFusionAppInfoSummary(context: Context): Pair<String, String> {
    val info = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    }.getOrNull()
    val versionName = info?.versionName ?: "현재 버전 정보를 확인할 수 없습니다."
    val versionCode = if (info != null) {
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toString()
        else @Suppress("DEPRECATION") info.versionCode.toString()
    } else {
        "-"
    }
    return versionName to versionCode
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.2f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.2f KB", bytes / kb)
        else -> "$bytes B"
    }
}
