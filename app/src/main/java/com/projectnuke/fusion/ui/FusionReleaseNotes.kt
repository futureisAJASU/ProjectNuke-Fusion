package com.projectnuke.fusion.ui

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ReleasePanelBg = Color(0xFF171717)
private val ReleaseCardBg = Color(0xFF111111)
private val ReleaseTextPrimary = Color(0xFFF5F5F5)
private val ReleaseTextSecondary = Color(0xFF9E9E9E)
private val ReleaseAccentBlue = Color(0xFF9FD0FF)

data class FusionReleaseNoteSection(
    val title: String,
    val items: List<String>
)

data class FusionReleaseNote(
    val version: String,
    val channel: String,
    val status: String,
    val sections: List<FusionReleaseNoteSection>
)

@Composable
fun ReleaseNotesDialog(
    context: Context,
    clipboard: ClipboardManager,
    onDismiss: () -> Unit
) {
    val appVersionName = remember(context) { resolveVersionName(context) }
    val releaseNotes = remember { buildFusionReleaseNotesHistory() }
    var selectedNote by remember { mutableStateOf<FusionReleaseNote?>(null) }

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
                            Text("현재 앱 버전: $appVersionName", color = ReleaseTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
                items(releaseNotes, key = { it.version }) { note ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ReleaseCardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedNote = note }
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${note.version}", color = ReleaseTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(note.status, color = ReleaseTextSecondary, fontSize = 11.sp)
                            }
                            Text(note.channel, color = ReleaseTextSecondary, fontSize = 11.sp)
                            val firstLine = note.sections.firstOrNull()?.items?.firstOrNull() ?: "변경사항을 확인해 주세요."
                            Text(firstLine, color = ReleaseTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("섹션 ${note.sections.size}개", color = ReleaseTextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(buildReleaseNotesHistoryText(appVersionName, releaseNotes)))
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

    selectedNote?.let { note ->
        AlertDialog(
            onDismissRequest = { selectedNote = null },
            title = { Text(note.version) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(460.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Surface(shape = RoundedCornerShape(12.dp), color = ReleaseCardBg, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(note.channel, color = ReleaseTextPrimary, fontSize = 12.sp)
                                Text(note.status, color = ReleaseTextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                    items(note.sections, key = { it.title }) { section ->
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
                    clipboard.setText(AnnotatedString(buildSingleReleaseNotesText(note)))
                    Toast.makeText(context, "업데이트 기록을 복사했습니다.", Toast.LENGTH_SHORT).show()
                }) { Text("업데이트 기록 복사", color = ReleaseAccentBlue) }
            },
            dismissButton = {
                TextButton(onClick = { selectedNote = null }) { Text("닫기", color = ReleaseTextSecondary) }
            },
            containerColor = ReleasePanelBg,
            titleContentColor = ReleaseTextPrimary,
            textContentColor = ReleaseTextPrimary
        )
    }
}

private fun buildFusionReleaseNotesHistory(): List<FusionReleaseNote> {
    return listOf(
        FusionReleaseNote(
            version = "0.3.2-alpha",
            channel = "?뚰뙆",
            status = "媛쒕컻 以?",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "?먮줈?뚮뒗 ?낆뿭",
                    items = listOf(
                        "?뤡留?뱀뀡?쒖쓽 ?뚮엺?놁? 援ъ“瑜?젣?곗뻽?듬땲??",
                        "?꾩옱 ?앹쓽 ?ㅽ겕 寃?을 ?붿옉 Android 踰꾩쟾怨??먯씠?꾩씠?꽣?먯뿉 ?맞꽭??媛?닔 ?덈룄濡?媛쒖꽑?덉뒿?덈떎.",
                        "踰꾩쟾蹂?`?뱀꽦?몃? ?쒖씠洹?濡?蹂닿퀬?섍쾶 媛쒖꽑?덉뒿?덈떎."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "UI 媛쒖꽑",
                    items = listOf(
                        "?낅튃遺??꾧닔怨?媛깆냽?듭뿭???뺣━?덉뒿?덈떎.",
                        "?ㅽ겕 UI? ?섎뒛??媛뺤“ ?됱긽???듭씪?덉뒿?덈떎."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.3.1-alpha",
            channel = "알파",
            status = "개발 중",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "모델 라이브러리",
                    items = listOf(
                        "모델 즐겨찾기와 숨김 관리를 개선했습니다.",
                        "모델 검색, 필터, 정렬 기능을 추가했습니다.",
                        "내 기기에 추천 모델을 확인할 수 있도록 개선했습니다.",
                        "최근 사용 모델과 모델별 메모를 추가했습니다.",
                        "모델별 벤치마크 요약을 확인할 수 있습니다.",
                        "무거운 모델 선택 전 확인 절차를 추가했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "진단 및 데이터 관리",
                    items = listOf(
                        "설정 백업 및 복원을 설정 화면으로 이동했습니다.",
                        "채팅 Markdown 내보내기에서 내보낼 채팅을 선택할 수 있도록 개선했습니다.",
                        "개발자 로그와 오류 보고서 복사를 추가했습니다.",
                        "업데이트 기록 화면을 추가했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "UI 개선",
                    items = listOf(
                        "모델 상세 화면을 더 간결하게 정리했습니다.",
                        "하단 메뉴와 더보기 메뉴의 가독성을 개선했습니다.",
                        "다크 UI와 하늘색 강조 색상을 통일했습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.3.0-alpha",
            channel = "알파",
            status = "알파",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "모델 라이브러리 기반",
                    items = listOf(
                        "Model Zoo 기반 구조를 추가했습니다.",
                        "Qwen, Llama, Phi, DeepSeek, Mistral, Kimi 등 다양한 모델 후보를 표시할 수 있도록 준비했습니다.",
                        "모델별 메모리 권장 정보와 링크 정보를 표시했습니다.",
                        "모델 상세 화면과 모델 선택 흐름을 개선했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "성능 및 안전",
                    items = listOf(
                        "기기 메모리와 모델 크기를 기준으로 권장 정보를 표시하도록 개선했습니다.",
                        "저메모리 기기에서 더 안전하게 동작하도록 메모리 경고를 개선했습니다.",
                        "벤치마크 기록과 MTP 비교 정보를 개선했습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.2-beta",
            channel = "베타",
            status = "베타",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "채팅 및 기본 기능",
                    items = listOf(
                        "기본 로컬 모델 채팅 기능을 정리했습니다.",
                        "고급 생성 설정을 추가했습니다.",
                        "MTP 가속 실험 옵션을 추가했습니다.",
                        "응답 속도와 토큰 정보를 표시하도록 개선했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "대화 관리",
                    items = listOf(
                        "채팅 고정, 이름 변경, 삭제, 아카이브 기능을 개선했습니다.",
                        "검색 결과와 기본 목록의 시각적 구분을 개선했습니다."
                    )
                )
            )
        )
    )
}

private fun buildSingleReleaseNotesText(note: FusionReleaseNote): String {
    return buildString {
        appendLine("업데이트 기록")
        appendLine("${note.version} (${note.status})")
        appendLine(note.channel)
        appendLine()
        note.sections.forEach { section ->
            appendLine("[${section.title}]")
            section.items.forEach { item -> appendLine("- $item") }
            appendLine()
        }
    }.trimEnd()
}

private fun buildReleaseNotesHistoryText(appVersionName: String, notes: List<FusionReleaseNote>): String {
    return buildString {
        appendLine("업데이트 기록")
        appendLine("현재 앱 버전: $appVersionName")
        appendLine()
        notes.forEach { note ->
            appendLine("${note.version} (${note.status})")
            note.sections.forEach { section ->
                appendLine("[${section.title}]")
                section.items.forEach { item -> appendLine("- $item") }
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
    return packageInfo?.versionName ?: "현재 앱 버전 정보를 확인할 수 없습니다."
}
