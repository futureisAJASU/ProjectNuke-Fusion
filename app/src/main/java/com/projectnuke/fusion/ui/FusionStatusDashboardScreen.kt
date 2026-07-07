package com.projectnuke.fusion.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectnuke.fusion.data.BenchmarkResultEntity
import com.projectnuke.fusion.modelzoo.FusionModelCatalog
import com.projectnuke.fusion.modelzoo.FusionModelMemoryPreflight
import com.projectnuke.fusion.util.FusionMemoryManager
import com.projectnuke.fusion.util.collectFusionSocInfo
import java.util.Locale

private val StatusPanelBg = Color(0xFF171717)
private val StatusCardBg = Color(0xFF111111)
private val StatusTextPrimary = Color(0xFFF5F5F5)
private val StatusTextSecondary = Color(0xFF9E9E9E)
private val StatusAccentBlue = Color(0xFF9FD0FF)

private data class FusionStatusDashboardSnapshot(
    val modelText: String,
    val memoryText: String,
    val performanceText: String,
    val deviceText: String,
    val appText: String,
    val copyText: String
)

@Composable
fun FusionStatusDashboardDialog(
    context: Context,
    prefs: SharedPreferences,
    clipboard: ClipboardManager,
    benchmarkResults: List<BenchmarkResultEntity>,
    onDismiss: () -> Unit
) {
    var abHistoryState by remember { mutableStateOf<List<StoredAbTestSession>?>(null) }
    LaunchedEffect(context) {
        abHistoryState = ModelAbTestHistoryStore.loadAsync(context)
    }

    val abHistory = abHistoryState
    val snapshot = remember(context, benchmarkResults, abHistory) {
        if (abHistory != null) {
            buildFusionStatusDashboardSnapshot(context, prefs, benchmarkResults, abHistory)
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("상태 대시보드") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusCard("상태 대시보드", "Fusion의 현재 실행 상태를 요약합니다.")
                if (snapshot == null) {
                    StatusCard("상태 정보", "상태 정보를 불러오는 중입니다.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { StatusCard("현재 모델", snapshot.modelText) }
                        item { StatusCard("메모리", snapshot.memoryText) }
                        item { StatusCard("성능", snapshot.performanceText) }
                        item { StatusCard("기기", snapshot.deviceText) }
                        item { StatusCard("앱", snapshot.appText) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = snapshot != null,
                onClick = {
                    snapshot?.let {
                        clipboard.setText(AnnotatedString(it.copyText))
                        Toast.makeText(context, "상태 정보를 복사했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            ) { Text("상태 복사", color = if (snapshot != null) StatusAccentBlue else StatusTextSecondary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("닫기", color = StatusTextSecondary) }
        },
        containerColor = StatusPanelBg,
        titleContentColor = StatusTextPrimary,
        textContentColor = StatusTextPrimary
    )
}

@Composable
private fun StatusCard(title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = StatusCardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, color = StatusTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            body.lines().filter { it.isNotBlank() }.forEach { line ->
                Text(
                    text = line,
                    color = StatusTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun buildFusionStatusDashboardSnapshot(
    context: Context,
    prefs: SharedPreferences,
    benchmarkResults: List<BenchmarkResultEntity>,
    abHistory: List<StoredAbTestSession>
): FusionStatusDashboardSnapshot {
    val selectedModel = prefs.getString("selected_model", null)
    val selectedModelPath = prefs.getString("selected_model_path", null)
    val accelerator = prefs.getString("accelerator", "GPU") ?: "GPU"
    val mtpEnabled = prefs.getBoolean("speculative_decoding_enabled", false)
    val maxTokens = prefs.getInt("max_tokens", 4000)
    val temperature = prefs.getFloat("temperature", 1.0f)
    val topK = prefs.getInt("top_k", 64)
    val topP = prefs.getFloat("top_p", 0.95f)
    val selectedSpec = FusionModelCatalog.all(context).firstOrNull {
        it.displayName == selectedModel || (!selectedModelPath.isNullOrBlank() && it.localPath == selectedModelPath)
    }
    val savedMemories = loadAllConversationMemoryCandidates(context)
    val summaries = loadAllConversationSummaries(context)
    val memoryContext = buildSavedMemoryContext(context, prefs, currentConversationId = null, globalPreviewOnly = true)
    val benchmarkHistory = benchmarkResults.sortedByDescending { it.createdAt }
    val latestBenchmark = benchmarkHistory.firstOrNull()

    val latestAb = abHistory.firstOrNull()
    val socInfo = collectFusionSocInfo()
    val memorySnapshot = FusionMemoryManager.getMemorySnapshot(context)
    val appInfo = resolveDashboardAppInfo(context)

    val modelText = if (selectedModel.isNullOrBlank()) {
        "선택된 모델 정보를 확인할 수 없습니다."
    } else {
        buildString {
            appendLine("모델: $selectedModel")
            appendLine("모델 패밀리: ${selectedSpec?.family?.name ?: "정보 없음"}")
            appendLine("런타임 형식: ${selectedSpec?.runtimeFormat?.name ?: "정보 없음"}")
            appendLine("가속기: $accelerator")
            appendLine("MTP: ${if (mtpEnabled) "켜짐" else "꺼짐"}")
            appendLine("maxTokens: $maxTokens")
            appendLine("temperature: $temperature")
            append("topK / topP: $topK / $topP")
        }.trimEnd()
    }

    val memoryText = buildString {
        appendLine("메모리 사용: ${if (isSavedMemoryContextEnabled(prefs)) "켜짐" else "꺼짐"}")
        appendLine("저장된 메모리 수: ${memoryContext.totalSavedCount}개")
        appendLine("사용 중인 메모리 수: ${memoryContext.enabledCount}개")
        appendLine("대화 요약 수: ${summaries.size}개")
        append("메모리 컨텍스트 예상 문자 수: ${memoryContext.characterCount}")
    }

    val performanceText = if (latestBenchmark == null && latestAb == null) {
        "최근 성능 기록이 없습니다."
    } else {
        buildString {
            appendLine("최근 벤치마크 모델: ${latestBenchmark?.modelName ?: "없음"}")
            appendLine("최근 벤치마크 tok/s: ${latestBenchmark?.totalTokensPerSecond?.let { formatSpeed(it.toDouble()) } ?: "정보 없음"}")
            appendLine("벤치마크 기록 수: ${benchmarkHistory.size}개")
            appendLine("A/B 테스트 기록 수: ${abHistory.size}개")
            appendLine("최근 A/B 대상 수: ${latestAb?.targetCount ?: 0}개")
            append("최근 A/B 실패 수: ${latestAb?.failureCount ?: 0}개")
        }.trimEnd()
    }

    val totalRamGb = bytesToGb(memorySnapshot.totalMem)
    val availableRamGb = bytesToGb(memorySnapshot.availMem)
    val deviceText = buildString {
        appendLine("기기: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE}")
        appendLine("SDK: ${Build.VERSION.SDK_INT}")
        appendLine("감지된 SoC/AP: ${socInfo.vendorLabel}")
        appendLine("전체 RAM: ${formatGb(totalRamGb)}GB")
        appendLine("사용 가능 RAM: ${formatGb(availableRamGb)}GB")
        append("RAM 등급: ${dashboardRamClassLabel(totalRamGb)}")
    }

    val appText = buildString {
        appendLine("앱 버전: ${appInfo.first}")
        appendLine("빌드 코드: ${appInfo.second}")
        append("릴리스 채널: 알파")
    }

    val copyText = buildString {
        appendLine("상태 대시보드")
        appendLine()
        appendLine("[현재 모델]")
        appendLine(modelText)
        appendLine()
        appendLine("[메모리]")
        appendLine(memoryText)
        appendLine()
        appendLine("[성능]")
        appendLine(performanceText)
        appendLine()
        appendLine("[기기]")
        appendLine(deviceText)
        appendLine()
        appendLine("[앱]")
        append(appText)
    }.trimEnd()

    return FusionStatusDashboardSnapshot(
        modelText = modelText,
        memoryText = memoryText,
        performanceText = performanceText,
        deviceText = deviceText,
        appText = appText,
        copyText = copyText
    )
}

private fun resolveDashboardAppInfo(context: Context): Pair<String, String> {
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

private fun bytesToGb(bytes: Long): Float {
    if (bytes <= 0L) return 0f
    return bytes / (1024f * 1024f * 1024f)
}

private fun formatGb(value: Float): String = String.format(Locale.US, "%.1f", value)

private fun formatSpeed(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun dashboardRamClassLabel(totalRamGb: Float): String =
    FusionModelMemoryPreflight.classifyDeviceRam((totalRamGb * 1024f * 1024f * 1024f).toLong()).label
