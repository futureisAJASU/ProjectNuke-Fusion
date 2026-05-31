package com.projectnuke.fusion.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TroublePanelBg = Color(0xFF171717)
private val TroubleCardBg = Color(0xFF111111)
private val TroubleTextPrimary = Color(0xFFF5F5F5)
private val TroubleTextSecondary = Color(0xFF9E9E9E)
private val TroubleAccentBlue = Color(0xFF9FD0FF)

private data class TroubleCategory(
    val title: String,
    val items: List<String>
)

@Composable
fun FusionTroubleshootingGuideDialog(
    context: Context,
    clipboard: ClipboardManager,
    onDismiss: () -> Unit
) {
    val categories = remember { buildTroubleshootingCategories() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("문제 해결 가이드") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TroubleHeaderCard("문제 해결 가이드", "Fusion 사용 중 자주 발생할 수 있는 문제와 확인 방법을 정리합니다.")
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        item {
                            TroubleCategoryCard(
                                category = category,
                                onCopy = {
                                    clipboard.setText(AnnotatedString(buildTroubleCategoryText(category)))
                                    Toast.makeText(context, "문제 해결 항목을 복사했습니다.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(buildAllTroubleshootingText(categories)))
                Toast.makeText(context, "문제 해결 가이드를 복사했습니다.", Toast.LENGTH_SHORT).show()
            }) { Text("전체 가이드 복사", color = TroubleAccentBlue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("닫기", color = TroubleTextSecondary) }
        },
        containerColor = TroublePanelBg,
        titleContentColor = TroubleTextPrimary,
        textContentColor = TroubleTextPrimary
    )
}

@Composable
private fun TroubleHeaderCard(title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = TroubleCardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, color = TroubleTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = TroubleTextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TroubleCategoryCard(
    category: TroubleCategory,
    onCopy: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = TroubleCardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(category.title, color = TroubleTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            category.items.forEach { item ->
                Text(item, color = TroubleTextSecondary, fontSize = 12.sp)
            }
            TextButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                Text("이 항목 복사", color = TroubleAccentBlue, fontSize = 12.sp)
            }
        }
    }
}

private fun buildTroubleshootingCategories(): List<TroubleCategory> {
    return listOf(
        TroubleCategory(
            "모델이 실행되지 않을 때",
            listOf(
                "모델 파일 경로가 올바른지 확인해 주세요.",
                "모델 파일이 삭제되었거나 이동된 경우 다시 연결해야 합니다.",
                "지원되지 않는 형식의 모델은 변환 후 사용할 수 있습니다.",
                "앱을 다시 시작한 뒤 같은 모델을 다시 선택해 보세요."
            )
        ),
        TroubleCategory(
            "메모리 부족 또는 앱 종료",
            listOf(
                "큰 모델 실행 전 다른 앱을 정리하는 것이 좋습니다.",
                "8GB급 기기에서는 5GB 이상 모델의 로컬 실행을 권장하지 않습니다.",
                "최대 토큰 수를 낮추면 KV 캐시와 런타임 메모리 부담을 줄일 수 있습니다.",
                "저메모리 기기에서는 소형 모델과 낮은 생성 설정을 우선 권장합니다."
            )
        ),
        TroubleCategory(
            "벤치마크 오류",
            listOf(
                "벤치마크 전 일반 채팅 생성이 진행 중인지 확인해 주세요.",
                "모델 설정을 바꾼 뒤에는 모델이 정상적으로 다시 로드되는지 확인해 주세요.",
                "MTP 켬/끔 비교는 디코딩 기준 토큰 속도를 중심으로 확인하는 것이 좋습니다.",
                "오류가 반복되면 개발자 로그와 logcat을 함께 확인해 주세요."
            )
        ),
        TroubleCategory(
            "MTP 또는 가속기 설정",
            listOf(
                "MTP가 항상 더 빠른 것은 아니며 기기와 모델에 따라 결과가 달라질 수 있습니다.",
                "Snapdragon, Exynos, Tensor, MediaTek 기기마다 사용 가능한 가속 경로가 다를 수 있습니다.",
                "현재 앱에서 NPU 실행은 보장하지 않으며 후보 정보로만 표시합니다.",
                "가속기 설정 변경 후에는 벤치마크로 실제 속도를 확인하는 것이 좋습니다."
            )
        ),
        TroubleCategory(
            "채팅 화면 문제",
            listOf(
                "키보드가 열릴 때 입력창이나 빠른 입력 영역이 어색하면 앱을 최신 버전으로 업데이트해 주세요.",
                "답변 재생성 중에는 다른 생성 작업을 동시에 시작하지 않는 것이 좋습니다.",
                "응답이 실패해도 기존 답변은 유지되도록 설계되어 있습니다.",
                "문제가 반복되면 개발자 로그를 복사해 확인해 주세요."
            )
        ),
        TroubleCategory(
            "메모리 기능 문제",
            listOf(
                "저장된 메모리는 메모리 사용 설정이 켜져 있을 때 답변 생성에 참고됩니다.",
                "사용 안 함으로 설정된 메모리는 답변 생성에 포함되지 않습니다.",
                "메모리 컨텍스트 미리보기에서 실제로 포함될 메모리를 확인할 수 있습니다.",
                "저장된 메모리 내용은 개발자 로그와 설정 백업에 포함되지 않습니다."
            )
        ),
        TroubleCategory(
            "ADB 설치 문제",
            listOf(
                "adb devices로 기기가 연결되어 있는지 확인해 주세요.",
                "adb 명령어를 찾을 수 없으면 Android SDK platform-tools 경로의 adb.exe를 직접 실행해 주세요.",
                "설치가 실패하면 기존 앱을 닫고 다시 설치해 보세요.",
                "Debug APK 경로는 app/build/outputs/apk/debug/app-debug.apk 입니다."
            )
        ),
        TroubleCategory(
            "로그 확인",
            listOf(
                "개발자 로그 화면에서 앱 상태와 오류 보고서를 복사할 수 있습니다.",
                "크래시가 발생하면 logcat에서 AndroidRuntime 또는 FATAL EXCEPTION을 확인해 주세요.",
                "오류 보고서에는 채팅 내용과 저장된 메모리 내용이 포함되지 않도록 설계되어 있습니다."
            )
        )
    )
}

private fun buildTroubleCategoryText(category: TroubleCategory): String {
    return buildString {
        appendLine("[${category.title}]")
        category.items.forEach { appendLine("- $it") }
    }.trimEnd()
}

private fun buildAllTroubleshootingText(categories: List<TroubleCategory>): String {
    return buildString {
        appendLine("Fusion 문제 해결 가이드")
        appendLine()
        categories.forEach { category ->
            appendLine("[${category.title}]")
            category.items.forEach { appendLine("- $it") }
            appendLine()
        }
    }.trimEnd()
}
