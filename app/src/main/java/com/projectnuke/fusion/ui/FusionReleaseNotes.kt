package com.projectnuke.fusion.ui

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
    val status: String,
    val summary: String,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    ReleaseCard {
                        Text("Fusion의 최근 변경사항입니다.", color = ReleaseTextPrimary, fontSize = 13.sp)
                        Text("현재 앱 버전: $appVersionName", color = ReleaseTextSecondary, fontSize = 12.sp)
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
                                Text(note.version, color = ReleaseTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(note.status, color = ReleaseTextSecondary, fontSize = 11.sp)
                            }
                            Text(note.summary, color = ReleaseTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(460.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        ReleaseCard {
                            Text(note.status, color = ReleaseTextPrimary, fontSize = 12.sp)
                            Text(note.summary, color = ReleaseTextSecondary, fontSize = 12.sp)
                        }
                    }
                    items(note.sections, key = { it.title }) { section ->
                        ReleaseCard {
                            Text(section.title, color = ReleaseTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            section.items.forEach { item ->
                                Text("- $item", color = ReleaseTextSecondary, fontSize = 12.sp)
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

@Composable
private fun ReleaseCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = ReleaseCardBg, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

private fun buildFusionReleaseNotesHistory(): List<FusionReleaseNote> {
    return listOf(
        FusionReleaseNote(
            version = "0.3.5-alpha",
            status = "알파",
            summary = "업데이트 기록과 버전 정보를 정리했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "업데이트 기록",
                    items = listOf(
                        "0.3.4-alpha 패치 기록을 추가했습니다.",
                        "버전별 업데이트 기록을 더 명확하게 정리했습니다.",
                        "일부 업데이트 기록에서 글자가 깨져 보이던 문제를 수정했습니다.",
                        "업데이트 기록 복사 시 깨진 문자가 포함되지 않도록 정리했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "버전 관리",
                    items = listOf(
                        "현재 앱 버전을 0.3.5-alpha로 업데이트했습니다.",
                        "앱에 표시되는 버전 정보가 Android 앱 버전과 일치하도록 개선했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안정성",
                    items = listOf(
                        "업데이트 기록 화면만 수정하며 채팅, 모델 실행, 벤치마크 데이터는 변경하지 않았습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.3.4-alpha",
            status = "알파",
            summary = "개발자 로그 화면과 채팅 입력 화면 배치를 개선했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "개발자 로그",
                    items = listOf(
                        "개발자 로그 화면의 카드 정렬과 하단 버튼 배치를 개선했습니다.",
                        "개발자 로그 복사, 오류 보고서 복사, 로그 지우기, 닫기 버튼을 더 정돈된 형태로 배치했습니다.",
                        "개발자 로그 내용과 하단 동작 영역이 서로 겹치지 않도록 조정했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "채팅 화면",
                    items = listOf(
                        "키보드가 열릴 때 빈 채팅 화면과 입력 영역의 배치가 어긋나는 문제를 개선했습니다.",
                        "새 채팅 화면의 안내 문구가 입력창이나 하단 영역에 가려지지 않도록 조정했습니다.",
                        "입력창, 빠른 입력 영역, 시스템 내비게이션 영역의 하단 여백 처리를 개선했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "UI 안정성",
                    items = listOf(
                        "화면 하단 안전 영역 처리 방식을 정리했습니다.",
                        "일부 화면에서 하단 버튼이 가려질 수 있는 문제를 줄였습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.3.3-alpha",
            status = "알파",
            summary = "업데이트 기록과 앱 버전 표시를 개선했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "업데이트 기록",
                    items = listOf(
                        "업데이트 기록 화면을 추가했습니다.",
                        "버전별 변경사항을 목록에서 선택해 확인할 수 있도록 개선했습니다.",
                        "업데이트 기록을 클립보드로 복사할 수 있도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "앱 버전",
                    items = listOf(
                        "현재 앱 버전을 업데이트 기록 화면에 표시하도록 개선했습니다.",
                        "앱 버전 정보가 확인되지 않을 때 안내 문구를 표시하도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "UI",
                    items = listOf(
                        "업데이트 기록 화면을 다크 UI와 하늘색 강조 색상에 맞춰 정리했습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.3.2-alpha",
            status = "알파",
            summary = "개발자 로그와 진단 기능을 추가했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "개발자 로그",
                    items = listOf(
                        "현재 앱 상태, 모델, 메모리, 설정 정보를 확인할 수 있는 개발자 로그 화면을 추가했습니다.",
                        "개발자 로그와 오류 보고서를 클립보드로 복사할 수 있도록 했습니다.",
                        "최근 오류가 없을 때 안내 문구를 표시하도록 개선했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "진단 정보",
                    items = listOf(
                        "기기 모델, Android 버전, SoC/AP 정보, 메모리 상태를 진단 정보에 포함했습니다.",
                        "선택된 모델, 런타임 형식, 가속기, MTP 상태를 확인할 수 있도록 했습니다.",
                        "벤치마크 기록 요약을 개발자 로그에서 확인할 수 있도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "개인정보 보호",
                    items = listOf(
                        "오류 보고서에 채팅 내용, 첨부파일 내용, 전체 프롬프트가 포함되지 않도록 정리했습니다.",
                        "모델 메모 내용은 기본 진단 정보에 포함하지 않도록 했습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.3.1-alpha",
            status = "알파",
            summary = "모델 라이브러리 관리 기능을 개선했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "모델 관리",
                    items = listOf(
                        "모델 즐겨찾기와 숨김 관리를 추가했습니다.",
                        "모델 검색, 필터, 정렬 기능을 추가했습니다.",
                        "최근 사용 모델과 모델별 메모를 추가했습니다.",
                        "모델별 벤치마크 요약을 확인할 수 있도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "기기 추천",
                    items = listOf(
                        "기기 메모리와 모델 크기를 기준으로 추천 모델을 확인할 수 있도록 개선했습니다.",
                        "8GB 기기에서 사용할 수 있는 소형 모델을 더 자연스럽게 분류하도록 개선했습니다.",
                        "무거운 모델 선택 전 확인 절차를 추가했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "데이터 관리",
                    items = listOf(
                        "설정 백업 및 복원 기능을 추가했습니다.",
                        "채팅 Markdown 내보내기에서 내보낼 채팅을 선택할 수 있도록 개선했습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.3.0-alpha",
            status = "알파",
            summary = "Model Zoo 기반 구조를 추가했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "모델 라이브러리 기반",
                    items = listOf(
                        "Model Zoo 기반 구조를 추가했습니다.",
                        "Gemma, Qwen, Llama, Phi, DeepSeek, Mistral, Kimi 등 다양한 모델 후보를 표시할 수 있도록 준비했습니다.",
                        "모델별 패밀리, 런타임 형식, 권장 메모리 정보를 표시하도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "모델 상세 정보",
                    items = listOf(
                        "모델 상세 화면에서 파일 크기, 권장 메모리, 권장 토큰 수를 확인할 수 있도록 했습니다.",
                        "모델 페이지와 공식 링크를 열 수 있도록 했습니다.",
                        "기기 메모리 기준으로 모델 실행 경고를 표시하도록 개선했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "NPU/GPU/CPU 안내",
                    items = listOf(
                        "기기 SoC/AP에 따라 Exynos, Snapdragon, MediaTek, Tensor 계열 안내 문구를 다르게 표시하도록 준비했습니다.",
                        "전용 NPU 실행은 보장하지 않고 후보 정보로만 표시하도록 정리했습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.2-beta",
            status = "베타",
            summary = "기본 로컬 모델 채팅 기능을 정리했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "채팅",
                    items = listOf(
                        "기본 로컬 모델 채팅 기능을 정리했습니다.",
                        "고급 생성 설정을 추가했습니다.",
                        "Reasoning, 웹 검색, MTP 가속 실험 옵션을 사용할 수 있도록 했습니다.",
                        "응답 속도와 토큰 정보를 확인할 수 있도록 개선했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "대화 관리",
                    items = listOf(
                        "채팅 고정, 이름 변경, 삭제, 아카이브 기능을 개선했습니다.",
                        "사이드바 검색과 검색 결과 표시를 개선했습니다.",
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
        appendLine(note.summary)
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
            appendLine(note.summary)
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
