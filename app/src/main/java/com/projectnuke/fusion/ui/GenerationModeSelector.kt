package com.projectnuke.fusion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectnuke.fusion.ai.model.AiProviderConfig

private val SelectorPanelBg = Color(0xFF171717)
private val SelectorMenuBg = Color(0xFF202020)
private val SelectorTextPrimary = Color(0xFFF5F5F5)
private val SelectorTextSecondary = Color(0xFF9E9E9E)
private val SelectorBorder = Color(0xFF2F2F2F)
private val SelectorActiveBg = Color(0xFF2A2A2A)

@Composable
internal fun GenerationModeSelector(
    mode: ChatGenerationMode,
    selectedProviderName: String?,
    selectedProviderId: String?,
    externalProviders: List<AiProviderConfig>,
    enabled: Boolean,
    onModeSelected: (ChatGenerationMode) -> Unit,
    onExternalProviderSelected: (String) -> Unit
) {
    var providerExpanded by remember { mutableStateOf(false) }
    val runnableProviders = externalProviders.filter(::isRunnableExternalProvider)
    val providerLabel = when {
        runnableProviders.isEmpty() -> "외부 AI API 설정이 필요합니다."
        !selectedProviderName.isNullOrBlank() -> "API: $selectedProviderName"
        else -> "외부 AI API 제공자를 선택해 주세요."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeChip(
                label = "로컬 모델",
                selected = mode == ChatGenerationMode.LOCAL_MODEL,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onModeSelected(ChatGenerationMode.LOCAL_MODEL) }
            )
            ModeChip(
                label = "외부 AI API",
                selected = mode == ChatGenerationMode.EXTERNAL_AI_API,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onModeSelected(ChatGenerationMode.EXTERNAL_AI_API) }
            )
        }

        if (mode == ChatGenerationMode.EXTERNAL_AI_API) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled && runnableProviders.isNotEmpty()) { providerExpanded = true },
                    shape = RoundedCornerShape(8.dp),
                    color = SelectorPanelBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = providerLabel,
                            color = if (runnableProviders.isEmpty()) SelectorTextSecondary else SelectorTextPrimary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (runnableProviders.isEmpty()) "설정 필요" else "변경",
                            color = SelectorTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                DropdownMenu(
                    expanded = providerExpanded,
                    onDismissRequest = { providerExpanded = false },
                    containerColor = SelectorMenuBg
                ) {
                    if (runnableProviders.isEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "사용 가능한 외부 AI API 제공자가 없습니다.",
                                    color = SelectorTextSecondary
                                )
                            },
                            onClick = {},
                            enabled = false
                        )
                    } else {
                        runnableProviders.forEach { provider ->
                            val selected = provider.id == selectedProviderId
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = buildString {
                                            if (selected) append("선택됨 · ")
                                            append("API: ")
                                            append(provider.displayName)
                                        },
                                        color = if (selected) SelectorTextPrimary else SelectorTextSecondary
                                    )
                                },
                                onClick = {
                                    providerExpanded = false
                                    onExternalProviderSelected(provider.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .border(width = 1.dp, color = if (selected) SelectorTextPrimary else SelectorBorder, shape = shape)
            .background(color = if (selected) SelectorActiveBg else SelectorPanelBg, shape = shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) SelectorTextPrimary else SelectorTextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun isRunnableExternalProvider(provider: AiProviderConfig): Boolean {
    return provider.isEnabled &&
        !provider.apiKeySecretId.isNullOrBlank() &&
        provider.baseUrl.isNotBlank() &&
        provider.modelId.isNotBlank()
}
