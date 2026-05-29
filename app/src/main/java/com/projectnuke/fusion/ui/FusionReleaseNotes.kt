package com.projectnuke.fusion.ui

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ReleasePanelBg = Color(0xFF171717)
private val ReleaseCardBg = Color(0xFF111111)
private val ReleaseTextPrimary = Color(0xFFF5F5F5)
private val ReleaseTextSecondary = Color(0xFF9E9E9E)
private val ReleaseAccentBlue = Color(0xFF9FD0FF)

private data class ReleaseSection(
    val title: String,
    val items: List<String>
)

@Composable
fun ReleaseNotesDialog(
    context: Context,
    clipboard: ClipboardManager,
    onDismiss: () -> Unit
) {
    val versionName = remember(context) { resolveVersionName(context) }
    val sections = remember {
        listOf(
            ReleaseSection(
                title = "모델 라이브러리",
                items = listOf(
                    "모델 검색, 필터, 정렬 기능을 추가했습니다.",
                    "즐겨찾기와 숨긴 모델 관리를 추가했습니다.",
                    "내 기기에 추천 모델을 확인할 수 있습니다.",
                    "최근 사용 모델과 모델별 메모를 추가했습니다.",
                    "모델별 벤치마크 요약을 확인할 수 있습니다.",
                    "무거운 모델 선택 전 확인 절차를 추가했습니다."
                )
            ),
            ReleaseSection(
                title = "성능 및 안전",
                items = listOf(
                    "기기 메모리와 모델 크기를 기준으로 권장 정보를 표시합니다.",
                    "저메모리 기기에서 더 안전하게 동작하도록 메모리 경고를 개선했습니다.",
                    "벤치마크 기록과 MTP 비교 정보를 개선했습니다."
                )
            ),
            ReleaseSection(
                title = "데이터 관리",
                items = listOf(
                    "설정 백업 및 복원을 추가했습니다.",
                    "채팅 Markdown 내보내기 기능을 개선했습니다.",
                    "개발자 로그와 오류 보고서 복사를 추가했습니다."
                )
            ),
            ReleaseSection(
                title = "UI 개선",
                items = listOf(
                    "모델 상세 화면을 더 간결하게 정리했습니다.",
                    "다크 UI와 하늘색 강조 색상을 통일했습니다.",
                    "검색 결과와 모델 목록의 시각적 구분을 개선했습니다."
                )
            )
        )
    }
    val copyText = remember(versionName, sections) { buildReleaseNotesCopyText(versionName, sections) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("업데이트 기록") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Surface(shape = RoundedCornerShape(12.dp), color = ReleaseCardBg, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Fusion의 최근 변경사항입니다.", color = ReleaseTextPrimary, fontSize = 13.sp)
                            Text("현재 버전: $versionName", color = ReleaseTextSecondary, fontSize = 12.sp)
                            Text("0.3.0-alpha · 개발 중", color = ReleaseTextSecondary, fontSize = 11.sp)
                        }
                    }
                }
                items(sections, key = { it.title }) { section ->
                    Surface(shape = RoundedCornerShape(12.dp), color = ReleaseCardBg, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(section.title, color = ReleaseTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            section.items.forEach { item ->
                                Text("• $item", color = ReleaseTextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(copyText))
                Toast.makeText(context, "업데이트 기록을 복사했습니다.", Toast.LENGTH_SHORT).show()
            }) { Text("업데이트 기록 복사", color = ReleaseAccentBlue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("닫기", color = ReleaseTextSecondary) }
        },
        containerColor = ReleasePanelBg,
        titleContentColor = ReleaseTextPrimary,
        textContentColor = ReleaseTextPrimary
    )
}

private fun buildReleaseNotesCopyText(versionName: String, sections: List<ReleaseSection>): String {
    return buildString {
        appendLine("업데이트 기록")
        appendLine("Fusion의 최근 변경사항입니다.")
        appendLine("현재 버전: $versionName")
        appendLine("0.3.0-alpha · 개발 중")
        appendLine()
        sections.forEach { section ->
            appendLine("[${section.title}]")
            section.items.forEach { item ->
                appendLine("- $item")
            }
            appendLine()
        }
    }.trimEnd()
}

private fun resolveVersionName(context: Context): String {
    val packageInfo = runCatching {
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
    return packageInfo?.versionName ?: "현재 버전 정보를 확인할 수 없습니다."
}
