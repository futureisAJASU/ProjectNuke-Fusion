package com.projectnuke.fusion.ui

import android.content.Context
import android.widget.Toast
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.projectnuke.fusion.data.ConversationEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MemoryManagerPanelBg = Color(0xFF171717)
private val MemoryManagerCardBg = Color(0xFF111111)
private val MemoryManagerTextPrimary = Color(0xFFF5F5F5)
private val MemoryManagerTextSecondary = Color(0xFF9E9E9E)
private val MemoryManagerAccentBlue = Color(0xFF9FD0FF)
private val MemoryManagerDangerRed = Color(0xFFFF7A7A)

@Composable
fun MemoryManagerDialog(
    context: Context,
    clipboard: ClipboardManager,
    conversations: List<ConversationEntity>,
    archivedConversations: List<ConversationEntity>,
    onDismiss: () -> Unit
) {
    var refreshKey by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var editingMemory by remember { mutableStateOf<ConversationMemoryCandidate?>(null) }
    var editingMemoryText by remember { mutableStateOf("") }
    var deletingMemory by remember { mutableStateOf<ConversationMemoryCandidate?>(null) }
    var editingSummary by remember { mutableStateOf<ConversationSummaryMemory?>(null) }
    var editingSummaryText by remember { mutableStateOf("") }
    var deletingSummary by remember { mutableStateOf<ConversationSummaryMemory?>(null) }
    var showContextPreview by remember { mutableStateOf(false) }
    val settingsPrefs = remember {
        context.getSharedPreferences("fusion_chat_settings", Context.MODE_PRIVATE)
    }
    var memoryContextEnabled by remember {
        mutableStateOf(isSavedMemoryContextEnabled(settingsPrefs))
    }

    val conversationMap = remember(conversations, archivedConversations) {
        (conversations + archivedConversations).associateBy { it.id }
    }
    val savedMemories = remember(refreshKey) { loadAllConversationMemoryCandidates(context) }
    val savedSummaries = remember(refreshKey) { loadAllConversationSummaries(context) }
    val memoryContext = remember(refreshKey, memoryContextEnabled) {
        buildSavedMemoryContext(context, settingsPrefs, currentConversationId = null)
    }
    val query = searchQuery.trim()
    val filteredMemories = remember(savedMemories, conversationMap, query) {
        if (query.isBlank()) {
            savedMemories
        } else {
            savedMemories.filter { candidate ->
                candidate.text.contains(query, ignoreCase = true) ||
                    resolveConversationTitle(candidate.conversationId, candidate.conversationTitle, conversationMap)
                        .contains(query, ignoreCase = true)
            }
        }
    }
    val filteredSummaries = remember(savedSummaries, conversationMap, query) {
        if (query.isBlank()) {
            savedSummaries
        } else {
            savedSummaries.filter { summary ->
                summary.summary.contains(query, ignoreCase = true) ||
                    resolveConversationTitle(summary.conversationId, null, conversationMap)
                        .contains(query, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("메모리 관리") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    MemoryManagerCard {
                        Text("Fusion이 나중에 참고할 수 있도록 저장한 정보를 관리합니다.", color = MemoryManagerTextSecondary, fontSize = 13.sp)
                    }
                }
                item {
                    MemoryManagerCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("메모리 사용", color = MemoryManagerTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("저장된 메모리를 답변 생성에 참고합니다.", color = MemoryManagerTextSecondary, fontSize = 12.sp)
                            }
                            Switch(
                                checked = memoryContextEnabled,
                                onCheckedChange = { enabled ->
                                    settingsPrefs.edit().putBoolean(PrefSavedMemoryContextEnabled, enabled).apply()
                                    memoryContextEnabled = enabled
                                    Toast.makeText(context, "메모리 사용 설정을 저장했습니다.", Toast.LENGTH_SHORT).show()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MemoryManagerAccentBlue,
                                    checkedTrackColor = MemoryManagerAccentBlue.copy(alpha = 0.45f),
                                    uncheckedThumbColor = MemoryManagerTextSecondary,
                                    uncheckedTrackColor = MemoryManagerCardBg
                                )
                            )
                        }
                        TextButton(onClick = { showContextPreview = true }) {
                            Text("메모리 컨텍스트 미리보기", color = MemoryManagerAccentBlue)
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("메모리를 검색합니다.") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MemoryManagerTextPrimary,
                            unfocusedTextColor = MemoryManagerTextPrimary,
                            focusedBorderColor = MemoryManagerAccentBlue,
                            unfocusedBorderColor = MemoryManagerTextSecondary,
                            focusedContainerColor = MemoryManagerCardBg,
                            unfocusedContainerColor = MemoryManagerCardBg,
                            focusedPlaceholderColor = MemoryManagerTextSecondary,
                            unfocusedPlaceholderColor = MemoryManagerTextSecondary
                        )
                    )
                }
                if (query.isNotBlank() && filteredMemories.isEmpty() && filteredSummaries.isEmpty()) {
                    item {
                        MemoryManagerCard {
                            Text("검색 결과가 없습니다.", color = MemoryManagerTextSecondary, fontSize = 14.sp)
                        }
                    }
                } else {
                    item {
                        MemoryManagerSectionTitle("저장된 메모리")
                    }
                    if (filteredMemories.isEmpty()) {
                        item {
                            MemoryManagerCard {
                                Text("저장된 메모리가 없습니다.", color = MemoryManagerTextSecondary, fontSize = 14.sp)
                            }
                        }
                    } else {
                        items(filteredMemories, key = { it.id }) { candidate ->
                            val conversationTitle = resolveConversationTitle(candidate.conversationId, candidate.conversationTitle, conversationMap)
                            MemoryManagerCard {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (candidate.enabled) "사용 중" else "사용 안 함", color = if (candidate.enabled) MemoryManagerAccentBlue else MemoryManagerTextSecondary, fontSize = 11.sp)
                                    Text(formatMemoryTime(candidate.updatedAt ?: candidate.createdAt), color = MemoryManagerTextSecondary, fontSize = 11.sp)
                                }
                                Text("수동 저장", color = MemoryManagerTextSecondary, fontSize = 11.sp)
                                Text(conversationTitle, color = MemoryManagerTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(candidate.text, color = MemoryManagerTextPrimary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = {
                                        editingMemory = candidate
                                        editingMemoryText = candidate.text
                                    }) { Text("수정", color = MemoryManagerAccentBlue) }
                                    TextButton(onClick = {
                                        clipboard.setText(AnnotatedString(candidate.text))
                                        Toast.makeText(context, "메모리를 복사했습니다.", Toast.LENGTH_SHORT).show()
                                    }) { Text("복사", color = MemoryManagerAccentBlue) }
                                    TextButton(onClick = {
                                        if (setConversationMemoryCandidateEnabled(context, candidate.id, !candidate.enabled)) {
                                            refreshKey++
                                            Toast.makeText(context, "메모리 사용 상태를 변경했습니다.", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Text(if (candidate.enabled) "메모리 사용 안 함" else "메모리 사용", color = MemoryManagerAccentBlue)
                                    }
                                    TextButton(onClick = { deletingMemory = candidate }) { Text("삭제", color = MemoryManagerDangerRed) }
                                }
                            }
                        }
                    }

                    item {
                        MemoryManagerSectionTitle("대화 요약")
                    }
                    if (filteredSummaries.isEmpty()) {
                        item {
                            MemoryManagerCard {
                                Text("저장된 대화 요약이 없습니다.", color = MemoryManagerTextSecondary, fontSize = 14.sp)
                            }
                        }
                    } else {
                        items(filteredSummaries, key = { it.conversationId }) { summary ->
                            val conversationTitle = resolveConversationTitle(summary.conversationId, null, conversationMap)
                            MemoryManagerCard {
                                Text(conversationTitle, color = MemoryManagerTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(formatSummaryUpdated(summary.updatedAt), color = MemoryManagerTextSecondary, fontSize = 11.sp)
                                Text(summary.summary, color = MemoryManagerTextSecondary, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = {
                                        editingSummary = summary
                                        editingSummaryText = summary.summary
                                    }) { Text("열기", color = MemoryManagerAccentBlue) }
                                    TextButton(onClick = {
                                        clipboard.setText(AnnotatedString(summary.summary))
                                        Toast.makeText(context, "메모리를 복사했습니다.", Toast.LENGTH_SHORT).show()
                                    }) { Text("복사", color = MemoryManagerAccentBlue) }
                                    TextButton(onClick = { deletingSummary = summary }) { Text("삭제", color = MemoryManagerDangerRed) }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        val text = buildAllMemoriesCopyText(filteredMemories, conversationMap)
                        if (text.isBlank()) {
                            Toast.makeText(context, "저장된 메모리가 없습니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            clipboard.setText(AnnotatedString(text))
                            Toast.makeText(context, "저장된 메모리를 복사했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("전체 복사", color = MemoryManagerAccentBlue) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("닫기", color = MemoryManagerTextSecondary) }
        },
        containerColor = MemoryManagerPanelBg,
        titleContentColor = MemoryManagerTextPrimary,
        textContentColor = MemoryManagerTextPrimary
    )

    if (showContextPreview) {
        AlertDialog(
            onDismissRequest = { showContextPreview = false },
            title = { Text("메모리 컨텍스트 미리보기") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("답변 생성에 포함될 메모리를 확인합니다.", color = MemoryManagerTextSecondary, fontSize = 13.sp)
                    Text("메모리 사용: ${if (memoryContextEnabled) "켜짐" else "꺼짐"}", color = MemoryManagerTextPrimary, fontSize = 13.sp)
                    Text("저장된 메모리: ${savedMemories.size}개", color = MemoryManagerTextPrimary, fontSize = 13.sp)
                    Text("사용 중인 메모리: ${savedMemories.count { it.enabled }}개", color = MemoryManagerTextPrimary, fontSize = 13.sp)
                    Text("예상 문자 수: ${memoryContext.characterCount}", color = MemoryManagerTextPrimary, fontSize = 13.sp)
                    if (memoryContext.trimmed) {
                        Text("길이 제한에 맞춰 일부 메모리를 제외했습니다.", color = MemoryManagerTextSecondary, fontSize = 12.sp)
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MemoryManagerCardBg
                    ) {
                        Text(
                            text = when {
                                !memoryContextEnabled -> "메모리 사용이 꺼져 있습니다."
                                memoryContext.text.isNullOrBlank() -> "사용 중인 메모리가 없습니다."
                                else -> memoryContext.text
                            },
                            modifier = Modifier.padding(10.dp),
                            color = MemoryManagerTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = memoryContext.text.orEmpty()
                        if (text.isNotBlank()) {
                            clipboard.setText(AnnotatedString(text))
                            Toast.makeText(context, "메모리 컨텍스트를 복사했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !memoryContext.text.isNullOrBlank()
                ) { Text("복사", color = MemoryManagerAccentBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showContextPreview = false }) { Text("닫기", color = MemoryManagerTextSecondary) }
            },
            containerColor = MemoryManagerPanelBg,
            titleContentColor = MemoryManagerTextPrimary,
            textContentColor = MemoryManagerTextPrimary
        )
    }

    if (editingMemory != null) {
        AlertDialog(
            onDismissRequest = { editingMemory = null },
            title = { Text("메모리 수정") },
            text = {
                OutlinedTextField(
                    value = editingMemoryText,
                    onValueChange = { editingMemoryText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MemoryManagerTextPrimary,
                        unfocusedTextColor = MemoryManagerTextPrimary,
                        focusedBorderColor = MemoryManagerAccentBlue,
                        unfocusedBorderColor = MemoryManagerTextSecondary,
                        focusedContainerColor = MemoryManagerCardBg,
                        unfocusedContainerColor = MemoryManagerCardBg
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val candidate = editingMemory ?: return@TextButton
                    if (updateConversationMemoryCandidate(context, candidate.id, editingMemoryText)) {
                        refreshKey++
                        editingMemory = null
                        Toast.makeText(context, "메모리를 저장했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("저장", color = MemoryManagerAccentBlue) }
            },
            dismissButton = {
                TextButton(onClick = { editingMemory = null }) { Text("취소", color = MemoryManagerTextSecondary) }
            },
            containerColor = MemoryManagerPanelBg,
            titleContentColor = MemoryManagerTextPrimary,
            textContentColor = MemoryManagerTextPrimary
        )
    }

    if (deletingMemory != null) {
        AlertDialog(
            onDismissRequest = { deletingMemory = null },
            title = { Text("메모리를 삭제하시겠습니까?") },
            text = { Text("저장된 메모리만 삭제되며 채팅 기록은 삭제되지 않습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    val candidate = deletingMemory ?: return@TextButton
                    if (deleteConversationMemoryCandidate(context, candidate.id)) {
                        refreshKey++
                        deletingMemory = null
                        Toast.makeText(context, "메모리를 삭제했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("삭제", color = MemoryManagerDangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { deletingMemory = null }) { Text("취소", color = MemoryManagerTextSecondary) }
            },
            containerColor = MemoryManagerPanelBg,
            titleContentColor = MemoryManagerTextPrimary,
            textContentColor = MemoryManagerTextPrimary
        )
    }

    if (editingSummary != null) {
        AlertDialog(
            onDismissRequest = { editingSummary = null },
            title = { Text("대화 요약 편집") },
            text = {
                OutlinedTextField(
                    value = editingSummaryText,
                    onValueChange = { editingSummaryText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MemoryManagerTextPrimary,
                        unfocusedTextColor = MemoryManagerTextPrimary,
                        focusedBorderColor = MemoryManagerAccentBlue,
                        unfocusedBorderColor = MemoryManagerTextSecondary,
                        focusedContainerColor = MemoryManagerCardBg,
                        unfocusedContainerColor = MemoryManagerCardBg
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val summary = editingSummary ?: return@TextButton
                    if (saveConversationSummary(context, summary.conversationId, editingSummaryText) != null) {
                        refreshKey++
                        editingSummary = null
                        Toast.makeText(context, "메모리를 저장했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("저장", color = MemoryManagerAccentBlue) }
            },
            dismissButton = {
                TextButton(onClick = { editingSummary = null }) { Text("취소", color = MemoryManagerTextSecondary) }
            },
            containerColor = MemoryManagerPanelBg,
            titleContentColor = MemoryManagerTextPrimary,
            textContentColor = MemoryManagerTextPrimary
        )
    }

    if (deletingSummary != null) {
        AlertDialog(
            onDismissRequest = { deletingSummary = null },
            title = { Text("메모리를 삭제하시겠습니까?") },
            text = { Text("저장된 메모리만 삭제되며 채팅 기록은 삭제되지 않습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    val summary = deletingSummary ?: return@TextButton
                    deleteConversationSummary(context, summary.conversationId)
                    refreshKey++
                    deletingSummary = null
                    Toast.makeText(context, "메모리를 삭제했습니다.", Toast.LENGTH_SHORT).show()
                }) { Text("삭제", color = MemoryManagerDangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { deletingSummary = null }) { Text("취소", color = MemoryManagerTextSecondary) }
            },
            containerColor = MemoryManagerPanelBg,
            titleContentColor = MemoryManagerTextPrimary,
            textContentColor = MemoryManagerTextPrimary
        )
    }
}

@Composable
private fun MemoryManagerCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MemoryManagerCardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

@Composable
private fun MemoryManagerSectionTitle(text: String) {
    Text(text, color = MemoryManagerTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

private fun resolveConversationTitle(
    conversationId: Long,
    storedTitle: String?,
    conversationMap: Map<Long, ConversationEntity>
): String {
    return storedTitle?.takeIf { it.isNotBlank() }
        ?: conversationMap[conversationId]?.title?.takeIf { it.isNotBlank() }
        ?: "대화 #$conversationId"
}

private fun formatMemoryTime(time: Long): String {
    return if (time > 0L) {
        SimpleDateFormat("yyyy.MM.dd a h:mm", Locale.KOREAN).format(Date(time))
    } else {
        "-"
    }
}

private fun formatSummaryUpdated(updatedAt: Long): String {
    return if (updatedAt > 0L) {
        "업데이트: ${SimpleDateFormat("yyyy.MM.dd a h:mm", Locale.KOREAN).format(Date(updatedAt))}"
    } else {
        "업데이트 정보가 없습니다."
    }
}

private fun buildAllMemoriesCopyText(
    memories: List<ConversationMemoryCandidate>,
    conversationMap: Map<Long, ConversationEntity>
): String {
    return buildString {
        memories.forEachIndexed { index, candidate ->
            if (index > 0) appendLine().appendLine()
            appendLine(resolveConversationTitle(candidate.conversationId, candidate.conversationTitle, conversationMap))
            appendLine(candidate.text)
        }
    }.trim()
}
