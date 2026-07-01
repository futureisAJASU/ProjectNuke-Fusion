package com.projectnuke.fusion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SelectorPanelBg = Color(0xFF171717)
private val SelectorMenuBg = Color(0xFF202020)
private val SelectorTextPrimary = Color(0xFFF5F5F5)
private val SelectorTextSecondary = Color(0xFF9E9E9E)

@Composable
internal fun GenerationModeSelector(
    mode: ChatGenerationMode,
    selectedProviderName: String?,
    enabled: Boolean,
    onModeSelected: (ChatGenerationMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (mode) {
        ChatGenerationMode.LOCAL_MODEL -> "생성 모드: 로컬 모델"
        ChatGenerationMode.EXTERNAL_AI_API -> "생성 모드: 외부 AI API" +
            selectedProviderName?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { expanded = true },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            color = SelectorPanelBg
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = SelectorTextPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "▼", color = SelectorTextSecondary, fontSize = 12.sp)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = SelectorMenuBg
        ) {
            DropdownMenuItem(
                text = { Text("로컬 모델", color = SelectorTextPrimary) },
                onClick = {
                    expanded = false
                    onModeSelected(ChatGenerationMode.LOCAL_MODEL)
                }
            )
            DropdownMenuItem(
                text = { Text("외부 AI API", color = SelectorTextPrimary) },
                onClick = {
                    expanded = false
                    onModeSelected(ChatGenerationMode.EXTERNAL_AI_API)
                }
            )
        }
    }
}
