package com.projectnuke.fusion.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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

private val ReleaseChecklistPanelBg = Color(0xFF171717)
private val ReleaseChecklistCardBg = Color(0xFF111111)
private val ReleaseChecklistTextPrimary = Color(0xFFF5F5F5)
private val ReleaseChecklistTextSecondary = Color(0xFF9E9E9E)
private val ReleaseChecklistAccentBlue = Color(0xFF9FD0FF)

private data class ReleaseChecklistGroup(
    val title: String,
    val items: List<String>
)

@Composable
fun FusionReleaseChecklistDialog(
    context: Context,
    clipboard: ClipboardManager,
    onDismiss: () -> Unit
) {
    val groups = remember { buildFusionReleaseChecklistGroups() }
    val checkedStates = remember(groups) {
        mutableStateMapOf<String, Boolean>().apply {
            groups.flatMap { group -> group.items.map { item -> "${group.title}::$item" } }
                .forEach { put(it, false) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("릴리즈 체크리스트") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReleaseChecklistHeaderCard("릴리즈 체크리스트", "배포 전 확인할 항목을 체크합니다.")
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groups.forEach { group ->
                        item {
                            ReleaseChecklistGroupCard(
                                group = group,
                                checkedStates = checkedStates
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    checkedStates.keys.forEach { key -> checkedStates[key] = true }
                }) { Text("전체 선택", color = ReleaseChecklistAccentBlue) }
                TextButton(onClick = {
                    checkedStates.keys.forEach { key -> checkedStates[key] = false }
                }) { Text("초기화", color = ReleaseChecklistAccentBlue) }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(buildReleaseChecklistText(groups, checkedStates)))
                    Toast.makeText(context, "체크리스트를 복사했습니다.", Toast.LENGTH_SHORT).show()
                }) { Text("체크리스트 복사", color = ReleaseChecklistAccentBlue) }
                TextButton(onClick = onDismiss) { Text("닫기", color = ReleaseChecklistTextSecondary) }
            }
        },
        containerColor = ReleaseChecklistPanelBg,
        titleContentColor = ReleaseChecklistTextPrimary,
        textContentColor = ReleaseChecklistTextPrimary
    )
}

@Composable
private fun ReleaseChecklistHeaderCard(title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ReleaseChecklistCardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, color = ReleaseChecklistTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = ReleaseChecklistTextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ReleaseChecklistGroupCard(
    group: ReleaseChecklistGroup,
    checkedStates: MutableMap<String, Boolean>
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ReleaseChecklistCardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(group.title, color = ReleaseChecklistTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            group.items.forEach { item ->
                val key = "${group.title}::$item"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = checkedStates[key] == true,
                        onCheckedChange = { checkedStates[key] = it }
                    )
                    Text(
                        text = item,
                        color = ReleaseChecklistTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f).padding(top = 12.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun buildFusionReleaseChecklistGroups(): List<ReleaseChecklistGroup> {
    return listOf(
        ReleaseChecklistGroup(
            "빌드",
            listOf(
                "Gradle 빌드 확인",
                "APK 설치 확인",
                "앱 실행 확인",
                "앱 버전 표시 확인"
            )
        ),
        ReleaseChecklistGroup(
            "기본 기능",
            listOf(
                "기존 채팅 목록 확인",
                "새 채팅 생성 확인",
                "일반 메시지 전송 확인",
                "답변 다시 생성 확인"
            )
        ),
        ReleaseChecklistGroup(
            "모델",
            listOf(
                "모델 라이브러리 열기 확인",
                "모델 상세 화면 확인",
                "모델 호환성 가이드 확인",
                "모델 선택 흐름 확인"
            )
        ),
        ReleaseChecklistGroup(
            "메모리",
            listOf(
                "메모리 관리 화면 확인",
                "메모리 컨텍스트 미리보기 확인",
                "메모리 사용 켬/끔 확인",
                "대화 요약 확인"
            )
        ),
        ReleaseChecklistGroup(
            "실험",
            listOf(
                "벤치마크 화면 확인",
                "A/B 테스트 화면 확인",
                "A/B 테스트 기록 확인",
                "Prompt Lab 확인"
            )
        ),
        ReleaseChecklistGroup(
            "데이터",
            listOf(
                "설정 백업 및 복원 화면 확인",
                "채팅 Markdown 내보내기 확인",
                "아카이브 화면 확인",
                "첨부파일 저장공간 화면 확인"
            )
        ),
        ReleaseChecklistGroup(
            "진단",
            listOf(
                "상태 대시보드 확인",
                "기기 정보 화면 확인",
                "개발자 로그 복사 확인",
                "업데이트 기록 확인"
            )
        )
    )
}

private fun buildReleaseChecklistText(
    groups: List<ReleaseChecklistGroup>,
    checkedStates: Map<String, Boolean>
): String {
    return buildString {
        appendLine("Fusion 릴리즈 체크리스트")
        appendLine()
        groups.forEach { group ->
            appendLine("[${group.title}]")
            group.items.forEach { item ->
                val checked = checkedStates["${group.title}::$item"] == true
                appendLine("- [${if (checked) "x" else " "}] $item")
            }
            appendLine()
        }
    }.trimEnd()
}
