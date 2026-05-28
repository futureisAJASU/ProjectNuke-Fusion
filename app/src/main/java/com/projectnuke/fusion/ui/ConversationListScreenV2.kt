package com.projectnuke.fusion.ui

import android.content.Intent
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectnuke.fusion.data.AppDatabase
import com.projectnuke.fusion.data.BenchmarkDao
import com.projectnuke.fusion.data.ConversationEntity
import com.projectnuke.fusion.data.MessageEntity
import com.projectnuke.fusion.util.AttachmentStorageManager
import com.projectnuke.fusion.util.AttachmentStorageStats
import com.projectnuke.fusion.util.collectFusionSocInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val PrefArchiveLockEnabled = "archive_lock_enabled"

private data class AppInfoSummary(
    val versionName: String,
    val versionCode: String
)

private val DrawerBlackBg = Color(0xFF000000)
private val DrawerPanelBg = Color(0xFF171717)
private val DrawerCardBg = Color(0xFF111111)
private val DrawerCardSelectedBg = Color(0xFF202020)
private val DrawerTextPrimary = Color(0xFFF5F5F5)
private val DrawerTextSecondary = Color(0xFF9E9E9E)
private val DrawerAccentBlue = Color(0xFF9FD0FF)

private enum class SidebarPage {
    HOME,
    SETTINGS,
    ARCHIVE,
    PROMPT_LAB,
    BENCHMARK
}

@Composable
fun ConversationListScreenV2(
    currentConversationId: Long,
    onBack: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onConversationRemovedFromList: (removedConversationId: Long, nextConversationId: Long?) -> Unit,
    onNewChat: () -> Unit,
    isDrawerOpen: Boolean = true,
    onOpenModelLibrary: (() -> Unit)? = null,
    onOpenAdvancedSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val db = remember { AppDatabase.getInstance(context) }
    val dao = remember { db.chatDao() }
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("fusion_chat_settings", android.content.Context.MODE_PRIVATE) }

    var page by remember { mutableStateOf(SidebarPage.HOME) }
    var searchQuery by remember { mutableStateOf("") }
    var menuConversation by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameConversation by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameTitle by remember { mutableStateOf("") }
    var deleteConversation by remember { mutableStateOf<ConversationEntity?>(null) }
    var showAttachmentStorageDialog by remember { mutableStateOf(false) }
    var showAppInfoDialog by remember { mutableStateOf(false) }
    var showReleaseNotesDialog by remember { mutableStateOf(false) }
    var attachmentStorageStats by remember { mutableStateOf<AttachmentStorageStats?>(null) }
    var attachmentStorageLoading by remember { mutableStateOf(false) }
    var archiveLockEnabled by remember { mutableStateOf(prefs.getBoolean(PrefArchiveLockEnabled, false)) }
    var archiveUnlockedForSession by remember { mutableStateOf(false) }

    LaunchedEffect(isDrawerOpen) {
        if (!isDrawerOpen) {
            archiveUnlockedForSession = false
        }
    }

    var reasoningEnabled by remember { mutableStateOf(prefs.getBoolean("reasoning_enabled", false)) }
    var webSearchEnabled by remember { mutableStateOf(prefs.getBoolean("web_search_enabled", false)) }
    var speculativeEnabled by remember { mutableStateOf(prefs.getBoolean("speculative_decoding_enabled", false)) }

    val conversations by dao.observeConversations().collectAsState(initial = emptyList())
    val archivedConversations by dao.observeArchivedConversations().collectAsState(initial = emptyList())

    val trimmedSearchQuery = searchQuery.trim()
    val isSearchMode = trimmedSearchQuery.isNotEmpty()
    val matchingMessageConversationIds by remember(trimmedSearchQuery) {
        if (trimmedSearchQuery.isBlank()) flowOf(emptyList())
        else dao.observeConversationIdsMatchingMessages(trimmedSearchQuery)
    }.collectAsState(initial = emptyList())

    val filteredConversations = remember(conversations, trimmedSearchQuery, matchingMessageConversationIds) {
        if (trimmedSearchQuery.isBlank()) {
            conversations
        } else {
            val ids = matchingMessageConversationIds.toSet()
            conversations.filter { it.title.contains(trimmedSearchQuery, true) || it.id in ids }
        }
    }

    fun saveSettings() {
        prefs.edit()
            .putBoolean("reasoning_enabled", reasoningEnabled)
            .putBoolean("web_search_enabled", webSearchEnabled)
            .putBoolean("speculative_decoding_enabled", speculativeEnabled)
            .apply()
        Toast.makeText(context, "설정을 저장했습니다.", Toast.LENGTH_SHORT).show()
    }

    fun leaveArchive() {
        archiveUnlockedForSession = false
        page = SidebarPage.HOME
    }

    fun closeDrawer() {
        archiveUnlockedForSession = false
        onBack()
    }

    fun openConversation(conversationId: Long) {
        archiveUnlockedForSession = false
        onOpenConversation(conversationId)
    }

    fun regenerateConversationTitle(conversation: ConversationEntity) {
        menuConversation = null
        scope.launch {
            runCatching {
                val messages = dao.getMessagesForConversation(conversation.id)
                val newTitle = buildSafeConversationTitleFromMessages(messages)
                val currentTitle = conversation.title.ifBlank { "새 대화" }
                if (newTitle == currentTitle) {
                    Toast.makeText(context, "이미 적절한 제목입니다.", Toast.LENGTH_SHORT).show()
                } else {
                    dao.updateConversationTitle(conversation.id, newTitle)
                    Toast.makeText(context, "채팅 제목을 다시 생성했습니다.", Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(context, "채팅 제목을 생성할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openArchiveWithAuth() {
        if (!archiveLockEnabled) {
            page = SidebarPage.ARCHIVE
            return
        }

        if (!isArchiveAuthenticationAvailable(context)) {
            Toast.makeText(context, "이 기기에서는 아카이브 잠금을 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        authenticateArchiveAccess(
            context = context,
            onSuccess = {
                archiveUnlockedForSession = true
                page = SidebarPage.ARCHIVE
                Toast.makeText(context, "인증이 완료되었습니다.", Toast.LENGTH_SHORT).show()
            },
            onCanceled = {
                Toast.makeText(context, "인증이 취소되었습니다.", Toast.LENGTH_SHORT).show()
            },
            onError = {
                Toast.makeText(context, "아카이브를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    BackHandler(enabled = isDrawerOpen && (trimmedSearchQuery.isNotBlank() || page != SidebarPage.HOME)) {
        when {
            trimmedSearchQuery.isNotBlank() -> {
                searchQuery = ""
            }
            page == SidebarPage.ARCHIVE -> {
                leaveArchive()
            }
            page == SidebarPage.SETTINGS -> {
                leaveArchive()
            }
            page == SidebarPage.PROMPT_LAB -> {
                page = SidebarPage.SETTINGS
            }
            else -> Unit
        }
    }

    if (!isSearchMode && page == SidebarPage.BENCHMARK) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DrawerBlackBg)
        ) {
            FusionBenchmarkScreen(onBack = { leaveArchive() })
        }
        return
    }

    if (isSearchMode || page == SidebarPage.HOME) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DrawerBlackBg)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (!isSearchMode) {
                DrawerTopBar(onClose = { closeDrawer() }, onNewChat = onNewChat)
                Spacer(modifier = Modifier.height(12.dp))
            }

            DrawerSearchBox(value = searchQuery, onValueChange = { searchQuery = it })
            Spacer(modifier = Modifier.height(10.dp))
            DrawerSectionTitle(if (isSearchMode) "검색 결과" else "대화")
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filteredConversations.isEmpty()) {
                    item { EmptyConversationList(hasSearchQuery = isSearchMode) }
                } else {
                    items(filteredConversations, key = { it.id }) { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            onClick = { openConversation(conversation.id) },
                            menuExpanded = menuConversation?.id == conversation.id,
                            onDismissMenu = { menuConversation = null },
                            onMenuClick = { menuConversation = conversation },
                            onTogglePinned = {
                                menuConversation = null
                                scope.launch { dao.updateConversationPinned(conversation.id, !conversation.isPinned) }
                            },
                            onRename = {
                                menuConversation = null
                                renameConversation = conversation
                                renameTitle = conversation.title
                            },
                            onRegenerateTitle = {
                                regenerateConversationTitle(conversation)
                            },
                            onArchive = {
                                menuConversation = null
                                scope.launch {
                                    dao.archiveConversation(conversation.id)
                                    if (conversation.id == currentConversationId) {
                                        val nextId = dao.getLatestConversation()?.id
                                        onConversationRemovedFromList(conversation.id, nextId)
                                    }
                                }
                            },
                            onDelete = {
                                menuConversation = null
                                deleteConversation = conversation
                            }
                        )
                    }
                }
            }

            if (!isSearchMode) {
                Spacer(modifier = Modifier.height(10.dp))
                DrawerBottomNavRow(
                    title = "벤치마크",
                    subtitle = "선택한 모델의 성능을 측정합니다.",
                    leading = "B"
                ) { page = SidebarPage.BENCHMARK }
                DrawerBottomNavRow(
                    title = "Prompt Lab",
                    subtitle = "시스템 프롬프트와 응답 스타일을 조정합니다.",
                    leading = "L"
                ) { page = SidebarPage.PROMPT_LAB }
                DrawerBottomNavRow(
                    title = "아카이브",
                    subtitle = "보관한 채팅을 확인하고 복원합니다.",
                    leading = "A"
                ) { openArchiveWithAuth() }
                DrawerBottomNavRow(
                    title = "설정",
                    subtitle = "모델, 생성, 데이터 관련 설정을 관리합니다.",
                    leading = "S"
                ) {
                    archiveUnlockedForSession = false
                    page = SidebarPage.SETTINGS
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DrawerBlackBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!isSearchMode) {
            item {
                when (page) {
                    SidebarPage.HOME -> DrawerTopBar(onClose = { closeDrawer() }, onNewChat = onNewChat)
                    SidebarPage.SETTINGS -> DrawerPageTopBar("설정", onBack = { leaveArchive() })
                    SidebarPage.ARCHIVE -> DrawerPageTopBar("아카이브", onBack = { leaveArchive() })
                    SidebarPage.PROMPT_LAB -> DrawerPageTopBar("Prompt Lab", onBack = { leaveArchive() })
                    SidebarPage.BENCHMARK -> DrawerPageTopBar("벤치마크", onBack = { leaveArchive() })
                }
            }
        }

        item {
            if (!isSearchMode) Spacer(modifier = Modifier.height(6.dp))
            DrawerSearchBox(value = searchQuery, onValueChange = { searchQuery = it })
        }

        if (isSearchMode) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                DrawerSectionTitle("검색 결과")
            }
            if (filteredConversations.isEmpty()) {
                item { EmptyConversationList(hasSearchQuery = true) }
            } else {
                items(filteredConversations, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        onClick = { openConversation(conversation.id) },
                        menuExpanded = menuConversation?.id == conversation.id,
                        onDismissMenu = { menuConversation = null },
                        onMenuClick = { menuConversation = conversation },
                        onTogglePinned = {
                            menuConversation = null
                            scope.launch { dao.updateConversationPinned(conversation.id, !conversation.isPinned) }
                        },
                        onRename = {
                            menuConversation = null
                            renameConversation = conversation
                            renameTitle = conversation.title
                        },
                        onRegenerateTitle = {
                            regenerateConversationTitle(conversation)
                        },
                        onArchive = {
                            menuConversation = null
                            scope.launch {
                                dao.archiveConversation(conversation.id)
                                if (conversation.id == currentConversationId) {
                                    val nextId = dao.getLatestConversation()?.id
                                    onConversationRemovedFromList(conversation.id, nextId)
                                }
                            }
                        },
                        onDelete = {
                            menuConversation = null
                            deleteConversation = conversation
                        }
                    )
                }
            }
        } else {
            when (page) {
                SidebarPage.HOME -> {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        DrawerPrimaryButton(
                            title = "새 채팅",
                            subtitle = "빈 대화로 시작합니다.",
                            leading = "+",
                            onClick = onNewChat
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)); DrawerSectionTitle("프로젝트") }
                    item {
                        DrawerActionRow("새 프로젝트", "대화, 파일, 메모리를 묶는 공간입니다.", "P") {
                            Toast.makeText(context, "아직 준비 중입니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    item {
                        DrawerActionRow("프로젝트에 추가", "현재 대화를 프로젝트에 추가합니다.", "↗") {
                            Toast.makeText(context, "아직 준비 중입니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)); DrawerSectionTitle("실험실") }
                    item { DrawerActionRow("모델 라이브러리", "Gemma, Fusion, 커스텀 모델을 관리합니다.", "M") { Toast.makeText(context, "아직 준비 중입니다.", Toast.LENGTH_SHORT).show() } }
                    item { DrawerActionRow("벤치마크", "TTFT, 토큰 속도, 메모리 사용량을 측정합니다.", "B") { page = SidebarPage.BENCHMARK } }
                    item { DrawerActionRow("Prompt Lab", "시스템 프롬프트와 응답 스타일을 테스트합니다.", "L") { page = SidebarPage.PROMPT_LAB } }
                    item { DrawerActionRow("에이전트 모드", "기기 제어 실험 기능입니다.", "A") { Toast.makeText(context, "아직 준비 중입니다.", Toast.LENGTH_SHORT).show() } }
                    item { Spacer(modifier = Modifier.height(8.dp)); DrawerSectionTitle("대화") }
                    if (filteredConversations.isEmpty()) {
                        item { EmptyConversationList(hasSearchQuery = false) }
                    } else {
                        items(filteredConversations, key = { it.id }) { conversation ->
                            ConversationRow(
                                conversation = conversation,
                                onClick = { openConversation(conversation.id) },
                                menuExpanded = menuConversation?.id == conversation.id,
                                onDismissMenu = { menuConversation = null },
                                onMenuClick = { menuConversation = conversation },
                                onTogglePinned = {
                                    menuConversation = null
                                    scope.launch { dao.updateConversationPinned(conversation.id, !conversation.isPinned) }
                                },
                                onRename = {
                                    menuConversation = null
                                    renameConversation = conversation
                                    renameTitle = conversation.title
                                },
                                onRegenerateTitle = {
                                    regenerateConversationTitle(conversation)
                                },
                                onArchive = {
                                    menuConversation = null
                                    scope.launch {
                                        dao.archiveConversation(conversation.id)
                                        if (conversation.id == currentConversationId) {
                                            val nextId = dao.getLatestConversation()?.id
                                            onConversationRemovedFromList(conversation.id, nextId)
                                        }
                                    }
                                },
                                onDelete = {
                                    menuConversation = null
                                    deleteConversation = conversation
                                }
                            )
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        DrawerBottomNavRow(
                            title = "아카이브",
                            subtitle = "보관한 채팅을 확인하고 복원합니다.",
                            leading = "A"
                        ) { openArchiveWithAuth() }
                    }
                    item {
                        DrawerBottomNavRow(
                            title = "설정",
                            subtitle = "모델, 생성, 데이터 관련 설정을 관리합니다.",
                            leading = "S"
                        ) {
                            archiveUnlockedForSession = false
                            page = SidebarPage.SETTINGS
                        }
                    }
                }

                SidebarPage.ARCHIVE -> {
                    item { DrawerSectionTitle("보관한 대화를 다시 열거나 복원할 수 있습니다.") }
                    if (archivedConversations.isEmpty()) {
                        item { EmptyArchiveList() }
                    } else {
                        items(archivedConversations, key = { it.id }) { conversation ->
                            ConversationRow(
                                conversation = conversation,
                                onClick = { openConversation(conversation.id) },
                                menuExpanded = menuConversation?.id == conversation.id,
                                onDismissMenu = { menuConversation = null },
                                onMenuClick = { menuConversation = conversation },
                                onTogglePinned = {
                                    menuConversation = null
                                    scope.launch { dao.updateConversationPinned(conversation.id, !conversation.isPinned) }
                                },
                                onRename = {
                                    menuConversation = null
                                    renameConversation = conversation
                                    renameTitle = conversation.title
                                },
                                onRegenerateTitle = {
                                    regenerateConversationTitle(conversation)
                                },
                                onArchive = {
                                    menuConversation = null
                                    scope.launch { dao.setConversationArchived(conversation.id, false) }
                                },
                                onDelete = {
                                    menuConversation = null
                                    deleteConversation = conversation
                                },
                                archiveMenuLabel = "복원",
                                pinMenuLabel = if (conversation.isPinned) "고정 해제" else "채팅 고정"
                            )
                        }
                    }
                }

                SidebarPage.SETTINGS -> {
                    val modelName = prefs.getString("selected_model", "Gemma 4 E2B-it") ?: "Gemma 4 E2B-it"
                    val modelPath = prefs.getString("selected_model_path", null)
                    val accelerator = prefs.getString("accelerator", "GPU") ?: "GPU"
                    val maxTokens = prefs.getInt("max_tokens", 4000)
                    val topK = prefs.getInt("top_k", 64)
                    val topP = prefs.getFloat("top_p", 0.95f)
                    val temperature = prefs.getFloat("temperature", 1.0f)

                    item { DrawerSectionTitle("모델") }
                    item {
                        DrawerSettingActionRow("모델 라이브러리", "모델을 선택합니다.") {
                            if (onOpenModelLibrary != null) onOpenModelLibrary()
                            else Toast.makeText(context, "아직 준비 중입니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    item { DrawerSettingInfoRow("기본 모델", modelPath?.let { "$modelName\n$it" } ?: modelName) }
                    item { DrawerSettingInfoRow("가속기", accelerator) }

                    item { Spacer(modifier = Modifier.height(6.dp)); DrawerSectionTitle("생성") }
                    item {
                        DrawerSettingActionRow("고급 설정", "maxTokens=$maxTokens · temp=$temperature · topK=$topK · topP=$topP") {
                            if (onOpenAdvancedSettings != null) onOpenAdvancedSettings()
                            else Toast.makeText(context, "아직 준비 중입니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    item {
                        DrawerSettingToggleRow(
                            title = "Reasoning",
                            subtitle = "답변 전 추론 과정을 사용합니다.",
                            checked = reasoningEnabled,
                            onToggle = {
                                reasoningEnabled = it
                                saveSettings()
                            }
                        )
                    }
                    item {
                        DrawerSettingToggleRow(
                            title = "웹 검색",
                            subtitle = "최신 정보가 필요한 질문에서 인터넷 검색을 사용합니다.",
                            checked = webSearchEnabled,
                            onToggle = {
                                webSearchEnabled = it
                                saveSettings()
                            }
                        )
                    }
                    item {
                        DrawerSettingToggleRow(
                            title = "MTP 가속",
                            subtitle = "지원 모델에서 speculative decoding으로 출력 속도를 높입니다.",
                            checked = speculativeEnabled,
                            onToggle = {
                                speculativeEnabled = it
                                saveSettings()
                            }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(6.dp)); DrawerSectionTitle("데이터") }
                    item { DrawerSettingActionRow("아카이브", "보관한 채팅을 확인하고 복원합니다.") { openArchiveWithAuth() } }
                    item {
                        DrawerSettingToggleRow(
                            title = "아카이브 잠금",
                            subtitle = "보관된 채팅을 열 때 생체 인증 또는 기기 잠금 인증을 요구합니다.",
                            checked = archiveLockEnabled,
                            onToggle = { enabled ->
                                if (!enabled) {
                                    archiveLockEnabled = false
                                    archiveUnlockedForSession = false
                                    prefs.edit().putBoolean(PrefArchiveLockEnabled, false).apply()
                                    Toast.makeText(context, "아카이브 잠금을 해제했습니다.", Toast.LENGTH_SHORT).show()
                                } else if (!isArchiveAuthenticationAvailable(context)) {
                                    archiveLockEnabled = false
                                    archiveUnlockedForSession = false
                                    prefs.edit().putBoolean(PrefArchiveLockEnabled, false).apply()
                                    Toast.makeText(context, "이 기기에서는 아카이브 잠금을 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
                                } else {
                                    authenticateArchiveAccess(
                                        context = context,
                                        onSuccess = {
                                            archiveLockEnabled = true
                                            archiveUnlockedForSession = false
                                            prefs.edit().putBoolean(PrefArchiveLockEnabled, true).apply()
                                            Toast.makeText(context, "아카이브 잠금을 사용합니다.", Toast.LENGTH_SHORT).show()
                                        },
                                        onCanceled = {
                                            archiveLockEnabled = false
                                            archiveUnlockedForSession = false
                                            prefs.edit().putBoolean(PrefArchiveLockEnabled, false).apply()
                                            Toast.makeText(context, "인증이 취소되었습니다.", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = {
                                            archiveLockEnabled = false
                                            archiveUnlockedForSession = false
                                            prefs.edit().putBoolean(PrefArchiveLockEnabled, false).apply()
                                            Toast.makeText(context, "아카이브 잠금을 설정할 수 없습니다.", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        )
                    }
                    item {
                        DrawerSettingActionRow("첨부파일 저장공간", "이미지와 파일 캐시를 관리합니다.") {
                            showAttachmentStorageDialog = true
                            attachmentStorageLoading = true
                            scope.launch {
                                attachmentStorageStats = runCatching {
                                    AttachmentStorageManager.calculateAttachmentStorageStats(context, dao)
                                }.getOrNull()
                                attachmentStorageLoading = false
                            }
                        }
                    }
                    item {
                        DrawerSettingActionRow("채팅 내보내기", "현재 채팅을 Markdown 텍스트로 공유합니다.") {
                            if (currentConversationId == 0L) {
                                Toast.makeText(context, "내보낼 채팅이 없습니다.", Toast.LENGTH_SHORT).show()
                            } else {
                                scope.launch {
                                    val conversation = dao.getConversationById(currentConversationId)
                                    val messages = dao.getMessagesForConversation(currentConversationId)
                                    if (conversation == null || messages.isEmpty()) {
                                        Toast.makeText(context, "내보낼 채팅이 없습니다.", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }

                                    val markdown = buildChatExportMarkdown(
                                        title = conversation.title.ifBlank { "새 대화" },
                                        messages = messages
                                    )

                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/markdown"
                                        putExtra(Intent.EXTRA_SUBJECT, "Fusion 채팅 내보내기")
                                        putExtra(Intent.EXTRA_TEXT, markdown)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "채팅 내보내기"))
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(6.dp)); DrawerSectionTitle("실험실") }
                    item { DrawerSettingActionRow("벤치마크", "TTFT, 토큰 속도, 메모리 사용량을 측정합니다.") { page = SidebarPage.BENCHMARK } }
                    item { DrawerSettingActionRow("Prompt Lab", "시스템 프롬프트와 응답 스타일을 테스트합니다.") { page = SidebarPage.PROMPT_LAB } }
                    item { DrawerSettingActionRow("에이전트 모드", "기기 제어 실험 기능입니다.") { Toast.makeText(context, "아직 준비 중입니다.", Toast.LENGTH_SHORT).show() } }

                    item { Spacer(modifier = Modifier.height(6.dp)); DrawerSectionTitle("앱 정보") }
                    item {
                        DrawerSettingActionRow("앱 정보", "버전, 모델 상태, 데이터 정보를 확인합니다.") {
                            showAppInfoDialog = true
                        }
                    }
                    item {
                        DrawerSettingActionRow("릴리즈 노트", "Fusion Beta 변경 사항을 확인합니다.") {
                            showReleaseNotesDialog = true
                        }
                    }
                    item {
                        DrawerSettingActionRow("디버그 정보 복사", "모델, 설정, 기기, 최근 상태 정보를 클립보드에 복사합니다.") {
                            scope.launch {
                                try {
                                    val debugText = buildFusionDebugInfo(
                                        context = context,
                                        dao = dao,
                                        benchmarkDao = db.benchmarkDao(),
                                        currentConversationId = currentConversationId,
                                        modelName = modelName,
                                        modelPath = modelPath,
                                        accelerator = accelerator,
                                        maxTokens = maxTokens,
                                        topK = topK,
                                        topP = topP,
                                        temperature = temperature,
                                        reasoningBudgetTokens = prefs.getInt("reasoning_budget_tokens", 0),
                                        reasoningEnabled = reasoningEnabled,
                                        webSearchEnabled = webSearchEnabled,
                                        speculativeEnabled = speculativeEnabled
                                    )
                                    clipboard.setText(AnnotatedString(debugText))
                                    Toast.makeText(context, "디버그 정보를 복사했습니다.", Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {
                                    Toast.makeText(context, "디버그 정보 복사 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
                SidebarPage.PROMPT_LAB -> {
                    item {
                        PromptLabScreen(onBack = { leaveArchive() })
                    }
                }
                SidebarPage.BENCHMARK -> {
                    // Rendered above outside this LazyColumn to avoid nested vertical scrolling.
                }
            }
        }
    }

    if (showAppInfoDialog) {
        val modelName = prefs.getString("selected_model", "Gemma 4 E2B-it") ?: "Gemma 4 E2B-it"
        val modelPath = prefs.getString("selected_model_path", null)
        val modelExists = modelPath?.let { File(it).exists() } == true
        val accelerator = prefs.getString("accelerator", "GPU") ?: "GPU"
        val mtpEnabled = prefs.getBoolean("speculative_decoding_enabled", false)
        val appInfo = rememberAppInfo(context)
        AlertDialog(
            onDismissRequest = { showAppInfoDialog = false },
            title = { Text("앱 정보") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Fusion Beta", color = DrawerTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("버전 이름: ${appInfo.versionName}", color = DrawerTextPrimary, fontSize = 13.sp)
                    Text("버전 코드: ${appInfo.versionCode}", color = DrawerTextPrimary, fontSize = 13.sp)
                    Text("기기: ${Build.MANUFACTURER} ${Build.MODEL}", color = DrawerTextPrimary, fontSize = 13.sp)
                    Text("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})", color = DrawerTextPrimary, fontSize = 13.sp)
                    Text("선택 모델: $modelName", color = DrawerTextPrimary, fontSize = 13.sp)
                    Text("모델 경로: ${modelPath ?: "없음"}", color = DrawerTextPrimary, fontSize = 13.sp)
                    Text("모델 파일 존재: ${if (modelExists) "예" else "아니요"}", color = DrawerTextPrimary, fontSize = 13.sp)
                    Text("가속기: $accelerator", color = DrawerTextPrimary, fontSize = 13.sp)
                    Text("MTP 설정: ${if (mtpEnabled) "켜짐" else "꺼짐"}", color = DrawerTextPrimary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("채팅과 설정 데이터는 이 기기에 저장됩니다.", color = DrawerTextSecondary, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppInfoDialog = false }) { Text("확인") }
            },
            containerColor = DrawerPanelBg,
            titleContentColor = DrawerTextPrimary,
            textContentColor = DrawerTextPrimary
        )
    }

    if (showReleaseNotesDialog) {
        AlertDialog(
            onDismissRequest = { showReleaseNotesDialog = false },
            title = { Text("릴리즈 노트") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("• 로컬 모델 채팅 안정성을 개선했습니다.", color = DrawerTextPrimary, fontSize = 13.sp)
                    Text("• 벤치마크와 기록 기능을 추가했습니다.", color = DrawerTextPrimary, fontSize = 13.sp)
                    Text("• MTP 켜짐/꺼짐 비교를 지원합니다.", color = DrawerTextPrimary, fontSize = 13.sp)
                    Text("• Prompt Lab을 추가했습니다.", color = DrawerTextPrimary, fontSize = 13.sp)
                    Text("• 아카이브 잠금을 추가했습니다.", color = DrawerTextPrimary, fontSize = 13.sp)
                    Text("• 첨부파일 미리보기와 관리를 개선했습니다.", color = DrawerTextPrimary, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showReleaseNotesDialog = false }) { Text("확인") }
            },
            containerColor = DrawerPanelBg,
            titleContentColor = DrawerTextPrimary,
            textContentColor = DrawerTextPrimary
        )
    }

    if (showAttachmentStorageDialog) {
        AlertDialog(
            onDismissRequest = { showAttachmentStorageDialog = false },
            title = { Text("첨부파일 저장공간") },
            text = {
                val stats = attachmentStorageStats
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (attachmentStorageLoading) {
                        Text("저장공간 정보를 계산하고 있습니다.", color = DrawerTextSecondary, fontSize = 13.sp)
                    } else if (stats != null) {
                        Text("총 용량: ${formatBytes(stats.totalBytes)}", color = DrawerTextPrimary, fontSize = 14.sp)
                        Text("파일 수: ${stats.totalFiles}", color = DrawerTextPrimary, fontSize = 14.sp)
                        Text("참조 중인 파일: ${stats.referencedFiles}", color = DrawerTextPrimary, fontSize = 14.sp)
                        Text("미참조 파일: ${stats.unreferencedFiles}", color = DrawerTextPrimary, fontSize = 14.sp)
                    } else {
                        Text("저장공간 정보를 불러오지 못했습니다.", color = DrawerTextSecondary, fontSize = 13.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAttachmentStorageDialog = false }) {
                    Text("닫기")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (attachmentStorageLoading) return@TextButton
                        scope.launch {
                            try {
                                val result = AttachmentStorageManager.cleanupUnreferencedAttachments(context, dao)
                                if (result.deletedFiles > 0) {
                                    Toast.makeText(context, "사용하지 않는 첨부파일을 정리했습니다.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "정리할 첨부파일이 없습니다.", Toast.LENGTH_SHORT).show()
                                }
                                attachmentStorageLoading = true
                                attachmentStorageStats = AttachmentStorageManager.calculateAttachmentStorageStats(context, dao)
                                attachmentStorageLoading = false
                            } catch (_: Exception) {
                                Toast.makeText(context, "첨부파일 정리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("사용하지 않는 첨부파일 정리", color = Color(0xFFFF7A7A))
                }
            },
            containerColor = DrawerPanelBg,
            titleContentColor = DrawerTextPrimary,
            textContentColor = DrawerTextPrimary
        )
    }

    renameConversation?.let { conversation ->
        AlertDialog(
            onDismissRequest = { renameConversation = null },
            title = { Text("채팅 이름 변경") },
            text = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = DrawerCardSelectedBg
                ) {
                    BasicTextField(
                        value = renameTitle,
                        onValueChange = { renameTitle = it },
                        singleLine = true,
                        textStyle = TextStyle(color = DrawerTextPrimary, fontSize = 16.sp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            },
            dismissButton = { TextButton(onClick = { renameConversation = null }) { Text("취소") } },
            confirmButton = {
                TextButton(onClick = {
                    val title = renameTitle.trim()
                    if (title.isNotBlank()) {
                        scope.launch { dao.updateConversationTitle(conversation.id, title) }
                    }
                    renameConversation = null
                }) { Text("저장") }
            },
            containerColor = DrawerPanelBg,
            titleContentColor = DrawerTextPrimary,
            textContentColor = DrawerTextPrimary
        )
    }

    deleteConversation?.let { conversation ->
        AlertDialog(
            onDismissRequest = { deleteConversation = null },
            title = { Text("채팅 삭제") },
            text = { Text("이 채팅을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.") },
            dismissButton = { TextButton(onClick = { deleteConversation = null }) { Text("취소") } },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConversation = null
                        scope.launch {
                            dao.deleteConversation(conversation.id)
                            val nextConversationId = dao.getLatestConversation()?.id
                            if (conversation.id == currentConversationId) {
                                onConversationRemovedFromList(conversation.id, nextConversationId)
                            }
                        }
                    }
                ) { Text("삭제", color = Color(0xFFFF7A7A)) }
            },
            containerColor = DrawerPanelBg,
            titleContentColor = DrawerTextPrimary,
            textContentColor = DrawerTextPrimary
        )
    }
}

@Composable
private fun DrawerPageTopBar(
    title: String,
    onBack: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DrawerCircleButton("←", onBack)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = title, color = DrawerTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DrawerTopBar(
    onClose: () -> Unit,
    onNewChat: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DrawerCircleButton("←", onClose)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Fusion", color = DrawerTextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(text = "Local AI Workspace", color = DrawerTextSecondary, fontSize = 12.sp)
        }
        DrawerCircleButton("+", onNewChat)
    }
}

@Composable
private fun DrawerSearchBox(
    value: String,
    onValueChange: (String) -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = DrawerPanelBg) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "⌕", color = DrawerTextSecondary, fontSize = 19.sp)
            Spacer(modifier = Modifier.width(10.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = DrawerTextPrimary, fontSize = 15.sp),
                decorationBox = { inner ->
                    if (value.isBlank()) {
                        Text(text = "대화를 검색합니다.", color = DrawerTextSecondary, fontSize = 15.sp)
                    }
                    inner()
                }
            )
        }
    }
}

@Composable
private fun DrawerPrimaryButton(title: String, subtitle: String, leading: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = DrawerPanelBg
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            DrawerAvatar(text = leading, accent = true)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = DrawerTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, color = DrawerTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DrawerActionRow(title: String, subtitle: String, leading: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(15.dp),
        color = DrawerBlackBg
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            DrawerAvatar(text = leading, accent = false)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = DrawerTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = subtitle, color = DrawerTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun DrawerBottomNavRow(title: String, subtitle: String, leading: String, onClick: () -> Unit) {
    DrawerActionRow(title = title, subtitle = subtitle, leading = leading, onClick = onClick)
}

@Composable
private fun DrawerSettingInfoRow(title: String, subtitle: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = DrawerCardBg) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp)) {
            Text(text = title, color = DrawerTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = DrawerTextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DrawerSettingActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = DrawerCardBg
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = DrawerTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = DrawerTextSecondary, fontSize = 12.sp)
            }
            Text(text = "›", color = DrawerTextSecondary, fontSize = 20.sp)
        }
    }
}

@Composable
private fun DrawerSettingToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = DrawerCardBg) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = DrawerTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = DrawerTextSecondary, fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: ConversationEntity,
    onClick: () -> Unit,
    menuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onMenuClick: () -> Unit,
    onTogglePinned: () -> Unit,
    onRename: () -> Unit,
    onRegenerateTitle: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    archiveMenuLabel: String = "아카이브에 보관",
    pinMenuLabel: String = if (conversation.isPinned) "고정 해제" else "채팅 고정"
) {
    val dateText = remember(conversation.updatedAt) {
        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(conversation.updatedAt))
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(15.dp),
        color = DrawerCardBg
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            DrawerAvatar(
                text = conversation.title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "F",
                accent = false
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.isPinned) {
                        Text(text = "PIN", color = DrawerAccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 6.dp))
                    }
                    Text(
                        text = conversation.title.ifBlank { "새 대화" },
                        color = DrawerTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = dateText, color = DrawerTextSecondary, fontSize = 11.sp)
            }
            Box {
                Box(modifier = Modifier.size(34.dp).clip(CircleShape).clickable { onMenuClick() }, contentAlignment = Alignment.Center) {
                    Text(text = "⋯", color = DrawerTextSecondary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu, containerColor = DrawerPanelBg) {
                    DropdownMenuItem(text = { Text(pinMenuLabel, color = DrawerTextPrimary) }, onClick = onTogglePinned)
                    DropdownMenuItem(text = { Text("이름 변경", color = DrawerTextPrimary) }, onClick = onRename)
                    DropdownMenuItem(text = { Text("제목 다시 생성", color = DrawerTextPrimary) }, onClick = onRegenerateTitle)
                    DropdownMenuItem(text = { Text(archiveMenuLabel, color = DrawerTextPrimary) }, onClick = onArchive)
                    DropdownMenuItem(text = { Text("삭제", color = Color(0xFFFF7A7A)) }, onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun EmptyConversationList(hasSearchQuery: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (hasSearchQuery) "검색 결과가 없습니다." else "아직 저장된 대화가 없습니다.",
            color = DrawerTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (hasSearchQuery) "다른 키워드로 검색해 보세요." else "새 채팅을 시작하면 여기에 저장됩니다.",
            color = DrawerTextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun EmptyArchiveList() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "보관된 채팅이 없습니다.",
            color = DrawerTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DrawerSectionTitle(text: String) {
    Text(
        text = text,
        color = DrawerTextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun DrawerAvatar(text: String, accent: Boolean) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (accent) DrawerCardSelectedBg else DrawerPanelBg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.take(2),
            color = if (accent) DrawerAccentBlue else DrawerTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DrawerCircleButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(DrawerPanelBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = DrawerTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

private fun buildSafeConversationTitleFromMessages(messages: List<MessageEntity>): String {
    val userCandidates = messages
        .asSequence()
        .filter { it.role == "user" }
        .map { stripHiddenForTitle(it.content) }
        .filter { it.isNotBlank() && it.length >= 2 }
        .toList()

    val assistantCandidates = messages
        .asSequence()
        .filter { it.role != "user" }
        .map { stripHiddenForTitle(it.content) }
        .filter { it.isNotBlank() && it.length >= 2 }
        .toList()

    val base = (userCandidates + assistantCandidates).firstOrNull().orEmpty()
    if (base.isBlank()) return "새 대화"
    return truncateSafeTitle(base)
}

private fun truncateSafeTitle(text: String): String {
    val normalized = text.replace('\n', ' ').trim()
    if (normalized.isBlank()) return "새 대화"
    val hasNonAscii = normalized.any { it.code > 127 }
    val limit = if (hasNonAscii) 18 else 28
    return if (normalized.length <= limit) normalized else normalized.take(limit).trimEnd() + "…"
}

private fun buildConversationTitleFromMessages(messages: List<MessageEntity>): String {
    val userCandidates = messages
        .asSequence()
        .filter { it.role == "user" }
        .map { stripHiddenForTitle(it.content) }
        .filter { it.isNotBlank() && it.length >= 2 }
        .toList()

    val assistantCandidates = messages
        .asSequence()
        .filter { it.role != "user" }
        .map { stripHiddenForTitle(it.content) }
        .filter { it.isNotBlank() && it.length >= 2 }
        .toList()

    val base = (userCandidates + assistantCandidates).firstOrNull().orEmpty()
    if (base.isBlank()) return "새 대화"
    return truncateTitle(base)
}

private fun stripHiddenForTitle(raw: String): String {
    return raw
        .replace(Regex("""(?is)<fusion_metrics>.*?</fusion_metrics>"""), " ")
        .replace(Regex("""(?is)<fusion_thinking>.*?</fusion_thinking>"""), " ")
        .replace(Regex("""(?is)</?fusion_answer>"""), " ")
        .replace(Regex("""(?is)<fusion_attachment_v2>.*?</fusion_attachment_v2>"""), " ")
        .replace(Regex("""(?is)<fusion_attachment>.*?</fusion_attachment>"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

private fun truncateTitle(text: String): String {
    val normalized = text.replace('\n', ' ').trim()
    if (normalized.isBlank()) return "새 대화"
    val hasNonAscii = normalized.any { it.code > 127 }
    val limit = if (hasNonAscii) 18 else 28
    return if (normalized.length <= limit) normalized else normalized.take(limit).trimEnd() + "…"
}

private fun buildChatExportMarkdown(
    title: String,
    messages: List<MessageEntity>
): String {
    val dateText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(Date())

    val body = buildString {
        appendLine("# $title")
        appendLine(dateText)
        appendLine()
        messages.forEach { message ->
            val heading = if (message.role == "user") "사용자" else "Fusion"
            val visibleContent = stripHiddenFusionBlocks(message.content).trim()
            if (visibleContent.isNotBlank()) {
                appendLine("## $heading")
                appendLine(visibleContent)
                appendLine()
            }
        }
    }

    return body.trimEnd()
}

private fun stripHiddenFusionBlocks(content: String): String {
    val withAttachmentPlaceholders = replaceAttachmentTagsWithPlaceholders(content)
    return withAttachmentPlaceholders
        .replace(Regex("""(?is)<fusion_metrics>.*?</fusion_metrics>"""), "")
        .replace(Regex("""(?is)<fusion_thinking>.*?</fusion_thinking>"""), "")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}

private fun replaceAttachmentTagsWithPlaceholders(content: String): String {
    val v2Regex = Regex("""(?is)<fusion_attachment_v2>(.*?)\|(.*?)\|(.*?)</fusion_attachment_v2>""")
    val legacyRegex = Regex("""(?is)<fusion_attachment>(.*?)</fusion_attachment>""")

    var replaced = v2Regex.replace(content) { match ->
        val rawName = match.groupValues.getOrNull(1).orEmpty().trim()
        val fileName = if (rawName.isNotBlank()) rawName else "파일"
        "\n[첨부파일: $fileName]\n"
    }

    replaced = legacyRegex.replace(replaced) { match ->
        val payload = match.groupValues.getOrNull(1).orEmpty()
        val nameFromField = Regex("""(?i)(?:^|[|,\s])(?:name|filename)\s*=\s*([^|,\n]+)""")
            .find(payload)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        val pathFromField = Regex("""(?i)(?:^|[|,\s])path\s*=\s*([^|,\n]+)""")
            .find(payload)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        val fileName = when {
            !nameFromField.isNullOrBlank() -> nameFromField
            !pathFromField.isNullOrBlank() -> pathFromField.substringAfterLast('/').substringAfterLast('\\')
            else -> "파일"
        }
        "\n[첨부파일: $fileName]\n"
    }

    return replaced
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.2f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.2f KB", bytes / kb)
        else -> "$bytes B"
    }
}

private fun archiveAuthenticators(): Int {
    return BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
}

private fun Context.findActivity(): Activity? {
    var currentContext: Context? = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}

private fun isArchiveAuthenticationAvailable(context: Context): Boolean {
    if (context.findActivity() == null) return false
    return runCatching {
        val biometricManager = context.getSystemService(BiometricManager::class.java) ?: return false
        biometricManager.canAuthenticate(archiveAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)
}

private fun authenticateArchiveAccess(
    context: Context,
    onSuccess: () -> Unit,
    onCanceled: () -> Unit,
    onError: () -> Unit
) {
    val activity = context.findActivity() ?: run {
        onError()
        return
    }

    runCatching {
        val cancellationSignal = CancellationSignal()
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle("본인 인증")
            .setSubtitle("아카이브를 열려면 인증이 필요합니다.")
            .setAllowedAuthenticators(archiveAuthenticators())
            .build()

        prompt.authenticate(
            cancellationSignal,
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    if (
                        errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED ||
                        errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED
                    ) {
                        onCanceled()
                    } else {
                        onError()
                    }
                }
            }
        )
    }.onFailure {
        onError()
    }
}

private suspend fun buildFusionDebugInfo(
    context: android.content.Context,
    dao: com.projectnuke.fusion.data.ChatDao,
    benchmarkDao: BenchmarkDao,
    currentConversationId: Long,
    modelName: String,
    modelPath: String?,
    accelerator: String,
    maxTokens: Int,
    topK: Int,
    topP: Float,
    temperature: Float,
    reasoningBudgetTokens: Int,
    reasoningEnabled: Boolean,
    webSearchEnabled: Boolean,
    speculativeEnabled: Boolean
): String {
    val appInfo = getAppInfoSummary(context)
    val latestBenchmark = runCatching { benchmarkDao.getLatest() }.getOrNull()
    val socInfo = collectFusionSocInfo()

    val modelFile = modelPath?.let { File(it) }
    val modelExists = modelFile?.exists() == true
    val modelSize = if (modelExists) formatBytes(modelFile?.length() ?: 0L) else "unknown"

    val attachmentDir = AttachmentStorageManager.getAttachmentDirectory(context)
    val attachmentStats = runCatching {
        AttachmentStorageManager.calculateAttachmentStorageStats(context, dao)
    }.getOrNull()

    val messageCount = if (currentConversationId > 0L) {
        runCatching { dao.getMessageCountForConversation(currentConversationId) }.getOrNull()
    } else null
    val totalConversations = runCatching { dao.getConversationCount() }.getOrNull()
    val archivedConversations = runCatching { dao.getArchivedConversationCount() }.getOrNull()

    return buildString {
        appendLine("Fusion Debug Info")
        appendLine("-----------------")
        appendLine("Package: ${context.packageName}")
        appendLine("App version: ${appInfo.versionName} (${appInfo.versionCode})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Hardware: ${socInfo.hardware.ifBlank { "unknown" }}")
        appendLine("Board: ${socInfo.board.ifBlank { "unknown" }}")
        appendLine("SoC manufacturer: ${socInfo.socManufacturer.ifBlank { "unknown" }}")
        appendLine("SoC model: ${socInfo.socModel.ifBlank { "unknown" }}")
        appendLine("Detected SoC vendor: ${socInfo.detectedSocVendor.name}")
        appendLine()
        appendLine("Model")
        appendLine("- Name: $modelName")
        appendLine("- Path: ${modelPath ?: "none"}")
        appendLine("- Exists: $modelExists")
        appendLine("- Size: $modelSize")
        appendLine()
        appendLine("Generation Settings")
        appendLine("- Accelerator: $accelerator")
        appendLine("- Max tokens: $maxTokens")
        appendLine("- TopK: $topK")
        appendLine("- TopP: $topP")
        appendLine("- Temperature: $temperature")
        appendLine("- Reasoning budget tokens: $reasoningBudgetTokens")
        appendLine("- Reasoning enabled: $reasoningEnabled")
        appendLine("- Web search enabled: $webSearchEnabled")
        appendLine("- MTP enabled/requested: $speculativeEnabled")
        appendLine("- MTP status: ${if (speculativeEnabled) "requested" else "off"}")
        appendLine()
        appendLine("Runtime")
        appendLine("- Latest status: unavailable")
        appendLine("- Latest metrics: unavailable")
        appendLine("- Latest runtime error: unavailable")
        appendLine("- Streaming state: unavailable")
        appendLine("- Web search pipeline: enabled")
        appendLine()
        appendLine("Latest Benchmark")
        if (latestBenchmark != null) {
            appendLine("- Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(latestBenchmark.createdAt))}")
            appendLine("- Model: ${latestBenchmark.modelName}")
            appendLine("- Accelerator: ${latestBenchmark.actualBackend ?: latestBenchmark.accelerator}")
            appendLine("- MTP: ${latestBenchmark.mtpStatus}")
            appendLine("- Total tok/s: ${latestBenchmark.totalTokensPerSecond}")
            appendLine("- Decode tok/s: ${latestBenchmark.decodeTokensPerSecond ?: "unknown"}")
            appendLine("- Success: ${latestBenchmark.success}")
        } else {
            appendLine("- None")
        }
        appendLine()
        appendLine("Storage")
        appendLine("- Attachment dir: ${attachmentDir.absolutePath}")
        appendLine("- Attachment files: ${attachmentStats?.totalFiles ?: "unknown"}")
        appendLine("- Attachment total size: ${attachmentStats?.let { formatBytes(it.totalBytes) } ?: "unknown"}")
        appendLine()
        appendLine("Database")
        appendLine("- Current conversation id: $currentConversationId")
        appendLine("- Messages in current conversation: ${messageCount ?: "unknown"}")
        appendLine("- Total conversations: ${totalConversations ?: "unknown"}")
        appendLine("- Archived conversations: ${archivedConversations ?: "unknown"}")
    }.trimEnd()
}

@Composable
private fun rememberAppInfo(context: Context): AppInfoSummary {
    return remember(context) { getAppInfoSummary(context) }
}

private fun getAppInfoSummary(context: Context): AppInfoSummary {
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

    val versionName = packageInfo?.versionName ?: "unknown"
    val versionCode = if (packageInfo != null) {
        if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode.toString()
        else @Suppress("DEPRECATION") packageInfo.versionCode.toString()
    } else {
        "unknown"
    }

    return AppInfoSummary(versionName = versionName, versionCode = versionCode)
}
