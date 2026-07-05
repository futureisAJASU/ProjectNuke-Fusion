package com.projectnuke.fusion.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectnuke.fusion.ai.ui.AiProviderSettingsScreen
import com.projectnuke.fusion.data.AppDatabase
import com.projectnuke.fusion.data.BenchmarkDao
import com.projectnuke.fusion.data.ConversationEntity
import com.projectnuke.fusion.data.MessageEntity
import com.projectnuke.fusion.data.escapeSqlLikeQuery
import com.projectnuke.fusion.util.AttachmentStorageManager
import com.projectnuke.fusion.util.AttachmentStorageStats
import com.projectnuke.fusion.util.collectFusionSocInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.widthIn

private const val PrefArchiveLockEnabled = "archive_lock_enabled"
private const val FUSION_GITHUB_REPO_URL =
    "https://github.com/futureisAJASU/ProjectNuke-Fusion"

private fun openFusionGithubUrl(context: Context) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(FUSION_GITHUB_REPO_URL)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "GitHub 페이지를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}
private const val FUSION_GITHUB_ISSUES_URL = "https://github.com/futureisAJASU/ProjectNuke-Fusion/issues"

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
    onOpenAdvancedSettings: (() -> Unit)? = null,
    openBenchmarkRequest: Int = 0,
    benchmarkRequestModelFilter: String? = null,
    benchmarkRequestOpenHistory: Boolean = false
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
    var showDeveloperLogDialog by remember { mutableStateOf(false) }
    var showStatusDashboardDialog by remember { mutableStateOf(false) }
    var showFusionHealthDialog by remember { mutableStateOf(false) }
    var showDeviceInfoDialog by remember { mutableStateOf(false) }
    var showReleaseChecklistDialog by remember { mutableStateOf(false) }
    var showPromptPresetsDialog by remember { mutableStateOf(false) }
    var showExperimentNotesDialog by remember { mutableStateOf(false) }
    var showDeveloperModeDialog by remember { mutableStateOf(false) }
    var showTroubleshootingGuideDialog by remember { mutableStateOf(false) }
    var showPrivacyDataGuideDialog by remember { mutableStateOf(false) }
    var showMemoryManagerDialog by remember { mutableStateOf(false) }
    var showModelAbTestLab by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var attachmentStorageStats by remember { mutableStateOf<AttachmentStorageStats?>(null) }
    var attachmentStorageLoading by remember { mutableStateOf(false) }
    var archiveLockEnabled by remember { mutableStateOf(prefs.getBoolean(PrefArchiveLockEnabled, false)) }
    var archiveUnlockedForSession by remember { mutableStateOf(false) }
    var pendingBenchmarkModelFilter by remember { mutableStateOf<String?>(null) }
    var pendingBenchmarkOpenHistory by remember { mutableStateOf(false) }
    var showChatMarkdownExportPicker by remember { mutableStateOf(false) }
    var includeArchivedInMarkdownExport by remember { mutableStateOf(false) }
    var markdownExportSearchQuery by remember { mutableStateOf("") }
    var pendingChatMarkdownExport by remember { mutableStateOf<String?>(null) }
    var showSettingsBackupDialog by remember { mutableStateOf(false) }
    var pendingSettingsRestoreJson by remember { mutableStateOf<String?>(null) }
    var showModelCompatibilityGuide by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAiProviderSettingsDialog by remember { mutableStateOf(false) }
    var appLanguage by remember { mutableStateOf(getFusionAppLanguage(context)) }
    var developerModeEnabled by remember { mutableStateOf(isFusionDeveloperModeEnabled(context)) }

    LaunchedEffect(isDrawerOpen) {
        if (!isDrawerOpen) {
            archiveUnlockedForSession = false
        }
    }
    LaunchedEffect(openBenchmarkRequest) {
        if (openBenchmarkRequest > 0) {
            page = SidebarPage.BENCHMARK
            archiveUnlockedForSession = false
            pendingBenchmarkModelFilter = benchmarkRequestModelFilter
            pendingBenchmarkOpenHistory = benchmarkRequestOpenHistory
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
        else dao.observeConversationIdsMatchingMessages(escapeSqlLikeQuery(trimmedSearchQuery))
    }.collectAsState(initial = emptyList())

    val filteredConversations = remember(conversations, trimmedSearchQuery, matchingMessageConversationIds) {
        if (trimmedSearchQuery.isBlank()) {
            conversations
        } else {
            val ids = matchingMessageConversationIds.toSet()
            conversations.filter { it.title.contains(trimmedSearchQuery, true) || it.id in ids }
        }
    }
    val chatMarkdownExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        val markdown = pendingChatMarkdownExport
        if (uri == null || markdown.isNullOrBlank()) {
            pendingChatMarkdownExport = null
            return@rememberLauncherForActivityResult
        }
        runCatching {
            val stream = context.contentResolver.openOutputStream(uri)
                ?: error("Unable to open export target")
            stream.bufferedWriter().use { it.write(markdown) }
        }.onSuccess {
            Toast.makeText(context, "채팅 Markdown을 내보냈습니다.", Toast.LENGTH_SHORT).show()
        }.onFailure {
            clipboard.setText(AnnotatedString(markdown))
            Toast.makeText(context, "채팅 Markdown을 클립보드에 복사했습니다.", Toast.LENGTH_SHORT).show()
        }
        pendingChatMarkdownExport = null
    }
    val settingsBackupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val payload = buildSettingsBackupJson(context, prefs)
        runCatching {
            val stream = context.contentResolver.openOutputStream(uri)
                ?: error("Unable to open export target")
            stream.bufferedWriter().use { it.write(payload) }
        }.onSuccess {
            Toast.makeText(context, "설정을 내보냈습니다.", Toast.LENGTH_SHORT).show()
            Log.d("FusionModelSelect", "settings_export schema=1 keys=settings,modelLibrary success=true")
        }.onFailure {
            Toast.makeText(context, "설정 파일을 쓸 수 없습니다.", Toast.LENGTH_SHORT).show()
            Log.d("FusionModelSelect", "settings_export schema=1 keys=settings,modelLibrary success=false")
        }
    }
    val settingsBackupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            Toast.makeText(context, "설정 파일을 읽을 수 없습니다.", Toast.LENGTH_SHORT).show()
        } else {
            pendingSettingsRestoreJson = text
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
            FusionBenchmarkScreen(
                onBack = { leaveArchive() },
                initialShowHistory = pendingBenchmarkOpenHistory,
                initialHistoryModelFilter = pendingBenchmarkModelFilter
            )
        }
        return
    }

    if (showModelAbTestLab) {
        ModelAbTestLabScreen(onBack = { showModelAbTestLab = false })
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

                    item {
                        DrawerSettingActionRow(
                            "외부 AI API 설정",
                            "OpenAI 호환 API 키와 Base URL을 기기 내에 저장합니다."
                        ) {
                            showAiProviderSettingsDialog = true
                        }
                    }

                    item { DrawerSectionTitle("모델") }
                    item {
                        DrawerSettingActionRow("모델 라이브러리", "모델을 선택합니다.") {
                            if (onOpenModelLibrary != null) onOpenModelLibrary()
                            else Toast.makeText(context, "아직 준비 중입니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    item {
                        DrawerSettingActionRow(
                            "모델 호환성 가이드",
                            "모델군별 특징과 실행 기준을 확인합니다."
                        ) {
                            showModelCompatibilityGuide = true
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
                        DrawerSettingActionRow("설정 백업 및 복원", "모델, 생성 옵션, 모델 라이브러리 설정을 백업하거나 복원합니다.") {
                            showSettingsBackupDialog = true
                        }
                    }
                    item {
                        DrawerSettingActionRow("채팅 Markdown 내보내기", "선택한 채팅을 Markdown 파일로 저장합니다.") {
                            showChatMarkdownExportPicker = true
                        }
                    }
                    item {
                        DrawerSettingActionRow("메모리 관리", "저장된 메모리와 대화 요약을 확인하고 관리합니다.") {
                            showMemoryManagerDialog = true
                        }
                    }

                    item { Spacer(modifier = Modifier.height(6.dp)); DrawerSectionTitle("실험실") }
                    item { DrawerSettingActionRow("벤치마크", "TTFT, 토큰 속도, 메모리 사용량을 측정합니다.") { page = SidebarPage.BENCHMARK } }
                    item {
                        DrawerSettingActionRow("모델 A/B 테스트", "같은 프롬프트로 모델과 설정을 비교합니다.") {
                            showModelAbTestLab = true
                        }
                    }
                    item {
                        DrawerSettingActionRow("프롬프트 프리셋", "자주 쓰는 요청 문구를 확인하고 복사합니다.") {
                            showPromptPresetsDialog = true
                        }
                    }
                    item {
                        DrawerSettingActionRow("실험 노트", "모델 테스트와 벤치마크 메모를 기록합니다.") {
                            showExperimentNotesDialog = true
                        }
                    }
                    item { DrawerSettingActionRow("Prompt Lab", "시스템 프롬프트와 응답 스타일을 테스트합니다.") { page = SidebarPage.PROMPT_LAB } }
                    item { DrawerSettingActionRow("에이전트 모드", "기기 제어 실험 기능입니다.") { Toast.makeText(context, "아직 준비 중입니다.", Toast.LENGTH_SHORT).show() } }

                    item { Spacer(modifier = Modifier.height(6.dp)); DrawerSectionTitle("앱 정보") }
                    item {
                        DrawerSettingActionRow("사용 가이드", "Fusion의 주요 기능과 사용 방법을 확인합니다.") {
                            showHelpDialog = true
                        }
                    }
                    item {
                        DrawerSettingActionRow("개인정보 및 데이터 안내", "Fusion이 저장하거나 내보내는 데이터를 확인합니다.") {
                            showPrivacyDataGuideDialog = true
                        }
                    }
                    item {
                        DrawerSettingActionRow("GitHub 이슈 제보", "버그, 개선 요청, 기능 제안을 GitHub Issues에 남깁니다.") {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(FUSION_GITHUB_ISSUES_URL)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }.onFailure {
                                Toast.makeText(context, "GitHub 이슈 페이지를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    item {
                        DrawerSettingActionRow("언어(Language)", getFusionAppLanguageLabel(appLanguage)) {
                            showLanguageDialog = true
                        }
                    }
                    item {
                        DrawerSettingActionRow(
                            title = "앱 정보",
                            subtitle = "버전, 모델 상태, 데이터 정보를 확인합니다.",
                            modifier = Modifier.pointerInput(developerModeEnabled) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    val releasedEarly = withTimeoutOrNull(7_000L) {
                                        waitForUpOrCancellation()
                                    }
                                    if (releasedEarly == null && !developerModeEnabled) {
                                        setFusionDeveloperModeEnabled(context, true)
                                        developerModeEnabled = true
                                        Toast.makeText(context, "개발자 모드를 사용하도록 설정했습니다.", Toast.LENGTH_SHORT).show()
                                        waitForUpOrCancellation()
                                    }
                                }
                            }
                        ) {
                            showAppInfoDialog = true
                        }
                    }
                    item {
                        DrawerSettingActionRow("Fusion 상태 점검", "모델, 설정, 저장소, 메모리, 권한 상태를 확인합니다.") {
                            showFusionHealthDialog = true
                        }
                    }
                    item {
                        DrawerSettingActionRow("상태 대시보드", "현재 모델, 메모리, 벤치마크, 앱 상태를 한눈에 확인합니다.") {
                            showStatusDashboardDialog = true
                        }
                    }
                    item {
                        DrawerSettingActionRow("기기 정보", "기기 메모리와 AP 정보를 확인합니다.") {
                            showDeviceInfoDialog = true
                        }
                    }
                    item {
                        DrawerSettingActionRow("문제 해결 가이드", "모델 실행, 메모리, 설치, 로그 문제 해결 방법을 확인합니다.") {
                            showTroubleshootingGuideDialog = true
                        }
                    }
                    item {
                        DrawerSettingActionRow("업데이트 기록", "최근 추가된 기능과 변경사항을 확인합니다.") {
                            showReleaseNotesDialog = true
                        }
                    }
                    item {
                        DrawerSettingActionRow("개발자 로그", "최근 모델, 메모리, 오류, 벤치마크 상태를 확인합니다.") {
                            showDeveloperLogDialog = true
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
                    if (developerModeEnabled) {
                        item { DrawerSectionTitle("개발자 항목") }
                        item {
                            DrawerSettingActionRow("릴리즈 체크리스트", "빌드와 실기기 테스트 항목을 확인합니다.") {
                                showReleaseChecklistDialog = true
                            }
                        }
                        item {
                            DrawerSettingActionRow("개발자 모드", "릴리즈 체크리스트를 표시합니다.") {
                                showDeveloperModeDialog = true
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

    pendingSettingsRestoreJson?.let { raw ->
        AlertDialog(
            onDismissRequest = { pendingSettingsRestoreJson = null },
            title = { Text("설정을 복원하시겠습니까?") },
            text = { Text("현재 설정이 백업 파일의 값으로 변경됩니다. 채팅 기록과 모델 파일은 삭제되지 않습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    when (restoreSettingsBackupJson(prefs, raw)) {
                        SettingsRestoreResult.Success -> {
                            Toast.makeText(context, "설정을 복원했습니다.", Toast.LENGTH_SHORT).show()
                            Toast.makeText(context, "일부 설정은 화면을 다시 열면 반영됩니다.", Toast.LENGTH_SHORT).show()
                        }
                        SettingsRestoreResult.ModelPathMissing -> {
                            Toast.makeText(context, "설정을 복원했습니다.", Toast.LENGTH_SHORT).show()
                            Toast.makeText(context, "백업의 모델 파일을 찾을 수 없어 현재 모델을 유지했습니다.", Toast.LENGTH_SHORT).show()
                        }
                        SettingsRestoreResult.InvalidJson -> {
                            Toast.makeText(context, "설정 파일을 읽을 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                        SettingsRestoreResult.UnsupportedSchema -> {
                            Toast.makeText(context, "지원하지 않는 설정 백업 형식입니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    reasoningEnabled = prefs.getBoolean("reasoning_enabled", reasoningEnabled)
                    webSearchEnabled = prefs.getBoolean("web_search_enabled", webSearchEnabled)
                    speculativeEnabled = prefs.getBoolean("speculative_decoding_enabled", speculativeEnabled)
                    pendingSettingsRestoreJson = null
                }) { Text("복원", color = DrawerAccentBlue) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSettingsRestoreJson = null }) {
                    Text("취소", color = DrawerTextSecondary)
                }
            },
            containerColor = DrawerPanelBg,
            titleContentColor = DrawerTextPrimary,
            textContentColor = DrawerTextPrimary
        )
    }

    if (showSettingsBackupDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsBackupDialog = false },
            title = { Text("설정 백업 및 복원") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("채팅 기록과 모델 파일은 포함되지 않습니다.", color = DrawerTextSecondary, fontSize = 12.sp)
                    Text("모델 메모가 백업에 포함될 수 있습니다.", color = DrawerTextSecondary, fontSize = 12.sp)
                    Text("저장된 메모리와 대화 요약 내용은 설정 백업에 포함되지 않습니다.", color = DrawerTextSecondary, fontSize = 12.sp)
                    Text("A/B 테스트 기록은 설정 백업에 포함되지 않습니다.", color = DrawerTextSecondary, fontSize = 12.sp)
                    Text("실험 노트 내용은 설정 백업에 포함되지 않습니다.", color = DrawerTextSecondary, fontSize = 12.sp)
                    DrawerSettingActionRow("설정 내보내기", "JSON 파일로 저장합니다.") {
                        settingsBackupExportLauncher.launch("fusion-settings-backup.json")
                    }
                    DrawerSettingActionRow("설정 가져오기", "백업 JSON 파일에서 복원합니다.") {
                        settingsBackupImportLauncher.launch(arrayOf("application/json", "text/*"))
                    }
                    DrawerSettingActionRow("설정 JSON 복사", "백업 JSON을 클립보드에 복사합니다.") {
                        clipboard.setText(AnnotatedString(buildSettingsBackupJson(context, prefs)))
                        Toast.makeText(context, "설정 JSON을 복사했습니다.", Toast.LENGTH_SHORT).show()
                        Log.d("FusionModelSelect", "settings_export schema=1 keys=settings,modelLibrary success=true")
                    }
                    DrawerSettingActionRow("클립보드에서 복원", "클립보드의 백업 JSON을 복원합니다.") {
                        val raw = clipboard.getText()?.text?.toString()
                        if (raw.isNullOrBlank()) {
                            Toast.makeText(context, "설정 파일을 읽을 수 없습니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            pendingSettingsRestoreJson = raw
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsBackupDialog = false }) {
                    Text("확인", color = DrawerAccentBlue)
                }
            },
            containerColor = DrawerPanelBg,
            titleContentColor = DrawerTextPrimary,
            textContentColor = DrawerTextPrimary
        )
    }

    if (showChatMarkdownExportPicker) {
        val exportConversations = remember(
            conversations,
            archivedConversations,
            includeArchivedInMarkdownExport,
            markdownExportSearchQuery
        ) {
            val base = if (includeArchivedInMarkdownExport) conversations + archivedConversations else conversations
            val query = markdownExportSearchQuery.trim()
            if (query.isBlank()) base else base.filter { it.title.contains(query, ignoreCase = true) }
        }
        AlertDialog(
            onDismissRequest = {
                showChatMarkdownExportPicker = false
                markdownExportSearchQuery = ""
            },
            title = { Text("내보낼 채팅 선택") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Markdown 파일로 저장할 채팅을 선택해 주세요.", color = DrawerTextSecondary, fontSize = 12.sp)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DrawerCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            if (markdownExportSearchQuery.isBlank()) {
                                Text("채팅을 검색합니다.", color = DrawerTextSecondary, fontSize = 13.sp)
                            }
                            BasicTextField(
                                value = markdownExportSearchQuery,
                                onValueChange = { markdownExportSearchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(color = DrawerTextPrimary, fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("아카이브 포함", color = DrawerTextPrimary, fontSize = 12.sp)
                        Switch(
                            checked = includeArchivedInMarkdownExport,
                            onCheckedChange = { includeArchivedInMarkdownExport = it }
                        )
                    }
                    if (exportConversations.isEmpty()) {
                        Text("내보낼 채팅이 없습니다.", color = DrawerTextSecondary, fontSize = 12.sp)
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(exportConversations, key = { it.id }) { conversation ->
                                Surface(
                                    color = DrawerCardBg,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                val messages = runCatching {
                                                    dao.getMessagesForConversation(conversation.id)
                                                }.getOrElse {
                                                    Toast.makeText(context, "채팅을 내보낼 수 없습니다.", Toast.LENGTH_SHORT).show()
                                                    return@launch
                                                }
                                                if (messages.isEmpty()) {
                                                    Toast.makeText(context, "이 채팅에는 내보낼 메시지가 없습니다.", Toast.LENGTH_SHORT).show()
                                                    return@launch
                                                }
                                                val title = conversation.title.ifBlank { "새 대화" }
                                                pendingChatMarkdownExport = buildChatExportMarkdown(context, title, messages)
                                                chatMarkdownExportLauncher.launch("fusion-chat-${safeMarkdownExportTitle(title)}.md")
                                                showChatMarkdownExportPicker = false
                                                markdownExportSearchQuery = ""
                                            }
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Text(
                                            text = exportConversationTitle(conversation),
                                            color = DrawerTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(conversation.updatedAt)),
                                            color = DrawerTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showChatMarkdownExportPicker = false
                    markdownExportSearchQuery = ""
                }) {
                    Text("취소")
                }
            },
            containerColor = DrawerPanelBg,
            titleContentColor = DrawerTextPrimary,
            textContentColor = DrawerTextPrimary
        )
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
        ReleaseNotesDialog(
            context = context,
            clipboard = clipboard,
            onDismiss = { showReleaseNotesDialog = false }
        )
    }

    if (showDeveloperLogDialog) {
        val benchmarkResults by db.benchmarkDao().observeAll().collectAsState(initial = emptyList())
        DeveloperLogDialog(
            context = context,
            prefs = prefs,
            clipboard = clipboard,
            benchmarkResults = benchmarkResults,
            onDismiss = { showDeveloperLogDialog = false }
        )
    }

    if (showStatusDashboardDialog) {
        val benchmarkResults by db.benchmarkDao().observeAll().collectAsState(initial = emptyList())
        FusionStatusDashboardDialog(
            context = context,
            prefs = prefs,
            clipboard = clipboard,
            benchmarkResults = benchmarkResults,
            onDismiss = { showStatusDashboardDialog = false }
        )
    }

    if (showFusionHealthDialog) {
        FusionHealthCheckDialog(
            context = context,
            prefs = prefs,
            db = db,
            clipboard = clipboard,
            onDismiss = { showFusionHealthDialog = false },
            onOpenModelLibrary = onOpenModelLibrary,
            onOpenAdvancedSettings = onOpenAdvancedSettings
        )
    }

    if (showDeviceInfoDialog) {
        FusionDeviceInfoDialog(
            context = context,
            clipboard = clipboard,
            onDismiss = { showDeviceInfoDialog = false }
        )
    }

    if (showReleaseChecklistDialog) {
        FusionReleaseChecklistDialog(
            context = context,
            clipboard = clipboard,
            onDismiss = { showReleaseChecklistDialog = false }
        )
    }

    if (showPromptPresetsDialog) {
        FusionPromptPresetsDialog(
            context = context,
            clipboard = clipboard,
            onDismiss = { showPromptPresetsDialog = false }
        )
    }

    if (showExperimentNotesDialog) {
        FusionExperimentNotesDialog(
            context = context,
            clipboard = clipboard,
            onDismiss = { showExperimentNotesDialog = false }
        )
    }

    if (showTroubleshootingGuideDialog) {
        FusionTroubleshootingGuideDialog(
            context = context,
            clipboard = clipboard,
            onDismiss = { showTroubleshootingGuideDialog = false }
        )
    }

    if (showPrivacyDataGuideDialog) {
        FusionPrivacyDataGuideDialog(
            context = context,
            clipboard = clipboard,
            onDismiss = { showPrivacyDataGuideDialog = false }
        )
    }

    if (showMemoryManagerDialog) {
        MemoryManagerDialog(
            context = context,
            clipboard = clipboard,
            conversations = conversations,
            archivedConversations = archivedConversations,
            onDismiss = { showMemoryManagerDialog = false }
        )
    }

    if (showHelpDialog) {
        FusionHelpScreen(
            context = context,
            clipboard = clipboard,
            onDismiss = { showHelpDialog = false }
        )
    }

    if (showLanguageDialog) {
        FusionLanguageSettingsDialog(
            context = context,
            currentLanguage = appLanguage,
            onSelect = { language ->
                applyFusionAppLanguageIfSupported(context, language)
                appLanguage = getFusionAppLanguage(context)
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showAiProviderSettingsDialog) {
        AiProviderSettingsScreen(
            onDismiss = { showAiProviderSettingsDialog = false }
        )
    }

    if (showDeveloperModeDialog) {
        AlertDialog(
            onDismissRequest = { showDeveloperModeDialog = false },
            title = { Text("개발자 모드") },
            text = { Text("개발자 모드는 내부 테스트 항목을 표시하기 위한 UI 기능입니다. 보안 기능이 아닙니다.") },
            confirmButton = {
                TextButton(onClick = {
                    setFusionDeveloperModeEnabled(context, false)
                    developerModeEnabled = false
                    showDeveloperModeDialog = false
                    showReleaseChecklistDialog = false
                    Toast.makeText(context, "개발자 모드를 사용하지 않도록 설정했습니다.", Toast.LENGTH_SHORT).show()
                }) {
                    Text("끄기")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeveloperModeDialog = false }) {
                    Text("닫기")
                }
            },
            containerColor = DrawerPanelBg,
            titleContentColor = DrawerTextPrimary,
            textContentColor = DrawerTextPrimary
        )
    }

    if (showModelCompatibilityGuide) {
        ModelCompatibilityGuideDialog(
            onDismiss = { showModelCompatibilityGuide = false }
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
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DrawerCircleButton("←", onClose)

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = "Fusion",
                color = DrawerTextPrimary,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "Local AI Workspace",
                color = DrawerTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier.wrapContentWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawerGitHubPill(
                onClick = { openFusionGithubUrl(context) }
            )
            DrawerCircleButton("+", onNewChat)
        }
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
private fun DrawerSettingActionRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable { onClick() },
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
                    DropdownMenuItem(text = { Text(pinMenuLabel, color = DrawerTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = onTogglePinned)
                    DropdownMenuItem(text = { Text("이름 변경", color = DrawerTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = onRename)
                    DropdownMenuItem(text = { Text("제목 다시 생성", color = DrawerTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = onRegenerateTitle)
                    DropdownMenuItem(text = { Text(archiveMenuLabel, color = DrawerTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = onArchive)
                    DropdownMenuItem(text = { Text("삭제", color = Color(0xFFFF7A7A), maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = onDelete)
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

@Composable
private fun DrawerGitHubPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(28.dp)

    Row(
        modifier = modifier
            .wrapContentWidth()
            .height(34.dp)
            .clip(shape)
            .background(Color(0xFF17191F))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "GitHub",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = "↗",
            color = Color.White.copy(alpha = 0.9f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1
        )
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
    context: android.content.Context,
    title: String,
    messages: List<MessageEntity>
): String {
    val dateText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(Date())

    val body = buildString {
        appendLine("# $title")
        appendLine()
        appendLine("- 내보낸 날짜: $dateText")
        appendLine("- 메시지 수: ${messages.size}")
        appendLine()
        val conversationId = messages.firstOrNull()?.conversationId ?: 0L
        activeTimelineMessages(
            messages,
            loadResponseVersionState(context, conversationId)
        ).forEach { message ->
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

private fun exportConversationTitle(conversation: ConversationEntity): String {
    return buildString {
        append(conversation.title.ifBlank { "새 대화" })
        if (conversation.isPinned) append(" · 고정")
        if (conversation.isArchived) append(" · 아카이브")
    }
}

private fun safeMarkdownExportTitle(title: String): String {
    val normalized = title.trim().ifBlank { "chat" }
        .replace(Regex("""[\\/:*?"<>|]"""), "-")
        .replace(Regex("""\s+"""), "-")
        .trim('-')
    return normalized.take(48).ifBlank { "chat" }
}

private fun stripHiddenFusionBlocks(content: String): String {
    val withAttachmentPlaceholders = replaceAttachmentTagsWithPlaceholders(content)
    return withAttachmentPlaceholders
        .replace(Regex("""(?is)<fusion_metrics>.*?</fusion_metrics>"""), "")
        .replace(Regex("""(?is)<fusion_thinking>.*?</fusion_thinking>"""), "")
        .replace(Regex("""(?is)</?fusion_answer>"""), "")
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
