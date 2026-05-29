package com.projectnuke.fusion.ui

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectnuke.fusion.data.BenchmarkResultEntity

private val DevLogPanelBg = Color(0xFF171717)
private val DevLogCardBg = Color(0xFF111111)
private val DevLogTextPrimary = Color(0xFFF5F5F5)
private val DevLogTextSecondary = Color(0xFF9E9E9E)
private val DevLogAccentBlue = Color(0xFF9FD0FF)

@Composable
fun DeveloperLogDialog(
    context: Context,
    prefs: SharedPreferences,
    clipboard: ClipboardManager,
    benchmarkResults: List<BenchmarkResultEntity>,
    onDismiss: () -> Unit
) {
    var refreshKey by remember { mutableStateOf(0) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val events = remember(refreshKey) { FusionDeveloperLogStore.load(context) }
    val snapshot = remember(refreshKey, benchmarkResults) {
        buildFusionDeveloperLogSnapshot(
            context = context,
            prefs = prefs,
            benchmarkResults = benchmarkResults,
            events = events
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("개발자 로그") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { DeveloperSectionCard("현재 상태", snapshot.fullLogText, 0, 7) }
                item { DeveloperSectionCard("모델", snapshot.fullLogText, 8, 13) }
                item { DeveloperSectionCard("메모리", snapshot.fullLogText, 14, 18) }
                item { DeveloperSectionCard("최근 오류", snapshot.fullLogText, 28, 48) }
                item { DeveloperSectionCard("벤치마크", snapshot.fullLogText, 19, 23) }
                item { DeveloperSectionCard("설정", snapshot.fullLogText, 24, 27) }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(snapshot.fullLogText))
                        Toast.makeText(context, "개발자 로그를 복사했습니다.", Toast.LENGTH_SHORT).show()
                    }) { Text("개발자 로그 복사", color = DevLogAccentBlue, fontSize = 12.sp) }
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(snapshot.errorReportText))
                        Toast.makeText(context, "오류 보고서를 복사했습니다.", Toast.LENGTH_SHORT).show()
                    }) { Text("오류 보고서 복사", color = DevLogAccentBlue, fontSize = 12.sp) }
                }
                TextButton(onClick = { showClearConfirm = true }) {
                    Text("로그 지우기", color = DevLogAccentBlue, fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("닫기", color = DevLogTextSecondary) }
        },
        containerColor = DevLogPanelBg,
        titleContentColor = DevLogTextPrimary,
        textContentColor = DevLogTextPrimary
    )

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("개발자 로그를 지우시겠습니까?") },
            text = { Text("최근 오류 및 진단 기록만 지워집니다. 채팅 기록은 삭제되지 않습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    FusionDeveloperLogStore.clear(context)
                    refreshKey += 1
                    showClearConfirm = false
                    Toast.makeText(context, "개발자 로그를 지웠습니다.", Toast.LENGTH_SHORT).show()
                }) { Text("지우기", color = DevLogAccentBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("취소", color = DevLogTextSecondary) }
            },
            containerColor = DevLogPanelBg,
            titleContentColor = DevLogTextPrimary,
            textContentColor = DevLogTextPrimary
        )
    }
}

@Composable
private fun DeveloperSectionCard(title: String, text: String, startLine: Int, endLine: Int) {
    val lines = text.lines()
    val sectionLines = if (startLine < lines.size) lines.subList(startLine, minOf(endLine + 1, lines.size)) else emptyList()
    Surface(shape = RoundedCornerShape(12.dp), color = DevLogCardBg, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = DevLogTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            sectionLines.forEach { line ->
                if (line.isNotBlank()) {
                    Text(line, color = DevLogTextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}
