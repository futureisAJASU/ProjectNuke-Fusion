package com.projectnuke.fusion.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val PromptLabCustomPrompt = "fusion_prompt_lab_custom_prompt"
private const val PromptLabStyle = "fusion_prompt_lab_style"
private const val PromptLabShowUncertainty = "fusion_prompt_lab_show_uncertainty"
private const val PromptLabReduceQuestionRepetition = "fusion_prompt_lab_reduce_question_repetition"
private const val PromptLabLimitExcessiveEmoji = "fusion_prompt_lab_limit_excessive_emoji"

data class PromptLabSettings(
    val customPrompt: String = "",
    val style: String = "균형",
    val showUncertainty: Boolean = true,
    val reduceQuestionRepetition: Boolean = true,
    val limitExcessiveEmoji: Boolean = true
)

fun loadPromptLabSettings(context: Context): PromptLabSettings {
    val prefs = context.getSharedPreferences("fusion_chat_settings", Context.MODE_PRIVATE)
    return PromptLabSettings(
        customPrompt = prefs.getString(PromptLabCustomPrompt, "") ?: "",
        style = prefs.getString(PromptLabStyle, "균형") ?: "균형",
        showUncertainty = prefs.getBoolean(PromptLabShowUncertainty, true),
        reduceQuestionRepetition = prefs.getBoolean(PromptLabReduceQuestionRepetition, true),
        limitExcessiveEmoji = prefs.getBoolean(PromptLabLimitExcessiveEmoji, true)
    )
}

fun savePromptLabSettings(context: Context, settings: PromptLabSettings) {
    val prefs = context.getSharedPreferences("fusion_chat_settings", Context.MODE_PRIVATE)
    prefs.edit()
        .putString(PromptLabCustomPrompt, settings.customPrompt)
        .putString(PromptLabStyle, settings.style)
        .putBoolean(PromptLabShowUncertainty, settings.showUncertainty)
        .putBoolean(PromptLabReduceQuestionRepetition, settings.reduceQuestionRepetition)
        .putBoolean(PromptLabLimitExcessiveEmoji, settings.limitExcessiveEmoji)
        .apply()
}

fun buildPromptLabInstruction(settings: PromptLabSettings): String {
    val styleInstruction = when (settings.style) {
        "정확" -> "불확실한 내용은 추론이라고 구분하고, 모르는 내용은 모른다고 말합니다."
        "자세히" -> "사용자가 자세한 설명을 원하면 배경, 원리, 예시를 충분히 포함합니다."
        "간결" -> "답변은 핵심 위주로 간결하게 작성합니다."
        "전문가" -> "기술 질문에는 정확한 용어와 구조적인 설명을 사용합니다."
        else -> "응답 스타일은 자연스럽고 균형 있게 유지합니다."
    }
    val rules = buildList {
        if (settings.showUncertainty) add("불확실한 내용은 추론이라고 표시합니다.")
        if (settings.reduceQuestionRepetition) add("사용자의 질문 반복을 불필요하게 하지 않습니다.")
        if (settings.limitExcessiveEmoji) add("과한 이모지와 마케팅식 표현을 줄입니다.")
    }.joinToString("\n- ")

    return buildString {
        if (settings.customPrompt.isNotBlank()) {
            appendLine(settings.customPrompt.trim())
            appendLine()
        }
        appendLine(styleInstruction)
        if (rules.isNotBlank()) {
            appendLine("- $rules")
        }
    }.trim()
}

@Composable
fun PromptLabScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val defaultPrompt = "Fusion의 기본 성격과 응답 방식을 유지합니다."
    val loaded = remember { loadPromptLabSettings(context) }
    var customPrompt by remember { mutableStateOf(loaded.customPrompt) }
    var style by remember { mutableStateOf(loaded.style) }
    var showUncertainty by remember { mutableStateOf(loaded.showUncertainty) }
    var reduceQuestionRepetition by remember { mutableStateOf(loaded.reduceQuestionRepetition) }
    var limitExcessiveEmoji by remember { mutableStateOf(loaded.limitExcessiveEmoji) }
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF000000))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("뒤로", color = Color(0xFFF5F5F5)) }
            Column {
                Text("Prompt Lab", color = Color(0xFFF5F5F5), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("시스템 프롬프트와 응답 스타일을 조정합니다.", color = Color(0xFF9E9E9E), fontSize = 12.sp)
            }
        }

        PromptLabSection("기본 프롬프트", "Fusion의 기본 성격과 응답 방식을 설정합니다.") {
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF111111)) {
                BasicTextField(
                    value = customPrompt,
                    onValueChange = { customPrompt = it },
                    textStyle = TextStyle(color = Color(0xFFF5F5F5), fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth().height(160.dp).padding(10.dp),
                    decorationBox = { inner ->
                        if (customPrompt.isBlank()) {
                            Text(defaultPrompt, color = Color(0xFF9E9E9E), fontSize = 13.sp)
                        }
                        inner()
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PromptLabButton("저장") {
                    savePromptLabSettings(context, PromptLabSettings(customPrompt, style, showUncertainty, reduceQuestionRepetition, limitExcessiveEmoji))
                    Toast.makeText(context, "설정을 저장했습니다.", Toast.LENGTH_SHORT).show()
                }
                PromptLabButton("초기화") { showResetDialog = true }
                PromptLabButton("복사") {
                    clipboard.setText(AnnotatedString(if (customPrompt.isBlank()) defaultPrompt else customPrompt))
                    Toast.makeText(context, "프롬프트를 복사했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        PromptLabSection("응답 스타일", "") {
            listOf(
                "균형" to "일상 대화와 기술 설명을 균형 있게 답변합니다.",
                "정확" to "추론과 불확실성을 명확히 구분합니다.",
                "자세히" to "배경 설명과 예시를 충분히 포함합니다.",
                "간결" to "핵심만 짧고 명확하게 답변합니다.",
                "전문가" to "기술적 깊이를 높이고 용어를 정확히 사용합니다."
            ).forEach { (label, sub) ->
                Surface(
                    color = if (style == label) Color(0xFF202020) else Color(0xFF111111),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, color = Color(0xFFF5F5F5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(sub, color = Color(0xFF9E9E9E), fontSize = 12.sp)
                        }
                        TextButton(onClick = { style = label }) { Text(if (style == label) "선택됨" else "선택", color = Color(0xFF9FD0FF)) }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        PromptLabSection("출력 규칙", "") {
            PromptLabToggle("불확실성 표시", "불확실한 내용은 추론이라고 표시합니다.", showUncertainty) { showUncertainty = it }
            PromptLabToggle("질문 반복 줄이기", "사용자의 질문을 불필요하게 반복하지 않습니다.", reduceQuestionRepetition) { reduceQuestionRepetition = it }
            PromptLabToggle("과한 이모지 제한", "과장된 이모지와 마케팅식 표현을 줄입니다.", limitExcessiveEmoji) { limitExcessiveEmoji = it }
        }

        PromptLabSection("테스트 및 관리", "") {
            PromptLabButton("현재 프롬프트 복사") {
                val composed = buildPromptLabInstruction(PromptLabSettings(customPrompt, style, showUncertainty, reduceQuestionRepetition, limitExcessiveEmoji))
                clipboard.setText(AnnotatedString(composed))
                Toast.makeText(context, "프롬프트를 복사했습니다.", Toast.LENGTH_SHORT).show()
            }
            Spacer(modifier = Modifier.height(6.dp))
            PromptLabButton("기본값으로 초기화") { showResetDialog = true }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("프롬프트 초기화") },
            text = { Text("Prompt Lab 설정을 기본값으로 되돌리시겠습니까?") },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("취소") } },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    customPrompt = ""
                    style = "균형"
                    showUncertainty = true
                    reduceQuestionRepetition = true
                    limitExcessiveEmoji = true
                    savePromptLabSettings(context, PromptLabSettings())
                    Toast.makeText(context, "Prompt Lab 설정을 초기화했습니다.", Toast.LENGTH_SHORT).show()
                }) { Text("초기화") }
            }
        )
    }
}

@Composable
private fun PromptLabSection(title: String, description: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color(0xFFF5F5F5), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        if (description.isNotBlank()) {
            Text(description, color = Color(0xFF9E9E9E), fontSize = 12.sp)
        }
        content()
    }
}

@Composable
private fun PromptLabButton(label: String, onClick: () -> Unit) {
    Surface(color = Color(0xFF171717), shape = RoundedCornerShape(10.dp)) {
        TextButton(onClick = onClick) { Text(label, color = Color(0xFFF5F5F5)) }
    }
}

@Composable
private fun PromptLabToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(color = Color(0xFF111111), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color(0xFFF5F5F5), fontSize = 14.sp)
                Text(subtitle, color = Color(0xFF9E9E9E), fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
