package com.projectnuke.fusion.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color

private val GuideBg = Color(0xFF101318)
private val GuideCard = Color(0xFF171B22)
private val GuideText = Color(0xFFE8EAED)
private val GuideSubText = Color(0xFFB6BDC7)
private val GuideAccent = Color(0xFF7CCBFF)

data class ModelGuideSection(
    val title: String,
    val items: List<String>
)

private fun modelGuideSections(): List<ModelGuideSection> = listOf(
    ModelGuideSection(
        title = "Gemma",
        items = listOf(
            "Fusion의 기본 로컬 채팅 후보입니다.",
            "현재 앱의 기본 모델 실행 흐름과 가장 잘 맞는 모델군입니다.",
            "8GB 기기에서는 작은 모델과 낮은 최대 토큰 수 설정을 권장합니다."
        )
    ),
    ModelGuideSection(
        title = "Qwen",
        items = listOf(
            "소형 모델, 다국어, 코딩 실험 후보로 적합합니다.",
            "8GB 기기에서는 0.6B~1.7B급 모델을 우선 권장합니다.",
            "4B 이상 모델은 메모리 상태에 따라 주의가 필요합니다."
        )
    ),
    ModelGuideSection(
        title = "Llama",
        items = listOf(
            "도구와 생태계가 강한 기준 모델군입니다.",
            "1B~3B급 모델은 비교 실험 후보로 사용할 수 있습니다.",
            "대형 Llama 계열은 모바일 로컬 실행보다 PC 또는 서버 환경이 적합합니다."
        )
    ),
    ModelGuideSection(
        title = "Phi",
        items = listOf(
            "작은 모델에서 추론과 코딩 성능을 실험하기 좋은 후보입니다.",
            "8GB 기기에서는 최대 토큰 수를 낮게 설정하는 것이 좋습니다.",
            "Reasoning 계열은 출력이 길어질 수 있어 메모리와 속도에 주의해야 합니다."
        )
    ),
    ModelGuideSection(
        title = "DeepSeek",
        items = listOf(
            "추론 특화 모델 실험 후보입니다.",
            "Distill 소형 모델은 로컬 실험 후보로 볼 수 있습니다.",
            "대형 DeepSeek 모델은 모바일 로컬 실행보다 원격 실행에 적합합니다."
        )
    ),
    ModelGuideSection(
        title = "Mistral",
        items = listOf(
            "엣지와 온디바이스 모델 실험 후보입니다.",
            "기기별 변환과 런타임 호환성 확인이 필요합니다.",
            "Ministral 계열은 모바일 실험 후보로 검토할 수 있습니다."
        )
    ),
    ModelGuideSection(
        title = "Kimi",
        items = listOf(
            "대형 원격 모델 후보입니다.",
            "모바일 로컬 실행보다는 서버 또는 원격 실행을 권장합니다.",
            "Fusion에서는 로컬 실행 후보보다 모델 카탈로그/원격 후보로 분류하는 것이 적합합니다."
        )
    ),
    ModelGuideSection(
        title = "실행 기준",
        items = listOf(
            "8GB 기기에서는 5GB 이상 모델을 기본 추천에서 제외하는 것이 안전합니다.",
            "4GB급 모델은 실행 가능성이 있더라도 주의 필요로 분류하는 것이 좋습니다.",
            "모델 파일 크기와 실제 실행 메모리는 다를 수 있습니다.",
            "KV 캐시, 런타임 버퍼, GPU delegate 사용량 때문에 실제 메모리 부담은 더 커질 수 있습니다."
        )
    )
)

private fun buildGuideText(): String {
    return buildString {
        appendLine("# Fusion 모델 호환성 가이드")
        appendLine()
        modelGuideSections().forEach { section ->
            appendLine("## ${section.title}")
            section.items.forEach { item ->
                appendLine("- $item")
            }
            appendLine()
        }
    }
}

@Composable
fun ModelCompatibilityGuideDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = GuideBg,
        titleContentColor = GuideText,
        textContentColor = GuideText,
        title = {
            Column {
                Text(
                    text = "모델 호환성 가이드",
                    color = GuideText,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "모델군별 특징과 실행 기준을 확인합니다.",
                    color = GuideSubText
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
            ) {
                modelGuideSections().forEach { section ->
                    GuideSectionCard(section)
                    Spacer(Modifier.height(10.dp))
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        copyTextToClipboard(
                            context = context,
                            label = "Fusion 모델 호환성 가이드",
                            text = buildGuideText()
                        )
                        Toast.makeText(context, "모델 호환성 가이드를 복사했습니다.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = GuideAccent)
                ) {
                    Text("가이드 복사")
                }

                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = GuideAccent)
                ) {
                    Text("닫기")
                }
            }
        }
    )
}

@Composable
private fun GuideSectionCard(section: ModelGuideSection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GuideCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = section.title,
                color = GuideAccent,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            section.items.forEach { item ->
                Text(
                    text = "• $item",
                    color = GuideText,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
    }
}

private fun copyTextToClipboard(
    context: Context,
    label: String,
    text: String
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}