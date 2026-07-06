package com.projectnuke.fusion.ui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectnuke.fusion.data.AppDatabase
import com.projectnuke.fusion.data.BenchmarkDao
import com.projectnuke.fusion.data.BenchmarkResultEntity
import com.projectnuke.fusion.llm.ChatGenerationRunningException
import com.projectnuke.fusion.llm.FusionRuntimeLock
import com.projectnuke.fusion.llm.LiteRtLlmEngine
import com.projectnuke.fusion.llm.FusionRuntimeManager
import com.projectnuke.fusion.llm.MtpRuntimeStatus
import com.projectnuke.fusion.model.AcceleratorMode
import com.projectnuke.fusion.model.ChatMessage
import com.projectnuke.fusion.model.GenerationSettings
import com.projectnuke.fusion.util.FusionMemoryManager
import com.projectnuke.fusion.util.buildEffectiveRuntimeSettings
import com.projectnuke.fusion.util.toKoreanMtpStatus
import java.io.File
import java.util.Locale
import kotlinx.coroutines.launch

private const val FusionBenchmarkPrompt = "반도체 공정과 메모리 계층 구조를 1000자 정도로 설명해 주세요. 핵심 개념, 성능 병목, 전력 효율 관점까지 포함해 주세요."
private const val FusionBenchmarkPromptLabel = "반도체 공정/메모리 계층 1000자 설명"

@Composable
fun FusionBenchmarkScreen(
    onBack: () -> Unit,
    initialShowHistory: Boolean = false,
    initialHistoryModelFilter: String? = null
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("fusion_chat_settings", Context.MODE_PRIVATE) }
    val engine = remember { FusionRuntimeManager.sharedEngine(context) }
    val db = remember { AppDatabase.getInstance(context) }
    val benchmarkDao = remember { db.benchmarkDao() }
    var showHistory by remember(initialShowHistory) { mutableStateOf(initialShowHistory) }

        DisposableEffect(engine) {
            val unregisterMemoryUnloader = FusionMemoryManager.registerIdleEngineUnloader {
                Log.i("FusionMemory", "Requesting idle shared engine unload under memory pressure")
                scope.launch {
                    FusionRuntimeManager.unloadSharedEngineWhenRuntimeIdle("benchmark_memory_pressure")
                }
            }
            onDispose {
                unregisterMemoryUnloader()
            }
        }

    if (showHistory) {
        BackHandler { showHistory = false }
        BenchmarkHistoryScreen(
            dao = benchmarkDao,
            onBack = { showHistory = false },
            initialModelFilter = initialHistoryModelFilter
        )
        return
    }

    val selectedModel = prefs.getString("selected_model", "Gemma 4 E2B-it") ?: "Gemma 4 E2B-it"
    val selectedModelPath = prefs.getString("selected_model_path", null)
    val settings = loadBenchmarkSettingsFromPrefs(prefs)
    val safeMaxTokensCap = benchmarkSafeMaxTokensCap(context)
    val resolvedModelPath = resolveBenchmarkModelPath(context, selectedModel, selectedModelPath)
    var isRunning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            status = null
            result = null
        }
    }

    fun leaveBenchmark() {
        if (isRunning) {
            Toast.makeText(context, "벤치마크 실행 중에는 화면을 나갈 수 없습니다. 완료 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isRunning) {
            status = null
            result = null
        }
        onBack()
    }

    BackHandler(enabled = isRunning) {
        Toast.makeText(context, "벤치마크 실행 중에는 뒤로 갈 수 없습니다. 완료 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF000000)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { leaveBenchmark() }) { Text("뒤로", color = Color(0xFFF5F5F5)) }
            Column(modifier = Modifier.weight(1f)) {
                Text("벤치마크", color = Color(0xFFF5F5F5), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("선택한 모델의 응답 속도와 생성 성능을 측정합니다.", color = Color(0xFF9E9E9E), fontSize = 12.sp)
            }
            TextButton(
                enabled = !isRunning,
                onClick = { showHistory = true }
            ) {
                Text("기록", color = Color(0xFFF5F5F5))
            }
        }

        BenchmarkCardBlock {
            Text("모델: $selectedModel", color = Color(0xFFF5F5F5))
            Text("경로: ${resolvedModelPath ?: "없음"}", color = Color(0xFF9E9E9E), fontSize = 12.sp)
            Text("가속기: ${settings.accelerator.name}", color = Color(0xFFF5F5F5))
            Text("MTP 가속: ${initialBenchmarkMtpStatusLabel(settings, selectedModel, resolvedModelPath)}", color = Color(0xFFF5F5F5))
            Text("maxTokens=${settings.maxTokens} / temp=${settings.temperature} / topK=${settings.topK} / topP=${settings.topP}", color = Color(0xFF9E9E9E), fontSize = 12.sp)
            safeMaxTokensCap?.let {
                Text("벤치마크 안전 제한: maxTokens=$it", color = Color(0xFF9E9E9E), fontSize = 12.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF171717)) {
                TextButton(
                    enabled = !isRunning,
                    onClick = {
                        val snapshot = loadBenchmarkSnapshot(context, prefs)
                        if (FusionRuntimeLock.isChatGenerationRunning) {
                            Toast.makeText(context, "현재 응답을 생성하는 중입니다. 완료 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        if (snapshot.modelPath.isNullOrBlank() || !File(snapshot.modelPath).exists()) {
                            Log.e("FusionBenchmark", "Selected model file missing: ${snapshot.modelPath}")
                            DeveloperLogStore.record(context, "benchmark", "벤치마크 시작 실패", "model file missing")
                            Toast.makeText(context, "선택한 모델 파일을 찾을 수 없습니다. 모델을 다시 선택해 주세요.", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        FusionMemoryManager.logMemorySnapshot("FusionMemory", context, "benchmark-start")
                        val memorySnapshot = FusionMemoryManager.getMemorySnapshot(context)
                        if (memorySnapshot.lowMemory || FusionMemoryManager.shouldBlockBenchmark(context)) {
                            status = "사용 가능한 메모리가 부족하여 벤치마크를 시작할 수 없습니다."
                            DeveloperLogStore.record(context, "memory", "벤치마크 시작 차단", "low memory")
                            Toast.makeText(context, "사용 가능한 메모리가 부족하여 벤치마크를 시작할 수 없습니다.", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        if (memorySnapshot.availMem < memorySnapshot.threshold * 2) {
                            Toast.makeText(context, "메모리 사용량이 높아 일부 기능이 느려질 수 있습니다.", Toast.LENGTH_SHORT).show()
                        }

                        isRunning = true
                        status = "벤치마크를 준비하는 중입니다."
                        result = null
                        DeveloperLogStore.record(context, "benchmark", "벤치마크 시작", snapshot.modelName)

                        scope.launch {
                            runBenchmark(
                                context = context,
                                engine = engine,
                                benchmarkDao = benchmarkDao,
                                snapshot = snapshot,
                                onStatus = { status = it },
                                onResult = {
                                    result = it
                                    clipboard.setText(AnnotatedString(it))
                                },
                                onFinished = { isRunning = false }
                            )
                        }
                    }
                ) { Text("벤치마크 시작", color = Color(0xFFF5F5F5)) }
            }
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF171717)) {
                TextButton(
                    enabled = !result.isNullOrBlank(),
                    onClick = {
                        clipboard.setText(AnnotatedString(result.orEmpty()))
                        Toast.makeText(context, "벤치마크 결과를 복사했습니다.", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("결과 복사", color = Color(0xFFF5F5F5)) }
            }
        }

        if (isRunning) {
            BenchmarkCardBlock {
                Text(status ?: "벤치마크를 준비하는 중입니다.", color = Color(0xFFF5F5F5), fontSize = 13.sp)
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF9FD0FF),
                    trackColor = Color(0xFF2A2A2A)
                )
            }
        }

        status?.takeIf { it.isNotBlank() }?.let {
            if (!isRunning) {
                BenchmarkCardBlock { Text(it, color = Color(0xFFF5F5F5)) }
            }
        }
        result?.takeIf { it.isNotBlank() }?.let {
            BenchmarkCardBlock { Text(it, color = Color(0xFFF5F5F5), fontSize = 13.sp) }
        }
    }
}

private data class BenchmarkSnapshot(
    val modelName: String,
    val modelPath: String?,
    val settings: GenerationSettings,
    val requestedMaxTokens: Int,
    val safeMaxTokensCap: Int?,
    val reasoningEnabled: Boolean,
    val webSearchEnabled: Boolean
)

private suspend fun runBenchmark(
    context: Context,
    engine: LiteRtLlmEngine,
    benchmarkDao: BenchmarkDao,
    snapshot: BenchmarkSnapshot,
    onStatus: (String?) -> Unit,
    onResult: (String) -> Unit,
    onFinished: () -> Unit
) {
    try {
        FusionRuntimeLock.withExclusiveBenchmark(
            onPrepareExclusiveMode = {
                onStatus("모델 리소스를 정리하는 중입니다.")
                Log.i("FusionEngine", "Requesting chat engine unload before benchmark")
                FusionRuntimeLock.requestChatEngineUnloadForBenchmark()
                FusionRuntimeManager.unloadSharedEngineAfterExclusive("benchmark_prepare")
            }
        ) {
            try {
                Log.i(
                    "FusionBenchmark",
                    "Benchmark start model=${snapshot.modelName} path=${snapshot.modelPath} accelerator=${snapshot.settings.accelerator.name} mtp=${snapshot.settings.speculativeDecodingEnabled == true} maxTokens=${snapshot.settings.maxTokens} temp=${snapshot.settings.temperature} topK=${snapshot.settings.topK} topP=${snapshot.settings.topP}"
                )
                Log.i("FusionEngine", "Benchmark marks engine stale and reloads runtime settings")
                onStatus("모델 설정을 다시 적용하는 중입니다.")
                val reloadStartMs = SystemClock.elapsedRealtime()
                FusionRuntimeManager.unloadSharedEngineAfterExclusive("benchmark_reload")
                val engineReloadMs = SystemClock.elapsedRealtime() - reloadStartMs
                onStatus("모델을 불러오는 중입니다.")

            val start = SystemClock.elapsedRealtime()
            var firstTokenMs: Long? = null
            val output = StringBuilder()
            onStatus("응답을 생성하는 중입니다.")
            engine.generateStreaming(
                messages = listOf(ChatMessage("user", FusionBenchmarkPrompt)),
                modelPath = snapshot.modelPath.orEmpty(),
                settings = snapshot.settings,
                onToken = { token ->
                    if (firstTokenMs == null && token.isNotEmpty()) {
                        firstTokenMs = SystemClock.elapsedRealtime() - start
                    }
                    output.append(token)
                }
            )

            val totalMs = SystemClock.elapsedRealtime() - start
            val text = output.toString().replace(Regex("""</?fusion_(thinking|answer|metrics)>"""), "").trim()
            output.clear()
            if (looksLikeBenchmarkLiteRtFailure(text)) {
                throw IllegalStateException("Benchmark LiteRT generation failed")
            }
            val estimatedTokens = estimateBenchmarkTokens(text)
            val totalTps = if (totalMs > 0) estimatedTokens * 1000.0 / totalMs else 0.0
            val decodeMs = firstTokenMs?.let { totalMs - it }?.takeIf { it > 0 }
            val decodeTps = decodeMs?.let { estimatedTokens * 1000.0 / it }
            val mtpStatus = engine.lastMtpStatus
            val effective = buildEffectiveRuntimeSettings(
                modelName = snapshot.modelName,
                modelPath = snapshot.modelPath,
                settings = snapshot.settings,
                reasoningEnabled = snapshot.reasoningEnabled,
                webSearchEnabled = snapshot.webSearchEnabled,
                mtpStatus = mtpStatus
            )

            val resultText = buildString {
                appendLine(buildAppliedBenchmarkSettingsLine(snapshot, effective.actualBackend, mtpStatus))
                if (snapshot.safeMaxTokensCap != null && snapshot.requestedMaxTokens != snapshot.settings.maxTokens) {
                    appendLine("설정값: maxTokens=${snapshot.requestedMaxTokens}")
                    appendLine("적용값: maxTokens=${snapshot.settings.maxTokens}")
                }
                appendLine("모델 설정을 다시 적용했습니다.")
                appendLine("모델 설정 재적용 시간: ${engineReloadMs}ms")
                appendLine("모델 로딩 시간: 측정 불가")
                appendLine("첫 토큰 시간: ${firstTokenMs?.let { "${it}ms" } ?: "측정 불가"}")
                appendLine("총 생성 시간: ${"%.2f".format(Locale.US, totalMs / 1000.0)}s")
                appendLine("추정 출력 토큰 수: $estimatedTokens")
                appendLine("전체 기준 토큰 속도: ${"%.1f".format(Locale.US, totalTps)} tok/s")
                appendLine("디코딩 기준 토큰 속도: ${decodeTps?.let { "${"%.1f".format(Locale.US, it)} tok/s" } ?: "측정 불가"}")
                appendLine("가속기: ${effective.actualBackend}")
                appendLine("MTP 가속: ${mtpStatus.toKoreanMtpStatus()}")
                appendLine()
                appendLine("정확한 비교를 위해 MTP 꺼짐/켜짐을 번갈아 3회 이상 측정해 주세요.")
            }.trim()

            onResult(resultText)
            onStatus("측정 결과를 저장하는 중입니다.")
            saveBenchmarkResult(
                context = context,
                benchmarkDao = benchmarkDao,
                snapshot = snapshot,
                mtpStatus = mtpStatus,
                modelLoadingMs = engineReloadMs,
                firstTokenLatencyMs = firstTokenMs,
                totalGenerationMs = totalMs,
                estimatedOutputTokens = estimatedTokens,
                totalTokensPerSecond = totalTps.toFloat(),
                decodeTokensPerSecond = decodeTps?.toFloat(),
                success = true,
                errorMessage = null
            )
            Toast.makeText(context, "벤치마크 기록을 저장했습니다.", Toast.LENGTH_SHORT).show()
            Log.i("FusionBenchmark", "Benchmark success totalMs=$totalMs tokens=$estimatedTokens totalTps=$totalTps decodeTps=$decodeTps mtp=${mtpStatus.name}")
            DeveloperLogStore.record(context, "benchmark", "벤치마크 성공", "model=${snapshot.modelName}, decodeTps=${decodeTps?.toFloat()}")
                onStatus("벤치마크가 완료되었습니다.")
            } finally {
                FusionRuntimeManager.unloadSharedEngineAfterExclusive("benchmark_after_run")
                System.gc()
            }
        }
    } catch (e: ChatGenerationRunningException) {
        Log.i("FusionBenchmark", "Benchmark blocked because chat generation is running")
        onStatus(null)
        Toast.makeText(context, "현재 응답을 생성하는 중입니다. 완료 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Log.e("FusionBenchmark", "Benchmark generation failed", e)
        Log.e("FusionEngine", "모델 설정을 적용할 수 없습니다.", e)
        DeveloperLogStore.record(context, "benchmark", "벤치마크 실패", e::class.java.simpleName)
        onStatus(null)
        val userMessage = benchmarkUserErrorMessage(e, snapshot)
        Toast.makeText(context, userMessage, Toast.LENGTH_SHORT).show()
        runCatching {
            saveBenchmarkResult(
                context = context,
                benchmarkDao = benchmarkDao,
                snapshot = snapshot,
                mtpStatus = engine.lastMtpStatus,
                modelLoadingMs = null,
                firstTokenLatencyMs = null,
                totalGenerationMs = 0L,
                estimatedOutputTokens = 0,
                totalTokensPerSecond = 0f,
                decodeTokensPerSecond = null,
                success = false,
                errorMessage = sanitizeBenchmarkErrorMessage(e, snapshot)
            )
        }.onFailure { saveError ->
            Log.e("FusionBenchmark", "Failed to save benchmark result", saveError)
            Toast.makeText(context, "벤치마크 기록을 저장할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    } finally {
        FusionRuntimeManager.unloadSharedEngineWhenRuntimeIdle("benchmark_final_cleanup")
        System.gc()
        onFinished()
    }
}

private suspend fun saveBenchmarkResult(
    context: Context,
    benchmarkDao: BenchmarkDao,
    snapshot: BenchmarkSnapshot,
    mtpStatus: MtpRuntimeStatus,
    modelLoadingMs: Long?,
    firstTokenLatencyMs: Long?,
    totalGenerationMs: Long,
    estimatedOutputTokens: Int,
    totalTokensPerSecond: Float,
    decodeTokensPerSecond: Float?,
    success: Boolean,
    errorMessage: String?
) {
    benchmarkDao.insert(
        BenchmarkResultEntity(
            createdAt = System.currentTimeMillis(),
            modelName = snapshot.modelName,
            modelPath = snapshot.modelPath,
            accelerator = snapshot.settings.accelerator.name,
            actualBackend = snapshot.settings.accelerator.name,
            mtpEnabled = snapshot.settings.speculativeDecodingEnabled == true,
            mtpStatus = mtpStatus.toKoreanMtpStatus(),
            maxTokens = snapshot.settings.maxTokens,
            temperature = snapshot.settings.temperature,
            topK = snapshot.settings.topK,
            topP = snapshot.settings.topP,
            reasoningEnabled = snapshot.reasoningEnabled,
            webSearchEnabled = snapshot.webSearchEnabled,
            promptLabel = FusionBenchmarkPromptLabel,
            promptText = FusionBenchmarkPrompt,
            modelLoadingMs = modelLoadingMs,
            firstTokenLatencyMs = firstTokenLatencyMs,
            totalGenerationMs = totalGenerationMs,
            estimatedOutputTokens = estimatedOutputTokens,
            totalTokensPerSecond = totalTokensPerSecond,
            decodeTokensPerSecond = decodeTokensPerSecond,
            success = success,
            errorMessage = errorMessage,
            appVersion = context.appVersionName(),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        )
    )
}

private fun looksLikeBenchmarkLiteRtFailure(text: String): Boolean {
    return text.contains("Failed to create engine", ignoreCase = true) ||
        text.contains("litert_compiled_model", ignoreCase = true) ||
        text.contains("LiteRT-LM", ignoreCase = true) && text.contains("INTERNAL", ignoreCase = true)
}

private fun buildAppliedBenchmarkSettingsLine(
    snapshot: BenchmarkSnapshot,
    actualBackend: String?,
    mtpStatus: MtpRuntimeStatus
): String {
    val mtpText = if (snapshot.settings.speculativeDecodingEnabled == true) {
        "MTP ${mtpStatus.toKoreanMtpStatus()}"
    } else {
        "MTP 꺼짐"
    }
    return "적용된 설정: ${actualBackend ?: snapshot.settings.accelerator.name} · $mtpText · maxTokens=${snapshot.settings.maxTokens} · temp=${snapshot.settings.temperature} · topK=${snapshot.settings.topK} · topP=${snapshot.settings.topP}"
}

@Composable
private fun BenchmarkCardBlock(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF111111), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

private fun loadBenchmarkSnapshot(context: Context, prefs: android.content.SharedPreferences): BenchmarkSnapshot {
    val modelName = prefs.getString("selected_model", "Gemma 4 E2B-it") ?: "Gemma 4 E2B-it"
    val selectedPath = prefs.getString("selected_model_path", null)
    val rawSettings = loadBenchmarkSettingsFromPrefs(prefs)
    val safeCap = benchmarkSafeMaxTokensCap(context)
    val recommendedMaxTokens = FusionMemoryManager.recommendedBenchmarkMaxTokens(context, rawSettings.maxTokens)
    val effectiveSettings = if (recommendedMaxTokens != rawSettings.maxTokens) {
        rawSettings.copy(
            maxTokens = recommendedMaxTokens,
            speculativeDecodingEnabled = resolveBenchmarkSpeculativeDecoding(rawSettings, modelName)
        )
    } else {
        rawSettings.copy(speculativeDecodingEnabled = resolveBenchmarkSpeculativeDecoding(rawSettings, modelName))
    }
    return BenchmarkSnapshot(
        modelName = modelName,
        modelPath = resolveBenchmarkModelPath(context, modelName, selectedPath),
        settings = effectiveSettings,
        requestedMaxTokens = rawSettings.maxTokens,
        safeMaxTokensCap = safeCap,
        reasoningEnabled = false,
        webSearchEnabled = false
    )
}

private fun benchmarkSafeMaxTokensCap(context: Context): Int? {
    val recommended = FusionMemoryManager.recommendedBenchmarkMaxTokens(context, Int.MAX_VALUE)
    return recommended.takeIf { it < Int.MAX_VALUE }
}

private fun resolveBenchmarkSpeculativeDecoding(
    settings: GenerationSettings,
    modelName: String
): Boolean? {
    if (!modelName.contains("Gemma 4", ignoreCase = true) &&
        !modelName.contains("gemma-4", ignoreCase = true)
    ) {
        return false
    }
    return settings.speculativeDecodingEnabled
}

private fun benchmarkUserErrorMessage(
    throwable: Throwable,
    snapshot: BenchmarkSnapshot
): String {
    val message = throwable.message.orEmpty()
    return when {
        message.contains("memory", ignoreCase = true) ||
            message.contains("oom", ignoreCase = true) ||
            message.contains("OutOfMemory", ignoreCase = true) -> {
            "메모리가 부족하여 벤치마크를 중단했습니다."
        }
        snapshot.settings.accelerator != AcceleratorMode.CPU -> {
            "GPU 벤치마크 중 오류가 발생했습니다. CPU 또는 낮은 maxTokens로 다시 시도해 주세요."
        }
        else -> {
            "모델을 불러올 수 없습니다. 모델 설정을 확인한 뒤 다시 시도해 주세요."
        }
    }
}

private fun sanitizeBenchmarkErrorMessage(
    throwable: Throwable,
    snapshot: BenchmarkSnapshot
): String {
    return when {
        throwable.message.orEmpty().contains("memory", ignoreCase = true) ||
            throwable.message.orEmpty().contains("oom", ignoreCase = true) ||
            throwable is OutOfMemoryError -> {
            "메모리가 부족하여 벤치마크를 중단했습니다."
        }
        snapshot.settings.accelerator != AcceleratorMode.CPU -> {
            "GPU 벤치마크 오류"
        }
        else -> {
            "모델 로딩 오류"
        }
    }.take(100)
}

private fun loadBenchmarkSettingsFromPrefs(prefs: android.content.SharedPreferences): GenerationSettings {
    return GenerationSettings(
        maxTokens = prefs.getInt("max_tokens", 4000).coerceIn(1, 32000),
        topK = prefs.getInt("top_k", 64).coerceIn(1, 100),
        topP = prefs.getFloat("top_p", 0.95f).coerceIn(0f, 1f),
        temperature = prefs.getFloat("temperature", 1.0f).coerceIn(0f, 2f),
        accelerator = runCatching {
            AcceleratorMode.valueOf(prefs.getString("accelerator", "GPU") ?: "GPU")
        }.getOrDefault(AcceleratorMode.GPU),
        reasoningBudgetTokens = prefs.getInt("reasoning_budget_tokens", 512).coerceIn(1, 8192),
        speculativeDecodingEnabled = if (prefs.contains("speculative_decoding_enabled")) {
            prefs.getBoolean("speculative_decoding_enabled", false)
        } else {
            null
        }
    )
}

private fun resolveBenchmarkModelPath(context: Context, modelName: String, selectedPath: String?): String? {
    if (!selectedPath.isNullOrBlank() && File(selectedPath).exists()) return selectedPath
    val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
    val modelsDir = File(baseDir, "models")
    val fileName = when (modelName) {
        "Gemma 4 E4B-it" -> "gemma-4-E4B-it.litertlm"
        else -> "gemma-4-E2B-it.litertlm"
    }
    val file = File(modelsDir, fileName)
    return if (file.exists()) file.absolutePath else null
}

private fun initialBenchmarkMtpStatusLabel(settings: GenerationSettings, modelName: String, modelPath: String?): String {
    if (settings.speculativeDecodingEnabled != true) return "꺼짐"
    val modelKey = "${modelName}\n${modelPath.orEmpty()}".lowercase()
    val supported = "gemma-4" in modelKey || "gemma4" in modelKey
    return if (supported) "요청됨" else "미지원"
}

private fun estimateBenchmarkTokens(text: String): Int {
    val cleaned = text.trim()
    if (cleaned.isBlank()) return 0
    return (cleaned.length / 4.0).toInt().coerceAtLeast(1)
}

private fun Context.appVersionName(): String? {
    return runCatching {
        val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        packageInfo.versionName
    }.getOrNull()
}
