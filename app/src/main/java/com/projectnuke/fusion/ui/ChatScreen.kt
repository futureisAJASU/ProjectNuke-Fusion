package com.projectnuke.fusion.ui
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectnuke.fusion.data.AppDatabase
import com.projectnuke.fusion.data.ConversationEntity
import com.projectnuke.fusion.data.MessageEntity
import com.projectnuke.fusion.llm.LiteRtLlmEngine
import com.projectnuke.fusion.model.AcceleratorMode
import com.projectnuke.fusion.model.ChatMessage
import com.projectnuke.fusion.model.GenerationSettings
import com.projectnuke.fusion.search.FusionWebSearch
import com.projectnuke.fusion.search.SearchIntent
import com.projectnuke.fusion.search.toStructuredContext
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.content.ActivityNotFoundException
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.FileProvider
import java.net.HttpURLConnection
private val BlackBg = Color(0xFF000000)
private val PanelBg = Color(0xFF171717)
private val MenuBg = Color(0xFF202020)
private val BubbleBg = Color(0xFF2A2A2A)
private val LineColor = Color(0xFF2B2B2B)
private val TextPrimary = Color(0xFFF5F5F5)
private val TextSecondary = Color(0xFF9E9E9E)
private val AccentBlue = Color(0xFF9FD0FF)
private val DangerRed = Color(0xFFFF7A7A)
private data class LocalModel(
    val name: String,
    val fileName: String,
    val downloadUrl: String? = null,
    val customPath: String? = null
)
private data class ParsedAssistantOutput(
    val thinking: String?,
    val answer: String
)
private data class LocalAttachment(
    val name: String,
    val mimeType: String,
    val localPath: String
)

private data class ParsedMessageContent(
    val body: String,
    val attachments: List<LocalAttachment>
)

private data class FusionMetricsSplit(
    val content: String,
    val metricsLine: String?
)
@Composable
fun ChatScreen(
    conversationId: Long,
    onConversationCreated: (Long) -> Unit,
    onOpenList: () -> Unit,
    onNewChat: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val dao = remember { db.chatDao() }
    var input by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var streamingAssistantText by remember { mutableStateOf<String?>(null) }
    var streamingMetricsLine by remember { mutableStateOf<String?>(null) }

    var chatMenuExpanded by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showAdvancedSettingsDialog by remember { mutableStateOf(false) }

    var webSearchEnabled by remember { mutableStateOf(false) }
    var reasoningEnabled by remember { mutableStateOf(false) }

    var generationSettings by remember { mutableStateOf(GenerationSettings()) }
    val pendingAttachments = remember { mutableStateListOf<LocalAttachment>() }

    val builtInModels = remember {
        listOf(
            LocalModel(
                name = "Gemma 4 E2B-it",
                fileName = "gemma-4-E2B-it.litertlm",
                downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true"
            ),
            LocalModel(
                name = "Gemma 4 E4B-it",
                fileName = "gemma-4-E4B-it.litertlm",
                downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=true"
            )
        )
    }

    val customModels = remember { mutableStateListOf<LocalModel>() }

    var selectedModel by remember { mutableStateOf("Gemma 4 E2B-it") }
    var selectedModelPath by remember { mutableStateOf<String?>(null) }

    var pendingDownloadModel by remember { mutableStateOf<LocalModel?>(null) }
    var downloadingModelName by remember { mutableStateOf<String?>(null) }
    var downloadProgressPercent by remember { mutableStateOf<Int?>(null) }
    var generationStatus by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val engine = remember { LiteRtLlmEngine(context.applicationContext) }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }

            val displayName = getDisplayNameFromUri(context, uri)
            Toast.makeText(context, "커스텀 모델 복사 중...", Toast.LENGTH_SHORT).show()

            scope.launch {
                val copiedFile = copyUriToModelFile(
                    context = context,
                    uri = uri,
                    displayName = displayName
                )

                if (copiedFile == null) {
                    Toast.makeText(context, "커스텀 모델 복사 실패", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val model = LocalModel(
                    name = "Custom: $displayName",
                    fileName = displayName,
                    customPath = copiedFile.absolutePath
                )

                if (customModels.none { it.name == model.name }) {
                    customModels.add(model)
                }

                selectedModel = model.name
                selectedModelPath = copiedFile.absolutePath

                Toast.makeText(context, "커스텀 모델 등록: $displayName", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        Toast.makeText(context, "첨부 파일 복사 중...", Toast.LENGTH_SHORT).show()

        scope.launch {
            uris.take(5).forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                }

                val displayName = getDisplayNameFromUri(context, uri)
                val mimeType = context.contentResolver.getType(uri)
                    ?: "application/octet-stream"

                val copiedFile = copyUriToAttachmentFile(
                    context = context,
                    uri = uri,
                    displayName = displayName
                )

                if (copiedFile != null) {
                    pendingAttachments.add(
                        LocalAttachment(
                            name = displayName,
                            mimeType = mimeType,
                            localPath = copiedFile.absolutePath
                        )
                    )
                }
            }

            Toast.makeText(context, "첨부 완료", Toast.LENGTH_SHORT).show()
        }
    }

    val messageFlow = remember(conversationId) {
        if (conversationId == 0L) {
            flowOf(emptyList<MessageEntity>())
        } else {
            dao.observeMessages(conversationId)
        }
    }

    val messageEntities by messageFlow.collectAsState(initial = emptyList())

    val messages = messageEntities.map {
        ChatMessage(
            role = it.role,
            content = it.content
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BlackBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BlackBg)
                    .statusBarsPadding()
            ) {
                ChatTopBar(
                    onOpenList = onOpenList,
                    onComposeClick = onNewChat,
                    onChatMenuClick = { chatMenuExpanded = true },
                    chatMenuExpanded = chatMenuExpanded,
                    onDismissChatMenu = { chatMenuExpanded = false },
                    selectedModel = selectedModel,
                    reasoningEnabled = reasoningEnabled,
                    webSearchEnabled = webSearchEnabled,
                    onModelPillClick = {
                        showModelDialog = true
                    },
                    onChatOption = { option ->
                        chatMenuExpanded = false
                        Toast.makeText(context, "$option 기능은 나중에 연결할게", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BlackBg)
                    .navigationBarsPadding()
                    .imePadding(),
                contentAlignment = Alignment.BottomCenter
            ) {
                ChatInputBar(
                    value = input,
                    onValueChange = { input = it },
                    enabled = !isGenerating,
                    isGenerating = isGenerating,
                    pendingAttachments = pendingAttachments,
                    onRemoveAttachment = { attachment ->
                        pendingAttachments.remove(attachment)
                    },
                    onPlusClick = {
                        attachmentPickerLauncher.launch(
                            arrayOf(
                                "image/*",
                                "application/pdf",
                                "text/*",
                                "application/octet-stream"
                            )
                        )
                    },
                    onMicClick = {
                        Toast.makeText(context, "음성 입력은 나중에 붙이자", Toast.LENGTH_SHORT).show()
                    },
                    onVoiceModeClick = {
                        Toast.makeText(context, "보이스 모드는 나중에 붙이자", Toast.LENGTH_SHORT).show()
                    },
                    onSendClick = {
                        val userInput = input.trim()
                        val attachmentsToSend = pendingAttachments.toList()
                        val shouldUseWebSearch = webSearchEnabled || shouldAutoUseWebSearch(userInput)

                        if (userInput.isNotEmpty() || attachmentsToSend.isNotEmpty()) {
                            input = ""
                            pendingAttachments.clear()
                            isGenerating = true
                            streamingAssistantText = null
                            streamingMetricsLine = null
                            generationStatus = if (shouldUseWebSearch) "웹 검색 중..." else "모델 준비 중..."

                            val userMessageContent = buildMessageContentWithAttachments(
                                body = userInput,
                                attachments = attachmentsToSend
                            )

                            scope.launch {
                                var activeConversationId = conversationId

                                try {
                                    val now = System.currentTimeMillis()

                                    if (activeConversationId == 0L) {
                                        val title = userInput.take(24).ifBlank { "새 대화" }

                                        activeConversationId = dao.insertConversation(
                                            ConversationEntity(
                                                title = title,
                                                createdAt = now,
                                                updatedAt = now
                                            )
                                        )

                                        onConversationCreated(activeConversationId)
                                    }

                                    dao.insertMessage(
                                        MessageEntity(
                                            conversationId = activeConversationId,
                                            role = "user",
                                            content = userMessageContent,
                                            createdAt = now
                                        )
                                    )

                                    dao.updateConversationTime(activeConversationId, now)

                                    val previousUserMessage = messages
                                        .asReversed()
                                        .firstOrNull { it.role == "user" }
                                        ?.content
                                        ?.let { parseMessageAttachments(it).body }
                                        ?.trim()
                                        .orEmpty()

                                    val webSearchResult = if (shouldUseWebSearch) {
                                        generationStatus = "검색 의도 분석 중..."
                                        val searchIntent = FusionWebSearch.detectIntent(userInput)
                                        generationStatus = when (searchIntent) {
                                            SearchIntent.NEWS -> "뉴스 검색 중..."
                                            else -> "웹 검색 중..."
                                        }

                                        val response = FusionWebSearch.search(
                                            userInput = userInput,
                                            previousUserMessage = previousUserMessage
                                        )

                                        generationStatus = "검색 결과 ${response.results.size}개 정리 중..."
                                        response.toStructuredContext()
                                    } else {
                                        null
                                    }

                                    if (shouldUseWebSearch) {
                                        generationStatus = "검색 결과를 바탕으로 답변 생성 중..."
                                    }

                                    val mtpEnabledForRequest = resolveSpeculativeDecodingEnabled(
                                        modelName = selectedModel,
                                        settings = generationSettings
                                    )
                                    val requestSettings = generationSettings.copy(
                                        speculativeDecodingEnabled = mtpEnabledForRequest
                                    )

                                    val fusionSystemPrompt = buildFusionSystemPrompt(
                                        reasoningEnabled = reasoningEnabled,
                                        webSearchEnabled = shouldUseWebSearch,
                                        webContext = webSearchResult
                                    )

                                    val currentMessages = buildList<ChatMessage> {
                                        add(
                                            ChatMessage(
                                                role = "system",
                                                content = """
                FUSION_GENERATION_SETTINGS
                maxTokens=${requestSettings.maxTokens}
                topK=${requestSettings.topK}
                topP=${requestSettings.topP}
                temperature=${requestSettings.temperature}
                accelerator=${requestSettings.accelerator.name}
                reasoningBudgetTokens=${requestSettings.reasoningBudgetTokens}
                speculativeDecoding=${requestSettings.speculativeDecodingEnabled == true}
            """.trimIndent()
                                            )
                                        )

                                        add(
                                            ChatMessage(
                                                role = "system",
                                                content = fusionSystemPrompt
                                            )
                                        )

                                        selectedModelPath?.let { modelPath ->
                                            add(
                                                ChatMessage(
                                                    role = "system",
                                                    content = "FUSION_SELECTED_MODEL_PATH=$modelPath"
                                                )
                                            )
                                        }

                                        addAll(messages)

                                        val finalUserContent = buildFinalUserContent(
                                            body = userInput,
                                            attachments = attachmentsToSend,
                                            webSearchEnabled = shouldUseWebSearch,
                                            webSearchResult = webSearchResult
                                        )

                                        add(
                                            ChatMessage(
                                                role = "user",
                                                content = finalUserContent
                                            )
                                        )
                                    }

                                    val activeModelPath = selectedModelPath?.takeIf { File(it).exists() }
                                        ?: builtInModels
                                            .firstOrNull {
                                                it.name == selectedModel && isModelDownloaded(
                                                    context,
                                                    it
                                                )
                                            }
                                            ?.let { getModelFile(context, it).absolutePath }

                                    if (activeModelPath == null) {
                                        dao.insertMessage(
                                            MessageEntity(
                                                conversationId = activeConversationId,
                                                role = "assistant",
                                                content = "아직 사용할 모델이 없어. 위쪽 모델 칩에서 Gemma 모델을 다운로드하거나 커스텀 모델을 업로드해줘.",
                                                createdAt = System.currentTimeMillis()
                                            )
                                        )

                                        dao.updateConversationTime(
                                            activeConversationId,
                                            System.currentTimeMillis()
                                        )

                                        return@launch
                                    }

                                    generationStatus = if (mtpEnabledForRequest) {
                                        "MTP 가속 활성화됨"
                                    } else {
                                        "MTP 미지원 모델/런타임"
                                    }

                                    kotlinx.coroutines.delay(250)
                                    generationStatus = "모델 응답 생성 중..."

                                    val generationStartMs = SystemClock.elapsedRealtime()
                                    var firstTokenLatencyMs: Long? = null

                                    val rawReply = if (reasoningEnabled) {
                                        engine.generate(
                                            messages = currentMessages,
                                            modelPath = activeModelPath,
                                            settings = requestSettings
                                        )
                                    } else {
                                        val streamedOutput = StringBuilder()
                                        streamingAssistantText = ""
                                        generationStatus = "응답 생성 중..."

                                        engine.generateStreaming(
                                            messages = currentMessages,
                                            modelPath = activeModelPath,
                                            settings = requestSettings,
                                            onToken = { token ->
                                                if (firstTokenLatencyMs == null && token.isNotEmpty()) {
                                                    firstTokenLatencyMs = SystemClock.elapsedRealtime() - generationStartMs
                                                }
                                                streamedOutput.append(token)
                                                val visibleText = streamedOutput.toString()
                                                scope.launch {
                                                    if (isGenerating) {
                                                        streamingAssistantText = visibleText
                                                    }
                                                }
                                            }
                                        )
                                    }

                                    val totalGenerationMs = SystemClock.elapsedRealtime() - generationStartMs
                                    val metricsLine = buildFusionMetricsLine(
                                        modelName = shortModelName(selectedModel),
                                        acceleratorName = buildAcceleratorLabel(
                                            acceleratorName = requestSettings.accelerator.name,
                                            speculativeDecodingEnabled = mtpEnabledForRequest
                                        ),
                                        generatedText = rawReply,
                                        totalGenerationMs = totalGenerationMs,
                                        firstTokenLatencyMs = firstTokenLatencyMs
                                    )
                                    streamingMetricsLine = metricsLine

                                    generationStatus = "답변 저장 중..."

                                    dao.insertMessage(
                                        MessageEntity(
                                            conversationId = activeConversationId,
                                            role = "assistant",
                                            content = appendFusionMetrics(rawReply, metricsLine),
                                            createdAt = System.currentTimeMillis()
                                        )
                                    )

                                    dao.updateConversationTime(
                                        activeConversationId,
                                        System.currentTimeMillis()
                                    )
                                    pendingAttachments.clear()
                                } catch (e: Exception) {
                                    if (activeConversationId != 0L) {
                                        dao.insertMessage(
                                            MessageEntity(
                                                conversationId = activeConversationId,
                                                role = "assistant",
                                                content = "오류가 났어:\n${e.message ?: e::class.java.simpleName}",
                                                createdAt = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                } finally {
                                    generationStatus = null
                                    isGenerating = false
                                    streamingAssistantText = null
                                    streamingMetricsLine = null
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BlackBg)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            if (messageEntities.isEmpty() && !isGenerating) {
                EmptyChatBody()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(
                        items = messageEntities,
                        key = { it.id }
                    ) { message ->
                        when (message.role) {
                            "user" -> UserMessageBubble(message.content)
                            else -> AssistantMessage(
                                content = message.content,
                                createdAt = message.createdAt,
                                selectedModel = selectedModel,
                                webSearchEnabled = webSearchEnabled,
                                reasoningEnabled = reasoningEnabled,
                                onRetry = {
                                    Toast.makeText(
                                        context,
                                        "재시도 기능은 다음 단계에서 연결할게",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onBranch = {
                                    Toast.makeText(
                                        context,
                                        "새 채팅으로 가지치기 기능은 다음 단계에서 연결할게",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onToggleWebSearch = {
                                    webSearchEnabled = !webSearchEnabled
                                }
                            )
                        }
                    }

                    if (isGenerating) {
                        item {
                            val streamingText = streamingAssistantText
                            if (!reasoningEnabled && streamingText != null) {
                                AssistantMessage(
                                    content = streamingMetricsLine?.let {
                                        appendFusionMetrics(streamingText, it)
                                    } ?: streamingText,
                                    createdAt = System.currentTimeMillis(),
                                    selectedModel = selectedModel,
                                    webSearchEnabled = webSearchEnabled,
                                    reasoningEnabled = false,
                                    onRetry = {},
                                    onBranch = {},
                                    onToggleWebSearch = {}
                                )
                            } else {
                                ModelLoadingBubble(
                                    status = generationStatus ?: "모델 준비 중..."
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showModelDialog) {
        ModelSelectDialog(
            currentModel = selectedModel,
            builtInModels = builtInModels,
            customModels = customModels,
            downloadingModelName = downloadingModelName,
            downloadProgressPercent = downloadProgressPercent,
            reasoningEnabled = reasoningEnabled,
            webSearchEnabled = webSearchEnabled,
            generationSettings = generationSettings,
            isDownloaded = { model ->
                isModelDownloaded(context, model)
            },
            onDismiss = {
                showModelDialog = false
            },
            onSelect = { model ->
                when {
                    model.customPath != null && File(model.customPath).exists() -> {
                        selectedModel = model.name
                        selectedModelPath = model.customPath
                    }

                    isModelDownloaded(context, model) -> {
                        selectedModel = model.name
                        selectedModelPath = getModelFile(context, model).absolutePath
                    }

                    else -> {
                        pendingDownloadModel = model
                    }
                }
            },
            onUploadCustomModel = {
                modelPickerLauncher.launch(arrayOf("*/*"))
            },
            onOpenAdvancedSettings = {
                showAdvancedSettingsDialog = true
            },
            onToggleReasoning = {
                reasoningEnabled = !reasoningEnabled
            },
            onToggleWebSearch = {
                webSearchEnabled = !webSearchEnabled
            }
        )
    }

    pendingDownloadModel?.let { model ->
        DownloadModelDialog(
            model = model,
            onDismiss = {
                pendingDownloadModel = null
            },
            onDownload = {
                if (model.downloadUrl == null) {
                    Toast.makeText(
                        context,
                        "아직 다운로드 URL이 등록되지 않은 모델이야",
                        Toast.LENGTH_SHORT
                    ).show()
                    pendingDownloadModel = null
                    return@DownloadModelDialog
                }

                pendingDownloadModel = null
                downloadingModelName = model.name
                downloadProgressPercent = 0

                scope.launch {
                    val success = downloadModelFile(
                        context = context,
                        model = model,
                        onProgress = { percent ->
                            downloadProgressPercent = percent
                        }
                    )

                    downloadingModelName = null
                    downloadProgressPercent = null

                    if (success) {
                        selectedModel = model.name
                        selectedModelPath = getModelFile(context, model).absolutePath
                        Toast.makeText(context, "${model.name} 다운로드 완료", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "${model.name} 다운로드 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showAdvancedSettingsDialog) {
        AdvancedSettingsDialog(
            settings = generationSettings,
            selectedModel = selectedModel,
            onDismiss = {
                showAdvancedSettingsDialog = false
            },
            onApply = { newSettings ->
                generationSettings = newSettings
                showAdvancedSettingsDialog = false

                Toast.makeText(
                    context,
                    "고급 설정 적용됨: ${newSettings.accelerator.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

}
@Composable
private fun ChatTopBar(
    onOpenList: () -> Unit,
    onComposeClick: () -> Unit,
    onChatMenuClick: () -> Unit,
    chatMenuExpanded: Boolean,
    onDismissChatMenu: () -> Unit,
    selectedModel: String,
    reasoningEnabled: Boolean,
    webSearchEnabled: Boolean,
    onModelPillClick: () -> Unit,
    onChatOption: (String) -> Unit
) {
    Surface(color = BlackBg) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleTextButton(
                text = "☰",
                onClick = onOpenList
            )
            Spacer(modifier = Modifier.width(8.dp))

            ModelPill(
                modifier = Modifier.weight(1f),
                modelName = selectedModel,
                reasoningEnabled = reasoningEnabled,
                webSearchEnabled = webSearchEnabled,
                onClick = onModelPillClick
            )

            Spacer(modifier = Modifier.width(8.dp))

            CircleIconButton(
                icon = Icons.Rounded.AddComment,
                contentDescription = "새 채팅",
                onClick = onComposeClick
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                CircleTextButton(
                    text = "⋮",
                    onClick = onChatMenuClick
                )

                DropdownMenu(
                    expanded = chatMenuExpanded,
                    onDismissRequest = onDismissChatMenu,
                    containerColor = MenuBg
                ) {
                    DropdownMenuItem(
                        text = { Text("채팅 고정", color = TextPrimary) },
                        onClick = { onChatOption("채팅 고정") }
                    )
                    DropdownMenuItem(
                        text = { Text("새 프로젝트", color = TextPrimary) },
                        onClick = { onChatOption("새 프로젝트") }
                    )
                    DropdownMenuItem(
                        text = { Text("프로젝트에 추가", color = TextPrimary) },
                        onClick = { onChatOption("프로젝트에 추가") }
                    )
                    DropdownMenuItem(
                        text = { Text("업로드한 파일", color = TextPrimary) },
                        onClick = { onChatOption("업로드한 파일") }
                    )
                    DropdownMenuItem(
                        text = { Text("홈 화면에 추가", color = TextPrimary) },
                        onClick = { onChatOption("홈 화면에 추가") }
                    )
                    DropdownMenuItem(
                        text = { Text("아카이브에 보관", color = TextPrimary) },
                        onClick = { onChatOption("아카이브에 보관") }
                    )
                    DropdownMenuItem(
                        text = { Text("삭제", color = DangerRed) },
                        onClick = { onChatOption("삭제") }
                    )
                }
            }
        }
    }

}
@Composable
private fun ModelPill(
    modifier: Modifier = Modifier,
    modelName: String,
    reasoningEnabled: Boolean,
    webSearchEnabled: Boolean,
    onClick: () -> Unit
) {
    val displayName = buildList {
        add(shortModelName(modelName))
        if (reasoningEnabled) add("Reasoning")
        if (webSearchEnabled) add("Searching")
    }.joinToString(" · ")
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        color = PanelBg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayName,
                color = AccentBlue,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "▾",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
    }

}
@Composable
private fun EmptyChatBody() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 120.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Fusion",
            color = TextPrimary,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "새 채팅을 시작해봐.",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "모델과 모드는 위쪽 칩에서 바꿀 수 있어.",
            color = TextSecondary,
            fontSize = 15.sp
        )
    }

}
@Composable
private fun ModelLoadingBubble(
    status: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = PanelBg
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = TextSecondary,
                    trackColor = LineColor
                )
                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = status,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "처음 로딩은 오래 걸릴 수 있어.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

}
@Composable
private fun AssistantMessage(
    content: String,
    createdAt: Long,
    selectedModel: String,
    webSearchEnabled: Boolean,
    reasoningEnabled: Boolean,
    onRetry: () -> Unit,
    onBranch: () -> Unit,
    onToggleWebSearch: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var moreExpanded by remember { mutableStateOf(false) }
    val metricsSplit = remember(content) {
        splitFusionMetrics(content)
    }
    val parsed = remember(metricsSplit.content, reasoningEnabled) {
        parseAssistantOutput(metricsSplit.content, reasoningEnabled)
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        parsed.thinking?.let { thinking ->
            ReasoningPanel(thinking = thinking)
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = parsed.answer,
            color = TextPrimary,
            fontSize = 18.sp,
            lineHeight = 29.sp
        )

        metricsSplit.metricsLine?.let { metricsLine ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = metricsLine,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionIcon(
                icon = Icons.Rounded.ContentCopy,
                contentDescription = "복사",
                onClick = {
                    clipboardManager.setText(AnnotatedString(parsed.answer))
                    Toast.makeText(context, "복사했어", Toast.LENGTH_SHORT).show()
                }
            )

            ActionIcon(
                icon = Icons.Rounded.VolumeUp,
                contentDescription = "음성으로 읽기",
                onClick = {
                    Toast.makeText(context, "음성 읽기는 나중에 연결할게", Toast.LENGTH_SHORT).show()
                }
            )

            Box {
                ActionIcon(
                    icon = Icons.Rounded.MoreVert,
                    contentDescription = "더보기",
                    onClick = {
                        moreExpanded = true
                    }
                )

                AssistantMoreMenu(
                    expanded = moreExpanded,
                    onDismiss = { moreExpanded = false },
                    createdAt = createdAt,
                    selectedModel = selectedModel,
                    webSearchEnabled = webSearchEnabled,
                    onBranch = {
                        moreExpanded = false
                        onBranch()
                    },
                    onRetry = {
                        moreExpanded = false
                        onRetry()
                    },
                    onToggleWebSearch = {
                        moreExpanded = false
                        onToggleWebSearch()
                    }
                )
            }
        }
    }

}
@Composable
private fun ReasoningPanel(
    thinking: String
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF141414)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "생각 과정 접기" else "생각 과정 보기",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = if (expanded) "⌃" else "⌄",
                    color = TextSecondary,
                    fontSize = 18.sp
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(LineColor)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = thinking,
                    color = Color(0xFFBDBDBD),
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            }
        }
    }

}
@Composable
private fun AssistantMoreMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    createdAt: Long,
    selectedModel: String,
    webSearchEnabled: Boolean,
    onBranch: () -> Unit,
    onRetry: () -> Unit,
    onToggleWebSearch: () -> Unit
) {
    val timeText = remember(createdAt) {
        SimpleDateFormat("오늘, a h:mm", Locale.KOREAN)
            .format(Date(createdAt))
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = MenuBg
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    text = timeText,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            onClick = {},
            enabled = false
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LineColor)
        )

        DropdownMenuItem(
            text = { Text("↗  새 채팅으로 가지치기", color = TextPrimary) },
            onClick = onBranch
        )

        DropdownMenuItem(
            text = { Text("$selectedModel 사용함", color = TextSecondary) },
            onClick = {},
            enabled = false
        )

        DropdownMenuItem(
            text = { Text("↻  재시도", color = TextPrimary) },
            onClick = onRetry
        )

        DropdownMenuItem(
            text = {
                Text(
                    text = if (webSearchEnabled) "🌐  웹 검색 끄기" else "🌐  웹 검색",
                    color = TextPrimary
                )
            },
            onClick = onToggleWebSearch
        )
    }

}
@Composable
private fun ActionIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = TextSecondary,
            modifier = Modifier.size(23.dp)
        )
    }
}
@Composable
private fun UserMessageBubble(
    content: String
) {
    val parsed = remember(content) {
        parseMessageAttachments(content)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        parsed.attachments.forEach { attachment ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .padding(bottom = 6.dp)
            ) {
                AttachmentCard(
                    attachment = attachment,
                    onRemove = null
                )
            }
        }

        if (parsed.body.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = BubbleBg
            ) {
                Text(
                    text = parsed.body,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                )
            }
        }
    }
}
@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    isGenerating: Boolean,
    onPlusClick: () -> Unit,
    onMicClick: () -> Unit,
    onVoiceModeClick: () -> Unit,
    onSendClick: () -> Unit,
    pendingAttachments: List<LocalAttachment>,
    onRemoveAttachment: (LocalAttachment) -> Unit,
) {
    Surface(color = BlackBg) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(LineColor)
            )
            if (pendingAttachments.isNotEmpty()) {
                PendingAttachmentTray(
                    attachments = pendingAttachments,
                    onRemove = onRemoveAttachment
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    color = PanelBg
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        CircleMiniButton(
                            text = "+",
                            onClick = onPlusClick
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            enabled = enabled,
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 18.sp,
                                lineHeight = 24.sp
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 10.dp),
                            maxLines = 4,
                            decorationBox = { innerTextField ->
                                if (value.isBlank()) {
                                    Text(
                                        text = "입력하여 채팅",
                                        color = TextSecondary,
                                        fontSize = 18.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        if (value.isBlank() && pendingAttachments.isEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircleMiniIconButton(
                                    icon = Icons.Rounded.Mic,
                                    contentDescription = "음성 입력",
                                    onClick = onMicClick
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Surface(
                                    modifier = Modifier.size(42.dp),
                                    shape = CircleShape,
                                    color = Color.White
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable { onVoiceModeClick() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "◉",
                                            color = Color.Black,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = CircleShape,
                                color = Color.White
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(enabled = !isGenerating) {
                                            onSendClick()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isGenerating) "…" else "↑",
                                        color = Color.Black,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
@Composable
private fun CircleTextButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(PanelBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 24.sp
        )
    }
}
@Composable
private fun PendingAttachmentTray(
    attachments: List<LocalAttachment>,
    onRemove: (LocalAttachment) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        attachments.forEach { attachment ->
            AttachmentCard(
                attachment = attachment,
                onRemove = { onRemove(attachment) }
            )
        }
    }
}

@Composable
private fun AttachmentCard(
    attachment: LocalAttachment,
    onRemove: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                openAttachmentFile(context, attachment)
            },
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF202020)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AttachmentPreview(attachment = attachment)

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = attachment.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = attachment.mimeType,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onRemove != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "×",
                    color = TextSecondary,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onRemove() }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun attachmentIcon(mimeType: String): String {
    return when {
        mimeType.startsWith("image/") -> "🖼"
        mimeType == "application/pdf" -> "📄"
        mimeType.startsWith("text/") -> "📝"
        else -> "📎"
    }
}
@Composable
private fun AttachmentPreview(
    attachment: LocalAttachment
) {
    val bitmap = remember(attachment.localPath, attachment.mimeType) {
        if (attachment.mimeType.startsWith("image/")) {
            loadAttachmentThumbnail(attachment.localPath)
        } else {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = attachment.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2B2B2B)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = attachmentIcon(attachment.mimeType),
                color = TextPrimary,
                fontSize = 22.sp
            )
        }
    }
}
@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(PanelBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = TextPrimary,
            modifier = Modifier.size(25.dp)
        )
    }
}
@Composable
private fun CircleMiniButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 22.sp
        )
    }
}
@Composable
private fun CircleMiniIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = TextPrimary,
            modifier = Modifier.size(23.dp)
        )
    }
}
@Composable
private fun ModelSelectDialog(
    currentModel: String,
    builtInModels: Iterable<LocalModel>,
    customModels: Iterable<LocalModel>,
    downloadingModelName: String?,
    downloadProgressPercent: Int?,
    reasoningEnabled: Boolean,
    webSearchEnabled: Boolean,
    generationSettings: GenerationSettings,
    isDownloaded: (LocalModel) -> Boolean,
    onDismiss: () -> Unit,
    onSelect: (LocalModel) -> Unit,
    onUploadCustomModel: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onToggleReasoning: () -> Unit,
    onToggleWebSearch: () -> Unit
) {
    val models = builtInModels.toList() + customModels.toList()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("모델 / 모드")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "모델 선택",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                models.forEach { model: LocalModel ->
                    val downloaded = model.customPath != null || isDownloaded(model)
                    val downloading = downloadingModelName == model.name

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (model.name == currentModel) BubbleBg else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(model) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = model.name,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(3.dp))

                                Text(
                                    text = when {
                                        model.customPath != null -> "커스텀 모델"
                                        downloading -> "다운로드 중 ${downloadProgressPercent ?: 0}%"
                                        downloaded -> "다운로드됨"
                                        model.downloadUrl != null -> "탭해서 다운로드"
                                        else -> "다운로드 URL 미등록"
                                    },
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )

                                if (downloading) {
                                    Spacer(modifier = Modifier.height(6.dp))

                                    LinearProgressIndicator(
                                        progress = { ((downloadProgressPercent ?: 0) / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = TextSecondary,
                                        trackColor = LineColor
                                    )
                                }
                            }

                            if (model.name == currentModel) {
                                Text(
                                    text = "사용 중",
                                    color = AccentBlue,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PanelBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUploadCustomModel() }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)
                    ) {
                        Text(
                            text = "+ 커스텀 모델 업로드",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = "다운로드한 .litertlm / .task / 모델 파일 선택",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(LineColor)
                )

                SettingSwitchRow(
                    title = "Reasoning mode",
                    subtitle = "생각 과정 표시와 reasoning 프롬프트 사용",
                    checked = reasoningEnabled,
                    onToggle = onToggleReasoning
                )

                SettingSwitchRow(
                    title = "Web search",
                    subtitle = "웹 검색 결과를 참고 정보로 추가",
                    checked = webSearchEnabled,
                    onToggle = onToggleWebSearch
                )

                AdvancedSettingsEntry(
                    settings = generationSettings,
                    onClick = onOpenAdvancedSettings
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
        containerColor = PanelBg,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary
    )

}
@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                uncheckedThumbColor = Color(0xFFD9D9D9),
                checkedTrackColor = Color(0xFF5A5A5A),
                uncheckedTrackColor = Color(0xFF3C3C3C),
                checkedBorderColor = Color.Transparent,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }

}
@Composable
private fun AdvancedSettingsEntry(
    settings: GenerationSettings,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = BubbleBg
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "고급 설정",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Max ${settings.maxTokens} · TopK ${settings.topK} · TopP ${
                    "%.2f".format(settings.topP)
                } · Temp ${"%.2f".format(settings.temperature)}",
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${settings.accelerator.name} · Reason ${settings.reasoningBudgetTokens} · MTP ${
                    when (settings.speculativeDecodingEnabled) {
                        true -> "On"
                        false -> "Off"
                        null -> "Auto"
                    }
                }",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
    }

}
@Composable
private fun DownloadModelDialog(
    model: LocalModel,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("모델 다운로드")
        },
        text = {
            Column {
                Text(
                    text = model.name,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (model.downloadUrl != null) {
                        "이 모델은 아직 기기에 없어. 다운로드한 뒤 로컬 추론 엔진에 연결할 수 있어."
                    } else {
                        "이 모델은 아직 다운로드 URL이 등록되지 않았어."
                    },
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDownload,
                enabled = model.downloadUrl != null
            ) {
                Text("다운로드")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
        containerColor = PanelBg,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary
    )

}
@Composable
private fun AdvancedSettingsDialog(
    settings: GenerationSettings,
    selectedModel: String,
    onDismiss: () -> Unit,
    onApply: (GenerationSettings) -> Unit
) {
    var maxTokens by remember(settings) {
        mutableStateOf(settings.maxTokens.coerceIn(2000, 32000))
    }
    var topK by remember(settings) {
        mutableStateOf(settings.topK.coerceIn(5, 100))
    }
    var topP by remember(settings) {
        mutableStateOf(settings.topP.coerceIn(0f, 1f))
    }
    var temperature by remember(settings) {
        mutableStateOf(settings.temperature.coerceIn(0f, 2f))
    }
    var reasoningBudget by remember(settings) {
        mutableStateOf(settings.reasoningBudgetTokens.coerceIn(128, 8192))
    }
    var accelerator by remember(settings) {
        mutableStateOf(
            if (settings.accelerator == AcceleratorMode.CPU) {
                AcceleratorMode.CPU
            } else {
                AcceleratorMode.GPU
            }
        )
    }
    var speculativeDecodingEnabled by remember(settings, selectedModel) {
        mutableStateOf(
            settings.speculativeDecodingEnabled
                ?: defaultSpeculativeDecodingEnabled(
                    modelName = selectedModel,
                    accelerator = settings.accelerator
                )
        )
    }
    var speculativeDecodingTouched by remember(settings, selectedModel) {
        mutableStateOf(settings.speculativeDecodingEnabled != null)
    }
    var maxTokensText by remember(settings) { mutableStateOf(maxTokens.toString()) }
    var topKText by remember(settings) { mutableStateOf(topK.toString()) }
    var topPText by remember(settings) { mutableStateOf("%.2f".format(topP)) }
    var temperatureText by remember(settings) { mutableStateOf("%.2f".format(temperature)) }
    var reasoningBudgetText by remember(settings) { mutableStateOf(reasoningBudget.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MenuBg,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = {
            Text(
                text = "Configurations",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Normal
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                IntSliderField(
                    title = "Max tokens",
                    value = maxTokens,
                    valueText = maxTokensText,
                    min = 2000,
                    max = 32000,
                    onSliderChange = { newValue ->
                        maxTokens = newValue
                        maxTokensText = newValue.toString()
                    },
                    onTextChange = { text ->
                        maxTokensText = text
                        text.toIntOrNull()?.let { parsed ->
                            maxTokens = parsed.coerceIn(2000, 32000)
                        }
                    }
                )

                IntSliderField(
                    title = "TopK",
                    value = topK,
                    valueText = topKText,
                    min = 5,
                    max = 100,
                    onSliderChange = { newValue ->
                        topK = newValue
                        topKText = newValue.toString()
                    },
                    onTextChange = { text ->
                        topKText = text
                        text.toIntOrNull()?.let { parsed ->
                            topK = parsed.coerceIn(5, 100)
                        }
                    }
                )

                FloatSliderField(
                    title = "TopP",
                    value = topP,
                    valueText = topPText,
                    min = 0f,
                    max = 1f,
                    onSliderChange = { newValue ->
                        topP = newValue
                        topPText = "%.2f".format(newValue)
                    },
                    onTextChange = { text ->
                        topPText = text
                        text.toFloatOrNull()?.let { parsed ->
                            topP = parsed.coerceIn(0f, 1f)
                        }
                    }
                )

                FloatSliderField(
                    title = "Temperature",
                    value = temperature,
                    valueText = temperatureText,
                    min = 0f,
                    max = 2f,
                    onSliderChange = { newValue ->
                        temperature = newValue
                        temperatureText = "%.2f".format(newValue)
                    },
                    onTextChange = { text ->
                        temperatureText = text
                        text.toFloatOrNull()?.let { parsed ->
                            temperature = parsed.coerceIn(0f, 2f)
                        }
                    }
                )

                IntSliderField(
                    title = "Reasoning budget",
                    value = reasoningBudget,
                    valueText = reasoningBudgetText,
                    min = 128,
                    max = 8192,
                    onSliderChange = { newValue ->
                        reasoningBudget = newValue
                        reasoningBudgetText = newValue.toString()
                    },
                    onTextChange = { text ->
                        reasoningBudgetText = text
                        text.toIntOrNull()?.let { parsed ->
                            reasoningBudget = parsed.coerceIn(128, 8192)
                        }
                    }
                )

                Column {
                    Text(
                        text = "Accelerator",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .border(
                                width = 1.dp,
                                color = Color(0xFF7A7A7A),
                                shape = RoundedCornerShape(999.dp)
                            )
                    ) {
                        AcceleratorSegment(
                            text = "GPU",
                            selected = accelerator == AcceleratorMode.GPU,
                            onClick = { accelerator = AcceleratorMode.GPU }
                        )

                        AcceleratorSegment(
                            text = "CPU",
                            selected = accelerator == AcceleratorMode.CPU,
                            onClick = { accelerator = AcceleratorMode.CPU }
                        )
                    }
                }

                SettingSwitchRow(
                    title = "MTP 가속",
                    subtitle = "Gemma 4에서 speculative decoding으로 출력 속도를 높입니다.",
                    checked = if (speculativeDecodingTouched) {
                        speculativeDecodingEnabled
                    } else {
                        defaultSpeculativeDecodingEnabled(
                            modelName = selectedModel,
                            accelerator = accelerator
                        )
                    },
                    onToggle = {
                        val current = if (speculativeDecodingTouched) {
                            speculativeDecodingEnabled
                        } else {
                            defaultSpeculativeDecodingEnabled(
                                modelName = selectedModel,
                                accelerator = accelerator
                            )
                        }
                        speculativeDecodingEnabled = !current
                        speculativeDecodingTouched = true
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = Color(0xFFD0D0D0)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        settings.copy(
                            maxTokens = maxTokensText.toIntOrNull()
                                ?.coerceIn(2000, 32000)
                                ?: maxTokens.coerceIn(2000, 32000),
                            topK = topKText.toIntOrNull()
                                ?.coerceIn(5, 100)
                                ?: topK.coerceIn(5, 100),
                            topP = topPText.toFloatOrNull()
                                ?.coerceIn(0f, 1f)
                                ?: topP.coerceIn(0f, 1f),
                            temperature = temperatureText.toFloatOrNull()
                                ?.coerceIn(0f, 2f)
                                ?: temperature.coerceIn(0f, 2f),
                            accelerator = accelerator,
                            reasoningBudgetTokens = reasoningBudgetText.toIntOrNull()
                                ?.coerceIn(128, 8192)
                                ?: reasoningBudget.coerceIn(128, 8192),
                            speculativeDecodingEnabled = if (speculativeDecodingTouched) {
                                speculativeDecodingEnabled
                            } else {
                                null
                            }
                        )
                    )
                }
            ) {
                Text(
                    text = "OK",
                    color = Color.White
                )
            }
        }
    )

}
@Composable
private fun IntSliderField(
    title: String,
    value: Int,
    valueText: String,
    min: Int,
    max: Int,
    onSliderChange: (Int) -> Unit,
    onTextChange: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = min.toString(),
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.width(50.dp)
            )

            Slider(
                value = value.coerceIn(min, max).toFloat(),
                onValueChange = { sliderValue ->
                    onSliderChange(sliderValue.roundToInt().coerceIn(min, max))
                },
                valueRange = min.toFloat()..max.toFloat(),
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFFBDBDBD),
                    inactiveTrackColor = Color(0xFF4D4D4D),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.width(12.dp))

            OutlinedTextField(
                value = valueText,
                onValueChange = onTextChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 14.sp
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.width(110.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8A8A8A),
                    unfocusedBorderColor = Color(0xFF6A6A6A),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Color.White,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
        }
    }

}
@Composable
private fun FloatSliderField(
    title: String,
    value: Float,
    valueText: String,
    min: Float,
    max: Float,
    onSliderChange: (Float) -> Unit,
    onTextChange: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "%.2f".format(min),
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.width(50.dp)
            )

            Slider(
                value = value.coerceIn(min, max),
                onValueChange = { sliderValue ->
                    onSliderChange(sliderValue.coerceIn(min, max))
                },
                valueRange = min..max,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFFBDBDBD),
                    inactiveTrackColor = Color(0xFF4D4D4D),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.width(12.dp))

            OutlinedTextField(
                value = valueText,
                onValueChange = onTextChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 14.sp
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier.width(110.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8A8A8A),
                    unfocusedBorderColor = Color(0xFF6A6A6A),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Color.White,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
        }
    }

}
@Composable
private fun AcceleratorSegment(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(if (selected) Color(0xFF5B5B5B) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (selected) "✓ $text" else text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
private fun parseAssistantOutput(
    raw: String,
    reasoningEnabled: Boolean
): ParsedAssistantOutput {
    val thinkingTagRegex = Regex("""</?fusion_thinking>""", RegexOption.IGNORE_CASE)
    val answerTagRegex = Regex("""</?fusion_answer>""", RegexOption.IGNORE_CASE)

    val thinkingBlockRegex = Regex(
        pattern = """(?is)<fusion_thinking>(.*?)</fusion_thinking>"""
    )
    val answerBlockRegex = Regex(
        pattern = """(?is)<fusion_answer>(.*?)</fusion_answer>"""
    )

    val thinkingBlocks = thinkingBlockRegex.findAll(raw)
        .map { match -> stripFusionTags(match.groupValues[1]).trim() }
        .filter { it.isNotBlank() }
        .toList()

    val answerBlocks = answerBlockRegex.findAll(raw)
        .map { match -> stripFusionTags(match.groupValues[1]).trim() }
        .filter { it.isNotBlank() }
        .toList()

    val thinking = if (reasoningEnabled) {
        thinkingBlocks
            .joinToString("\n\n")
            .ifBlank { null }
    } else {
        null
    }

    val answer = when {
        answerBlocks.isNotEmpty() -> answerBlocks.joinToString("\n\n")

        raw.contains("</fusion_thinking>", ignoreCase = true) -> raw
            .substringAfterLast("</fusion_thinking>")
            .let(::stripFusionTags)
            .trim()
            .ifBlank {
                if (thinkingBlocks.isNotEmpty()) {
                    ""
                } else {
                    stripFusionTags(raw).trim()
                }
            }

        raw.contains("<fusion_answer>", ignoreCase = true) -> raw
            .substringAfterLast("<fusion_answer>")
            .let(::stripFusionTags)
            .trim()

        raw.contains("<fusion_thinking>", ignoreCase = true) -> raw
            .substringBefore("<fusion_thinking>")
            .let(::stripFusionTags)
            .trim()
            .ifBlank {
                if (thinkingBlocks.isNotEmpty()) {
                    ""
                } else {
                    stripFusionTags(raw).trim()
                }
            }

        thinkingTagRegex.containsMatchIn(raw) || answerTagRegex.containsMatchIn(raw) -> {
            val withoutCompleteThinking = thinkingBlockRegex.replace(raw, "")
            stripFusionTags(withoutCompleteThinking)
                .trim()
                .ifBlank {
                    if (thinkingBlocks.isNotEmpty()) {
                        ""
                    } else {
                        stripFusionTags(raw).trim()
                    }
                }
        }

        else -> raw.trim()
    }.let(::stripFusionTags).trim()

    return ParsedAssistantOutput(
        thinking = thinking,
        answer = answer.ifBlank {
            if (thinkingBlocks.isNotEmpty() || thinkingTagRegex.containsMatchIn(raw)) {
                "No final answer was generated."
            } else {
                stripFusionTags(raw).trim()
            }
        }
    )

}

private fun estimateOutputTokens(text: String): Int {
    val trimmed = stripFusionTags(splitFusionMetrics(text).content)
        .replace(Regex("\\s+"), " ")
        .trim()

    if (trimmed.isBlank()) return 0

    val wordLikeTokens = Regex("""[A-Za-z0-9_]+|[가-힣]+|[^\s]""")
        .findAll(trimmed)
        .sumOf { match ->
            val value = match.value
            when {
                value.any { it in '가'..'힣' } -> (value.length + 1) / 2
                value.length > 8 -> (value.length + 3) / 4
                else -> 1
            }
        }

    return wordLikeTokens.coerceAtLeast(1)
}

private fun buildFusionMetricsLine(
    modelName: String,
    acceleratorName: String,
    generatedText: String,
    totalGenerationMs: Long,
    firstTokenLatencyMs: Long?
): String {
    val outputTokens = estimateOutputTokens(generatedText)
    val totalSeconds = totalGenerationMs.coerceAtLeast(1L) / 1000.0
    val tokensPerSecond = outputTokens / totalSeconds
    val base = "${modelName} · ${acceleratorName} · ${String.format(Locale.US, "%.1fs", totalSeconds)} · 약 ${
        String.format(Locale.US, "%.1f", tokensPerSecond)
    } tok/s · 약 ${outputTokens} tokens"

    return firstTokenLatencyMs?.let {
        "$base · first ${String.format(Locale.US, "%.1fs", it / 1000.0)}"
    } ?: base
}

private fun appendFusionMetrics(
    content: String,
    metricsLine: String
): String {
    return "${splitFusionMetrics(content).content.trimEnd()}\n\n<fusion_metrics>$metricsLine</fusion_metrics>"
}

private fun splitFusionMetrics(
    content: String
): FusionMetricsSplit {
    val metricsRegex = Regex("""(?is)<fusion_metrics>(.*?)</fusion_metrics>""")
    val metricsLine = metricsRegex.findAll(content)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotBlank() }
        .lastOrNull()

    return FusionMetricsSplit(
        content = metricsRegex.replace(content, "").trimEnd(),
        metricsLine = metricsLine
    )
}

private fun stripFusionTags(text: String): String {
    return text
        .replace(Regex("""</?fusion_(?:thinking|answer|metrics)>""", RegexOption.IGNORE_CASE), "")
        .trim()
}
private fun getModelDirectory(context: Context): File {
    val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
    return File(baseDir, "models").apply {
        if (!exists()) mkdirs()
    }

}
private fun getModelFile(
    context: Context,
    model: LocalModel
): File {
    return File(getModelDirectory(context), model.fileName)
}
private fun isModelDownloaded(
    context: Context,
    model: LocalModel
): Boolean {
    if (model.customPath != null) {
        return File(model.customPath).exists()
    }
    return getModelFile(context, model).exists()

}
private suspend fun copyUriToModelFile(
    context: Context,
    uri: Uri,
    displayName: String
): File? {
    return withContext(Dispatchers.IO) {
        try {
            val safeName = displayName.ifBlank { "custom_model.litertlm" }
            val outputFile = File(getModelDirectory(context), safeName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            outputFile
        } catch (_: Exception) {
            null
        }
    }

}
private suspend fun downloadModelFile(
    context: Context,
    model: LocalModel,
    onProgress: (Int) -> Unit
): Boolean {
    val url = model.downloadUrl ?: return false
    val outputFile = getModelFile(context, model)
    val parentDir = outputFile.parentFile ?: getModelDirectory(context)
    val tempFile = File(parentDir, "${outputFile.name}.tmp")
    return withContext(Dispatchers.IO) {
        try {
            if (tempFile.exists()) {
                tempFile.delete()
            }

            val connection = URL(url).openConnection()
            connection.connect()

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            connection.getInputStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break

                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        if (totalBytes > 0L) {
                            val percent = ((downloadedBytes * 100) / totalBytes)
                                .toInt()
                                .coerceIn(0, 100)

                            withContext(Dispatchers.Main) {
                                onProgress(percent)
                            }
                        }
                    }
                }
            }

            if (outputFile.exists()) {
                outputFile.delete()
            }

            val renamed = tempFile.renameTo(outputFile)

            withContext(Dispatchers.Main) {
                onProgress(100)
            }

            renamed
        } catch (_: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }

            false
        }
    }

}
private fun getDisplayNameFromUri(
    context: Context,
    uri: Uri
): String {
    var displayName: String? = null
    context.contentResolver.query(
        uri,
        null,
        null,
        null,
        null
    )?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

        if (nameIndex >= 0 && cursor.moveToFirst()) {
            displayName = cursor.getString(nameIndex)
        }
    }

    return displayName ?: uri.lastPathSegment ?: "custom_model"

}
private suspend fun performSimpleWebSearch(
    query: String
): String? {
    return withContext(Dispatchers.IO) {
        val instantResult = runCatching {
            performDuckDuckGoInstantSearch(query)
        }.getOrNull()

        if (!instantResult.isNullOrBlank() && !instantResult.contains("검색 결과가 충분하지 않았어")) {
            return@withContext instantResult
        }

        val htmlResult = runCatching {
            performDuckDuckGoHtmlSearch(query)
        }.getOrNull()

        if (!htmlResult.isNullOrBlank()) {
            return@withContext htmlResult
        }

        instantResult ?: "검색 결과를 가져오지 못했어. 일반 지식과 추론을 구분해서 답해야 해."
    }
}
private fun performDuckDuckGoInstantSearch(
    query: String
): String? {
    val encodedQuery = URLEncoder.encode(query, "UTF-8")
    val url = URL(
        "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"
    )

    val jsonText = url.readText()
    val json = JSONObject(jsonText)
    val lines = mutableListOf<String>()

    val heading = json.optString("Heading")
    val abstractText = json.optString("AbstractText")
    val abstractUrl = json.optString("AbstractURL")
    val answer = json.optString("Answer")
    val definition = json.optString("Definition")

    if (answer.isNotBlank()) {
        lines.add("즉답: $answer")
    }

    if (definition.isNotBlank()) {
        lines.add("정의: $definition")
    }

    if (abstractText.isNotBlank()) {
        if (heading.isNotBlank()) {
            lines.add("주제: $heading")
        }
        lines.add("요약: $abstractText")
        if (abstractUrl.isNotBlank()) {
            lines.add("출처: $abstractUrl")
        }
    }

    val relatedTopics = json.optJSONArray("RelatedTopics")
    if (relatedTopics != null) {
        var added = 0
        var index = 0

        while (index < relatedTopics.length() && added < 3) {
            val item = relatedTopics.optJSONObject(index)

            if (item != null) {
                val text = item.optString("Text")
                val firstUrl = item.optString("FirstURL")

                if (text.isNotBlank()) {
                    lines.add("관련: $text")
                    if (firstUrl.isNotBlank()) {
                        lines.add("관련 출처: $firstUrl")
                    }
                    added += 1
                } else {
                    val nestedTopics = item.optJSONArray("Topics")
                    if (nestedTopics != null) {
                        var nestedIndex = 0

                        while (nestedIndex < nestedTopics.length() && added < 3) {
                            val nested = nestedTopics.optJSONObject(nestedIndex)
                            val nestedText = nested?.optString("Text").orEmpty()
                            val nestedUrl = nested?.optString("FirstURL").orEmpty()

                            if (nestedText.isNotBlank()) {
                                lines.add("관련: $nestedText")
                                if (nestedUrl.isNotBlank()) {
                                    lines.add("관련 출처: $nestedUrl")
                                }
                                added += 1
                            }

                            nestedIndex += 1
                        }
                    }
                }
            }

            index += 1
        }
    }

    return if (lines.isEmpty()) {
        null
    } else {
        lines.joinToString("\n")
    }
}

private fun performDuckDuckGoHtmlSearch(
    query: String
): String? {
    val encodedQuery = URLEncoder.encode(query, "UTF-8")
    val url = URL("https://html.duckduckgo.com/html/?q=$encodedQuery")

    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8000
        readTimeout = 8000
        setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Android; Fusion) AppleWebKit/537.36 Chrome Mobile Safari/537.36"
        )
    }

    val html = connection.inputStream.bufferedReader().use { it.readText() }

    val resultRegex = Regex(
        pattern = """(?s)<div class="result results_links.*?</div>\s*</div>"""
    )

    val titleRegex = Regex(
        pattern = """(?s)<a rel="nofollow" class="result__a" href="(.*?)">(.*?)</a>"""
    )

    val snippetRegex = Regex(
        pattern = """(?s)<a class="result__snippet".*?>(.*?)</a>|<div class="result__snippet".*?>(.*?)</div>"""
    )

    val results = resultRegex.findAll(html)
        .mapNotNull { blockMatch ->
            val block = blockMatch.value
            val titleMatch = titleRegex.find(block) ?: return@mapNotNull null

            val rawUrl = titleMatch.groupValues[1]
            val rawTitle = titleMatch.groupValues[2]

            val snippetMatch = snippetRegex.find(block)
            val rawSnippet = snippetMatch?.groupValues
                ?.drop(1)
                ?.firstOrNull { it.isNotBlank() }
                .orEmpty()

            val title = cleanHtmlText(rawTitle)
            val snippet = cleanHtmlText(rawSnippet)
            val link = cleanDuckDuckGoUrl(rawUrl)

            if (title.isBlank()) {
                null
            } else {
                SearchResultText(
                    title = title,
                    snippet = snippet,
                    url = link
                )
            }
        }
        .take(5)
        .toList()

    if (results.isEmpty()) return null

    return buildString {
        appendLine("검색어: $query")
        appendLine("아래는 DuckDuckGo HTML 검색에서 가져온 참고 결과다.")
        appendLine()

        results.forEachIndexed { index, result ->
            appendLine("${index + 1}. ${result.title}")
            if (result.snippet.isNotBlank()) {
                appendLine("요약: ${result.snippet}")
            }
            if (result.url.isNotBlank()) {
                appendLine("출처: ${result.url}")
            }
            appendLine()
        }
    }.trim()
}

private data class SearchResultText(
    val title: String,
    val snippet: String,
    val url: String
)

private fun cleanHtmlText(raw: String): String {
    return raw
        .replace(Regex("<.*?>"), " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun cleanDuckDuckGoUrl(raw: String): String {
    val cleaned = cleanHtmlText(raw)

    return cleaned
        .replace("&amp;", "&")
        .trim()
}
private fun loadAttachmentThumbnail(
    path: String,
    targetSize: Int = 160
): android.graphics.Bitmap? {
    return try {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        BitmapFactory.decodeFile(path, boundsOptions)

        val originalWidth = boundsOptions.outWidth
        val originalHeight = boundsOptions.outHeight

        if (originalWidth <= 0 || originalHeight <= 0) {
            return null
        }

        var sampleSize = 1
        while (
            originalWidth / sampleSize > targetSize * 2 ||
            originalHeight / sampleSize > targetSize * 2
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }

        BitmapFactory.decodeFile(path, decodeOptions)
    } catch (_: Exception) {
        null
    }
}

private fun openAttachmentFile(
    context: Context,
    attachment: LocalAttachment
) {
    val file = File(attachment.localPath)

    if (!file.exists()) {
        Toast.makeText(
            context,
            "첨부 파일을 찾을 수 없습니다: ${attachment.localPath}",
            Toast.LENGTH_LONG
        ).show()
        return
    }

    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, attachment.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(intent, "파일 열기")
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "이 파일을 열 앱이 없어", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "파일 열기 실패: ${e.message ?: e::class.java.simpleName}",
            Toast.LENGTH_SHORT
        ).show()
    }
}
private fun shortModelName(name: String): String {
    return when {
        name.contains("Gemma 4 E2B", ignoreCase = true) -> "Gemma 4 E2B"
        name.contains("Gemma 4 E4B", ignoreCase = true) -> "Gemma 4 E4B"
        else -> name
    }
}

private fun isGemma4Model(modelName: String): Boolean {
    return modelName.contains("Gemma 4", ignoreCase = true) ||
        modelName.contains("gemma-4", ignoreCase = true)
}

private fun isGemma4E4BModel(modelName: String): Boolean {
    return modelName.contains("E4B", ignoreCase = true) ||
        modelName.contains("e4b", ignoreCase = true)
}

private fun isGemma4E2BModel(modelName: String): Boolean {
    return modelName.contains("E2B", ignoreCase = true) ||
        modelName.contains("e2b", ignoreCase = true)
}

private fun defaultSpeculativeDecodingEnabled(
    modelName: String,
    accelerator: AcceleratorMode
): Boolean {
    if (!isGemma4Model(modelName)) return false

    return when (accelerator) {
        AcceleratorMode.GPU,
        AcceleratorMode.AUTO -> true

        AcceleratorMode.CPU -> isGemma4E4BModel(modelName) && !isGemma4E2BModel(modelName)
    }
}

private fun resolveSpeculativeDecodingEnabled(
    modelName: String,
    settings: GenerationSettings
): Boolean {
    if (!isGemma4Model(modelName)) return false

    return settings.speculativeDecodingEnabled
        ?: defaultSpeculativeDecodingEnabled(
            modelName = modelName,
            accelerator = settings.accelerator
        )
}

private fun buildAcceleratorLabel(
    acceleratorName: String,
    speculativeDecodingEnabled: Boolean
): String {
    return if (speculativeDecodingEnabled) {
        "$acceleratorName+MTP"
    } else {
        acceleratorName
    }
}

private fun shouldAutoUseWebSearch(userInput: String): Boolean {
    return FusionWebSearch.shouldAutoUseWebSearch(userInput)
}

private fun isGenericWebSearchRequest(userInput: String): Boolean {
    val normalized = userInput.trim().lowercase(Locale.ROOT)
    if (normalized.isBlank()) return false

    return listOf(
        "검색",
        "검색해줘",
        "검색해서 알려줘",
        "찾아줘",
        "찾아서 알려줘",
        "웹 검색",
        "웹검색",
        "알려줘"
    ).any { keyword -> normalized == keyword }
}

private fun normalizeWebSearchQuery(query: String): String {
    val normalized = query.trim()

    return if (normalized.contains("삼성전자") && normalized.contains("주가")) {
        "삼성전자 005930 주가 오늘 네이버 금융"
    } else {
        normalized
    }
}

private fun buildWebSearchQuery(
    latestUserInput: String,
    messages: List<ChatMessage>
): String {
    val trimmed = latestUserInput.trim()

    val genericSearchRequest = isGenericWebSearchRequest(trimmed)

    if (!genericSearchRequest || trimmed.length > 30) {
        return normalizeWebSearchQuery(trimmed)
    }

    val previousUserMessage = messages
        .asReversed()
        .firstOrNull { it.role == "user" }
        ?.content
        ?.let { parseMessageAttachments(it).body }
        ?.trim()
        .orEmpty()

    return if (previousUserMessage.isNotBlank()) {
        normalizeWebSearchQuery("$previousUserMessage $trimmed")
    } else {
        normalizeWebSearchQuery(trimmed)
    }
}

private fun buildFinalUserContent(
    body: String,
    attachments: List<LocalAttachment>,
    webSearchEnabled: Boolean,
    webSearchResult: String?
): String {
    val baseUserContent = buildModelUserContent(
        body = body,
        attachments = attachments
    )

    if (!webSearchEnabled) {
        return baseUserContent
    }

    val resultText = webSearchResult
        ?.trim()
        .orEmpty()

    return """
        너는 지금 앱의 웹 검색 기능을 통해 검색 결과를 받은 상태다.

        웹 검색 결과:
        ${resultText.ifBlank { "검색 결과를 가져오지 못했다." }}

        답변 규칙:
        - [FUSION_WEB_SEARCH_RESULTS]의 Result count가 1 이상이면 검색 결과를 직접 요약한다.
        - "실시간 정보를 조회할 수 없다", "웹 검색을 할 수 없다"라고 말하지 않는다.
        - 뉴스 사이트를 확인하라고 권하지 않는다.
        - 어떤 주제에 관심 있는지 되묻지 않는다.
        - 검색 결과가 있으면 그 결과를 바탕으로 답한다.
        - 검색 결과가 부족하면 "검색 결과만으로는 부족하다"고 짧게 말한 뒤, 일반 지식과 추론을 구분한다.
        - 오늘 주요 뉴스처럼 넓은 뉴스 질문이면 중요한 항목 5개를 번호 목록으로 정리한다.
        - 각 뉴스 항목에는 분야/주제, 짧은 요약, 가능한 경우 출처를 포함한다.
        - 사용자가 주가, 뉴스, 최신 정보처럼 현재성이 필요한 질문을 하면 검색 결과 기준이라고 분명히 말한다.
        - 뭘 검색할지 다시 묻지 말고, 아래 사용자 요청에 바로 답한다.

        사용자 요청:
        $baseUserContent
    """.trimIndent()
}
private fun buildFusionSystemPrompt(
    reasoningEnabled: Boolean,
    webSearchEnabled: Boolean,
    webContext: String?
): String {
    val basePrompt = """
You are Fusion, a personal AI friend for the user.

Your default speaking style is friendly and polite Korean.
For technical questions, answer accurately, calmly, and clearly.
For casual conversations, respond naturally and lightly.
If you do not know something, say that you do not know.
If something is uncertain, clearly label it as an inference.
Do not repeat the user's question unnecessarily.
Do not output internal role names or labels such as thought, user, model, assistant.
Avoid exaggerated headings, excessive emojis, and marketing-like expressions.

한국어 지침:
너는 Fusion이다.
사용자와 자연스럽게 대화하는 개인 AI 친구다.

기본 말투는 친근한 존댓말이다.
기술 질문에는 정확하고 차분하게 답한다.
일상 대화에는 부담 없이 자연스럽게 반응한다.
모르는 내용은 모른다고 말한다.
불확실한 내용은 추론이라고 구분한다.
사용자의 질문을 그대로 반복하지 않는다.
thought, user, model, assistant 같은 내부 태그나 역할명을 출력하지 않는다.
과장된 제목, 과한 이모지, 마케팅식 표현은 피한다.
""".trimIndent()

    val outputRule = if (reasoningEnabled) {
        """
When reasoning mode is enabled, use exactly this format:

<fusion_thinking>
Briefly write your reasoning process here in Korean.
</fusion_thinking>
<fusion_answer>
Write the final answer here in Korean.
</fusion_answer>

Use each tag exactly once.
Always close both tags.
Put all user-visible content inside <fusion_answer>.
Do not output role labels such as thought, user, model, or assistant.
Do not add anything outside these two tags.
""".trimIndent()
    } else {
        """
Output only the final answer.
Do not output <fusion_thinking> or <fusion_answer> tags.
Do not output hidden reasoning.
최종 답변만 출력한다.
내부 태그를 출력하지 않는다.
""".trimIndent()
    }

    val webRule = if (webSearchEnabled && !webContext.isNullOrBlank()) {
        """
        아래는 웹 검색에서 가져온 참고 정보다.

        규칙:
        1. 검색 결과가 충분하면 반드시 이 정보를 우선 사용한다.
        2. "실시간 정보를 조회할 수 없다", "웹 검색을 할 수 없다"라고 말하지 않는다.
        3. 검색 결과가 부족하면 "검색 결과만으로는 부족하다"고 짧게 말하고, 일반 지식과 추론을 구분한다.
        4. "검색 결과가 비어 있다"는 말을 반복하지 않는다.
        5. 사용자가 "검색해서 알려줘", "찾아줘"처럼 말하면 직전 대화의 주제를 이어받아 검색 의도로 해석한다.
        6. 뭘 검색할지 다시 묻지 않는다.

        웹 검색 참고 정보:
        $webContext
    """.trimIndent()
    } else {
        ""
    }

    return listOf(basePrompt, outputRule, webRule)
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
}
private fun getAttachmentDirectory(context: Context): File {
    return (context.getExternalFilesDir("attachments") ?: File(context.filesDir, "attachments")).apply {
        if (!exists()) mkdirs()
    }
}

private suspend fun copyUriToAttachmentFile(
    context: Context,
    uri: Uri,
    displayName: String
): File? {
    return withContext(Dispatchers.IO) {
        try {
            val safeName = displayName
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .ifBlank { "attachment" }

            val outputFile = File(
                getAttachmentDirectory(context),
                "${System.currentTimeMillis()}_$safeName"
            )

            context.contentResolver.openInputStream(uri)?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            if (outputFile.exists() && outputFile.length() > 0L) {
                outputFile
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}

private fun buildMessageContentWithAttachments(
    body: String,
    attachments: List<LocalAttachment>
): String {
    if (attachments.isEmpty()) return body

    val attachmentTags = attachments.joinToString("\n") { attachment ->
        val safeName = attachment.name
            .replace("\\", "\\\\")
            .replace("|", "\\|")

        val safeMime = attachment.mimeType
            .replace("\\", "\\\\")
            .replace("|", "\\|")

        val safePath = attachment.localPath
            .replace("\\", "\\\\")
            .replace("|", "\\|")

        "<fusion_attachment_v2>$safeName|$safeMime|$safePath</fusion_attachment_v2>"
    }

    return listOf(attachmentTags, body)
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
}

private fun parseMessageAttachments(raw: String): ParsedMessageContent {
    val newRegex = Regex(
        pattern = """<fusion_attachment_v2>(.*?)\|(.*?)\|(.*?)</fusion_attachment_v2>"""
    )

    val newAttachments = newRegex.findAll(raw).map { match ->
        LocalAttachment(
            name = match.groupValues[1].unescapeAttachmentField(),
            mimeType = match.groupValues[2].unescapeAttachmentField(),
            localPath = match.groupValues[3].unescapeAttachmentField()
        )
    }.toList()

    val oldRegex = Regex(
        pattern = """(?s)<fusion_attachment>\s*name=(.*?)\nmime=(.*?)\npath=(.*?)\s*</fusion_attachment>"""
    )

    val oldAttachments = oldRegex.findAll(raw).map { match ->
        LocalAttachment(
            name = match.groupValues[1].trim(),
            mimeType = match.groupValues[2].trim(),
            localPath = match.groupValues[3].trim()
        )
    }.toList()

    val body = oldRegex
        .replace(newRegex.replace(raw, ""), "")
        .trim()

    return ParsedMessageContent(
        body = body,
        attachments = newAttachments + oldAttachments
    )
}

private fun String.unescapeAttachmentField(): String {
    return this
        .replace("\\|", "|")
        .replace("\\\\", "\\")
        .trim()
}
private fun buildModelUserContent(
    body: String,
    attachments: List<LocalAttachment>
): String {
    if (attachments.isEmpty()) return body

    val attachmentText = attachments.joinToString("\n") { attachment ->
        "- ${attachment.name} (${attachment.mimeType})"
    }

    return """
        사용자가 파일을 첨부했다.

        첨부 파일:
        $attachmentText

        참고:
        현재 연결된 텍스트 모델은 파일 내용을 직접 읽지 못할 수 있다.
        이미지/파일 내용을 실제로 분석하려면 멀티모달 모델 또는 파일 파서 연결이 필요하다.

        사용자 메시지:
        ${body.ifBlank { "첨부 파일을 보냈다." }}
    """.trimIndent()
}
