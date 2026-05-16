package com.projectnuke.fusion.ui

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.projectnuke.fusion.llm.LiteRtLlmEngine
import com.projectnuke.fusion.llm.MtpRuntimeStatus
import com.projectnuke.fusion.model.AcceleratorMode
import com.projectnuke.fusion.model.ChatMessage
import com.projectnuke.fusion.model.GenerationSettings
import java.io.File
import java.util.Locale
import kotlinx.coroutines.launch

private const val BenchmarkPrompt = "반도체 공정과 메모리 계층 구조를 1000자 정도로 설명해 주세요. 핵심 개념, 성능 병목, 전력 효율 관점까지 포함해 주세요."
private const val SafeBenchmarkPrompt = "반도체 공정과 메모리 계층 구조를 1000자 정도로 설명해 주세요. 핵심 개념, 성능 병목, 전력 효율 관점까지 포함해 주세요."

@Composable
fun SafeBenchmarkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("fusion_chat_settings", Context.MODE_PRIVATE) }
    val engine = remember { LiteRtLlmEngine(context.applicationContext) }

    val selectedModel = prefs.getString("selected_model", "Gemma 4 E2B-it") ?: "Gemma 4 E2B-it"
    val selectedModelPath = prefs.getString("selected_model_path", null)
    val settings = remember { loadSettingsFromPrefs(prefs) }
    val resolvedModelPath = remember(selectedModel, selectedModelPath) {
        resolveModelPath(context, selectedModel, selectedModelPath)
    }
    var isRunning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF000000)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("뒤로", color = Color(0xFFF5F5F5)) }
            Column {
                Text("벤치마크", color = Color(0xFFF5F5F5), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("선택한 모델의 응답 속도와 생성 성능을 측정합니다.", color = Color(0xFF9E9E9E), fontSize = 12.sp)
            }
        }

        CardBlock {
            Text("모델: $selectedModel", color = Color(0xFFF5F5F5))
            Text("경로: ${resolvedModelPath ?: "없음"}", color = Color(0xFF9E9E9E), fontSize = 12.sp)
            Text("가속기: ${settings.accelerator.name}", color = Color(0xFFF5F5F5))
            Text("MTP 가속: ${initialMtpStatusLabel(settings, selectedModel, resolvedModelPath)}", color = Color(0xFFF5F5F5))
            Text("maxTokens=${settings.maxTokens} / temp=${settings.temperature} / topK=${settings.topK} / topP=${settings.topP}", color = Color(0xFF9E9E9E), fontSize = 12.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF171717)) {
                TextButton(
                    enabled = !isRunning,
                    onClick = {
                        val modelPath = resolvedModelPath
                        if (modelPath.isNullOrBlank() || !File(modelPath).exists()) {
                            Toast.makeText(context, "선택된 모델이 없습니다.", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        isRunning = true
                        status = "모델 성능을 측정하는 중입니다."
                        result = null
                        scope.launch {
                            try {
                                val start = SystemClock.elapsedRealtime()
                                var firstTokenMs: Long? = null
                                val output = StringBuilder()
                                engine.generateStreaming(
                                    messages = listOf(ChatMessage("user", SafeBenchmarkPrompt)),
                                    modelPath = modelPath,
                                    settings = settings,
                                    onToken = { token ->
                                        if (firstTokenMs == null && token.isNotEmpty()) {
                                            firstTokenMs = SystemClock.elapsedRealtime() - start
                                        }
                                        output.append(token)
                                    }
                                )
                                val totalMs = SystemClock.elapsedRealtime() - start
                                val text = output.toString().replace(Regex("""</?fusion_(thinking|answer|metrics)>"""), "").trim()
                                val estTokens = estimateTokens(text)
                                val tps = if (totalMs > 0) (estTokens * 1000.0 / totalMs) else 0.0
                                val decodeMs = firstTokenMs?.let { totalMs - it }?.takeIf { it > 0 }
                                val decodeTps = decodeMs?.let { estTokens * 1000.0 / it }
                                val mtpStatus = engine.lastMtpStatus
                                result = buildString {
                                    appendLine("모델 로딩 시간: 측정 불가")
                                    appendLine("첫 토큰 시간: ${firstTokenMs?.let { "${it}ms" } ?: "측정 불가"}")
                                    appendLine("총 생성 시간: ${"%.2f".format(Locale.US, totalMs / 1000.0)}s")
                                    appendLine("추정 출력 토큰 수: $estTokens")
                                    appendLine("전체 기준 토큰 속도: ${"%.1f".format(Locale.US, tps)} tok/s")
                                    appendLine("디코딩 기준 토큰 속도: ${decodeTps?.let { "${"%.1f".format(Locale.US, it)} tok/s" } ?: "측정 불가"}")
                                    appendLine("가속기: ${settings.accelerator.name}")
                                    appendLine("MTP 가속: ${mtpStatus.toKoreanLabel()}")
                                    appendLine()
                                    appendLine("정확한 비교를 위해 같은 조건에서 MTP 꺼짐/켜짐을 각각 2회 이상 측정해 주세요.")
                                }.trim()
                                status = null
                            } catch (e: Exception) {
                                Log.e("FusionBenchmark", "Benchmark generation failed", e)
                                status = null
                                Toast.makeText(context, "벤치마크 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                            } finally {
                                isRunning = false
                            }
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

        if (!status.isNullOrBlank()) {
            CardBlock { Text(status!!, color = Color(0xFFF5F5F5)) }
        }
        if (!result.isNullOrBlank()) {
            CardBlock { Text(result!!, color = Color(0xFFF5F5F5), fontSize = 13.sp) }
        }
    }
}

@Composable
fun BenchmarkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("fusion_chat_settings", Context.MODE_PRIVATE) }
    val engine = remember { LiteRtLlmEngine(context.applicationContext) }

    val selectedModel = prefs.getString("selected_model", "Gemma 4 E2B-it") ?: "Gemma 4 E2B-it"
    val selectedModelPath = prefs.getString("selected_model_path", null)
    val settings = remember { loadSettingsFromPrefs(prefs) }
    val resolvedModelPath = remember(selectedModel, selectedModelPath) {
        resolveModelPath(context, selectedModel, selectedModelPath)
    }
    var isRunning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF000000)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("뒤로", color = Color(0xFFF5F5F5)) }
            Column {
                Text("벤치마크", color = Color(0xFFF5F5F5), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("선택한 모델의 응답 속도와 생성 성능을 측정합니다.", color = Color(0xFF9E9E9E), fontSize = 12.sp)
            }
        }

        CardBlock {
            Text("모델: $selectedModel", color = Color(0xFFF5F5F5))
            Text("경로: ${resolvedModelPath ?: "없음"}", color = Color(0xFF9E9E9E), fontSize = 12.sp)
            Text("가속기: ${settings.accelerator.name}", color = Color(0xFFF5F5F5))
            Text("MTP 가속: ${if (settings.speculativeDecodingEnabled == true) "사용" else "해제"}", color = Color(0xFFF5F5F5))
            Text("maxTokens=${settings.maxTokens} / temp=${settings.temperature} / topK=${settings.topK} / topP=${settings.topP}", color = Color(0xFF9E9E9E), fontSize = 12.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF171717)) {
                TextButton(
                    enabled = !isRunning,
                    onClick = {
                        val modelPath = resolvedModelPath
                        if (modelPath.isNullOrBlank() || !File(modelPath).exists()) {
                            Toast.makeText(context, "선택된 모델이 없습니다.", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        isRunning = true
                        status = "모델 성능을 측정하는 중입니다."
                        result = null
                        scope.launch {
                            try {
                                val messages = listOf(ChatMessage("user", BenchmarkPrompt))
                                val start = SystemClock.elapsedRealtime()
                                var firstTokenMs: Long? = null
                                val output = StringBuilder()
                                engine.generateStreaming(
                                    messages = messages,
                                    modelPath = modelPath,
                                    settings = settings.copy(reasoningBudgetTokens = settings.reasoningBudgetTokens),
                                    onToken = { token ->
                                        if (firstTokenMs == null && token.isNotEmpty()) {
                                            firstTokenMs = SystemClock.elapsedRealtime() - start
                                        }
                                        output.append(token)
                                    }
                                )
                                val totalMs = SystemClock.elapsedRealtime() - start
                                val text = output.toString().replace(Regex("""</?fusion_(thinking|answer|metrics)>"""), "").trim()
                                val estTokens = estimateTokens(text)
                                val tps = if (totalMs > 0) (estTokens * 1000.0 / totalMs) else 0.0
                                result = buildString {
                                    appendLine("모델 로딩 시간: 측정 불가")
                                    appendLine("첫 토큰 시간: ${firstTokenMs?.let { "${it}ms" } ?: "측정 불가"}")
                                    appendLine("총 생성 시간: ${"%.2f".format(Locale.US, totalMs / 1000.0)}s")
                                    appendLine("추정 출력 토큰 수: $estTokens")
                                    appendLine("추정 토큰 속도: ${"%.1f".format(Locale.US, tps)} tok/s")
                                    appendLine("가속기: ${settings.accelerator.name}")
                                    appendLine("MTP 가속: ${if (settings.speculativeDecodingEnabled == true) "사용" else "해제"}")
                                }.trim()
                                status = null
                            } catch (e: Exception) {
                                Log.e("FusionBenchmark", "Benchmark generation failed", e)
                                status = null
                                Toast.makeText(context, "벤치마크 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                            } finally {
                                isRunning = false
                            }
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

        if (!status.isNullOrBlank()) {
            CardBlock { Text(status!!, color = Color(0xFFF5F5F5)) }
        }
        if (!result.isNullOrBlank()) {
            CardBlock { Text(result!!, color = Color(0xFFF5F5F5), fontSize = 13.sp) }
        }
    }
}

@Composable
private fun CardBlock(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF111111), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

private fun loadSettingsFromPrefs(prefs: android.content.SharedPreferences): GenerationSettings {
    return GenerationSettings(
        maxTokens = prefs.getInt("max_tokens", 4000),
        topK = prefs.getInt("top_k", 64),
        topP = prefs.getFloat("top_p", 0.95f),
        temperature = prefs.getFloat("temperature", 1.0f),
        accelerator = runCatching { AcceleratorMode.valueOf(prefs.getString("accelerator", "GPU") ?: "GPU") }.getOrDefault(AcceleratorMode.GPU),
        reasoningBudgetTokens = prefs.getInt("reasoning_budget_tokens", 512),
        speculativeDecodingEnabled = if (prefs.contains("speculative_decoding_enabled")) prefs.getBoolean("speculative_decoding_enabled", false) else null
    )
}

private fun resolveModelPath(context: Context, modelName: String, selectedPath: String?): String? {
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

private fun estimateTokens(text: String): Int {
    val cleaned = text.trim()
    if (cleaned.isBlank()) return 0
    return (cleaned.length / 4.0).toInt().coerceAtLeast(1)
}

private fun initialMtpStatusLabel(
    settings: GenerationSettings,
    modelName: String,
    modelPath: String?
): String {
    if (settings.speculativeDecodingEnabled != true) return "꺼짐"
    val modelKey = "${modelName}\n${modelPath.orEmpty()}".lowercase()
    val looksSupported = "gemma-4" in modelKey || "gemma4" in modelKey
    return if (looksSupported) "요청됨" else "미지원"
}

private fun MtpRuntimeStatus.toKoreanLabel(): String {
    return when (this) {
        MtpRuntimeStatus.OFF -> "꺼짐"
        MtpRuntimeStatus.REQUESTED -> "요청됨"
        MtpRuntimeStatus.APPLIED -> "적용됨"
        MtpRuntimeStatus.UNSUPPORTED -> "미지원"
        MtpRuntimeStatus.FAILED -> "적용 실패"
    }
}
