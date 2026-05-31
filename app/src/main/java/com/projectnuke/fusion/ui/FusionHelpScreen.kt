package com.projectnuke.fusion.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HelpPanelBg = Color(0xFF171717)
private val HelpCardBg = Color(0xFF111111)
private val HelpTextPrimary = Color(0xFFF5F5F5)
private val HelpTextSecondary = Color(0xFF9E9E9E)
private val HelpAccentBlue = Color(0xFF9FD0FF)

private data class HelpSection(
    val title: String,
    val items: List<String>
)

private val FusionHelpSections = listOf(
    HelpSection(
        title = "모델 라이브러리",
        items = listOf(
            "모델을 검색, 필터, 정렬하여 확인할 수 있습니다.",
            "즐겨찾기와 숨김 기능으로 자주 쓰는 모델을 정리할 수 있습니다.",
            "기기 메모리 기준으로 추천 모델과 주의가 필요한 모델을 확인할 수 있습니다."
        )
    ),
    HelpSection(
        title = "메모리",
        items = listOf(
            "대화 요약과 저장된 메모리를 통해 긴 대화의 맥락을 유지할 수 있습니다.",
            "저장된 메모리는 사용 여부와 적용 범위를 직접 관리할 수 있습니다.",
            "메모리 컨텍스트 미리보기에서 답변 생성에 포함될 내용을 확인할 수 있습니다."
        )
    ),
    HelpSection(
        title = "벤치마크",
        items = listOf(
            "모델의 응답 속도와 토큰 속도를 측정할 수 있습니다.",
            "MTP 켬/끔 결과를 비교해 기기별 성능 차이를 확인할 수 있습니다.",
            "모델별 벤치마크 요약을 모델 상세 화면에서 확인할 수 있습니다."
        )
    ),
    HelpSection(
        title = "A/B 테스트",
        items = listOf(
            "같은 프롬프트로 여러 모델 또는 설정을 비교할 수 있습니다.",
            "결과 카드에서 답변과 속도 정보를 함께 확인할 수 있습니다.",
            "A/B 테스트는 메모리 부담을 줄이기 위해 순차적으로 실행됩니다."
        )
    ),
    HelpSection(
        title = "데이터 관리",
        items = listOf(
            "설정 백업 및 복원으로 앱 설정을 보관할 수 있습니다.",
            "채팅 Markdown 내보내기로 선택한 대화를 파일로 저장할 수 있습니다.",
            "모델 파일과 채팅 기록은 설정 백업에 포함되지 않습니다."
        )
    ),
    HelpSection(
        title = "문제 해결",
        items = listOf(
            "모델 실행 문제가 발생하면 개발자 로그를 확인해 주세요.",
            "오류 보고서 복사 기능으로 기기, 모델, 설정 정보를 정리할 수 있습니다.",
            "8GB RAM 기기에서는 큰 모델 실행 전 다른 앱을 정리하는 것이 좋습니다."
        )
    )
)

@Composable
fun FusionHelpScreen(
    context: Context,
    clipboard: ClipboardManager,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("사용 가이드") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Fusion의 주요 기능을 빠르게 확인합니다.", color = HelpTextSecondary, fontSize = 12.sp)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(FusionHelpSections) { section ->
                        HelpSectionCard(section)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(buildFusionHelpGuideText()))
                Toast.makeText(context, "사용 가이드를 복사했습니다.", Toast.LENGTH_SHORT).show()
            }) { Text("가이드 복사", color = HelpAccentBlue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("닫기", color = HelpTextSecondary) }
        },
        containerColor = HelpPanelBg,
        titleContentColor = HelpTextPrimary,
        textContentColor = HelpTextPrimary
    )
}

@Composable
private fun HelpSectionCard(section: HelpSection) {
    Surface(shape = RoundedCornerShape(12.dp), color = HelpCardBg, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(section.title, color = HelpTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            section.items.forEach { item ->
                Text("• $item", color = HelpTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

private fun buildFusionHelpGuideText(): String {
    return buildString {
        appendLine("Fusion 사용 가이드")
        appendLine("Fusion의 주요 기능을 빠르게 확인합니다.")
        FusionHelpSections.forEach { section ->
            appendLine()
            appendLine("[${section.title}]")
            section.items.forEach { item ->
                appendLine("- $item")
            }
        }
    }.trimEnd()
}
