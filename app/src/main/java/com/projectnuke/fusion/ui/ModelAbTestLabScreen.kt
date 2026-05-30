package com.projectnuke.fusion.ui

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectnuke.fusion.llm.ChatGenerationRunningException
import com.projectnuke.fusion.llm.FusionRuntimeLock
import com.projectnuke.fusion.llm.LiteRtLlmEngine
import com.projectnuke.fusion.model.AcceleratorMode
import com.projectnuke.fusion.model.ChatMessage
import com.projectnuke.fusion.model.GenerationSettings
import com.projectnuke.fusion.modelzoo.FusionModelCatalog
import com.projectnuke.fusion.modelzoo.FusionModelProfiles
import com.projectnuke.fusion.modelzoo.FusionModelSpec
import com.projectnuke.fusion.modelzoo.ModelAvailability
import com.projectnuke.fusion.util.FusionMemoryManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AbBg = Color(0xFF000000)
private val AbCard = Color(0xFF111111)
private val AbPanel = Color(0xFF171717)
private val AbText = Color(0xFFF5F5F5)
private val AbSubtle = Color(0xFF9E9E9E)
private val AbAccent = Color(0xFF9FD0FF)
private val AbFail = Color(0xFFFF7A7A)

private data class AbModelOption(
    val spec: FusionModelSpec,
    val path: String
)

private data class AbTarget(
    val id: Long,
    val model: AbModelOption,
    val settings: GenerationSettings,
    val reasoningEnabled: Boolean
)

private data class AbResult(
    val targetLabel: String,
    val modelName: String,
    val settings: GenerationSettings,
    val reasoningEnabled: Boolean,
    val answer: String?,
    val firstTokenLatencyMs: Long?,
    val totalGenerationMs: Long,
    val estimatedTokens: Int,
    val totalTokensPerSecond: Double,
    val decodeTokensPerSecond: Double?,
    val createdAt: Long,
    val errorMessage: String? = null
) {
    val succeeded: Boolean get() = errorMessage == null
}

@Composable
fun ModelAbTestLabScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("fusion_chat_settings", Context.MODE_PRIVATE) }
    val engine = remember { LiteRtLlmEngine(context.applicationContext) }
    val selectedModel = prefs.getString("selected_model", "Gemma 4 E2B-it") ?: "Gemma 4 E2B-it"
    val selectedPath = prefs.getString("selected_model_path", null)
    val currentSettings = remember { loadAbSettings(prefs) }
    val localModels = remember(selectedModel, selectedPath) {
        loadLocalAbModels(context, selectedModel, selectedPath)
    }
    val initialModel = localModels.firstOrNull { it.spec.displayName == selectedModel } ?: localModels.firstOrNull()
    var prompt by remember { mutableStateOf("") }
    var targets by remember(initialModel) {
        mutableStateOf(
            initialModel?.let {
                listOf(
                    AbTarget(1L, it, currentSettings, prefs.getBoolean("reasoning_enabled", false)),
                    AbTarget(2L, it, recommendedAbSettings(context, it.spec), recommendedAbReasoning(context, it.spec))
                )
            } ?: emptyList()
        )
    }
    var results by remember { mutableStateOf<List<AbResult>>(emptyList()) }
    var isRunning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var modelPickerTargetId by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(engine) {
        val unregister = FusionMemoryManager.registerIdleEngineUnloader {
            if (!FusionRuntimeLock.isChatGenerationRunning && !FusionRuntimeLock.isBenchmarkRunning) {
                engine.unload()
            }
        }
        onDispose {
            unregister()
            runCatching { engine.unload() }
        }
    }

    BackHandler(enabled = !isRunning) { onBack() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AbBg).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("모델 A/B 테스트", color = AbText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("같은 입력을 여러 모델 또는 설정으로 실행해 결과를 비교합니다.", color = AbSubtle, fontSize = 12.sp)
                }
                TextButton(enabled = !isRunning, onClick = onBack) { Text("뒤로", color = AbText) }
            }
        }

        item {
            AbCardBlock {
                Text("테스트 프롬프트", color = AbText, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    placeholder = { Text("비교할 질문이나 지시문을 입력해 주세요.", color = AbSubtle) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AbAccent,
                        unfocusedBorderColor = AbPanel,
                        cursorColor = AbAccent,
                        focusedTextColor = AbText,
                        unfocusedTextColor = AbText
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { prompt = "" }) { Text("입력 지우기", color = AbSubtle) }
                    TextButton(onClick = { prompt = "Fusion 앱의 다음 개선 방향을 제안해 주세요." }) {
                        Text("예시 입력", color = AbAccent)
                    }
                }
            }
        }

        item {
            AbCardBlock {
                Text("테스트 대상", color = AbText, fontWeight = FontWeight.SemiBold)
                Text("로컬에서 실행 가능한 모델만 표시합니다. 테스트는 순차적으로 실행됩니다.", color = AbSubtle, fontSize = 12.sp)
                Text(
                    "저장된 메모리: ${if (isSavedMemoryContextEnabled(prefs)) "현재 전역 설정을 따릅니다." else "사용 안 함"}",
                    color = AbSubtle,
                    fontSize = 12.sp
                )
            }
        }

        if (localModels.isEmpty()) {
            item {
                AbCardBlock {
                    Text("로컬 테스트에 사용할 수 있는 모델 파일이 없습니다.", color = AbFail)
                    Text("모델 라이브러리에서 실행 가능한 모델 파일을 먼저 가져와 주세요.", color = AbSubtle, fontSize = 12.sp)
                }
            }
        }

        items(targets, key = { it.id }) { target ->
            val label = targetLabel(targets.indexOf(target))
            item@ AbTargetCard(
                label = label,
                target = target,
                canRemove = targets.size > 2,
                onSelectModel = { modelPickerTargetId = target.id },
                onUseCurrentSettings = {
                    targets = targets.replaceTarget(target.id) {
                        it.copy(settings = currentSettings, reasoningEnabled = prefs.getBoolean("reasoning_enabled", false))
                    }
                },
                onUseRecommendedSettings = {
                    targets = targets.replaceTarget(target.id) {
                        it.copy(
                            settings = recommendedAbSettings(context, it.model.spec),
                            reasoningEnabled = recommendedAbReasoning(context, it.model.spec)
                        )
                    }
                },
                onCopyTarget = {
                    if (targets.size < 4) {
                        targets = targets + target.copy(id = (targets.maxOfOrNull { it.id } ?: 0L) + 1L)
                    } else {
                        Toast.makeText(context, "테스트 대상은 최대 4개까지 추가할 수 있습니다.", Toast.LENGTH_SHORT).show()
                    }
                },
                onRemove = { targets = targets.filterNot { it.id == target.id } },
                memoryEnabled = isSavedMemoryContextEnabled(prefs)
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !isRunning,
                    onClick = {
                        val cleanPrompt = prompt.trim()
                        when {
                            cleanPrompt.isBlank() -> Toast.makeText(context, "테스트 프롬프트를 입력해 주세요.", Toast.LENGTH_SHORT).show()
                            targets.size < 2 -> Toast.makeText(context, "테스트 대상을 2개 이상 선택해 주세요.", Toast.LENGTH_SHORT).show()
                            FusionRuntimeLock.isChatGenerationRunning || FusionRuntimeLock.isBenchmarkRunning -> {
                                Toast.makeText(context, "현재 다른 응답 또는 벤치마크를 실행하는 중입니다.", Toast.LENGTH_SHORT).show()
                            }
                            FusionMemoryManager.shouldBlockBenchmark(context) -> {
                                Toast.makeText(context, "사용 가능한 메모리가 부족하여 테스트를 시작할 수 없습니다.", Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                val snapshot = FusionMemoryManager.getMemorySnapshot(context)
                                if (snapshot.availMem < snapshot.threshold * 2) {
                                    Toast.makeText(context, "메모리 사용량이 높아 테스트가 느려질 수 있습니다.", Toast.LENGTH_SHORT).show()
                                }
                                isRunning = true
                                results = emptyList()
                                status = "테스트를 준비하는 중입니다."
                                DeveloperLogStore.record(context, "ab_test", "A/B 테스트 시작", "targets=${targets.size}, models=${targets.joinToString { it.model.spec.displayName }}")
                                scope.launch {
                                    try {
                                        runAbTests(
                                            context = context,
                                            engine = engine,
                                            prefs = prefs,
                                            prompt = cleanPrompt,
                                            targets = targets,
                                            onStatus = { status = it },
                                            onResult = { results = results + it }
                                        )
                                        status = "A/B 테스트가 완료되었습니다."
                                    } catch (_: ChatGenerationRunningException) {
                                        status = "현재 응답을 생성하는 중입니다."
                                    } catch (e: Exception) {
                                        Log.e("FusionAbTest", "A/B test failed", e)
                                        DeveloperLogStore.record(context, "ab_test", "A/B 테스트 실패", e::class.java.simpleName)
                                        status = "테스트를 완료할 수 없습니다."
                                    } finally {
                                        isRunning = false
                                    }
                                }
                            }
                        }
                    }
                ) { Text("테스트 시작", color = if (isRunning) AbSubtle else AbAccent) }
            }
        }

        if (isRunning) {
            item {
                AbCardBlock {
                    Text(status ?: "테스트를 준비하는 중입니다.", color = AbText)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AbAccent, trackColor = AbPanel)
                }
            }
        } else {
            status?.let { item { AbCardBlock { Text(it, color = AbText) } } }
        }

        if (results.isNotEmpty()) {
            item { AbComparisonSummary(results) }
            items(results, key = { "${it.targetLabel}-${it.createdAt}" }) { result ->
                AbResultCard(
                    result = result,
                    onCopyAnswer = {
                        clipboard.setText(AnnotatedString(result.answer.orEmpty()))
                        Toast.makeText(context, "답변을 복사했습니다.", Toast.LENGTH_SHORT).show()
                    },
                    onCopySettings = {
                        clipboard.setText(AnnotatedString(result.settingsSummary()))
                        Toast.makeText(context, "설정을 복사했습니다.", Toast.LENGTH_SHORT).show()
                    },
                    onCopyResult = {
                        clipboard.setText(AnnotatedString(result.copyText()))
                        Toast.makeText(context, "결과를 복사했습니다.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    modelPickerTargetId?.let { targetId ->
        AlertDialog(
            onDismissRequest = { modelPickerTargetId = null },
            title = { Text("모델 선택") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(localModels, key = { it.spec.id }) { option ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                targets = targets.replaceTarget(targetId) { it.copy(model = option) }
                                modelPickerTargetId = null
                            }
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(option.spec.displayName, color = AbText)
                                Text(option.path, color = AbSubtle, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { modelPickerTargetId = null }) { Text("닫기", color = AbSubtle) }
            },
            containerColor = AbPanel,
            titleContentColor = AbText,
            textContentColor = AbText
        )
    }
}

@Composable
private fun AbTargetCard(
    label: String,
    target: AbTarget,
    canRemove: Boolean,
    onSelectModel: () -> Unit,
    onUseCurrentSettings: () -> Unit,
    onUseRecommendedSettings: () -> Unit,
    onCopyTarget: () -> Unit,
    onRemove: () -> Unit,
    memoryEnabled: Boolean
) {
    AbCardBlock {
        Text("대상 $label · ${target.model.spec.displayName}", color = AbText, fontWeight = FontWeight.SemiBold)
        Text(target.settingsSummary(), color = AbSubtle, fontSize = 12.sp)
        Text("저장된 메모리=${if (memoryEnabled) "사용" else "사용 안 함"}", color = AbSubtle, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onSelectModel) { Text("모델 선택", color = AbAccent) }
            TextButton(onClick = onUseCurrentSettings) { Text("현재 설정 사용", color = AbText) }
            TextButton(onClick = onUseRecommendedSettings) { Text("권장 설정 사용", color = AbText) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onCopyTarget) { Text("복사해서 비교 대상 만들기", color = AbText) }
            if (canRemove) TextButton(onClick = onRemove) { Text("삭제", color = AbFail) }
        }
    }
}

@Composable
private fun AbResultCard(
    result: AbResult,
    onCopyAnswer: () -> Unit,
    onCopySettings: () -> Unit,
    onCopyResult: () -> Unit
) {
    AbCardBlock {
        Text("대상 ${result.targetLabel} · ${result.modelName}", color = AbText, fontWeight = FontWeight.SemiBold)
        Text("상태: ${if (result.succeeded) "성공" else "실패"} · ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(result.createdAt))}", color = AbSubtle, fontSize = 12.sp)
        Text(result.settingsSummary(), color = AbSubtle, fontSize = 12.sp)
        if (result.succeeded) {
            Text(result.answer.orEmpty(), color = AbText, fontSize = 13.sp)
            Text(
                "첫 토큰 ${result.firstTokenLatencyMs?.let { "${it}ms" } ?: "측정 불가"} · 총 ${result.totalGenerationMs}ms · 추정 ${result.estimatedTokens} tokens",
                color = AbSubtle,
                fontSize = 12.sp
            )
            Text(
                "전체 ${formatSpeed(result.totalTokensPerSecond)} · 디코딩 ${result.decodeTokensPerSecond?.let(::formatSpeed) ?: "측정 불가"}",
                color = AbSubtle,
                fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onCopyAnswer) { Text("답변 복사", color = AbAccent) }
                TextButton(onClick = onCopySettings) { Text("설정 복사", color = AbText) }
                TextButton(onClick = onCopyResult) { Text("결과 복사", color = AbText) }
            }
        } else {
            Text("이 대상의 실행에 실패했습니다.", color = AbFail)
            Text(result.errorMessage.orEmpty(), color = AbSubtle, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AbComparisonSummary(results: List<AbResult>) {
    val successful = results.filter { it.succeeded }
    val fastestFirstToken = successful.filter { it.firstTokenLatencyMs != null }.minByOrNull { it.firstTokenLatencyMs ?: Long.MAX_VALUE }
    val fastestDecode = successful.filter { it.decodeTokensPerSecond != null }.maxByOrNull { it.decodeTokensPerSecond ?: 0.0 }
    val shortest = successful.minByOrNull { it.answer.orEmpty().length }
    val longest = successful.maxByOrNull { it.answer.orEmpty().length }
    val failures = results.count { !it.succeeded }
    AbCardBlock {
        Text("비교 요약", color = AbText, fontWeight = FontWeight.SemiBold)
        Text("가장 빠른 응답 시작: ${fastestFirstToken?.targetLabel ?: "측정 불가"}", color = AbSubtle, fontSize = 12.sp)
        Text("가장 높은 디코딩 속도: ${fastestDecode?.targetLabel ?: "측정 불가"}", color = AbSubtle, fontSize = 12.sp)
        Text("가장 짧은 답변: ${shortest?.targetLabel ?: "측정 불가"} · 가장 긴 답변: ${longest?.targetLabel ?: "측정 불가"}", color = AbSubtle, fontSize = 12.sp)
        Text("실패한 대상: ${if (failures == 0) "없음" else "${failures}개"}", color = AbSubtle, fontSize = 12.sp)
    }
}

private suspend fun runAbTests(
    context: Context,
    engine: LiteRtLlmEngine,
    prefs: android.content.SharedPreferences,
    prompt: String,
    targets: List<AbTarget>,
    onStatus: (String) -> Unit,
    onResult: (AbResult) -> Unit
) {
    FusionRuntimeLock.withExclusiveBenchmark(
        onPrepareExclusiveMode = {
            onStatus("모델 리소스를 정리하는 중입니다.")
            FusionRuntimeLock.requestChatEngineUnloadForBenchmark()
            runCatching { engine.unload() }
        }
    ) {
        targets.forEachIndexed { index, target ->
            val label = targetLabel(index)
            onStatus("$label 실행 중...")
            runCatching {
                runCatching { engine.unload() }
                System.gc()
                delay(250L)
                runSingleAbTarget(context, engine, prefs, prompt, label, target)
            }.onSuccess { result ->
                onResult(result)
                DeveloperLogStore.record(
                    context,
                    "ab_test",
                    "A/B 대상 성공",
                    "target=$label, model=${target.model.spec.displayName}, totalMs=${result.totalGenerationMs}"
                )
            }.onFailure { error ->
                Log.e("FusionAbTest", "Target $label failed", error)
                onResult(
                    AbResult(
                        targetLabel = label,
                        modelName = target.model.spec.displayName,
                        settings = target.settings,
                        reasoningEnabled = target.reasoningEnabled,
                        answer = null,
                        firstTokenLatencyMs = null,
                        totalGenerationMs = 0L,
                        estimatedTokens = 0,
                        totalTokensPerSecond = 0.0,
                        decodeTokensPerSecond = null,
                        createdAt = System.currentTimeMillis(),
                        errorMessage = safeAbErrorMessage(error)
                    )
                )
                DeveloperLogStore.record(context, "ab_test", "A/B 대상 실패", "target=$label, model=${target.model.spec.displayName}, error=${error::class.java.simpleName}")
            }
        }
    }
    runCatching { engine.unload() }
    DeveloperLogStore.record(context, "ab_test", "A/B 테스트 완료", "targets=${targets.size}")
}

private suspend fun runSingleAbTarget(
    context: Context,
    engine: LiteRtLlmEngine,
    prefs: android.content.SharedPreferences,
    prompt: String,
    label: String,
    target: AbTarget
): AbResult {
    if (!File(target.model.path).exists()) error("model file missing")
    val memoryText = buildSavedMemoryContext(
        context = context,
        prefs = prefs,
        currentConversationId = null,
        currentModelId = target.model.spec.displayName,
        globalPreviewOnly = false
    ).text
    val input = if (memoryText.isNullOrBlank()) prompt else "$memoryText\n\n[테스트 프롬프트]\n$prompt"
    val output = StringBuilder()
    val startedAt = SystemClock.elapsedRealtime()
    var firstTokenLatencyMs: Long? = null
    engine.generateStreaming(
        messages = listOf(ChatMessage("user", input)),
        modelPath = target.model.path,
        settings = target.settings,
        onToken = { token ->
            if (firstTokenLatencyMs == null && token.isNotEmpty()) {
                firstTokenLatencyMs = SystemClock.elapsedRealtime() - startedAt
            }
            output.append(token)
        }
    )
    val totalMs = SystemClock.elapsedRealtime() - startedAt
    val answer = sanitizeAbAnswer(output.toString())
    if (answer.isBlank()) error("empty model response")
    val estimatedTokens = estimateAbTokens(answer)
    val totalTps = if (totalMs > 0) estimatedTokens * 1000.0 / totalMs else 0.0
    val decodeMs = firstTokenLatencyMs?.let { totalMs - it }?.takeIf { it > 0 }
    return AbResult(
        targetLabel = label,
        modelName = target.model.spec.displayName,
        settings = target.settings,
        reasoningEnabled = target.reasoningEnabled,
        answer = answer,
        firstTokenLatencyMs = firstTokenLatencyMs,
        totalGenerationMs = totalMs,
        estimatedTokens = estimatedTokens,
        totalTokensPerSecond = totalTps,
        decodeTokensPerSecond = decodeMs?.let { estimatedTokens * 1000.0 / it },
        createdAt = System.currentTimeMillis()
    )
}

private fun loadLocalAbModels(context: Context, selectedModel: String, selectedPath: String?): List<AbModelOption> {
    val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
    val modelsDir = File(baseDir, "models")
    return FusionModelCatalog.all(context)
        .asSequence()
        .filter { spec ->
            spec.canRunLocalRuntime &&
                spec.availability != ModelAvailability.REMOTE_ONLY &&
                spec.availability != ModelAvailability.UNSUPPORTED_ON_DEVICE &&
                spec.availability != ModelAvailability.NEEDS_CONVERSION
        }
        .mapNotNull { spec ->
            val path = when {
                spec.displayName == selectedModel && !selectedPath.isNullOrBlank() && File(selectedPath).exists() -> selectedPath
                !spec.localPath.isNullOrBlank() && File(spec.localPath).exists() -> spec.localPath
                !spec.fileName.isNullOrBlank() && File(modelsDir, spec.fileName).exists() -> File(modelsDir, spec.fileName).absolutePath
                else -> null
            }
            path?.let { AbModelOption(spec, it) }
        }
        .distinctBy { it.spec.id }
        .toList()
}

private fun loadAbSettings(prefs: android.content.SharedPreferences): GenerationSettings {
    return GenerationSettings(
        maxTokens = prefs.getInt("max_tokens", 4000).coerceIn(1, 32000),
        topK = prefs.getInt("top_k", 64).coerceIn(1, 100),
        topP = prefs.getFloat("top_p", 0.95f).coerceIn(0f, 1f),
        temperature = prefs.getFloat("temperature", 1.0f).coerceIn(0f, 2f),
        accelerator = runCatching { AcceleratorMode.valueOf(prefs.getString("accelerator", "GPU") ?: "GPU") }.getOrDefault(AcceleratorMode.GPU),
        reasoningBudgetTokens = prefs.getInt("reasoning_budget_tokens", 512).coerceIn(1, 8192),
        speculativeDecodingEnabled = prefs.getBoolean("speculative_decoding_enabled", false)
    )
}

private fun recommendedAbSettings(context: Context, spec: FusionModelSpec): GenerationSettings {
    return FusionModelProfiles.recommended(context, spec)?.settings ?: GenerationSettings(maxTokens = spec.recommendedMaxTokens8Gb.coerceAtLeast(1024))
}

private fun recommendedAbReasoning(context: Context, spec: FusionModelSpec): Boolean {
    return FusionModelProfiles.recommended(context, spec)?.reasoningEnabled ?: false
}

private fun List<AbTarget>.replaceTarget(id: Long, transform: (AbTarget) -> AbTarget): List<AbTarget> {
    return map { if (it.id == id) transform(it) else it }
}

private fun targetLabel(index: Int): String = ('A'.code + index).toChar().toString()

private fun AbTarget.settingsSummary(): String {
    return "가속기=${settings.accelerator.name} · maxTokens=${settings.maxTokens} · temp=${settings.temperature} · topK=${settings.topK} · topP=${settings.topP} · MTP=${if (settings.speculativeDecodingEnabled == true) "켜짐" else "꺼짐"} · Reasoning=${if (reasoningEnabled) "켜짐" else "꺼짐"}"
}

private fun AbResult.settingsSummary(): String {
    return "가속기=${settings.accelerator.name} · maxTokens=${settings.maxTokens} · temp=${settings.temperature} · topK=${settings.topK} · topP=${settings.topP} · MTP=${if (settings.speculativeDecodingEnabled == true) "켜짐" else "꺼짐"} · Reasoning=${if (reasoningEnabled) "켜짐" else "꺼짐"}"
}

private fun AbResult.copyText(): String = buildString {
    appendLine("Fusion Model A/B Test")
    appendLine("대상: $targetLabel")
    appendLine("모델: $modelName")
    appendLine(settingsSummary())
    appendLine("시간: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(createdAt))}")
    if (succeeded) {
        appendLine("첫 토큰 시간: ${firstTokenLatencyMs?.let { "${it}ms" } ?: "측정 불가"}")
        appendLine("총 생성 시간: ${totalGenerationMs}ms")
        appendLine("추정 출력 토큰 수: $estimatedTokens")
        appendLine("전체 기준 토큰 속도: ${formatSpeed(totalTokensPerSecond)}")
        appendLine("디코딩 기준 토큰 속도: ${decodeTokensPerSecond?.let(::formatSpeed) ?: "측정 불가"}")
        appendLine()
        appendLine(answer.orEmpty())
    } else {
        appendLine("실패: ${errorMessage.orEmpty()}")
    }
}.trim()

private fun sanitizeAbAnswer(raw: String): String {
    return raw
        .replace(Regex("""(?is)<fusion_metrics>.*?</fusion_metrics>"""), "")
        .replace(Regex("""(?is)<fusion_thinking>.*?</fusion_thinking>"""), "")
        .replace(Regex("""(?is)<fusion_answer>(.*?)</fusion_answer>"""), "$1")
        .replace(Regex("""(?is)<fusion_attachment_v2>.*?</fusion_attachment_v2>"""), "")
        .replace(Regex("""(?is)<fusion_attachment>.*?</fusion_attachment>"""), "")
        .trim()
}

private fun estimateAbTokens(text: String): Int = (text.length / 4.0).toInt().coerceAtLeast(1)

private fun safeAbErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    return if (message.contains("memory", ignoreCase = true) || message.contains("oom", ignoreCase = true)) {
        "메모리가 부족하여 이 대상의 실행을 중단했습니다."
    } else {
        "모델 파일과 실행 설정을 확인해 주세요."
    }
}

private fun formatSpeed(value: Double): String = String.format(Locale.US, "%.1f tok/s", value)

@Composable
private fun AbCardBlock(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(8.dp), color = AbCard, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}
