package com.projectnuke.fusion.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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

private enum class MemoryFilter {
    ALL,
    ENABLED,
    DISABLED,
    CURRENT_CONVERSATION,
    GLOBAL
}

@Composable
fun MemoryManagerDialog(
    context: Context,
    clipboard: ClipboardManager,
    conversations: List<ConversationEntity>,
    archivedConversations: List<ConversationEntity>,
    onDismiss: () -> Unit,
    currentConversationId: Long? = null
) {
    var refreshKey by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(MemoryFilter.ALL) }
    var editingMemory by remember { mutableStateOf<ConversationMemoryCandidate?>(null) }
    var editingMemoryText by remember { mutableStateOf("") }
    var deletingMemory by remember { mutableStateOf<ConversationMemoryCandidate?>(null) }
    var changingScopeMemory by remember { mutableStateOf<ConversationMemoryCandidate?>(null) }
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
    var sortMode by remember {
        mutableStateOf(
            settingsPrefs.getString(PrefMemoryManagerSortMode, null)
                ?.let { runCatching { MemoryManagerSortMode.valueOf(it) }.getOrNull() }
                ?: MemoryManagerSortMode.UPDATED_DESC
        )
    }

    val conversationMap = remember(conversations, archivedConversations) {
        (conversations + archivedConversations).associateBy { it.id }
    }
    val savedMemories = remember(refreshKey) { loadAllConversationMemoryCandidates(context) }
    val savedSummaries = remember(refreshKey) { loadAllConversationSummaries(context) }
    val selectedModel = settingsPrefs.getString("selected_model", null)
    val memoryContext = remember(refreshKey, memoryContextEnabled, selectedModel, currentConversationId) {
        buildSavedMemoryContext(
            context = context,
            prefs = settingsPrefs,
            currentConversationId = currentConversationId,
            currentModelId = selectedModel,
            globalPreviewOnly = currentConversationId == null
        )
    }
    val query = searchQuery.trim()
    val filteredMemories = remember(savedMemories, conversationMap, query, filter, sortMode, currentConversationId) {
        savedMemories
            .filter { candidate ->
                query.isBlank() ||
                    candidate.text.contains(query, ignoreCase = true) ||
                    resolveConversationTitle(candidate.conversationId, candidate.conversationTitle, conversationMap)
                        .contains(query, ignoreCase = true) ||
                    memoryScopeLabel(candidate.scope).contains(query, ignoreCase = true) ||
                    memoryEnabledLabel(candidate).contains(query, ignoreCase = true)
            }
            .filter { candidate ->
                when (filter) {
                    MemoryFilter.ALL -> true
                    MemoryFilter.ENABLED -> candidate.enabled && candidate.scope != MemoryScope.DISABLED
                    MemoryFilter.DISABLED -> !candidate.enabled || candidate.scope == MemoryScope.DISABLED
                    MemoryFilter.CURRENT_CONVERSATION -> currentConversationId != null &&
                        candidate.conversationId == currentConversationId
                    MemoryFilter.GLOBAL -> candidate.scope == MemoryScope.GLOBAL
                }
            }
            .let { sortMemories(it, sortMode) }
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
                        Text(
                            "Fusion이 나중에 참고할 수 있도록 저장한 정보를 관리합니다.",
                            color = MemoryManagerTextSecondary,
                            fontSize = 13.sp
                        )
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
                        colors = memoryManagerTextFieldColors()
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("필터", color = MemoryManagerTextSecondary, fontSize = 12.sp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MemoryFilterChip("전체", filter == MemoryFilter.ALL) { filter = MemoryFilter.ALL }
                            MemoryFilterChip("사용 중", filter == MemoryFilter.ENABLED) { filter = MemoryFilter.ENABLED }
                            MemoryFilterChip("사용 안 함", filter == MemoryFilter.DISABLED) { filter = MemoryFilter.DISABLED }
                            if (currentConversationId != null) {
                                MemoryFilterChip("현재 대화", filter == MemoryFilter.CURRENT_CONVERSATION) {
                                    filter = MemoryFilter.CURRENT_CONVERSATION
                                }
                            }
                            MemoryFilterChip("전체 대화", filter == MemoryFilter.GLOBAL) { filter = MemoryFilter.GLOBAL }
                            MemoryFilterChip("최근 저장", sortMode == MemoryManagerSortMode.CREATED_DESC) {
                                updateSortMode(settingsPrefs, MemoryManagerSortMode.CREATED_DESC) { sortMode = it }
                            }
                        }
                        if (currentConversationId == null) {
                            Text("현재 대화 기준 필터를 사용할 수 없습니다.", color = MemoryManagerTextSecondary, fontSize = 11.sp)
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("정렬", color = MemoryManagerTextSecondary, fontSize = 12.sp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MemoryManagerSortMode.entries.forEach { mode ->
                                MemoryFilterChip(memorySortLabel(mode), sortMode == mode) {
                                    updateSortMode(settingsPrefs, mode) { sortMode = it }
                                }
                            }
                        }
                    }
                }
                item { MemoryManagerSectionTitle("저장된 메모리") }
                if (filteredMemories.isEmpty()) {
                    item {
                        MemoryManagerCard {
                            Text(
                                if (query.isNotBlank() || filter != MemoryFilter.ALL) "검색 결과가 없습니다." else "저장된 메모리가 없습니다.",
                                color = MemoryManagerTextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(filteredMemories, key = { it.id }) { candidate ->
                        val conversationTitle = resolveConversationTitle(candidate.conversationId, candidate.conversationTitle, conversationMap)
                        MemoryManagerCard {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(memoryEnabledLabel(candidate), color = if (candidate.enabled) MemoryManagerAccentBlue else MemoryManagerTextSecondary, fontSize = 11.sp)
                                Text(formatMemoryTime(candidate.updatedAt ?: candidate.createdAt), color = MemoryManagerTextSecondary, fontSize = 11.sp)
                            }
                            Text(memoryScopeLabel(candidate.scope), color = MemoryManagerTextSecondary, fontSize = 11.sp)
                            Text(conversationTitle, color = MemoryManagerTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(candidate.text, color = MemoryManagerTextPrimary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TextButton(onClick = {
                                    editingMemory = candidate
                                    editingMemoryText = candidate.text
                                }) { Text("수정", color = MemoryManagerAccentBlue) }
                                TextButton(onClick = {
                                    clipboard.setText(AnnotatedString(candidate.text))
                                    Toast.makeText(context, "메모리를 복사했습니다.", Toast.LENGTH_SHORT).show()
                                }) { Text("복사", color = MemoryManagerAccentBlue) }
                                TextButton(onClick = { changingScopeMemory = candidate }) {
                                    Text("적용 범위 변경", color = MemoryManagerAccentBlue)
                                }
                                TextButton(onClick = { deletingMemory = candidate }) { Text("삭제", color = MemoryManagerDangerRed) }
                            }
                        }
                    }
                }

                item { MemoryManagerSectionTitle("대화 요약") }
                if (filteredSummaries.isEmpty()) {
                    item {
                        MemoryManagerCard {
                            Text(
                                if (query.isNotBlank()) "검색 결과가 없습니다." else "저장된 대화 요약이 없습니다.",
                                color = MemoryManagerTextSecondary,
                                fontSize = 14.sp
                            )
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
                                    Toast.makeText(context, "요약을 복사했습니다.", Toast.LENGTH_SHORT).show()
                                }) { Text("복사", color = MemoryManagerAccentBlue) }
                                TextButton(onClick = { deletingSummary = summary }) { Text("삭제", color = MemoryManagerDangerRed) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
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
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("닫기", color = MemoryManagerTextSecondary) }
        },
        containerColor = MemoryManagerPanelBg,
        titleContentColor = MemoryManagerTextPrimary,
        textContentColor = MemoryManagerTextPrimary
    )

    if (showContextPreview) {
        MemoryContextPreviewDialog(
            clipboard = clipboard,
            context = context,
            memoryContextEnabled = memoryContextEnabled,
            memoryContext = memoryContext,
            globalPreviewOnly = currentConversationId == null,
            onDismiss = { showContextPreview = false }
        )
    }
    changingScopeMemory?.let { candidate ->
        MemoryScopeDialog(
            context = context,
            candidate = candidate,
            selectedModel = selectedModel,
            onDismiss = { changingScopeMemory = null },
            onScopeChanged = {
                refreshKey++
                changingScopeMemory = null
            }
        )
    }
    editingMemory?.let { candidate ->
        MemoryEditDialog(
            title = "메모리 수정",
            value = editingMemoryText,
            onValueChange = { editingMemoryText = it },
            onDismiss = { editingMemory = null },
            onSave = {
                if (updateConversationMemoryCandidate(context, candidate.id, editingMemoryText)) {
                    refreshKey++
                    editingMemory = null
                    Toast.makeText(context, "메모리를 저장했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    deletingMemory?.let { candidate ->
        MemoryDeleteDialog(
            title = "메모리를 삭제하시겠습니까?",
            message = "저장된 메모리만 삭제되며 채팅 기록은 삭제되지 않습니다.",
            onDismiss = { deletingMemory = null },
            onDelete = {
                if (deleteConversationMemoryCandidate(context, candidate.id)) {
                    refreshKey++
                    deletingMemory = null
                    Toast.makeText(context, "메모리를 삭제했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    editingSummary?.let { summary ->
        MemoryEditDialog(
            title = "대화 요약 편집",
            value = editingSummaryText,
            onValueChange = { editingSummaryText = it },
            onDismiss = { editingSummary = null },
            onSave = {
                if (saveConversationSummary(context, summary.conversationId, editingSummaryText) != null) {
                    refreshKey++
                    editingSummary = null
                    Toast.makeText(context, "대화 요약을 저장했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    deletingSummary?.let { summary ->
        MemoryDeleteDialog(
            title = "대화 요약을 삭제하시겠습니까?",
            message = "저장된 요약만 삭제되며 채팅 기록은 삭제되지 않습니다.",
            onDismiss = { deletingSummary = null },
            onDelete = {
                deleteConversationSummary(context, summary.conversationId)
                refreshKey++
                deletingSummary = null
                Toast.makeText(context, "대화 요약을 삭제했습니다.", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun MemoryContextPreviewDialog(
    clipboard: ClipboardManager,
    context: Context,
    memoryContextEnabled: Boolean,
    memoryContext: FusionSavedMemoryContext,
    globalPreviewOnly: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("메모리 컨텍스트 미리보기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("답변 생성에 포함될 메모리를 확인합니다.", color = MemoryManagerTextSecondary, fontSize = 13.sp)
                if (globalPreviewOnly) {
                    Text("현재 대화 기준 정보가 없어 전체 메모리 기준으로 표시합니다.", color = MemoryManagerTextSecondary, fontSize = 12.sp)
                }
                Text("메모리 사용: ${if (memoryContextEnabled) "켜짐" else "꺼짐"}", color = MemoryManagerTextPrimary, fontSize = 13.sp)
                Text("저장된 메모리: ${memoryContext.totalSavedCount}개", color = MemoryManagerTextPrimary, fontSize = 13.sp)
                Text("사용 중인 메모리: ${memoryContext.enabledCount}개", color = MemoryManagerTextPrimary, fontSize = 13.sp)
                Text("현재 포함 메모리: ${memoryContext.itemCount}개", color = MemoryManagerTextPrimary, fontSize = 13.sp)
                Text("적용 범위로 제외됨: ${memoryContext.excludedByScopeCount}개", color = MemoryManagerTextPrimary, fontSize = 13.sp)
                Text("예상 문자 수: ${memoryContext.characterCount}", color = MemoryManagerTextPrimary, fontSize = 13.sp)
                Text("길이 제한 적용: ${if (memoryContext.trimmed) "예" else "아니요"}", color = MemoryManagerTextPrimary, fontSize = 13.sp)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MemoryManagerCardBg
                ) {
                    Text(
                        text = when {
                            !memoryContextEnabled -> "메모리 사용이 꺼져 있습니다."
                            memoryContext.text.isNullOrBlank() -> "답변 생성에 포함될 메모리가 없습니다."
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
                    memoryContext.text?.takeIf { it.isNotBlank() }?.let {
                        clipboard.setText(AnnotatedString(it))
                        Toast.makeText(context, "메모리 컨텍스트를 복사했습니다.", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !memoryContext.text.isNullOrBlank()
            ) { Text("복사", color = MemoryManagerAccentBlue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("닫기", color = MemoryManagerTextSecondary) }
        },
        containerColor = MemoryManagerPanelBg,
        titleContentColor = MemoryManagerTextPrimary,
        textContentColor = MemoryManagerTextPrimary
    )
}

@Composable
private fun MemoryScopeDialog(
    context: Context,
    candidate: ConversationMemoryCandidate,
    selectedModel: String?,
    onDismiss: () -> Unit,
    onScopeChanged: () -> Unit
) {
    fun applyScope(scope: MemoryScope, modelId: String? = null) {
        if (setConversationMemoryCandidateScope(context, candidate.id, scope, modelId)) {
            Toast.makeText(context, "메모리 적용 범위를 변경했습니다.", Toast.LENGTH_SHORT).show()
            onScopeChanged()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("메모리 적용 범위") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("이 메모리를 답변 생성에 언제 사용할지 선택합니다.", color = MemoryManagerTextSecondary, fontSize = 13.sp)
                TextButton(onClick = { applyScope(MemoryScope.GLOBAL) }) { Text("전체 대화에서 사용", color = MemoryManagerAccentBlue) }
                TextButton(onClick = { applyScope(MemoryScope.CONVERSATION_ONLY) }) { Text("현재 대화에서만 사용", color = MemoryManagerAccentBlue) }
                TextButton(
                    onClick = {
                        if (selectedModel.isNullOrBlank()) {
                            Toast.makeText(context, "현재 모델 정보를 확인할 수 없습니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            applyScope(MemoryScope.MODEL_ONLY, selectedModel)
                        }
                    }
                ) { Text("현재 모델에 연결", color = MemoryManagerAccentBlue) }
                TextButton(onClick = { applyScope(MemoryScope.DISABLED) }) { Text("사용 안 함", color = MemoryManagerTextSecondary) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("닫기", color = MemoryManagerTextSecondary) }
        },
        containerColor = MemoryManagerPanelBg,
        titleContentColor = MemoryManagerTextPrimary,
        textContentColor = MemoryManagerTextPrimary
    )
}

@Composable
private fun MemoryEditDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                colors = memoryManagerTextFieldColors()
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("저장", color = MemoryManagerAccentBlue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", color = MemoryManagerTextSecondary) }
        },
        containerColor = MemoryManagerPanelBg,
        titleContentColor = MemoryManagerTextPrimary,
        textContentColor = MemoryManagerTextPrimary
    )
}

@Composable
private fun MemoryDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDelete) { Text("삭제", color = MemoryManagerDangerRed) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", color = MemoryManagerTextSecondary) }
        },
        containerColor = MemoryManagerPanelBg,
        titleContentColor = MemoryManagerTextPrimary,
        textContentColor = MemoryManagerTextPrimary
    )
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

@Composable
private fun MemoryFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) }
    )
}

@Composable
private fun memoryManagerTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MemoryManagerTextPrimary,
    unfocusedTextColor = MemoryManagerTextPrimary,
    focusedBorderColor = MemoryManagerAccentBlue,
    unfocusedBorderColor = MemoryManagerTextSecondary,
    focusedContainerColor = MemoryManagerCardBg,
    unfocusedContainerColor = MemoryManagerCardBg,
    focusedPlaceholderColor = MemoryManagerTextSecondary,
    unfocusedPlaceholderColor = MemoryManagerTextSecondary
)

private fun updateSortMode(
    prefs: android.content.SharedPreferences,
    mode: MemoryManagerSortMode,
    updateState: (MemoryManagerSortMode) -> Unit
) {
    prefs.edit().putString(PrefMemoryManagerSortMode, mode.name).apply()
    updateState(mode)
}

private fun sortMemories(
    memories: List<ConversationMemoryCandidate>,
    mode: MemoryManagerSortMode
): List<ConversationMemoryCandidate> {
    return when (mode) {
        MemoryManagerSortMode.UPDATED_DESC -> memories.sortedByDescending { it.updatedAt ?: it.createdAt }
        MemoryManagerSortMode.CREATED_DESC -> memories.sortedByDescending { it.createdAt }
        MemoryManagerSortMode.SHORTEST_FIRST -> memories.sortedWith(compareBy<ConversationMemoryCandidate> { it.text.length }.thenByDescending { it.updatedAt ?: it.createdAt })
        MemoryManagerSortMode.LONGEST_FIRST -> memories.sortedWith(compareByDescending<ConversationMemoryCandidate> { it.text.length }.thenByDescending { it.updatedAt ?: it.createdAt })
        MemoryManagerSortMode.ENABLED_FIRST -> memories.sortedWith(compareByDescending<ConversationMemoryCandidate> { it.enabled && it.scope != MemoryScope.DISABLED }.thenByDescending { it.updatedAt ?: it.createdAt })
    }
}

private fun memorySortLabel(mode: MemoryManagerSortMode): String {
    return when (mode) {
        MemoryManagerSortMode.UPDATED_DESC -> "최근 수정순"
        MemoryManagerSortMode.CREATED_DESC -> "최근 저장순"
        MemoryManagerSortMode.SHORTEST_FIRST -> "짧은 메모리 우선"
        MemoryManagerSortMode.LONGEST_FIRST -> "긴 메모리 우선"
        MemoryManagerSortMode.ENABLED_FIRST -> "사용 중 우선"
    }
}

private fun memoryScopeLabel(scope: MemoryScope): String {
    return when (scope) {
        MemoryScope.GLOBAL -> "전체 대화"
        MemoryScope.CONVERSATION_ONLY -> "현재 대화"
        MemoryScope.MODEL_ONLY -> "특정 모델"
        MemoryScope.DISABLED -> "사용 안 함"
    }
}

private fun memoryEnabledLabel(candidate: ConversationMemoryCandidate): String {
    return if (candidate.enabled && candidate.scope != MemoryScope.DISABLED) "사용 중" else "사용 안 함"
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
