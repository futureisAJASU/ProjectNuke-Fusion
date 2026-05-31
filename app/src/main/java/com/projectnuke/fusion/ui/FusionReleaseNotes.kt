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

private data class FusionReleaseNoteGroup(
    val key: String,
    val notes: List<FusionReleaseNote>
)

@Composable
fun ReleaseNotesDialog(
    context: Context,
    clipboard: ClipboardManager,
    onDismiss: () -> Unit
) {
    val appVersionName = remember(context) { resolveVersionName(context) }
    val releaseNotes = remember { buildFusionReleaseNotesHistory() }
    val groupedReleaseNotes = remember(releaseNotes) { buildGroupedReleaseNotes(releaseNotes) }
    val defaultExpandedGroup = remember(groupedReleaseNotes) { groupedReleaseNotes.firstOrNull()?.key }
    var expandedGroups by remember(defaultExpandedGroup) {
        mutableStateOf(defaultExpandedGroup?.let(::setOf) ?: emptySet())
    }
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
                items(groupedReleaseNotes, key = { it.key }) { group ->
                    val isExpanded = group.key in expandedGroups
                    ReleaseNotesGroupCard(
                        group = group,
                        expanded = isExpanded,
                        onToggle = {
                            expandedGroups = if (isExpanded) {
                                expandedGroups - group.key
                            } else {
                                expandedGroups + group.key
                            }
                        }
                    )
                }
                groupedReleaseNotes.forEach { group ->
                    if (group.key in expandedGroups) {
                        items(group.notes, key = { it.version }) { note ->
                            ReleaseNoteSummaryCard(note = note, onClick = { selectedNote = note })
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

@Composable
private fun ReleaseNotesGroupCard(
    group: FusionReleaseNoteGroup,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val latestVersion = group.notes.firstOrNull()?.version ?: "-"
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ReleaseCardBg,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(group.key, color = ReleaseAccentBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(if (expanded) "접기" else "펼치기", color = ReleaseAccentBlue, fontSize = 11.sp)
            }
            Text("${group.notes.size}개 업데이트", color = ReleaseTextPrimary, fontSize = 12.sp)
            Text("최신: $latestVersion", color = ReleaseTextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ReleaseNoteSummaryCard(
    note: FusionReleaseNote,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ReleaseCardBg,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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

private fun buildGroupedReleaseNotes(notes: List<FusionReleaseNote>): List<FusionReleaseNoteGroup> {
    val grouped = linkedMapOf<String, MutableList<FusionReleaseNote>>()
    notes.forEach { note ->
        val key = releaseNoteGroupKey(note.version)
        grouped.getOrPut(key) { mutableListOf() }.add(note)
    }
    return grouped.entries
        .sortedBy { releaseNoteGroupOrder(it.key) }
        .map { (key, groupNotes) -> FusionReleaseNoteGroup(key = key, notes = groupNotes) }
}

private fun releaseNoteGroupKey(version: String): String {
    val baseVersion = version.substringBefore("-")
    val parts = baseVersion.split(".")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (parts.size < 2) return "기타"
    val major = parts[0].toIntOrNull() ?: return "기타"
    val minor = parts[1].toIntOrNull() ?: return "기타"
    return "$major.$minor.x"
}

private fun releaseNoteGroupOrder(groupKey: String): Int {
    return when (groupKey) {
        "0.4.x" -> 0
        "0.3.x" -> 1
        "0.2.x" -> 2
        "0.1.x" -> 3
        "기타" -> 4
        else -> 5
    }
}

private fun buildFusionReleaseNotesHistory(): List<FusionReleaseNote> {
    return listOf(
        FusionReleaseNote(
            version = "0.4.16-alpha",
            status = "알파",
            summary = "GitHub 바로가기 표시를 개선했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "GitHub 연결",
                    items = listOf(
                        "대화 목록 상단에 GitHub 저장소 바로가기 버튼을 추가했습니다.",
                        "GitHub 버튼을 누르면 외부 브라우저에서 Fusion 저장소를 열 수 있도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "UI",
                    items = listOf(
                        "GitHub 바로가기를 검색창과 어울리는 둥근 pill 형태로 정리했습니다.",
                        "상단 헤더에서 새 채팅 버튼과 함께 자연스럽게 보이도록 배치를 조정했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안전성",
                    items = listOf(
                        "이번 업데이트는 외부 링크와 UI 배치만 변경합니다.",
                        "채팅, 모델 실행, 벤치마크, 메모리 데이터 구조는 변경하지 않았습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.4.15-alpha",
            status = "알파",
            summary = "모델 메모리 점검 기준을 동적으로 개선했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "모델 점검",
                    items = listOf(
                        "모델 실행 전 점검에서 총 메모리와 현재 사용 가능한 메모리를 함께 반영하도록 개선했습니다.",
                        "모델 크기, 권장 메모리, 현재 최대 토큰 수를 기준으로 실행 위험도를 계산하도록 했습니다.",
                        "8GB 기준에 고정되지 않고 기기별 RAM 상태에 따라 권장, 주의 필요, 무거움, 권장하지 않음 단계를 표시합니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "모델 추천",
                    items = listOf(
                        "모델 목록과 상세 화면의 경고 문구가 현재 기기 상태를 더 잘 반영하도록 정리했습니다.",
                        "총 메모리는 충분하지만 현재 가용 메모리가 낮은 경우를 구분해 안내합니다.",
                        "무거운 모델 선택 전 확인 절차가 동적 위험도 계산을 사용하도록 개선했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안전성",
                    items = listOf(
                        "이번 업데이트는 모델 실행 전 안내와 추천 기준을 개선합니다.",
                        "채팅, 모델 실행, 벤치마크, 메모리 데이터 구조는 변경하지 않았습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.4.14-alpha",
            status = "알파",
            summary = "GitHub 이슈 제보와 실험 노트 UI를 개선했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "GitHub 연결",
                    items = listOf(
                        "GitHub Issues로 버그와 개선 요청을 남길 수 있는 진입점을 추가했습니다.",
                        "브라우저에서 이슈 페이지를 열 수 있도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "실험 노트",
                    items = listOf(
                        "실험 노트 화면의 하단 버튼 배치를 더 정돈된 형태로 개선했습니다.",
                        "복사, 저장, 초기화, 닫기 버튼을 2줄 구성으로 정리했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안전성",
                    items = listOf(
                        "이번 업데이트는 링크 연결과 UI 정리 중심으로 적용했습니다.",
                        "채팅, 모델 실행, 벤치마크, 메모리 데이터 구조는 변경하지 않았습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.4.12-alpha",
            status = "알파",
            summary = "앱 정보 화면 표시 방식을 정리했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "앱 정보",
                    items = listOf(
                        "앱 정보 영역의 개발 관련 항목 배치를 정리했습니다.",
                        "릴리즈 체크리스트를 일반 화면에서 숨기고, 개발자 모드에서만 표시하도록 했습니다.",
                        "앱 정보 카드를 7초 이상 길게 누르면 개발자 모드를 활성화할 수 있도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "개발자 항목",
                    items = listOf(
                        "개발 명령어 화면을 제거했습니다.",
                        "릴리즈 체크리스트는 개발자 항목으로 이동했습니다.",
                        "개발자 모드는 비밀번호가 아닌 UI 표시 전환 기능으로 동작합니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안전성",
                    items = listOf(
                        "이번 업데이트는 설정 메뉴 표시와 안내 화면 정리 중심으로 적용했습니다.",
                        "채팅, 모델 실행, 벤치마크, 메모리 데이터 구조는 변경하지 않았습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.4.11-alpha",
            status = "알파",
            summary = "가이드 화면 내용을 더 실용적으로 확장했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "가이드 확장",
                    items = listOf(
                        "사용 가이드에 시작 순서, 모델 선택, 채팅, 메모리, 벤치마크, A/B 테스트, 데이터 관리 설명을 더 자세히 추가했습니다.",
                        "문제 해결 가이드에 모델 실행, 메모리 부족, 응답 지연, A/B 테스트, ADB 설치, 로그 확인 항목을 더 실용적으로 정리했습니다.",
                        "모델 호환성 가이드에 기기 RAM 기준, 실제 메모리 차이, 로컬 실행 구분, 모델 선택 전 확인 사항을 추가했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "복사 개선",
                    items = listOf(
                        "가이드 복사 시 확장된 전체 내용을 함께 복사하도록 정리했습니다.",
                        "깨진 한글 없이 실사용 안내 문구를 읽기 쉽게 다듬었습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안전성",
                    items = listOf(
                        "이번 업데이트는 가이드 화면의 정적 내용만 확장했습니다.",
                        "채팅, 모델 실행, 벤치마크, 메모리, 데이터 스키마 동작은 변경하지 않았습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.4.10-alpha",
            status = "알파",
            summary = "문제 해결 가이드를 추가했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "문제 해결",
                    items = listOf(
                        "모델 실행, 메모리 부족, 벤치마크 오류, MTP 설정 문제를 확인할 수 있는 문제 해결 가이드를 추가했습니다.",
                        "채팅 화면, 메모리 기능, ADB 설치, 로그 확인 관련 안내를 정리했습니다.",
                        "각 문제 해결 항목을 클립보드로 복사할 수 있도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "개발 보조",
                    items = listOf(
                        "개발자 로그와 logcat 확인 흐름을 더 쉽게 찾을 수 있도록 했습니다.",
                        "릴리즈 체크리스트와 함께 사용할 수 있는 진단용 안내 화면을 추가했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안전성",
                    items = listOf(
                        "이번 업데이트는 정적 안내 화면과 복사 기능만 추가합니다.",
                        "앱 내부에서 명령어를 실행하지 않으며, 채팅과 모델 실행 데이터는 변경하지 않습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.4.8-alpha",
            status = "알파",
            summary = "실험 노트 화면을 추가했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "실험 노트",
                    items = listOf(
                        "모델 테스트와 벤치마크 메모를 기록할 수 있는 실험 노트 화면을 추가했습니다.",
                        "실험 노트를 저장, 복사, 초기화할 수 있도록 했습니다.",
                        "실험 노트는 로컬 SharedPreferences에 저장됩니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "개발 보조",
                    items = listOf(
                        "A/B 테스트, 벤치마크, 모델 실행 결과를 사람이 직접 정리할 수 있도록 했습니다.",
                        "반복 실험 중 발견한 내용을 앱 안에서 간단히 기록할 수 있도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안전성",
                    items = listOf(
                        "실험 노트는 채팅, 모델 실행, 메모리 컨텍스트에 자동으로 반영되지 않습니다.",
                        "실험 노트 내용은 개발자 로그와 설정 백업에 포함하지 않습니다.",
                        "채팅, 모델 실행, 벤치마크, 메모리 데이터 구조는 변경하지 않았습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.4.7-alpha",
            status = "알파",
            summary = "프롬프트 프리셋 화면을 추가했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "프롬프트 프리셋",
                    items = listOf(
                        "자주 쓰는 요청 문구를 확인하고 복사할 수 있는 프롬프트 프리셋 화면을 추가했습니다.",
                        "요약, 설명, 비교, 코드, Fusion 개발, 실험 카테고리를 추가했습니다.",
                        "각 프리셋을 클립보드로 복사할 수 있도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "개발 보조",
                    items = listOf(
                        "Fusion 기능 구현과 테스트에 사용할 수 있는 안전한 개발용 프롬프트를 추가했습니다.",
                        "벤치마크와 A/B 테스트 결과 해석에 사용할 수 있는 실험용 프롬프트를 추가했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안전성",
                    items = listOf(
                        "이번 업데이트는 정적 프리셋과 복사 기능만 추가합니다.",
                        "채팅, 모델 실행, 벤치마크, 메모리 데이터 구조는 변경하지 않았습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.4.6-alpha",
            status = "알파",
            summary = "릴리즈 체크리스트를 추가했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "릴리즈 확인",
                    items = listOf(
                        "빌드와 실기기 테스트 항목을 확인할 수 있는 릴리즈 체크리스트 화면을 추가했습니다.",
                        "채팅, 모델, 메모리, 실험, 데이터, 진단 항목을 그룹별로 확인할 수 있도록 했습니다.",
                        "전체 선택과 초기화 기능을 추가했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "개발 보조",
                    items = listOf(
                        "체크리스트를 클립보드로 복사할 수 있도록 했습니다.",
                        "배포 전 반복 테스트 흐름을 더 쉽게 확인할 수 있도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안전성",
                    items = listOf(
                        "체크리스트는 로컬 화면 상태로만 동작하며 앱 데이터는 변경하지 않습니다.",
                        "채팅, 모델 실행, 벤치마크, 메모리 데이터 구조는 변경하지 않았습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.4.5-alpha",
            status = "알파",
            summary = "기기 정보 화면을 추가했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "기기 정보",
                    items = listOf(
                        "현재 기기의 모델명, Android 버전, AP/SoC 정보를 확인할 수 있는 기기 정보 화면을 추가했습니다.",
                        "총 메모리와 현재 사용 가능한 메모리를 확인할 수 있도록 했습니다.",
                        "기기 메모리 기준으로 Fusion 권장 모드를 표시하도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "모델 실행 참고",
                    items = listOf(
                        "8GB급 기기에서 소형 모델과 낮은 최대 토큰 수 설정을 권장하도록 안내를 추가했습니다.",
                        "모델 파일 크기와 실제 실행 메모리가 다를 수 있다는 안내를 추가했습니다.",
                        "KV 캐시와 런타임 버퍼로 인한 메모리 부담을 설명했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안전성",
                    items = listOf(
                        "이번 업데이트는 읽기 전용 기기 정보 화면 중심으로 추가했습니다.",
                        "채팅, 모델 실행, 벤치마크, 메모리 데이터 구조는 변경하지 않았습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.4.4-alpha",
            status = "알파",
            summary = "상태 대시보드를 추가했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "상태 확인",
                    items = listOf(
                        "현재 모델, 메모리, 성능, 기기, 앱 정보를 한눈에 확인할 수 있는 상태 대시보드를 추가했습니다.",
                        "저장된 메모리와 대화 요약은 내용이 아니라 개수와 상태만 표시하도록 했습니다.",
                        "벤치마크와 A/B 테스트 기록 요약을 확인할 수 있도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "사용성",
                    items = listOf(
                        "상태 정보를 클립보드로 복사할 수 있도록 했습니다.",
                        "개발자 로그보다 간단한 사용자용 상태 확인 화면을 추가했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안정성",
                    items = listOf(
                        "이번 업데이트는 읽기 전용 상태 화면 중심으로 추가했습니다.",
                        "채팅, 모델 실행, 벤치마크, 메모리 데이터 구조는 변경하지 않았습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.4.3-alpha",
            status = "알파",
            summary = "모델 호환성 가이드를 추가했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "모델 가이드",
                    items = listOf(
                        "모델군별 특징과 실행 기준을 확인할 수 있는 모델 호환성 가이드를 추가했습니다.",
                        "Gemma, Qwen, Llama, Phi, DeepSeek, Mistral, Kimi 모델군의 용도와 주의사항을 정리했습니다.",
                        "8GB 기기에서 권장되는 모델 크기와 실행 기준을 확인할 수 있도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "사용성",
                    items = listOf(
                        "설정 화면에서 모델 호환성 가이드를 열 수 있도록 했습니다.",
                        "가이드 내용을 클립보드로 복사할 수 있도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안전성",
                    items = listOf(
                        "이번 업데이트는 읽기 전용 가이드 화면만 추가합니다.",
                        "채팅, 모델 실행, 벤치마크, 메모리 데이터는 변경하지 않았습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.4.2-alpha",
            status = "알파",
            summary = "사용 가이드를 추가했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "사용 가이드",
                    items = listOf(
                        "Fusion의 주요 기능과 사용 방법을 빠르게 확인할 수 있는 가이드 화면을 추가했습니다.",
                        "모델 라이브러리, 메모리, 벤치마크, A/B 테스트, 데이터 관리, 문제 해결 흐름을 한곳에서 정리했습니다.",
                        "가이드 내용을 클립보드로 복사할 수 있도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "UI",
                    items = listOf(
                        "설정 화면에서 사용 가이드로 바로 들어갈 수 있도록 했습니다.",
                        "다크 UI와 하늘색 강조 색상에 맞춰 가이드 화면을 정리했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안전성",
                    items = listOf(
                        "이번 패치는 읽기 전용 안내 화면과 버전 정보만 수정합니다.",
                        "채팅, 모델 실행, 벤치마크, 메모리, A/B 테스트 동작은 변경하지 않았습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.4.1-alpha",
            status = "알파",
            summary = "A/B 테스트 기록과 결과 관리를 추가했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "A/B 테스트 기록",
                    items = listOf(
                        "실행한 A/B 테스트 결과를 최근 기록으로 확인할 수 있도록 했습니다.",
                        "프롬프트, 모델, 설정, 응답 결과, 속도 정보를 함께 볼 수 있도록 했습니다.",
                        "최근 A/B 테스트 기록을 열기, 복사, 삭제할 수 있도록 개선했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "결과 비교",
                    items = listOf(
                        "A/B 테스트 결과 상세 화면에서 각 대상의 답변과 성능 정보를 비교할 수 있습니다.",
                        "가장 빠른 응답 시작, 가장 높은 디코딩 속도, 실패한 대상 수를 요약해 표시합니다.",
                        "필요한 경우 결과를 Markdown 형식으로 복사할 수 있도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안전성",
                    items = listOf(
                        "A/B 테스트 기록은 채팅 기록이나 모델 파일과 별도로 관리됩니다.",
                        "개발자 로그에는 A/B 테스트 본문이 아니라 기록 개수와 상태만 표시합니다.",
                        "설정 백업에는 A/B 테스트 결과 본문을 포함하지 않습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.4.0-alpha",
            status = "알파",
            summary = "모델 A/B 테스트 기능을 추가했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "모델 비교",
                    items = listOf(
                        "같은 프롬프트로 여러 모델 또는 설정을 비교할 수 있는 A/B 테스트 화면을 추가했습니다.",
                        "각 테스트 대상의 모델, 생성 설정, 응답 결과를 함께 확인할 수 있도록 했습니다.",
                        "테스트는 메모리 부담을 줄이기 위해 순차적으로 실행됩니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "성능 비교",
                    items = listOf(
                        "First Token Latency, 총 생성 시간, 토큰 속도 등 주요 성능 정보를 결과 카드에 표시합니다.",
                        "가장 빠른 응답 시작과 가장 높은 디코딩 속도를 비교 요약으로 확인할 수 있습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안전성",
                    items = listOf(
                        "지원되지 않는 모델이나 원격 전용 모델은 로컬 A/B 테스트 대상에서 제외합니다.",
                        "일부 테스트가 실패해도 다른 결과는 유지되도록 처리했습니다.",
                        "개발자 로그에는 테스트 결과의 본문이 아니라 상태와 요약 정보만 기록합니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.3.14-alpha",
            status = "알파",
            summary = "메모리 검색과 적용 범위를 추가했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "메모리 관리",
                    items = listOf(
                        "저장된 메모리를 검색하고 필터링할 수 있도록 개선했습니다.",
                        "메모리를 최근 수정순, 최근 저장순, 길이 기준으로 정렬할 수 있도록 했습니다.",
                        "메모리별 사용 상태와 적용 범위를 더 명확하게 확인할 수 있도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "메모리 적용 범위",
                    items = listOf(
                        "메모리를 전체 대화 또는 특정 대화 기준으로 사용할 수 있도록 적용 범위 설정을 추가했습니다.",
                        "사용 중지된 메모리는 답변 생성에 포함되지 않도록 정리했습니다.",
                        "메모리 컨텍스트 미리보기가 실제 답변 생성 규칙과 더 일치하도록 개선했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안전성",
                    items = listOf(
                        "저장된 메모리와 대화 요약 내용은 설정 백업에 포함하지 않도록 유지했습니다.",
                        "개발자 로그에는 메모리 내용이 아니라 개수와 상태만 표시하도록 정리했습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.3.13-alpha",
            status = "알파",
            summary = "저장된 메모리를 답변 생성에 반영할 수 있도록 했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "메모리",
                    items = listOf(
                        "저장된 메모리를 답변 생성에 참고하는 설정을 추가했습니다.",
                        "메모리별 사용 여부를 관리할 수 있도록 개선했습니다.",
                        "답변 생성에 포함될 메모리 컨텍스트를 미리 확인할 수 있도록 했습니다.",
                        "대화 요약과 저장된 메모리를 함께 활용할 수 있도록 정리했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "채팅",
                    items = listOf(
                        "일반 답변 생성과 답변 다시 생성이 동일한 메모리 컨텍스트를 사용하도록 개선했습니다.",
                        "긴 대화에서도 저장된 핵심 정보를 더 안정적으로 참고할 수 있도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안전성",
                    items = listOf(
                        "삭제되거나 사용 중지된 메모리는 답변 생성에 포함되지 않도록 했습니다.",
                        "메모리 컨텍스트 길이에 제한을 적용해 긴 메모리로 인한 문제를 줄였습니다.",
                        "개발자 로그에는 메모리 내용이 아니라 개수와 상태만 표시하도록 했습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.3.12-alpha",
            status = "알파",
            summary = "메모리 관리 화면을 추가했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "메모리 관리",
                    items = listOf(
                        "저장된 메모리와 대화 요약을 한곳에서 확인하고 관리할 수 있는 화면을 추가했습니다.",
                        "저장된 메모리를 검색하고 수정, 복사, 삭제할 수 있도록 했습니다.",
                        "대화 요약도 열기, 복사, 삭제로 관리할 수 있도록 정리했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "저장소 재사용",
                    items = listOf(
                        "메모리 후보 추출에서 저장한 메모리를 같은 저장소로 불러오도록 연결했습니다.",
                        "기존 대화 요약 저장 구조를 그대로 재사용해 별도 데이터베이스 변경 없이 관리할 수 있도록 했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "버전 관리",
                    items = listOf(
                        "현재 앱 버전을 0.3.12-alpha로 업데이트했습니다.",
                        "0.3.12-alpha 패치 기록을 업데이트 기록에 추가했습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.3.11-alpha",
            status = "알파",
            summary = "메모리 후보 추출과 버전 정보를 정리했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "메모리 후보 추출",
                    items = listOf(
                        "현재 대화에서 나중에 참고할 만한 메모리 후보를 추출할 수 있도록 했습니다.",
                        "추출한 후보를 검토한 뒤 복사하거나 선택해서 저장할 수 있도록 했습니다.",
                        "메모리 후보는 자동 저장하지 않고 사용자가 확인한 항목만 저장합니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "대화 맥락",
                    items = listOf(
                        "저장된 대화 요약과 최근 대화를 함께 참고해 메모리 후보를 정리하도록 개선했습니다.",
                        "민감한 정보는 자동 저장하지 않고 검토용 후보로만 표시합니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "버전 관리",
                    items = listOf(
                        "현재 앱 버전을 0.3.11-alpha로 업데이트했습니다.",
                        "0.3.11-alpha 패치 기록을 업데이트 기록에 추가했습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.3.10-alpha",
            status = "알파",
            summary = "업데이트 기록, 채팅 설정의 인코딩 문제를 수정했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "업데이트 기록",
                    items = listOf(
                        "일부 버전의 패치노트에서 글자가 깨져 보이던 문제를 수정했습니다.",
                        "버전별 업데이트 기록이 정상적인 한국어로 표시되도록 정리했습니다.",
                        "업데이트 기록 복사 시 깨진 문자가 포함되지 않도록 수정했습니다.",
                        "채팅 설정에서 잘못된 설정 문구를 삭제했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "버전 관리",
                    items = listOf(
                        "현재 앱 버전을 0.3.10-alpha로 업데이트했습니다.",
                        "0.3.10-alpha 패치 기록을 업데이트 기록에 추가했습니다.",
                        "기존 0.3.x-alpha 패치노트의 순서와 요약 문구를 정리했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안정성",
                    items = listOf(
                        "이번 패치는 업데이트 기록과 버전 정보만 수정합니다.",
                        "채팅, 모델 실행, 벤치마크, 설정 데이터는 변경하지 않았습니다."
                    )
                )
            )
        ),
        FusionReleaseNote(
            version = "0.3.9-alpha",
            status = "알파",
            summary = "대화 요약 메모리와 패치노트를 정리했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "대화 요약 메모리",
                    items = listOf(
                        "대화 요약을 저장해 긴 대화에서도 핵심 맥락을 유지할 수 있도록 했습니다.",
                        "요약 생성, 직접 편집, 복사, 삭제 기능을 추가했습니다.",
                        "요약은 현재 대화의 생성과 응답 재생성에 함께 반영됩니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "업데이트 기록",
                    items = listOf(
                        "0.3.9-alpha 버전을 추가했습니다.",
                        "버전별 패치노트를 최신 상태로 유지했습니다."
                    )
                )
            )
        ),        FusionReleaseNote(
            version = "0.3.8-alpha",
            status = "알파",
            summary = "버전과 패치노트를 정리했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "업데이트 기록",
                    items = listOf(
                        "0.3.8-alpha 버전을 추가했습니다.",
                        "버전별 패치노트를 최신 상태로 유지했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "안정성",
                    items = listOf(
                        "앱 버전 표시와 업데이트 기록만 정리했습니다.",
                        "채팅, 모델, 벤치마크, 설정 동작은 변경하지 않았습니다."
                    )
                )
            )
        ),        FusionReleaseNote(
            version = "0.3.7-alpha",
            status = "알파",
            summary = "응답 재생성과 패치노트 정리를 반영했습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "답변 다시 생성",
                    items = listOf(
                        "답변을 자동으로 다시 생성할 수 있도록 개선했습니다.",
                        "더 짧게, 더 자세히, 표로 정리, 전문가 톤 재생성 동작을 추가했습니다.",
                        "재생성 실패 시 기존 답변은 그대로 유지하도록 안전하게 처리했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "업데이트 기록",
                    items = listOf(
                        "0.3.7-alpha 버전을 추가했습니다.",
                        "버전별 패치노트를 더 정리된 형태로 유지했습니다."
                    )
                )
            )
        ),        FusionReleaseNote(
            version = "0.3.6-alpha",
            status = "알파",
            summary = "채팅 입력 영역의 배치를 다듬었습니다.",
            sections = listOf(
                FusionReleaseNoteSection(
                    title = "채팅 화면",
                    items = listOf(
                        "빠른 입력 버튼의 세로 위치를 입력창에 더 자연스럽게 맞췄습니다.",
                        "빈 채팅 화면에서 빠른 입력 영역이 과하게 위로 떠 보이는 문제를 개선했습니다.",
                        "키보드가 열리거나 닫힐 때 입력 영역의 배치가 어색해지지 않도록 조정했습니다."
                    )
                ),
                FusionReleaseNoteSection(
                    title = "UI 개선",
                    items = listOf(
                        "하단 입력 영역의 여백과 정렬을 더 일관되게 다듬었습니다."
                    )
                )
            )
        ),
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
