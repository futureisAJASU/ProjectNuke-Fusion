package com.projectnuke.fusion.ui
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.app.ActivityManager
import android.app.DownloadManager
import android.os.SystemClock
import android.os.Environment
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import com.projectnuke.fusion.ai.buildExternalAiMessages
import com.projectnuke.fusion.ai.ExternalAiChatResult
import com.projectnuke.fusion.ai.ExternalAiChatRunner
import com.projectnuke.fusion.ai.data.AiProviderRepository
import com.projectnuke.fusion.ai.model.AiProviderConfig
import com.projectnuke.fusion.ai.network.OpenAiCompatibleClient
import com.projectnuke.fusion.ai.secure.AndroidKeystoreSecretStore
import com.projectnuke.fusion.data.AppDatabase
import com.projectnuke.fusion.data.BenchmarkResultEntity
import com.projectnuke.fusion.data.ConversationEntity
import com.projectnuke.fusion.data.MessageEntity
import com.projectnuke.fusion.llm.BenchmarkRunningException
import com.projectnuke.fusion.llm.FusionRuntimeLock
import com.projectnuke.fusion.llm.FusionRuntimeManager
import com.projectnuke.fusion.llm.GenerationOutcome
import com.projectnuke.fusion.llm.FailureKind
import com.projectnuke.fusion.llm.LiteRtLlmEngine
import com.projectnuke.fusion.model.AcceleratorMode
import com.projectnuke.fusion.model.ChatMessage
import com.projectnuke.fusion.model.GenerationSettings
import com.projectnuke.fusion.modelzoo.FusionModelCatalog
import com.projectnuke.fusion.modelzoo.FusionModelCompatibility
import com.projectnuke.fusion.modelzoo.FusionModelCompatibilityReport
import com.projectnuke.fusion.util.AttachmentStorageManager
import com.projectnuke.fusion.modelzoo.FusionModelMemoryPreflight
import com.projectnuke.fusion.modelzoo.FusionModelMemoryRiskLevel
import com.projectnuke.fusion.modelzoo.FusionModelSpec
import com.projectnuke.fusion.modelzoo.ModelAvailability
import com.projectnuke.fusion.modelzoo.ModelFamily
import com.projectnuke.fusion.modelzoo.ModelMemoryClass
import com.projectnuke.fusion.modelzoo.ModelRecommendedDeviceClass
import com.projectnuke.fusion.modelzoo.ModelRuntimeFormat
import com.projectnuke.fusion.modelzoo.deviceLabel
import com.projectnuke.fusion.modelzoo.statusLabel
import com.projectnuke.fusion.search.FusionWebSearch
import com.projectnuke.fusion.search.FusionSearchResponse
import com.projectnuke.fusion.search.SearchIntent
import com.projectnuke.fusion.search.WebSearchProviderRepository
import com.projectnuke.fusion.search.WebSearchProviderSettingsSection
import com.projectnuke.fusion.search.WebSearchSource
import com.projectnuke.fusion.search.appendSearchSourcesMetadata
import com.projectnuke.fusion.search.parseSearchSourcesMetadata
import com.projectnuke.fusion.search.stripSearchSourcesMetadata
import com.projectnuke.fusion.search.toStructuredContext
import com.projectnuke.fusion.util.buildEffectiveRuntimeSettings
import com.projectnuke.fusion.util.buildEffectiveSettingsLine
import com.projectnuke.fusion.util.collectFusionSocInfo
import com.projectnuke.fusion.util.fusionNpuCandidateLabel
import com.projectnuke.fusion.util.fusionNpuNoteText
import com.projectnuke.fusion.util.fusionNpuNoteTitle
import com.projectnuke.fusion.util.FusionMemoryManager
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.projectnuke.fusion.chat.GenerationRequestSnapshot
import com.projectnuke.fusion.chat.TokenCoalescer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import android.content.ActivityNotFoundException
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.FileProvider
import com.projectnuke.fusion.util.FusionFileProvider
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
private data class PendingExternalModel(
    val displayName: String,
    val originalFileName: String,
    val uri: Uri,
    val fileSizeBytes: Long?
)
private data class PendingModelImport(
    val displayName: String,
    val originalFileName: String,
    val uri: Uri,
    val fileSizeBytes: Long?,
    val permissionPersisted: Boolean,
    val initialFamily: ModelFamily
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

private enum class FusionPulseState {
    Idle,
    Responding,
    ModelLoading,
    WebSearching,
    MemoryExtracting,
    ErrorPulse
}

private enum class ResponseRegenerationAction(
    val menuLabel: String,
    val instruction: String?
) {
    Retry("답변 다시 생성", null),
    Shorter("더 짧게", "이전 답변의 핵심 내용을 유지하면서 더 짧고 간결하게 다시 작성해 주세요."),
    MoreDetailed("더 자세히", "이전 답변의 핵심 내용을 유지하면서 더 자세히 풀어서 설명해 주세요."),
    Table("표로 정리", "이전 답변의 핵심 내용을 표로 정리해 주세요. 필요한 경우 짧은 요약도 함께 포함해 주세요."),
    ExpertTone("전문가 톤", "이전 답변의 핵심 내용을 유지하면서 더 전문적이고 정확한 톤으로 다시 작성해 주세요.")
}

@Composable
private fun FusionPulseAmbientLight(
    state: FusionPulseState,
    modifier: Modifier = Modifier
) {
    if (state == FusionPulseState.Idle) return

    val deepNavy = Color(0xFF06132D)
    val darkBlue = Color(0xFF0B2A5B)
    val blue = Color(0xFF1677FF)
    val cyan = Color(0xFF3FD8FF)
    val brightTeal = Color(0xFF5FF2D6)
    val softWhiteBlue = Color(0xFFE1F6FF)
    val errorRed = Color(0xFFFF6B7A)
    val transition = rememberInfiniteTransition(label = "fusion-pulse")
    val phaseA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000),
            repeatMode = RepeatMode.Restart
        ),
        label = "fusion-pulse-phase-a"
    )
    val phaseB by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 13000),
            repeatMode = RepeatMode.Restart
        ),
        label = "fusion-pulse-phase-b"
    )
    val phaseC by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 17000),
            repeatMode = RepeatMode.Restart
        ),
        label = "fusion-pulse-phase-c"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
    ) {
        val width = size.width
        val height = size.height
        val angleA = phaseA * 6.28318f
        val angleB = phaseB * 6.28318f
        val angleC = phaseC * 6.28318f
        val breath = 0.88f + 0.12f * sin(angleC)
        val maxStreakWidth = 56.dp.toPx()
        val minStreakWidth = 18.dp.toPx()
        val minStreakHeight = 82.dp.toPx()
        val highlightMinWidth = 8.dp.toPx()
        val highlightMaxWidth = 20.dp.toPx()

        when (state) {
            FusionPulseState.Responding -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.16f to deepNavy.copy(alpha = 0.10f * breath),
                            0.36f to darkBlue.copy(alpha = 0.16f * breath),
                            0.70f to blue.copy(alpha = 0.045f * breath),
                            1f to Color.Transparent
                        )
                    ),
                    size = size
                )

                repeat(24) { index ->
                    val seed = index * 1.618f
                    val baseFraction = (index + 0.35f * sin(seed)) / 23f
                    val sway = sin(angleA + seed) * width * 0.028f +
                        cos(angleB + seed * 0.7f) * width * 0.012f
                    val x = width * baseFraction + sway
                    val streakWidth = minStreakWidth + (maxStreakWidth - minStreakWidth) * (0.5f + 0.5f * sin(seed * 1.7f))
                    val baseHeight = minStreakHeight + (height - minStreakHeight) * (0.62f + 0.18f * sin(seed * 0.9f))
                    val streakHeight = baseHeight * (0.90f + 0.10f * cos(angleB + seed))
                    val top = height * (0.02f + 0.04f * (0.5f + 0.5f * sin(seed * 1.3f)))
                    val colorWave = 0.5f + 0.5f * sin(angleC + seed * 1.25f)
                    val coreColor = if (colorWave < 0.5f) {
                        lerp(blue, cyan, colorWave * 2f)
                    } else {
                        lerp(cyan, brightTeal, (colorWave - 0.5f) * 2f)
                    }
                    val baseAlpha = 0.06f + 0.14f * (0.5f + 0.5f * sin(seed * 2.1f))
                    val alpha = baseAlpha * (0.78f + 0.22f * sin(angleC + seed)) * breath

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.18f to darkBlue.copy(alpha = alpha * 0.28f),
                                0.42f to coreColor.copy(alpha = alpha * 0.72f),
                                0.62f to brightTeal.copy(alpha = alpha * 0.52f),
                                0.84f to blue.copy(alpha = alpha * 0.18f),
                                1f to Color.Transparent
                            ),
                            startY = top,
                            endY = top + streakHeight
                        ),
                        topLeft = Offset(x - streakWidth / 2f, top),
                        size = Size(streakWidth, streakHeight),
                        cornerRadius = CornerRadius(streakWidth, streakWidth)
                    )
                }

                repeat(5) { index ->
                    val seed = index * 2.37f + 0.6f
                    val x = width * (0.12f + index * 0.19f) +
                        sin(angleA + seed) * width * 0.035f +
                        cos(angleB + seed * 0.7f) * width * 0.014f
                    val rayWidth = highlightMinWidth + (highlightMaxWidth - highlightMinWidth) * (0.5f + 0.5f * sin(seed))
                    val rayHeight = height * (0.72f + 0.12f * cos(angleB + seed))
                    val alpha = (0.10f + 0.11f * (0.5f + 0.5f * sin(angleC + seed))) * breath

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.20f to cyan.copy(alpha = alpha * 0.36f),
                                0.46f to brightTeal.copy(alpha = alpha),
                                0.70f to cyan.copy(alpha = alpha * 0.35f),
                                1f to Color.Transparent
                            ),
                            startY = 0f,
                            endY = rayHeight
                        ),
                        topLeft = Offset(x - rayWidth / 2f, 0f),
                        size = Size(rayWidth, rayHeight),
                        cornerRadius = CornerRadius(rayWidth, rayWidth)
                    )
                }
            }

            FusionPulseState.ModelLoading -> {
                val loadingBreath = 0.55f + 0.25f * (0.5f + 0.5f * sin(angleB))
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.34f to deepNavy.copy(alpha = 0.11f * loadingBreath),
                        0.58f to darkBlue.copy(alpha = 0.17f * loadingBreath),
                        1f to Color.Transparent
                    ),
                    size = size
                )
                repeat(8) { index ->
                    val seed = index * 1.91f
                    val streakWidth = width * (0.10f + 0.025f * sin(seed))
                    val x = width * (index + 0.5f) / 8f + sin(angleA + seed) * width * 0.012f
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.48f to darkBlue.copy(alpha = 0.09f * loadingBreath),
                            0.72f to blue.copy(alpha = 0.055f * loadingBreath),
                            1f to Color.Transparent
                        ),
                        topLeft = Offset(x - streakWidth / 2f, height * 0.12f),
                        size = Size(streakWidth, height * 0.78f),
                        cornerRadius = CornerRadius(streakWidth, streakWidth)
                    )
                }
            }

            FusionPulseState.WebSearching -> {
                val scanX = width * (0.16f + 0.68f * (0.5f + 0.5f * sin(angleA)))
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.44f to darkBlue.copy(alpha = 0.07f),
                        0.66f to cyan.copy(alpha = 0.055f),
                        1f to Color.Transparent
                    ),
                    size = size
                )
                repeat(3) { index ->
                    val scanWidth = width * (0.16f + index * 0.035f)
                    val x = scanX + sin(angleB + index * 1.7f) * width * 0.055f
                    val top = height * (0.18f + index * 0.17f)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.48f to cyan.copy(alpha = 0.13f - index * 0.025f),
                            1f to Color.Transparent
                        ),
                        topLeft = Offset(x - scanWidth / 2f, top),
                        size = Size(scanWidth, height * 0.42f),
                        cornerRadius = CornerRadius(scanWidth, scanWidth)
                    )
                }
            }

            FusionPulseState.MemoryExtracting -> {
                val memoryBreath = 0.72f + 0.18f * sin(angleC)
                repeat(12) { index ->
                    val seed = index * 1.43f
                    val streakWidth = 12.dp.toPx() + 22.dp.toPx() * (0.5f + 0.5f * sin(seed))
                    val x = width * (index + 0.5f) / 12f + sin(angleB + seed) * width * 0.014f
                    val streakHeight = height * (0.48f + 0.16f * (0.5f + 0.5f * cos(seed)))
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.38f to blue.copy(alpha = 0.055f * memoryBreath),
                            0.58f to softWhiteBlue.copy(alpha = 0.11f * memoryBreath),
                            1f to Color.Transparent
                        ),
                        topLeft = Offset(x - streakWidth / 2f, height * 0.20f),
                        size = Size(streakWidth, streakHeight),
                        cornerRadius = CornerRadius(streakWidth, streakWidth)
                    )
                }
            }

            FusionPulseState.ErrorPulse -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.52f to errorRed.copy(alpha = 0.16f * breath),
                        1f to Color.Transparent
                    ),
                    size = size
                )
            }

            FusionPulseState.Idle -> Unit
        }
    }
}

private const val ChatOptionConversationSummary = "대화 요약"
private const val ChatOptionMemoryCandidateExtraction = "메모리 후보 추출"
private const val FusionPrefsName = "fusion_chat_settings"
private const val PrefMaxTokens = "max_tokens"
private const val PrefTopK = "top_k"
private const val PrefTopP = "top_p"
private const val PrefTemperature = "temperature"
private const val PrefReasoningBudget = "reasoning_budget_tokens"
private const val PrefAccelerator = "accelerator"
private const val PrefReasoningEnabled = "reasoning_enabled"
private const val PrefWebSearchEnabled = "web_search_enabled"
private const val PrefSelectedModel = "selected_model"
private const val PrefSelectedModelPath = "selected_model_path"
private const val PrefGenerationMode = "generation_mode"
private const val PrefSpeculativeDecoding = "speculative_decoding_enabled"
private const val PrefFavoriteModelIds = "favorite_model_ids"
private const val PrefHiddenModelIds = "hidden_model_ids"
private const val PrefShowHiddenModels = "show_hidden_models"
private const val PrefModelLibrarySortMode = "model_library_sort_mode"
private const val PrefRecentModels = "recent_models"
private const val PrefModelNotes = "model_notes"
private const val SettingsBackupSchemaVersion = 1
private const val MaxRecentModels = 10

private enum class ModelLibrarySortMode(val key: String, val label: String) {
    RECOMMENDATION("recommendation", "추천순"),
    NAME("name", "이름순"),
    LIGHTWEIGHT("lightweight", "모델 크기 작은 순"),
    MEMORY_LOW("memory_low", "권장 사양 낮은 순"),
    LOCAL_EXECUTION("local_execution", "로컬 실행 가능 우선"),
    FAVORITES_FIRST("favorites_first", "즐겨찾기 우선");

    companion object {
        fun fromKey(value: String?): ModelLibrarySortMode {
            return values().firstOrNull { it.key == value } ?: RECOMMENDATION
        }
    }
}

private data class RecentModelEntry(
    val modelId: String,
    val displayName: String,
    val lastUsedAt: Long,
    val useCount: Int
)

private data class ModelNoteEditState(
    val modelId: String,
    val displayName: String,
    val initialNote: String
)

private data class ModelBenchmarkSummary(
    val count: Int,
    val latestAt: Long,
    val medianDecodeTps: Float?,
    val bestDecodeTps: Float?,
    val worstDecodeTps: Float?,
    val averageDecodeTps: Float?,
    val averageTotalTps: Float?,
    val recentAccelerator: String?,
    val failedCount: Int,
    val mtpRecommendation: String
)
private val QuickPromptPresets = listOf(
    "자세히 설명해 주세요.",
    "핵심만 요약해 주세요.",
    "문제점을 분석해 주세요.",
    "표로 정리해 주세요.",
    "반박해 주세요."
)
@Composable
fun ChatScreen(
    conversationId: Long,
    onConversationCreated: (Long) -> Unit,
    onOpenList: () -> Unit,
    onNewChat: () -> Unit,
    openModelLibraryRequest: Int = 0,
    openAdvancedSettingsRequest: Int = 0,
    onOpenBenchmark: (modelName: String?, openHistory: Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current
    val settingsPrefs = remember {
        context.getSharedPreferences(FusionPrefsName, Context.MODE_PRIVATE)
    }
    val db = remember { AppDatabase.getInstance(context) }
    val dao = remember { db.chatDao() }
    val aiSecretStore = remember { AndroidKeystoreSecretStore(context) }
    val aiProviderRepository = remember { AiProviderRepository(context, aiSecretStore) }
    val webSearchProviderRepository = remember { WebSearchProviderRepository(context, aiSecretStore) }
    val externalAiChatRunner = remember {
        ExternalAiChatRunner(
            providerRepository = aiProviderRepository,
            client = OpenAiCompatibleClient(aiSecretStore)
        )
    }
    val chatViewModel = remember { com.projectnuke.fusion.chat.ChatViewModel() }
    var pickerOriginConversationId by remember { mutableStateOf<Long?>(null) }
    DisposableEffect(chatViewModel) {
        onDispose { chatViewModel.dispose() }
    }
    var input by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var regeneratingMessageId by remember { mutableStateOf<Long?>(null) }
    var streamingAssistantText by remember { mutableStateOf<String?>(null) }
    var streamingMetricsLine by remember { mutableStateOf<String?>(null) }
    var composerHeightPx by remember { mutableStateOf(0) }
    val chatContentBottomPadding = with(density) {
        (composerHeightPx.takeIf { it > 0 } ?: 120.dp.roundToPx()).toDp() + 16.dp
    }

    var chatMenuExpanded by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showAdvancedSettingsDialog by remember { mutableStateOf(false) }
    var showDeleteChatDialog by remember { mutableStateOf(false) }
    var showConversationSummaryDialog by remember { mutableStateOf(false) }
    var showConversationSummaryEditor by remember { mutableStateOf(false) }
    var showConversationSummaryDeleteConfirm by remember { mutableStateOf(false) }
    var conversationSummaryDraft by remember { mutableStateOf("") }
    var conversationSummaryRefreshKey by remember { mutableStateOf(0) }
    var showMemoryCandidateDialog by remember { mutableStateOf(false) }
    var memoryCandidateText by remember { mutableStateOf("") }
    var extractingMemoryCandidates by remember { mutableStateOf(false) }
    var inChatSearchMode by remember { mutableStateOf(false) }
    var inChatSearchQuery by remember { mutableStateOf("") }

    var webSearchEnabled by remember { mutableStateOf(settingsPrefs.getBoolean(PrefWebSearchEnabled, false)) }
    var reasoningEnabled by remember { mutableStateOf(settingsPrefs.getBoolean(PrefReasoningEnabled, false)) }

    var generationSettings by remember { mutableStateOf(loadSavedGenerationSettings(settingsPrefs)) }
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
    var pendingModelImport by remember { mutableStateOf<PendingModelImport?>(null) }
    var showModelStorageManager by remember { mutableStateOf(false) }
    var storageRefreshKey by remember { mutableStateOf(0) }

    var selectedModel by remember {
        mutableStateOf(settingsPrefs.getString(PrefSelectedModel, "Gemma 4 E2B-it") ?: "Gemma 4 E2B-it")
    }
    var selectedModelPath by remember {
        mutableStateOf(settingsPrefs.getString(PrefSelectedModelPath, null))
    }
    var generationMode by remember {
        mutableStateOf(
            runCatching {
                ChatGenerationMode.valueOf(
                    settingsPrefs.getString(PrefGenerationMode, ChatGenerationMode.LOCAL_MODEL.name)
                        ?: ChatGenerationMode.LOCAL_MODEL.name
                )
            }.getOrDefault(ChatGenerationMode.LOCAL_MODEL)
        )
    }
    var externalProviders by remember { mutableStateOf<List<AiProviderConfig>>(emptyList()) }
    var selectedExternalProviderId by remember { mutableStateOf<String?>(null) }
    var selectedExternalProviderName by remember { mutableStateOf<String?>(null) }

    var pendingDownloadModel by remember { mutableStateOf<LocalModel?>(null) }
    var downloadingModelName by remember { mutableStateOf<String?>(null) }
    var downloadProgressPercent by remember { mutableStateOf<Int?>(null) }
    var generationStatus by remember { mutableStateOf<String?>(null) }

    fun isRunnableExternalProvider(provider: AiProviderConfig): Boolean {
        return provider.isEnabled &&
            !provider.apiKeySecretId.isNullOrBlank() &&
            provider.baseUrl.isNotBlank() &&
            provider.modelId.isNotBlank()
    }

    suspend fun refreshExternalProviderState() {
        val providers = aiProviderRepository.getProviders()
        val selected = aiProviderRepository.getSelectedRunnableProvider()
        externalProviders = providers
        selectedExternalProviderId = selected?.id
        selectedExternalProviderName = selected?.displayName
    }

    suspend fun runExternalAiRequest(
        currentMessages: List<ChatMessage>,
        hasAttachments: Boolean = false,
        providerId: String? = null,
    ): ExternalAiChatResult {
        return externalAiChatRunner.generateFromMessages(
            messages = buildExternalAiMessages(
                messages = currentMessages,
                stripAttachments = { stripSearchSourcesMetadata(parseMessageAttachments(it).body) }
            ),
            hasAttachments = hasAttachments,
            providerId = providerId,
        )
    }

    LaunchedEffect(aiProviderRepository) {
        aiProviderRepository.observeProviderChanges().collect {
            refreshExternalProviderState()
        }
    }

    DisposableEffect(settingsPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                PrefSelectedModel -> {
                    selectedModel = prefs.getString(PrefSelectedModel, selectedModel) ?: selectedModel
                }
                PrefSelectedModelPath -> {
                    selectedModelPath = prefs.getString(PrefSelectedModelPath, selectedModelPath)
                }
                PrefReasoningEnabled -> {
                    reasoningEnabled = prefs.getBoolean(PrefReasoningEnabled, reasoningEnabled)
                }
                PrefWebSearchEnabled -> {
                    webSearchEnabled = prefs.getBoolean(PrefWebSearchEnabled, webSearchEnabled)
                }
                PrefMaxTokens, PrefTopK, PrefTopP, PrefTemperature, PrefReasoningBudget, PrefAccelerator, PrefSpeculativeDecoding -> {
                    generationSettings = loadSavedGenerationSettings(prefs)
                }
            }
        }
        settingsPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            settingsPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    LaunchedEffect(openModelLibraryRequest) {
        if (openModelLibraryRequest > 0) {
            showModelDialog = true
        }
    }
    LaunchedEffect(openAdvancedSettingsRequest) {
        if (openAdvancedSettingsRequest > 0) {
            showAdvancedSettingsDialog = true
        }
    }

    val scope = rememberCoroutineScope()
    val currentConversationId by rememberUpdatedState(conversationId)
    BackHandler(
        enabled = showModelDialog || showAdvancedSettingsDialog || showDeleteChatDialog || inChatSearchMode || isGenerating
    ) {
        when {
            showDeleteChatDialog -> showDeleteChatDialog = false
            showAdvancedSettingsDialog -> showAdvancedSettingsDialog = false
            showModelDialog -> showModelDialog = false
            inChatSearchMode -> {
                inChatSearchMode = false
                inChatSearchQuery = ""
            }
            isGenerating -> {
                scope.launch {
                    chatViewModel.cancelGeneration(conversationId)
                }
            }
        }
    }
    val engine = remember { FusionRuntimeManager.sharedEngine(context) }
    DisposableEffect(engine) {
        val unregisterExclusive = FusionRuntimeLock.registerChatEngineUnloadCallback {
            Log.i("FusionEngine", "Unloading chat engine for exclusive benchmark mode")
            FusionRuntimeManager.unloadSharedEngineAfterExclusive("exclusive_prepare")
        }
        val unregisterIdle = FusionMemoryManager.registerIdleEngineUnloader {
            Log.i("FusionMemory", "Requesting idle shared engine unload from chat screen")
            scope.launch {
                FusionRuntimeManager.unloadSharedEngineWhenRuntimeIdle("chat_memory_pressure")
            }
        }
        onDispose {
            unregisterExclusive()
            unregisterIdle()
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
    var responseVersionState by remember(conversationId) {
        mutableStateOf(loadResponseVersionState(context, conversationId))
    }
    val chatTimeline = remember(messageEntities, responseVersionState) {
        buildChatTimeline(messageEntities, responseVersionState)
    }
    val activeMessageEntities = remember(messageEntities, responseVersionState) {
        activeTimelineMessages(messageEntities, responseVersionState)
    }
    val fusionPulseState = when {
        generationStatus?.contains("모델 로딩") == true ||
            generationStatus?.contains("이미지 분석 준비") == true -> FusionPulseState.ModelLoading
        generationStatus?.contains("인터넷 검색") == true ||
            generationStatus?.contains("검색 결과") == true -> FusionPulseState.WebSearching
        extractingMemoryCandidates -> FusionPulseState.MemoryExtracting
        isGenerating -> FusionPulseState.Responding
        else -> FusionPulseState.Idle
    }
    var displayedPulseState by remember { mutableStateOf(FusionPulseState.Responding) }
    LaunchedEffect(fusionPulseState) {
        if (fusionPulseState != FusionPulseState.Idle) {
            displayedPulseState = fusionPulseState
        }
    }
    val conversationSummary = remember(conversationId, conversationSummaryRefreshKey) {
        loadConversationSummary(context, conversationId)
    }
    val savedMemoryCandidates = remember(conversationId, memoryCandidateText) {
        loadConversationMemoryCandidates(context, conversationId)
    }
    LaunchedEffect(conversationId) {
        memoryCandidateText = ""
        // Isolate generation state per conversation. Restore the saved session
        // state (if any) for this conversation; clear the in-memory stream so a
        // switch does not show the previous conversation's tokens.
        val saved = chatViewModel.state(conversationId)
        isGenerating = saved.isGenerating
        streamingAssistantText = saved.streamingText
        streamingMetricsLine = saved.streamingMetricsLine
        generationStatus = saved.generationStatus
        regeneratingMessageId = saved.regeneratingMessageId
        extractingMemoryCandidates = saved.extractingMemoryCandidates
        // If a generation is still running for another conversation but this
        // conversation is now visible, do not show partial stream for it. This
        // makes streams conversation-specific without blocking background runs.
        if (!chatViewModel.registry.isActive(conversationId, saved.activeRequestId ?: "")) {
            streamingAssistantText = null
            streamingMetricsLine = null
            generationStatus = null
            isGenerating = false
            regeneratingMessageId = null
            extractingMemoryCandidates = false
        }
    }
    LaunchedEffect(chatViewModel, conversationId) {
        chatViewModel.states.collect { states ->
            val cur = states[conversationId] ?: return@collect
            if (currentConversationId == conversationId) {
                isGenerating = cur.isGenerating
                streamingAssistantText = cur.streamingText
                streamingMetricsLine = cur.streamingMetricsLine
                generationStatus = cur.generationStatus
                regeneratingMessageId = cur.regeneratingMessageId
                extractingMemoryCandidates = cur.extractingMemoryCandidates
            }
        }
    }

    val messages = activeMessageEntities.map {
        ChatMessage(
            role = it.role,
            content = it.content
        )
    }
    val listState = rememberLazyListState()
    val inChatSearchResults = remember(activeMessageEntities, inChatSearchQuery) {
        val query = inChatSearchQuery.trim()
        if (query.isBlank()) {
            emptyList()
        } else {
            activeMessageEntities.filter { message ->
                visibleSearchText(message.content).contains(query, ignoreCase = true)
            }
        }
    }

    fun startRegenerateResponse(
        targetMessage: MessageEntity,
        action: ResponseRegenerationAction
    ) {
        if (isGenerating || regeneratingMessageId != null || extractingMemoryCandidates || FusionRuntimeLock.isChatGenerationRunning) {
            Toast.makeText(context, "현재 응답을 생성하는 중입니다.", Toast.LENGTH_SHORT).show()
            return
        }
        if (FusionRuntimeLock.isBenchmarkRunning) {
            Toast.makeText(context, "현재 응답을 생성하는 중입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val snapshot = messageEntities.toList()
        val targetIndex = snapshot.indexOfFirst { it.id == targetMessage.id }
        if (targetIndex <= 0 || targetMessage.role == "user") {
            Toast.makeText(context, "답변을 다시 생성할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val storedGroupId = responseVersionState.groupByMessageId[targetMessage.id]
        val previousUserIndex = storedGroupId
            ?.let { groupId -> snapshot.indexOfFirst { it.id == groupId && it.role == "user" } }
            ?.takeIf { it >= 0 }
            ?: (targetIndex - 1 downTo 0).firstOrNull { snapshot[it].role == "user" }
        if (previousUserIndex == null) {
            Toast.makeText(context, "답변을 다시 생성할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val previousUser = snapshot[previousUserIndex]
        val parsedUserMessage = parseMessageAttachments(previousUser.content)
        val previousUserText = parsedUserMessage.body.trim()
        val originalAssistantText = visibleSearchText(targetMessage.content)
        val isStyleRegeneration = action.instruction != null
        val attachmentsToSend = if (isStyleRegeneration) emptyList() else parsedUserMessage.attachments
        val imageAttachments = attachmentsToSend.filter { isImageAttachment(it) }
        val nonImageAttachments = attachmentsToSend.filterNot { isImageAttachment(it) }
        val userInstruction = if (isStyleRegeneration) {
            """
            ${action.instruction}

            이전 사용자 요청:
            $previousUserText

            이전 답변:
            $originalAssistantText

            새 답변만 작성해 주세요.
            """.trimIndent()
        } else if (imageAttachments.isNotEmpty()) {
            buildImageUserInstruction(previousUserText)
        } else {
            previousUserText
        }
        val historyBeforeUser = snapshot
            .take(previousUserIndex)
            .map { message -> ChatMessage(role = message.role, content = if (message.role == "assistant") visibleAssistantHistoryText(message.content) else message.content) }
        val shouldUseWebSearch = !isStyleRegeneration && (webSearchEnabled || shouldAutoUseWebSearch(previousUserText))
        val externalApiAttachmentBlocked = generationMode == ChatGenerationMode.EXTERNAL_AI_API &&
            attachmentsToSend.isNotEmpty()
        val shouldUseWebSearchForRequest = shouldUseWebSearch && !externalApiAttachmentBlocked

if (!isStyleRegeneration && generationMode != ChatGenerationMode.EXTERNAL_AI_API) {
            val requestId = UUID.randomUUID().toString()
            val capturedSettings = generationSettings.copy(
                speculativeDecodingEnabled = resolveSpeculativeDecodingEnabled(
                    modelName = selectedModel,
                    settings = generationSettings
                )
            )
            val retrySnapshot = GenerationRequestSnapshot(
                requestId = requestId,
                conversationId = previousUser.conversationId,
                generationModeKey = generationMode.name,
                selectedModelId = selectedModel,
                selectedModelPath = selectedModelPath,
                settings = capturedSettings,
                reasoningEnabled = reasoningEnabled,
                webSearchPolicy = when {
                    webSearchEnabled -> GenerationRequestSnapshot.WebSearchPolicy.ENABLED
                    shouldAutoUseWebSearch(previousUserText) -> GenerationRequestSnapshot.WebSearchPolicy.AUTO
                    else -> GenerationRequestSnapshot.WebSearchPolicy.DISABLED
                },
                attachmentIds = attachmentsToSend.map { it.localPath },
                multimodalImagePaths = imageAttachments.map { it.localPath },
                promptText = userInstruction,
                rawUserText = previousUserText,
                createdAt = System.currentTimeMillis(),
                history = historyBeforeUser,
                promptLabInstruction = buildPromptLabInstruction(loadPromptLabSettings(context)),
            )
            chatViewModel.update(retrySnapshot.conversationId) {
                it.copy(
                    isGenerating = true,
                    activeRequestId = requestId,
                    streamingText = null,
                    streamingMetricsLine = null,
                    generationStatus = if (retrySnapshot.webSearchPolicy == GenerationRequestSnapshot.WebSearchPolicy.DISABLED) "모델 로딩 중..." else "인터넷 검색 중...",
                    regeneratingMessageId = targetMessage.id,
                    actualWebSearchUsed = false,
                )
            }
            chatViewModel.scope.launch {
                try {
                    chatViewModel.registry.start(chatViewModel.scope, retrySnapshot) { session ->
                        val request = retrySnapshot
                        try {
                            val webSearchResponse = if (request.webSearchPolicy != GenerationRequestSnapshot.WebSearchPolicy.DISABLED) {
                                chatViewModel.updateRequestState(request.conversationId, request.requestId, true) { it.copy(generationStatus = "인터넷 검색 중...") }
                                val response = FusionWebSearch.search(
                                    userInput = request.rawUserText,
                                    previousUserMessage = request.history.asReversed().firstOrNull { it.role == "user" }?.content.orEmpty(),
                                    providerRepository = webSearchProviderRepository
                                )
                                chatViewModel.updateRequestState(request.conversationId, request.requestId, true) { it.copy(generationStatus = "검색 결과 정리 중...") }
                                response
                            } else null
                            val actualWebSearchUsed = webSearchResponse != null
                            chatViewModel.updateRequestState(request.conversationId, request.requestId, true) { it.copy(actualWebSearchUsed = actualWebSearchUsed) }
                            val webSearchResult = webSearchResponse?.toStructuredContext()
                            val modelPath = request.selectedModelPath?.takeIf { File(it).exists() }
                                ?: builtInModels.firstOrNull { it.name == request.selectedModelId && isModelDownloaded(context, it) }
                                    ?.let { getModelFile(context, it).absolutePath }
                            if (modelPath == null) {
                                if (chatViewModel.registry.isActive(request.conversationId, request.requestId) && currentConversationId == request.conversationId) {
                                    Toast.makeText(context, "모델 파일을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                                }
                                return@start
                            }
                            val missingImage = imageAttachments.firstOrNull { !File(it.localPath).exists() }
                            if (missingImage != null) {
                                if (chatViewModel.registry.isActive(request.conversationId, request.requestId) && currentConversationId == request.conversationId) {
                                    Toast.makeText(context, "이미지 파일을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                                }
                                return@start
                            }
                            if (imageAttachments.isNotEmpty() && !isMultimodalCapableModel(request.selectedModelId.orEmpty(), modelPath)) {
                                if (chatViewModel.registry.isActive(request.conversationId, request.requestId) && currentConversationId == request.conversationId) {
                                    Toast.makeText(context, "이 모델은 이미지 입력을 지원하지 않습니다.", Toast.LENGTH_SHORT).show()
                                }
                                return@start
                            }
                            val messagesForGeneration = buildList<ChatMessage> {
                                add(
                                    ChatMessage(
                                        role = "system",
                                        content = """
                                        FUSION_GENERATION_SETTINGS
                                        maxTokens=${request.settings.maxTokens}
                                        topK=${request.settings.topK}
                                        topP=${request.settings.topP}
                                        temperature=${request.settings.temperature}
                                        accelerator=${request.settings.accelerator.name}
                                        reasoningBudgetTokens=${request.settings.reasoningBudgetTokens}
                                        speculativeDecoding=${request.settings.speculativeDecodingEnabled == true}
                                        """.trimIndent()
                                    )
                                )
                                add(ChatMessage(role = "system", content = buildFusionSystemPrompt(request.reasoningEnabled, actualWebSearchUsed, webSearchResult, request.promptLabInstruction)))
                                request.selectedModelPath?.let { add(ChatMessage(role = "system", content = "FUSION_SELECTED_MODEL_PATH=$it")) }
                                add(ChatMessage(role = "system", content = "FUSION_MODEL_FAMILY=${FusionModelCatalog.inferFamily(context, request.selectedModelId.orEmpty()).name}"))
                                buildSavedMemoryContext(context, settingsPrefs, request.conversationId, request.selectedModelId.orEmpty()).text?.let { add(ChatMessage(role = "system", content = it)) }
                                buildConversationSummaryContextText(loadConversationSummary(context, request.conversationId))?.let { add(ChatMessage(role = "system", content = it)) }
                                addAll(request.history)
                                add(ChatMessage(role = "user", content = buildFinalUserContent(request.promptText, if (imageAttachments.isNotEmpty()) nonImageAttachments else attachmentsToSend, actualWebSearchUsed, webSearchResult)))
                            }
                            val isImageGeneration = imageAttachments.isNotEmpty()
                            val isReasoningEnabled = request.reasoningEnabled
                            when {
                                isImageGeneration && !isReasoningEnabled -> {
                                    chatViewModel.updateRequestState(request.conversationId, request.requestId, true) { it.copy(generationStatus = "이미지 분석 준비 중...") }
                                }
                                isImageGeneration && isReasoningEnabled -> {
                                    chatViewModel.updateRequestState(request.conversationId, request.requestId, true) { it.copy(generationStatus = "이미지 분석 준비 중...") }
                                }
                                !isImageGeneration && isReasoningEnabled -> {
                                    chatViewModel.updateRequestState(request.conversationId, request.requestId, true) { it.copy(generationStatus = "더 깊게 생각하는 중...") }
                                }
                                else -> {
                                    chatViewModel.updateRequestState(request.conversationId, request.requestId, true) { it.copy(generationStatus = "답변 생성 중...") }
                                }
                            }
                            val started = SystemClock.elapsedRealtime()
                            val firstToken = java.util.concurrent.atomic.AtomicLong(-1L)
                            val outcome = FusionRuntimeLock.withChatGeneration {
                                generateWithLiteRtRecovery(
                                    engine = engine,
                                    onBeforeRetry = { chatViewModel.updateRequestState(request.conversationId, request.requestId, true) { it.copy(streamingText = null, generationStatus = "모델 로딩 중...") } },
                                    generateOnce = {
                                        val requestScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.currentCoroutineContext())
                                        if (isImageGeneration && !isReasoningEnabled) {
                                            val coalescer = TokenCoalescer(requestScope, shouldPublish = { chatViewModel.registry.isActive(request.conversationId, request.requestId) }, onPublish = { text -> chatViewModel.updateRequestState(request.conversationId, request.requestId, true) { it.copy(streamingText = text) } })
                                            chatViewModel.updateRequestState(request.conversationId, request.requestId, true) { it.copy(streamingText = "") }
                                            try {
                                                chatViewModel.updateRequestState(request.conversationId, request.requestId, true) { it.copy(generationStatus = "이미지 분석 중...") }
                                                val result = engine.generateMultimodalStreaming(messagesForGeneration, modelPath, request.settings, imageAttachments.map { it.localPath }) { token ->
                                                    if (token.isNotEmpty()) firstToken.compareAndSet(-1L, SystemClock.elapsedRealtime() - started)
                                                    coalescer.append(token)
                                                }
                                                if (result is GenerationOutcome.Success) coalescer.finish() else coalescer.abort()
                                                result
                                            } catch (e: Exception) { coalescer.abort(); throw e }
                                        } else if (isImageGeneration) {
                                            chatViewModel.updateRequestState(request.conversationId, request.requestId, true) { it.copy(generationStatus = "이미지 분석 중...") }
                                            engine.generateMultimodalStreaming(messagesForGeneration, modelPath, request.settings, imageAttachments.map { it.localPath }) { token ->
                                                if (token.isNotEmpty()) firstToken.compareAndSet(-1L, SystemClock.elapsedRealtime() - started)
                                            }
                                        } else if (!isReasoningEnabled) {
                                            val coalescer = TokenCoalescer(requestScope, shouldPublish = { chatViewModel.registry.isActive(request.conversationId, request.requestId) }, onPublish = { text -> chatViewModel.updateRequestState(request.conversationId, request.requestId, true) { it.copy(streamingText = text) } })
                                            chatViewModel.updateRequestState(request.conversationId, request.requestId, true) { it.copy(streamingText = "") }
                                            try {
                                                val result = engine.generateStreaming(messagesForGeneration, modelPath, request.settings) { token ->
                                                    if (token.isNotEmpty()) firstToken.compareAndSet(-1L, SystemClock.elapsedRealtime() - started)
                                                    coalescer.append(token)
                                                }
                                                if (result is GenerationOutcome.Success) coalescer.finish() else coalescer.abort()
                                                result
                                            } catch (e: Exception) { coalescer.abort(); throw e }
                                        } else {
                                            engine.generate(messagesForGeneration, modelPath, request.settings)
                                        }
                                    }
                                )
                            }
                            if (!chatViewModel.registry.isActive(request.conversationId, request.requestId)) return@start
                            val reply = when (outcome) {
                                is GenerationOutcome.Success -> outcome.text
                                is GenerationOutcome.Cancelled -> return@start
                                GenerationOutcome.Empty -> return@start
                                is GenerationOutcome.Failure -> {
                                    if (chatViewModel.registry.isActive(request.conversationId, request.requestId) && currentConversationId == request.conversationId) {
                                        Toast.makeText(context, outcome.message, Toast.LENGTH_SHORT).show()
                                    }
                                    return@start
                                }
                            }
                            val metrics = buildFusionMetricsLine(shortModelName(request.selectedModelId.orEmpty()), buildAcceleratorLabel(request.settings.accelerator.name, request.settings.speculativeDecodingEnabled == true), reply, SystemClock.elapsedRealtime() - started, firstToken.get().takeIf { it >= 0L }, buildEffectiveSettingsLine(buildEffectiveRuntimeSettings(request.selectedModelId.orEmpty(), modelPath, request.settings, request.reasoningEnabled, actualWebSearchUsed, engine.lastMtpStatus)))
                            if (!chatViewModel.registry.isActive(request.conversationId, request.requestId)) return@start
                            chatViewModel.updateRequestState(request.conversationId, request.requestId, true) { it.copy(streamingMetricsLine = metrics, generationStatus = "답변 저장 중...") }
                            if (!chatViewModel.registry.isActive(request.conversationId, request.requestId)) return@start
                            val persistedUpdatedState = withContext(NonCancellable) {
                                val previousVersionState = loadResponseVersionState(context, request.conversationId)
                                var insertedMessageId: Long? = null
                                var versionStateSaved = false
                                try {
                                    val newId = dao.insertMessage(MessageEntity(conversationId = request.conversationId, role = "assistant", content = appendSearchSourcesMetadata(appendFusionMetrics(reply, metrics), webSearchResponse?.sources.orEmpty()), createdAt = System.currentTimeMillis()))
                                    insertedMessageId = newId
                                    val groupId = previousUser.id
                                    val updated = previousVersionState.copy(groupByMessageId = previousVersionState.groupByMessageId + (targetMessage.id to groupId) + (newId to groupId), activeMessageIdByGroup = previousVersionState.activeMessageIdByGroup + (groupId to newId))
                                    saveResponseVersionState(context, request.conversationId, updated)
                                    versionStateSaved = true
                                    dao.updateConversationTime(request.conversationId, System.currentTimeMillis())
                                    updated
                                } catch (e: Exception) {
                                    if (insertedMessageId != null) {
                                        try {
                                            dao.deleteMessageById(insertedMessageId)
                                        } catch (rollbackException: Exception) {
                                            e.addSuppressed(rollbackException)
                                        }
                                    }
                                    if (versionStateSaved) {
                                        try {
                                            saveResponseVersionState(context, request.conversationId, previousVersionState)
                                        } catch (rollbackException: Exception) {
                                            e.addSuppressed(rollbackException)
                                        }
                                    }
                                    throw e
                                }
                            }
                            if (currentConversationId == request.conversationId) {
                                responseVersionState = persistedUpdatedState
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            if (chatViewModel.registry.isActive(request.conversationId, request.requestId) && currentConversationId == request.conversationId) {
                                Toast.makeText(context, "오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                            }
                        } finally {
                            chatViewModel.finishRequestState(request.conversationId, request.requestId)
                        }
                    }
                } catch (e: Exception) {
                    chatViewModel.finishRequestState(retrySnapshot.conversationId, retrySnapshot.requestId)

                    if (e is CancellationException) throw e

                    if (currentConversationId == retrySnapshot.conversationId) {
                        Toast.makeText(context, "오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            return
        }

        if (isStyleRegeneration && generationMode != ChatGenerationMode.EXTERNAL_AI_API) {
            val requestId = UUID.randomUUID().toString()
            val capturedSettings = generationSettings.copy(
                speculativeDecodingEnabled = resolveSpeculativeDecodingEnabled(
                    modelName = selectedModel,
                    settings = generationSettings
                )
            )
            val styleSnapshot = GenerationRequestSnapshot(
                requestId = requestId,
                conversationId = previousUser.conversationId,
                generationModeKey = generationMode.name,
                selectedModelId = selectedModel,
                selectedModelPath = selectedModelPath,
                settings = capturedSettings,
                reasoningEnabled = reasoningEnabled,
                webSearchPolicy = GenerationRequestSnapshot.WebSearchPolicy.DISABLED,
                attachmentIds = emptyList(),
                multimodalImagePaths = emptyList(),
                promptText = userInstruction,
                rawUserText = previousUserText,
                createdAt = System.currentTimeMillis(),
                history = historyBeforeUser,
                promptLabInstruction = buildPromptLabInstruction(loadPromptLabSettings(context)),
            )
            chatViewModel.update(styleSnapshot.conversationId) {
                it.copy(
                    isGenerating = true,
                    activeRequestId = requestId,
                    streamingText = null,
                    streamingMetricsLine = null,
                    generationStatus = "답변을 다시 작성하는 중입니다.",
                    regeneratingMessageId = targetMessage.id,
                    actualWebSearchUsed = false,
                )
            }
            chatViewModel.scope.launch {
                try {
                    chatViewModel.registry.start(chatViewModel.scope, styleSnapshot) { session ->
                        val request = styleSnapshot
                        try {
                            val modelPath = request.selectedModelPath?.takeIf { File(it).exists() }
                                ?: builtInModels.firstOrNull { it.name == request.selectedModelId && isModelDownloaded(context, it) }
                                    ?.let { getModelFile(context, it).absolutePath }
                            if (modelPath == null) {
                                if (chatViewModel.registry.isActive(request.conversationId, request.requestId) && currentConversationId == request.conversationId) {
                                    Toast.makeText(context, "모델 파일을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                                }
                                return@start
                            }
                            val messagesForGeneration = buildList<ChatMessage> {
                                add(
                                    ChatMessage(
                                        role = "system",
                                        content = """
                                        FUSION_GENERATION_SETTINGS
                                        maxTokens=${request.settings.maxTokens}
                                        topK=${request.settings.topK}
                                        topP=${request.settings.topP}
                                        temperature=${request.settings.temperature}
                                        accelerator=${request.settings.accelerator.name}
                                        reasoningBudgetTokens=${request.settings.reasoningBudgetTokens}
                                        speculativeDecoding=${request.settings.speculativeDecodingEnabled == true}
                                        """.trimIndent()
                                    )
                                )
                                add(ChatMessage(role = "system", content = buildFusionSystemPrompt(request.reasoningEnabled, false, null, request.promptLabInstruction)))
                                request.selectedModelPath?.let { add(ChatMessage(role = "system", content = "FUSION_SELECTED_MODEL_PATH=$it")) }
                                add(ChatMessage(role = "system", content = "FUSION_MODEL_FAMILY=${FusionModelCatalog.inferFamily(context, request.selectedModelId.orEmpty()).name}"))
                                buildSavedMemoryContext(context, settingsPrefs, request.conversationId, request.selectedModelId.orEmpty()).text?.let { add(ChatMessage(role = "system", content = it)) }
                                buildConversationSummaryContextText(loadConversationSummary(context, request.conversationId))?.let { add(ChatMessage(role = "system", content = it)) }
                                addAll(request.history)
                                add(ChatMessage(role = "user", content = request.promptText))
                            }
                            val isReasoningEnabled = request.reasoningEnabled
                            chatViewModel.updateRequestState(request.conversationId, request.requestId, true) {
                                it.copy(generationStatus = if (isReasoningEnabled) "더 깊게 생각하는 중..." else "답변을 다시 작성하는 중입니다.")
                            }
                            val started = SystemClock.elapsedRealtime()
                            val firstToken = java.util.concurrent.atomic.AtomicLong(-1L)
                            val outcome = FusionRuntimeLock.withChatGeneration {
                                generateWithLiteRtRecovery(
                                    engine = engine,
                                    onBeforeRetry = { chatViewModel.updateRequestState(request.conversationId, request.requestId, true) { it.copy(streamingText = null, generationStatus = "모델 로딩 중...") } },
                                    generateOnce = {
                                        val requestScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.currentCoroutineContext())
                                        if (!isReasoningEnabled) {
                                            val coalescer = TokenCoalescer(requestScope, shouldPublish = { chatViewModel.registry.isActive(request.conversationId, request.requestId) }, onPublish = { text -> chatViewModel.updateRequestState(request.conversationId, request.requestId, true) { it.copy(streamingText = text) } })
                                            chatViewModel.updateRequestState(request.conversationId, request.requestId, true) { it.copy(streamingText = "") }
                                            try {
                                                val result = engine.generateStreaming(messagesForGeneration, modelPath, request.settings) { token ->
                                                    if (token.isNotEmpty()) firstToken.compareAndSet(-1L, SystemClock.elapsedRealtime() - started)
                                                    coalescer.append(token)
                                                }
                                                if (result is GenerationOutcome.Success) coalescer.finish() else coalescer.abort()
                                                result
                                            } catch (e: Exception) { coalescer.abort(); throw e }
                                        } else {
                                            engine.generate(messagesForGeneration, modelPath, request.settings)
                                        }
                                    }
                                )
                            }
                            if (!chatViewModel.registry.isActive(request.conversationId, request.requestId)) return@start
                            val reply = when (outcome) {
                                is GenerationOutcome.Success -> outcome.text
                                is GenerationOutcome.Cancelled -> return@start
                                GenerationOutcome.Empty -> return@start
                                is GenerationOutcome.Failure -> {
                                    if (chatViewModel.registry.isActive(request.conversationId, request.requestId) && currentConversationId == request.conversationId) {
                                        Toast.makeText(context, outcome.message, Toast.LENGTH_SHORT).show()
                                    }
                                    return@start
                                }
                            }
                            val metrics = buildFusionMetricsLine(shortModelName(request.selectedModelId.orEmpty()), buildAcceleratorLabel(request.settings.accelerator.name, request.settings.speculativeDecodingEnabled == true), reply, SystemClock.elapsedRealtime() - started, firstToken.get().takeIf { it >= 0L }, buildEffectiveSettingsLine(buildEffectiveRuntimeSettings(request.selectedModelId.orEmpty(), modelPath, request.settings, request.reasoningEnabled, false, engine.lastMtpStatus)))
                            if (!chatViewModel.registry.isActive(request.conversationId, request.requestId)) return@start
                            chatViewModel.updateRequestState(request.conversationId, request.requestId, true) { it.copy(streamingMetricsLine = metrics, generationStatus = "답변 저장 중...") }
                            if (!chatViewModel.registry.isActive(request.conversationId, request.requestId)) return@start
                            val persistedUpdatedState = withContext(NonCancellable) {
                                val previousVersionState = loadResponseVersionState(context, request.conversationId)
                                var insertedMessageId: Long? = null
                                var versionStateSaved = false
                                try {
                                    val newId = dao.insertMessage(MessageEntity(conversationId = request.conversationId, role = "assistant", content = appendSearchSourcesMetadata(appendFusionMetrics(reply, metrics), emptyList()), createdAt = System.currentTimeMillis()))
                                    insertedMessageId = newId
                                    val groupId = previousUser.id
                                    val updated = previousVersionState.copy(
                                        groupByMessageId = previousVersionState.groupByMessageId + (targetMessage.id to groupId) + (newId to groupId),
                                        activeMessageIdByGroup = previousVersionState.activeMessageIdByGroup + (groupId to newId)
                                    )
                                    saveResponseVersionState(context, request.conversationId, updated)
                                    versionStateSaved = true
                                    dao.updateConversationTime(request.conversationId, System.currentTimeMillis())
                                    updated
                                } catch (e: Exception) {
                                    if (insertedMessageId != null) {
                                        try { dao.deleteMessageById(insertedMessageId) } catch (rollbackException: Exception) { e.addSuppressed(rollbackException) }
                                    }
                                    if (versionStateSaved) {
                                        try { saveResponseVersionState(context, request.conversationId, previousVersionState) } catch (rollbackException: Exception) { e.addSuppressed(rollbackException) }
                                    }
                                    throw e
                                }
                            }
                            if (currentConversationId == request.conversationId) {
                                responseVersionState = persistedUpdatedState
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            if (chatViewModel.registry.isActive(request.conversationId, request.requestId) && currentConversationId == request.conversationId) {
                                Toast.makeText(context, "오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                            }
                        } finally {
                            chatViewModel.finishRequestState(request.conversationId, request.requestId)
                        }
                    }
                } catch (e: Exception) {
                    chatViewModel.finishRequestState(styleSnapshot.conversationId, styleSnapshot.requestId)
                    if (e is CancellationException) throw e
                    if (currentConversationId == styleSnapshot.conversationId) {
                        Toast.makeText(context, "오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            return
        }

        if (!isStyleRegeneration && generationMode == ChatGenerationMode.EXTERNAL_AI_API) {
            val requestId = UUID.randomUUID().toString()
            val retrySnapshot = GenerationRequestSnapshot(
                requestId = requestId,
                conversationId = previousUser.conversationId,
                generationModeKey = generationMode.name,
                selectedModelId = selectedModel,
                selectedModelPath = null,
                settings = generationSettings.copy(
                    speculativeDecodingEnabled = resolveSpeculativeDecodingEnabled(
                        modelName = selectedModel,
                        settings = generationSettings
                    )
                ),
                reasoningEnabled = reasoningEnabled,
                webSearchPolicy = when {
                    externalApiAttachmentBlocked -> GenerationRequestSnapshot.WebSearchPolicy.DISABLED
                    webSearchEnabled -> GenerationRequestSnapshot.WebSearchPolicy.ENABLED
                    shouldAutoUseWebSearch(previousUserText) -> GenerationRequestSnapshot.WebSearchPolicy.AUTO
                    else -> GenerationRequestSnapshot.WebSearchPolicy.DISABLED
                },
                attachmentIds = attachmentsToSend.map { it.localPath },
                multimodalImagePaths = imageAttachments.map { it.localPath },
                promptText = userInstruction,
                rawUserText = previousUserText,
                createdAt = System.currentTimeMillis(),
                history = historyBeforeUser,
                promptLabInstruction = buildPromptLabInstruction(loadPromptLabSettings(context)),
                externalProviderId = selectedExternalProviderId ?: externalProviders.firstOrNull(::isRunnableExternalProvider)?.id,
            )

            chatViewModel.update(retrySnapshot.conversationId) {
                it.copy(
                    isGenerating = true,
                    activeRequestId = requestId,
                    streamingText = null,
                    streamingMetricsLine = null,
                    generationStatus = if (retrySnapshot.webSearchPolicy != GenerationRequestSnapshot.WebSearchPolicy.DISABLED) "인터넷 검색 중..." else "외부 AI API 응답을 기다리는 중...",
                    regeneratingMessageId = targetMessage.id,
                    actualWebSearchUsed = false,
                )
            }

            chatViewModel.scope.launch {
                try {
                    chatViewModel.registry.start(chatViewModel.scope, retrySnapshot) { s ->
                        if (!chatViewModel.registry.isActive(s.conversationId, s.requestId)) return@start
                        try {
                            if (retrySnapshot.attachmentIds.isNotEmpty()) {
                                if (chatViewModel.registry.isActive(s.conversationId, s.requestId) && currentConversationId == retrySnapshot.conversationId) {
                                    Toast.makeText(context, "현재 외부 AI API 모드에서는 첨부 파일을 전송할 수 없습니다.", Toast.LENGTH_LONG).show()
                                }
                                return@start
                            }

                            // === WEB SEARCH ===
                            val actualWebSearchEnabled = retrySnapshot.webSearchPolicy != GenerationRequestSnapshot.WebSearchPolicy.DISABLED
                            val webSearchResponse = if (actualWebSearchEnabled) {
                                chatViewModel.updateRequestState(s.conversationId, retrySnapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = "인터넷 검색 중...") }
                                val previousUserMsg = retrySnapshot.history
                                    .asReversed()
                                    .firstOrNull { it.role == "user" }
                                    ?.content
                                    ?.let { parseMessageAttachments(it).body }
                                    ?.trim()
                                    .orEmpty()
                                val searchIntent = FusionWebSearch.detectIntent(retrySnapshot.rawUserText)
                                chatViewModel.updateRequestState(s.conversationId, retrySnapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = when (searchIntent) {
                                    SearchIntent.NEWS -> "뉴스 검색 중..."
                                    else -> "인터넷 검색 중..."
                                }) }
                                val response = FusionWebSearch.search(
                                    userInput = retrySnapshot.rawUserText,
                                    previousUserMessage = previousUserMsg,
                                    providerRepository = webSearchProviderRepository
                                )
                                chatViewModel.updateRequestState(s.conversationId, retrySnapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = "검색 결과 정리 중...") }
                                response
                            } else null
                            val actualWebSearchUsed = webSearchResponse != null
                            chatViewModel.updateRequestState(s.conversationId, retrySnapshot.requestId, requireActiveSession = true) { it.copy(actualWebSearchUsed = actualWebSearchUsed) }
                            val webSearchResult = webSearchResponse?.toStructuredContext()
                            recordWebSearchDiagnostics(context, webSearchResponse)

                            // === PROMPT CONSTRUCTION ===
                            val requestSettings = retrySnapshot.settings.copy(
                                speculativeDecodingEnabled = resolveSpeculativeDecodingEnabled(
                                    modelName = retrySnapshot.selectedModelId ?: "",
                                    settings = retrySnapshot.settings
                                )
                            )
                            val fusionSystemPrompt = buildFusionSystemPrompt(
                                reasoningEnabled = retrySnapshot.reasoningEnabled,
                                webSearchEnabled = actualWebSearchUsed,
                                webContext = webSearchResult,
                                promptLabInstruction = retrySnapshot.promptLabInstruction
                            )
                            val currentMessages = buildList<ChatMessage> {
                                add(ChatMessage(role = "system", content = """
                    FUSION_GENERATION_SETTINGS
                    maxTokens=${requestSettings.maxTokens}
                    topK=${requestSettings.topK}
                    topP=${requestSettings.topP}
                    temperature=${requestSettings.temperature}
                    accelerator=${requestSettings.accelerator.name}
                    reasoningBudgetTokens=${requestSettings.reasoningBudgetTokens}
                    speculativeDecoding=${requestSettings.speculativeDecodingEnabled == true}
                """.trimIndent()))
                                add(ChatMessage(role = "system", content = fusionSystemPrompt))
                                add(ChatMessage(role = "system", content = "FUSION_MODEL_FAMILY=${FusionModelCatalog.inferFamily(context, retrySnapshot.selectedModelId ?: "").name}"))
                                buildSavedMemoryContext(context, settingsPrefs, s.conversationId, retrySnapshot.selectedModelId ?: "").text?.let { memoryContext ->
                                    add(ChatMessage(role = "system", content = memoryContext))
                                }
                                buildConversationSummaryContextText(loadConversationSummary(context, s.conversationId))?.let { summaryContext ->
                                    add(ChatMessage(role = "system", content = summaryContext))
                                }
                                addAll(retrySnapshot.history)
                                add(ChatMessage(role = "user", content = buildFinalUserContent(
                                    body = retrySnapshot.promptText,
                                    attachments = emptyList(),
                                    webSearchEnabled = actualWebSearchUsed,
                                    webSearchResult = webSearchResult
                                )))
                            }

                            chatViewModel.updateRequestState(s.conversationId, retrySnapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = "외부 AI API 응답을 기다리는 중...") }

                            // === EXTERNAL REQUEST ===
                            when (val result = runExternalAiRequest(currentMessages = currentMessages, hasAttachments = retrySnapshot.attachmentIds.isNotEmpty(), providerId = retrySnapshot.externalProviderId)) {
                                is ExternalAiChatResult.Success -> {
                                    refreshExternalProviderState()
                                    chatViewModel.updateRequestState(s.conversationId, retrySnapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = "답변 저장 중...") }
                                    if (!chatViewModel.registry.isActive(s.conversationId, s.requestId)) return@start
                                    withContext(NonCancellable) {
                                        val previousVersionState = loadResponseVersionState(context, retrySnapshot.conversationId)
                                        var insertedMessageId: Long? = null
                                        var versionStateSaved = false
                                        try {
                                            val newId = dao.insertMessage(MessageEntity(conversationId = retrySnapshot.conversationId, role = "assistant", content = appendSearchSourcesMetadata(result.content, webSearchResponse?.sources.orEmpty()), createdAt = System.currentTimeMillis()))
                                            insertedMessageId = newId
                                            val groupId = previousUser.id
                                            val updated = previousVersionState.copy(
                                                groupByMessageId = previousVersionState.groupByMessageId + (targetMessage.id to groupId) + (newId to groupId),
                                                activeMessageIdByGroup = previousVersionState.activeMessageIdByGroup + (groupId to newId)
                                            )
                                            saveResponseVersionState(context, retrySnapshot.conversationId, updated)
                                            versionStateSaved = true
                                            dao.updateConversationTime(retrySnapshot.conversationId, System.currentTimeMillis())
                                            updated
                                        } catch (e: Exception) {
                                            if (insertedMessageId != null) {
                                                try { dao.deleteMessageById(insertedMessageId) } catch (inner: Exception) { e.addSuppressed(inner) }
                                            }
                                            if (versionStateSaved) {
                                                try { saveResponseVersionState(context, retrySnapshot.conversationId, previousVersionState) } catch (inner: Exception) { e.addSuppressed(inner) }
                                            }
                                            throw e
                                        }
                                    }.let { updated ->
                                        if (currentConversationId == retrySnapshot.conversationId) {
                                            responseVersionState = updated
                                        }
                                    }
                                }
                                is ExternalAiChatResult.BlockedAttachment -> {
                                    if (chatViewModel.registry.isActive(s.conversationId, s.requestId) && currentConversationId == retrySnapshot.conversationId) {
                                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                                is ExternalAiChatResult.NoProvider -> {
                                    refreshExternalProviderState()
                                    if (chatViewModel.registry.isActive(s.conversationId, s.requestId) && currentConversationId == retrySnapshot.conversationId) {
                                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                                is ExternalAiChatResult.Empty -> {
                                    if (chatViewModel.registry.isActive(s.conversationId, s.requestId) && currentConversationId == retrySnapshot.conversationId) {
                                        Toast.makeText(context, "외부 AI API에서 빈 응답을 받았습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                is ExternalAiChatResult.Error -> {
                                    if (chatViewModel.registry.isActive(s.conversationId, s.requestId) && currentConversationId == retrySnapshot.conversationId) {
                                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            if (chatViewModel.registry.isActive(s.conversationId, s.requestId) && currentConversationId == retrySnapshot.conversationId) {
                                Toast.makeText(context, "오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                            }
                        } finally {
                            chatViewModel.finishRequestState(s.conversationId, retrySnapshot.requestId)
                        }
                    }
                } catch (e: Exception) {
                    chatViewModel.finishRequestState(retrySnapshot.conversationId, retrySnapshot.requestId)
                    if (e is CancellationException) throw e
                    if (currentConversationId == retrySnapshot.conversationId) {
                        Toast.makeText(context, "오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            return
        }

        isGenerating = true
        regeneratingMessageId = targetMessage.id
        streamingAssistantText = null
        streamingMetricsLine = null
        generationStatus = "답변을 다시 생성하는 중입니다."
        DeveloperLogStore.record(context, "regeneration", "답변 다시 생성 시작", action.menuLabel)

        scope.launch {
            val activeConversationId = targetMessage.conversationId
            try {
                val previousUserMessage = historyBeforeUser
                    .asReversed()
                    .firstOrNull { it.role == "user" }
                    ?.content
                    ?.let { parseMessageAttachments(it).body }
                    ?.trim()
                    .orEmpty()

                val webSearchResponse = if (shouldUseWebSearchForRequest) {
                    FusionWebSearch.search(
                        userInput = previousUserText,
                        previousUserMessage = previousUserMessage,
                        providerRepository = webSearchProviderRepository
                    )
                } else {
                    null
                }
                val webSearchResult = webSearchResponse?.toStructuredContext()
                recordWebSearchDiagnostics(context, webSearchResponse)

                val mtpEnabledForRequest = resolveSpeculativeDecodingEnabled(
                    modelName = selectedModel,
                    settings = generationSettings
                )
                val requestSettings = generationSettings.copy(
                    speculativeDecodingEnabled = mtpEnabledForRequest
                )
                val fusionSystemPrompt = buildFusionSystemPrompt(
                    reasoningEnabled = reasoningEnabled,
                    webSearchEnabled = shouldUseWebSearchForRequest,
                    webContext = webSearchResult,
                    promptLabInstruction = buildPromptLabInstruction(loadPromptLabSettings(context))
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
                    add(ChatMessage(role = "system", content = fusionSystemPrompt))
                    if (generationMode != ChatGenerationMode.EXTERNAL_AI_API) {
                        selectedModelPath?.let { modelPath ->
                            add(ChatMessage(role = "system", content = "FUSION_SELECTED_MODEL_PATH=$modelPath"))
                        }
                    }
                    add(ChatMessage(role = "system", content = "FUSION_MODEL_FAMILY=${FusionModelCatalog.inferFamily(context, selectedModel).name}"))
                    buildSavedMemoryContext(context, settingsPrefs, targetMessage.conversationId, selectedModel).text?.let { memoryContext ->
                        add(ChatMessage(role = "system", content = memoryContext))
                    }
                    buildConversationSummaryContextText(loadConversationSummary(context, targetMessage.conversationId))?.let { summaryContext ->
                        add(ChatMessage(role = "system", content = summaryContext))
                    }
                    addAll(historyBeforeUser)
                    add(
                        ChatMessage(
                            role = "user",
                            content = buildFinalUserContent(
                                body = userInstruction,
                                attachments = if (imageAttachments.isNotEmpty()) nonImageAttachments else attachmentsToSend,
                                webSearchEnabled = shouldUseWebSearchForRequest,
                                webSearchResult = webSearchResult
                            )
                        )
                    )
                }

                if (generationMode == ChatGenerationMode.EXTERNAL_AI_API) {
                    when (
                        val result = runExternalAiRequest(
                            currentMessages = currentMessages,
                            hasAttachments = attachmentsToSend.isNotEmpty()
                        )
                    ) {
                        is ExternalAiChatResult.Success -> {
                            refreshExternalProviderState()
                            generationStatus = "답변 저장 중..."
                            val newMessageId = dao.insertMessage(
                                MessageEntity(
                                    conversationId = activeConversationId,
                                    role = "assistant",
                                    content = appendSearchSourcesMetadata(result.content, webSearchResponse?.sources.orEmpty()),
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            val groupId = previousUser.id
                            val updatedVersionState = responseVersionState.copy(
                                groupByMessageId = responseVersionState.groupByMessageId +
                                    (targetMessage.id to groupId) +
                                    (newMessageId to groupId),
                                activeMessageIdByGroup = responseVersionState.activeMessageIdByGroup +
                                    (groupId to newMessageId)
                            )
                            saveResponseVersionState(context, activeConversationId, updatedVersionState)
                            responseVersionState = updatedVersionState
                            dao.updateConversationTime(activeConversationId, System.currentTimeMillis())
                            val versionCount = buildChatTimeline(
                                messageEntities + MessageEntity(
                                    id = newMessageId,
                                    conversationId = activeConversationId,
                                    role = "assistant",
                                    content = "",
                                    createdAt = System.currentTimeMillis()
                                ),
                                updatedVersionState
                            ).filterIsInstance<ChatTimelineItem.AssistantVersions>()
                                .firstOrNull { it.groupId == groupId }
                                ?.versions
                                ?.size
                                ?: 1
                            DeveloperLogStore.record(context, "regeneration", "답변 다시 생성 성공", "versions=$versionCount")
                            Toast.makeText(context, "답변을 다시 생성했습니다.", Toast.LENGTH_SHORT).show()
                        }

                        is ExternalAiChatResult.BlockedAttachment -> {
                            generationStatus = "답변 저장 중..."
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            val newMessageId = dao.insertMessage(
                                MessageEntity(
                                    conversationId = activeConversationId,
                                    role = "assistant",
                                    content = result.message,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            val groupId = previousUser.id
                            val updatedVersionState = responseVersionState.copy(
                                groupByMessageId = responseVersionState.groupByMessageId +
                                    (targetMessage.id to groupId) +
                                    (newMessageId to groupId),
                                activeMessageIdByGroup = responseVersionState.activeMessageIdByGroup +
                                    (groupId to newMessageId)
                            )
                            saveResponseVersionState(context, activeConversationId, updatedVersionState)
                            responseVersionState = updatedVersionState
                            dao.updateConversationTime(activeConversationId, System.currentTimeMillis())
                        }

                        is ExternalAiChatResult.NoProvider -> {
                            refreshExternalProviderState()
                            generationStatus = "답변 저장 중..."
                            val newMessageId = dao.insertMessage(
                                MessageEntity(
                                    conversationId = activeConversationId,
                                    role = "assistant",
                                    content = result.message,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            val groupId = previousUser.id
                            val updatedVersionState = responseVersionState.copy(
                                groupByMessageId = responseVersionState.groupByMessageId +
                                    (targetMessage.id to groupId) +
                                    (newMessageId to groupId),
                                activeMessageIdByGroup = responseVersionState.activeMessageIdByGroup +
                                    (groupId to newMessageId)
                            )
                            saveResponseVersionState(context, activeConversationId, updatedVersionState)
                            responseVersionState = updatedVersionState
                            dao.updateConversationTime(activeConversationId, System.currentTimeMillis())
                        }

                        is ExternalAiChatResult.Empty -> {
                            Toast.makeText(context, "외부 AI API에서 빈 응답을 받았습니다.", Toast.LENGTH_SHORT).show()
                        }

                        is ExternalAiChatResult.Error -> {
                            generationStatus = "답변 저장 중..."
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            val newMessageId = dao.insertMessage(
                                MessageEntity(
                                    conversationId = activeConversationId,
                                    role = "assistant",
                                    content = result.message,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            val groupId = previousUser.id
                            val updatedVersionState = responseVersionState.copy(
                                groupByMessageId = responseVersionState.groupByMessageId +
                                    (targetMessage.id to groupId) +
                                    (newMessageId to groupId),
                                activeMessageIdByGroup = responseVersionState.activeMessageIdByGroup +
                                    (groupId to newMessageId)
                            )
                            saveResponseVersionState(context, activeConversationId, updatedVersionState)
                            responseVersionState = updatedVersionState
                            dao.updateConversationTime(activeConversationId, System.currentTimeMillis())
                        }
                    }
                    return@launch
                }

                val activeModelPath = selectedModelPath?.takeIf { File(it).exists() }
                    ?: builtInModels
                        .firstOrNull { it.name == selectedModel && isModelDownloaded(context, it) }
                        ?.let { getModelFile(context, it).absolutePath }

                if (activeModelPath == null) {
                    DeveloperLogStore.record(context, "regeneration", "답변 다시 생성 실패", "model file missing")
                    Toast.makeText(context, "답변을 다시 생성할 수 없습니다.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val missingImage = imageAttachments.firstOrNull { !File(it.localPath).exists() }
                if (missingImage != null) {
                    DeveloperLogStore.record(context, "regeneration", "답변 다시 생성 실패", "image file missing")
                    Toast.makeText(context, "답변을 다시 생성할 수 없습니다.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val useMultimodalImages = imageAttachments.isNotEmpty()
                if (useMultimodalImages && !isMultimodalCapableModel(selectedModel, activeModelPath!!)) {
                    DeveloperLogStore.record(context, "regeneration", "답변 다시 생성 실패", "multimodal unsupported")
                    Toast.makeText(context, "답변을 다시 생성할 수 없습니다.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val generationStartMs = SystemClock.elapsedRealtime()
                val rawOutcome = FusionRuntimeLock.withChatGeneration {
                    generateWithLiteRtRecovery(
                        engine = engine,
                        onBeforeRetry = {
                            generationStatus = "답변을 다시 생성하는 중입니다."
                        },
                        generateOnce = {
                            if (useMultimodalImages) {
                                engine.generateMultimodalStreaming(
                                    messages = currentMessages,
                                    modelPath = activeModelPath!!,
                                    settings = requestSettings,
                                    imagePaths = imageAttachments.map { it.localPath },
                                    onToken = {}
                                )
                            } else {
                                engine.generate(
                                    messages = currentMessages,
                                    modelPath = activeModelPath!!,
                                    settings = requestSettings
                                )
                            }
                        }
                    )
                }
                val rawReply = when (rawOutcome) {
                    is GenerationOutcome.Success -> rawOutcome.text
                    is GenerationOutcome.Cancelled -> {
                        DeveloperLogStore.record(context, "regeneration", "답변 다시 생성 취소", "cancelled")
                        return@launch
                    }
                    GenerationOutcome.Empty -> {
                        DeveloperLogStore.record(context, "regeneration", "답변 다시 생성 실패", "empty")
                        Toast.makeText(context, "모델 응답이 비어 있습니다.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    is GenerationOutcome.Failure -> {
                        DeveloperLogStore.record(context, "regeneration", "답변 다시 생성 실패", rawOutcome.kind.name)
                        Toast.makeText(context, rawOutcome.message, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
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
                    firstTokenLatencyMs = null,
                    settingsLine = buildEffectiveSettingsLine(
                        buildEffectiveRuntimeSettings(
                            modelName = selectedModel,
                            modelPath = activeModelPath!!,
                            settings = requestSettings,
                            reasoningEnabled = reasoningEnabled,
                            webSearchEnabled = shouldUseWebSearchForRequest,
                            mtpStatus = engine.lastMtpStatus
                        )
                    )
                )

                val newMessageId = dao.insertMessage(
                    MessageEntity(
                        conversationId = activeConversationId,
                        role = "assistant",
                        content = appendSearchSourcesMetadata(appendFusionMetrics(rawReply, metricsLine), webSearchResponse?.sources.orEmpty()),
                        createdAt = System.currentTimeMillis()
                    )
                )
                val groupId = previousUser.id
                val updatedVersionState = responseVersionState.copy(
                    groupByMessageId = responseVersionState.groupByMessageId +
                        (targetMessage.id to groupId) +
                        (newMessageId to groupId),
                    activeMessageIdByGroup = responseVersionState.activeMessageIdByGroup +
                        (groupId to newMessageId)
                )
                saveResponseVersionState(context, activeConversationId, updatedVersionState)
                responseVersionState = updatedVersionState
                dao.updateConversationTime(activeConversationId, System.currentTimeMillis())
                val versionCount = buildChatTimeline(
                    messageEntities + MessageEntity(
                        id = newMessageId,
                        conversationId = activeConversationId,
                        role = "assistant",
                        content = "",
                        createdAt = System.currentTimeMillis()
                    ),
                    updatedVersionState
                ).filterIsInstance<ChatTimelineItem.AssistantVersions>()
                    .firstOrNull { it.groupId == groupId }
                    ?.versions
                    ?.size
                    ?: 1
                DeveloperLogStore.record(context, "regeneration", "답변 다시 생성 성공", "versions=$versionCount")
                Toast.makeText(context, "답변을 다시 생성했습니다.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("FusionEngine", "Response regeneration failed", e)
                DeveloperLogStore.record(context, "regeneration", "답변 다시 생성 실패", e::class.java.simpleName)
                Toast.makeText(context, "답변을 다시 생성할 수 없습니다.", Toast.LENGTH_SHORT).show()
            } finally {
                generationStatus = null
                isGenerating = false
                regeneratingMessageId = null
                streamingAssistantText = null
                streamingMetricsLine = null
            }
        }
    }

    fun startMemoryCandidateExtraction() {
        if (isGenerating || regeneratingMessageId != null || extractingMemoryCandidates || FusionRuntimeLock.isChatGenerationRunning) {
            Toast.makeText(context, "현재 응답을 생성하는 중입니다.", Toast.LENGTH_SHORT).show()
            return
        }
        if (FusionRuntimeLock.isBenchmarkRunning) {
            Toast.makeText(context, "현재 응답을 생성하는 중입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val snapshot = messageEntities.toList()
        if (conversationId == 0L || snapshot.isEmpty()) {
            Toast.makeText(context, "메모리 후보를 추출할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val recentVisibleMessages = snapshot
            .takeLast(12)
            .mapNotNull { entity ->
                val visibleText = if (entity.role == "user") {
                    parseMessageAttachments(entity.content).body.trim()
                } else {
                    visibleSearchText(entity.content).trim()
                }
                visibleText.takeIf { it.isNotBlank() }?.let {
                    ChatMessage(role = entity.role, content = it)
                }
            }
        if (recentVisibleMessages.isEmpty()) {
            Toast.makeText(context, "메모리 후보를 추출할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val mtpEnabledForRequest = resolveSpeculativeDecodingEnabled(
            modelName = selectedModel,
            settings = generationSettings
        )
        val requestSettings = generationSettings.copy(
            speculativeDecodingEnabled = mtpEnabledForRequest
        )
        val extractionPrompt = "현재 대화에서 나중에 참고할 만한 메모리 후보를 추출해 주세요. 사용자의 장기적인 선호, 진행 중인 프로젝트, 반복적으로 참고할 설정, 중요한 결정 사항만 짧은 bullet로 정리해 주세요. 일시적인 감정, 사소한 잡담, 민감한 개인정보, 추측성 내용은 제외해 주세요."
        val currentMessages = buildList {
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
                    content = buildFusionSystemPrompt(
                        reasoningEnabled = reasoningEnabled,
                        webSearchEnabled = false,
                        webContext = null,
                        promptLabInstruction = buildPromptLabInstruction(loadPromptLabSettings(context))
                    )
                )
            )
            if (generationMode != ChatGenerationMode.EXTERNAL_AI_API) {
                selectedModelPath?.let { modelPath ->
                    add(ChatMessage(role = "system", content = "FUSION_SELECTED_MODEL_PATH=$modelPath"))
                }
            }
            add(ChatMessage(role = "system", content = "FUSION_MODEL_FAMILY=${FusionModelCatalog.inferFamily(context, selectedModel).name}"))
            buildConversationSummaryContextText(loadConversationSummary(context, conversationId))?.let { summaryContext ->
                add(ChatMessage(role = "system", content = summaryContext))
            }
            addAll(recentVisibleMessages)
            add(ChatMessage(role = "user", content = extractionPrompt))
        }

        if (generationMode == ChatGenerationMode.EXTERNAL_AI_API) {
            isGenerating = true
            extractingMemoryCandidates = true
            generationStatus = "메모리 후보를 추출하는 중입니다."
            DeveloperLogStore.record(context, "memory", "메모리 후보 추출 시작", "conversationId=$conversationId")

            scope.launch {
                try {
                    when (
                        val result = runExternalAiRequest(
                            currentMessages = currentMessages
                        )
                    ) {
                        is ExternalAiChatResult.Success -> {
                            refreshExternalProviderState()
                            memoryCandidateText = result.content.trim()
                            DeveloperLogStore.record(
                                context,
                                "memory",
                                "메모리 후보 추출 성공",
                                "conversationId=$conversationId, length=${memoryCandidateText.length}, count=${parseMemoryCandidateLines(memoryCandidateText).size}"
                            )
                        }

                        is ExternalAiChatResult.NoProvider -> {
                            refreshExternalProviderState()
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        }

                        is ExternalAiChatResult.BlockedAttachment -> {
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        }

                        is ExternalAiChatResult.Empty -> {
                            Toast.makeText(context, "외부 AI API에서 빈 응답을 받았습니다.", Toast.LENGTH_SHORT).show()
                        }

                        is ExternalAiChatResult.Error -> {
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FusionMemory", "Memory candidate extraction failed", e)
                    DeveloperLogStore.record(context, "memory", "메모리 후보 추출 실패", e::class.java.simpleName)
                    Toast.makeText(context, "메모리 후보를 추출할 수 없습니다.", Toast.LENGTH_SHORT).show()
                } finally {
                    generationStatus = null
                    extractingMemoryCandidates = false
                    isGenerating = false
                    streamingAssistantText = null
                    streamingMetricsLine = null
                }
            }
            return
        }

        val activeModelPath = selectedModelPath?.takeIf { File(it).exists() }
            ?: builtInModels
                .firstOrNull { it.name == selectedModel && isModelDownloaded(context, it) }
                ?.let { getModelFile(context, it).absolutePath }

        if (activeModelPath == null) {
            DeveloperLogStore.record(context, "memory", "메모리 후보 추출 실패", "model file missing")
            Toast.makeText(context, "메모리 후보를 추출할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        isGenerating = true
        extractingMemoryCandidates = true
        generationStatus = "메모리 후보를 추출하는 중입니다."
        DeveloperLogStore.record(context, "memory", "메모리 후보 추출 시작", "conversationId=$conversationId")

        scope.launch {
            try {
                val rawOutcome = FusionRuntimeLock.withChatGeneration {
                    generateWithLiteRtRecovery(
                        engine = engine,
                        onBeforeRetry = {
                            generationStatus = "메모리 후보를 추출하는 중입니다."
                        },
                        generateOnce = {
                            engine.generate(
                                messages = currentMessages,
                                modelPath = activeModelPath,
                                settings = requestSettings
                            )
                        }
                    )
                }
                val rawReply = when (rawOutcome) {
                    is GenerationOutcome.Success -> rawOutcome.text
                    is GenerationOutcome.Cancelled -> {
                        return@launch
                    }
                    GenerationOutcome.Empty -> {
                        Toast.makeText(context, "모델 응답이 비어 있습니다.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    is GenerationOutcome.Failure -> {
                        Toast.makeText(context, "메모리 후보를 추출할 수 없습니다.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                }
                memoryCandidateText = rawReply.trim()
                DeveloperLogStore.record(
                    context,
                    "memory",
                    "메모리 후보 추출 성공",
                    "conversationId=$conversationId, length=${memoryCandidateText.length}, count=${parseMemoryCandidateLines(memoryCandidateText).size}"
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("FusionMemory", "Memory candidate extraction failed", e)
                DeveloperLogStore.record(context, "memory", "메모리 후보 추출 실패", e::class.java.simpleName)
                Toast.makeText(context, "메모리 후보를 추출할 수 없습니다.", Toast.LENGTH_SHORT).show()
            } finally {
                generationStatus = null
                extractingMemoryCandidates = false
                isGenerating = false
                streamingAssistantText = null
                streamingMetricsLine = null
            }
        }
    }

    fun startRegenerateLatestResponse() {
        val safeLatestAssistant = messageEntities.lastOrNull { it.role != "user" }
        if (safeLatestAssistant == null) {
            Toast.makeText(context, "답변을 다시 생성할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        startRegenerateResponse(safeLatestAssistant, ResponseRegenerationAction.Retry)
    }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var permissionPersisted = true
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                permissionPersisted = false
            }

            val displayName = getDisplayNameFromUri(context, uri)
            pendingModelImport = PendingModelImport(
                displayName = displayName,
                originalFileName = displayName,
                uri = uri,
                fileSizeBytes = getFileSizeFromUri(context, uri),
                permissionPersisted = permissionPersisted,
                initialFamily = FusionModelCatalog.inferFamily(context, displayName)
            )
        }
    }

    val externalModelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val displayName = getDisplayNameFromUri(context, uri)
        if (!isModelLikeFileName(displayName)) {
            Toast.makeText(context, "이 파일은 현재 직접 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        var permissionPersisted = true
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            permissionPersisted = false
            Toast.makeText(context, "모델 파일 권한을 유지할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }

        pendingModelImport = PendingModelImport(
            displayName = displayName,
            originalFileName = displayName,
            uri = uri,
            fileSizeBytes = getFileSizeFromUri(context, uri),
            permissionPersisted = permissionPersisted,
            initialFamily = FusionModelCatalog.inferFamily(context, displayName)
        )
    }

    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        val originConversationId = pickerOriginConversationId
        if (originConversationId != conversationId) {
            // Conversation switched between launch and result. Refuse to append
            // to a different conversation. Clean up the (possibly pending) URI
            // grants we no longer intend to keep.
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            Toast.makeText(context, "대화가 변경되어 첨부를 취소했습니다.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        Toast.makeText(context, "첨부 파일 복사 중...", Toast.LENGTH_SHORT).show()

        scope.launch {
            var success = 0
            var failed = 0
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

                if (conversationId != originConversationId) {
                    // Conversation switched during copy; delete the orphan.
                    copiedFile?.delete()
                    return@launch
                }

                if (copiedFile != null) {
                    pendingAttachments.add(
                        LocalAttachment(
                            name = displayName,
                            mimeType = mimeType,
                            localPath = copiedFile.absolutePath
                        )
                    )
                    success++
                } else {
                    failed++
                }
            }

            when {
                success > 0 && failed == 0 -> Toast.makeText(context, "첨부 완료", Toast.LENGTH_SHORT).show()
                success > 0 && failed > 0 -> Toast.makeText(context, "첨부 일부 실패 ($success 성공, $failed 실패)", Toast.LENGTH_SHORT).show()
                else -> Toast.makeText(context, "첨부 실패", Toast.LENGTH_SHORT).show()
            }
        }
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
                    onDeleteChat = {
                        chatMenuExpanded = false
                        showDeleteChatDialog = true
                    },
                    onChatOption = { option ->
                        chatMenuExpanded = false
                        if (option == ChatOptionConversationSummary) {
                            showConversationSummaryDialog = true
                            return@ChatTopBar
                        }
                        if (option == ChatOptionMemoryCandidateExtraction) {
                            showMemoryCandidateDialog = true
                            return@ChatTopBar
                        }
                        if (option == "대화 내 검색") {
                            inChatSearchMode = true
                        } else {
                            Toast.makeText(context, "$option 기능은 다음 단계에서 연결하겠습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { composerHeightPx = it.height }
                    .background(BlackBg)
                    .navigationBarsPadding()
                    .imePadding(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    GenerationModeSelector(
                        mode = generationMode,
                        selectedProviderName = selectedExternalProviderName,
                        selectedProviderId = selectedExternalProviderId,
                        externalProviders = externalProviders,
                        enabled = !isGenerating,
                        onOpenApiSettings = {
                            showAdvancedSettingsDialog = true
                        },
                        onModeSelected = { mode ->
                            generationMode = mode
                            settingsPrefs.edit().putString(PrefGenerationMode, mode.name).apply()
                            if (mode == ChatGenerationMode.EXTERNAL_AI_API) {
                                scope.launch {
                                    refreshExternalProviderState()
                                }
                            }
                        },
                        onExternalProviderSelected = { providerId ->
                            scope.launch {
                                aiProviderRepository.setSelectedProvider(providerId)
                                generationMode = ChatGenerationMode.EXTERNAL_AI_API
                                settingsPrefs.edit()
                                    .putString(PrefGenerationMode, ChatGenerationMode.EXTERNAL_AI_API.name)
                                    .apply()
                                refreshExternalProviderState()
                            }
                        }
                    )
                ChatInputBar(
                    value = input,
                    onValueChange = { input = it },
                    enabled = !isGenerating,
                    isGenerating = isGenerating,
                    pendingAttachments = pendingAttachments,
                    onRemoveAttachment = { attachment ->
                        AttachmentStorageManager.unregisterPendingAttachment(attachment.localPath)
                        pendingAttachments.remove(attachment)
                    },
                    onAppendQuickPrompt = { preset ->
                        input = appendQuickPrompt(input, preset)
                    },
                    onPlusClick = {
                        pickerOriginConversationId = conversationId
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
                        Toast.makeText(context, "음성 입력은 다음 단계에서 연결하겠습니다.", Toast.LENGTH_SHORT).show()
                    },
                    onVoiceModeClick = {
                        Toast.makeText(context, "보이스 모드는 다음 단계에서 연결하겠습니다.", Toast.LENGTH_SHORT).show()
                    },
                    onStopClick = {
                        val targetConversationId = conversationId
                        scope.launch {
                            chatViewModel.cancelGeneration(targetConversationId, reason = "user-stop")
                        }
                    },
                    onSendClick = sendClick@ {
                        val userInput = input.trim()
                        val attachmentsToSend = pendingAttachments.toList()
                        val imageAttachments = attachmentsToSend.filter { isImageAttachment(it) }
                        val nonImageAttachments = attachmentsToSend.filterNot { isImageAttachment(it) }
                        val userInstruction = if (imageAttachments.isNotEmpty()) {
                            buildImageUserInstruction(userInput)
                        } else {
                            userInput
                        }
                        val shouldUseWebSearch = webSearchEnabled || shouldAutoUseWebSearch(userInput)
                        val capturedMessages = messages
                        val capturedGenerationMode = generationMode
                        val capturedSelectedModel = selectedModel
                        val capturedSelectedModelPath = selectedModelPath
                        val capturedReasoningEnabled = reasoningEnabled
                        val capturedWebSearchEnabled = webSearchEnabled
                        val capturedGenerationSettings = generationSettings
                        val capturedPromptLabInstruction = buildPromptLabInstruction(loadPromptLabSettings(context))

                        if (userInput.isNotEmpty() || attachmentsToSend.isNotEmpty()) {
                            if (FusionRuntimeLock.isBenchmarkRunning) {
                                Toast.makeText(context, "벤치마크가 진행 중입니다. 완료 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                                return@sendClick
                            }

                            val userMessageContent = buildMessageContentWithAttachments(
                                body = userInput,
                                attachments = attachmentsToSend
                            )

                            input = ""
                            pendingAttachments.clear()

                            isGenerating = true
                            streamingAssistantText = null
                            streamingMetricsLine = null
                            generationStatus = if (shouldUseWebSearch) "인터넷 검색 중..." else "모델 로딩 중..."

                            scope.launch {
                                var activeConversationId = conversationId
                                var userMessageInserted = false

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
                                    userMessageInserted = true
                                    attachmentsToSend.forEach { attachment ->
                                        AttachmentStorageManager.unregisterPendingAttachment(attachment.localPath)
                                    }

                                    dao.updateConversationTime(activeConversationId, now)

                                    val requestId = UUID.randomUUID().toString()
                                    val snapshot = GenerationRequestSnapshot(
                                        requestId = requestId,
                                        conversationId = activeConversationId,
                                        generationModeKey = capturedGenerationMode.name,
                                        selectedModelId = capturedSelectedModel,
                                        selectedModelPath = capturedSelectedModelPath,
                                        settings = capturedGenerationSettings,
                                        reasoningEnabled = capturedReasoningEnabled,
                                        webSearchPolicy = when {
                                            capturedWebSearchEnabled -> GenerationRequestSnapshot.WebSearchPolicy.ENABLED
                                            shouldAutoUseWebSearch(userInput) -> GenerationRequestSnapshot.WebSearchPolicy.AUTO
                                            else -> GenerationRequestSnapshot.WebSearchPolicy.DISABLED
                                        },
                                        attachmentIds = attachmentsToSend.map { it.localPath },
                                        multimodalImagePaths = imageAttachments.map { it.localPath },
                                        promptText = userInstruction,
                                        rawUserText = userInput,
                                        createdAt = System.currentTimeMillis(),
                                        history = capturedMessages,
                                        promptLabInstruction = capturedPromptLabInstruction,
                                        externalProviderId = selectedExternalProviderId ?: externalProviders.firstOrNull(::isRunnableExternalProvider)?.id,
                                    )

                                    if (capturedGenerationMode == ChatGenerationMode.EXTERNAL_AI_API) {
                                        chatViewModel.update(snapshot.conversationId) {
                                            com.projectnuke.fusion.chat.ConversationGenerationState(
                                                isGenerating = true,
                                                activeRequestId = snapshot.requestId,
                                                streamingText = null,
                                                streamingMetricsLine = null,
                                                generationStatus = if (snapshot.webSearchPolicy != GenerationRequestSnapshot.WebSearchPolicy.DISABLED) "인터넷 검색 중..." else "외부 AI API 응답을 기다리는 중...",
                                                actualWebSearchUsed = false,
                                            )
                                        }

                                        try {
                                            chatViewModel.registry.start(chatViewModel.scope, snapshot) { s ->
                                                if (!chatViewModel.registry.isActive(s.conversationId, s.requestId)) return@start
                                                try {
                                                    if (snapshot.attachmentIds.isNotEmpty()) {
                                                        if (chatViewModel.registry.isActive(s.conversationId, s.requestId) && currentConversationId == snapshot.conversationId) {
                                                            Toast.makeText(context, "현재 외부 AI API 모드에서는 첨부 파일을 전송할 수 없습니다.", Toast.LENGTH_LONG).show()
                                                        }
                                                        return@start
                                                    }

                                                    // === WEB SEARCH ===
                                                    val actualWebSearchEnabled = snapshot.webSearchPolicy != GenerationRequestSnapshot.WebSearchPolicy.DISABLED
                                                    val webSearchResponse = if (actualWebSearchEnabled) {
                                                        chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = "인터넷 검색 중...") }
                                                        val searchIntent = FusionWebSearch.detectIntent(snapshot.rawUserText)
                                                        chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = when (searchIntent) {
                                                            SearchIntent.NEWS -> "뉴스 검색 중..."
                                                            else -> "인터넷 검색 중..."
                                                        }) }
                                                        val previousUserMessage = snapshot.history
                                                            .asReversed()
                                                            .firstOrNull { it.role == "user" }
                                                            ?.content
                                                            ?.let { parseMessageAttachments(it).body }
                                                            ?.trim()
                                                            .orEmpty()
                                                        val response = FusionWebSearch.search(
                                                            userInput = snapshot.rawUserText,
                                                            previousUserMessage = previousUserMessage,
                                                            providerRepository = webSearchProviderRepository
                                                        )
                                                        chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = "검색 결과 정리 중...") }
                                                        response
                                                    } else null
                                                    val actualWebSearchUsed = webSearchResponse != null
                                                    chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(actualWebSearchUsed = actualWebSearchUsed) }
                                                    val webSearchResult = webSearchResponse?.toStructuredContext()
                                                    recordWebSearchDiagnostics(context, webSearchResponse)

                                                    // === PROMPT CONSTRUCTION ===
                                                    val requestSettings = snapshot.settings.copy(
                                                        speculativeDecodingEnabled = resolveSpeculativeDecodingEnabled(
                                                            modelName = snapshot.selectedModelId ?: "",
                                                            settings = snapshot.settings
                                                        )
                                                    )
                                                    val fusionSystemPrompt = buildFusionSystemPrompt(
                                                        reasoningEnabled = snapshot.reasoningEnabled,
                                                        webSearchEnabled = actualWebSearchUsed,
                                                        webContext = webSearchResult,
                                                        promptLabInstruction = snapshot.promptLabInstruction
                                                    )
                                                    val currentMessages = buildList<ChatMessage> {
                                                        add(ChatMessage(role = "system", content = """
                FUSION_GENERATION_SETTINGS
                maxTokens=${requestSettings.maxTokens}
                topK=${requestSettings.topK}
                topP=${requestSettings.topP}
                temperature=${requestSettings.temperature}
                accelerator=${requestSettings.accelerator.name}
                reasoningBudgetTokens=${requestSettings.reasoningBudgetTokens}
                speculativeDecoding=${requestSettings.speculativeDecodingEnabled == true}
            """.trimIndent()))
                                                        add(ChatMessage(role = "system", content = fusionSystemPrompt))
                                                        add(ChatMessage(role = "system", content = "FUSION_MODEL_FAMILY=${FusionModelCatalog.inferFamily(context, snapshot.selectedModelId ?: "").name}"))
                                                        buildSavedMemoryContext(context, settingsPrefs, s.conversationId, snapshot.selectedModelId ?: "").text?.let { memoryContext ->
                                                            add(ChatMessage(role = "system", content = memoryContext))
                                                        }
                                                        buildConversationSummaryContextText(loadConversationSummary(context, s.conversationId))?.let { summaryContext ->
                                                            add(ChatMessage(role = "system", content = summaryContext))
                                                        }
                                                        addAll(snapshot.history.map { message -> ChatMessage(role = message.role, content = if (message.role == "assistant") visibleAssistantHistoryText(message.content) else message.content) })
                                                        val finalUserContent = buildFinalUserContent(
                                                            body = snapshot.promptText,
                                                            attachments = emptyList(),
                                                            webSearchEnabled = actualWebSearchUsed,
                                                            webSearchResult = webSearchResult
                                                        )
                                                        add(ChatMessage(role = "user", content = finalUserContent))
                                                    }

                                                    chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = "외부 AI API 응답을 기다리는 중...") }

                                                    // === EXTERNAL REQUEST ===
                                                    when (val result = runExternalAiRequest(currentMessages = currentMessages, hasAttachments = snapshot.attachmentIds.isNotEmpty(), providerId = snapshot.externalProviderId)) {
                                                        is ExternalAiChatResult.Success -> {
                                                            refreshExternalProviderState()
                                                            chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = "답변 저장 중...") }
                                                            if (!chatViewModel.registry.isActive(s.conversationId, s.requestId)) return@start
                                                            withContext(NonCancellable) {
                                                                var insertedId = -1L
                                                                try {
                                                                    insertedId = dao.insertMessage(MessageEntity(conversationId = s.conversationId, role = "assistant", content = appendSearchSourcesMetadata(result.content, webSearchResponse?.sources.orEmpty()), createdAt = System.currentTimeMillis()))
                                                                    dao.updateConversationTime(s.conversationId, System.currentTimeMillis())
                                                                } catch (e: Exception) {
                                                                    if (insertedId >= 0L) {
                                                                        try {
                                                                            dao.deleteMessageById(insertedId)
                                                                        } catch (inner: Exception) {
                                                                            e.addSuppressed(inner)
                                                                        }
                                                                    }
                                                                    throw e
                                                                }
                                                            }
                                                        }
                                                        is ExternalAiChatResult.BlockedAttachment -> {
                                                            if (chatViewModel.registry.isActive(s.conversationId, s.requestId) && currentConversationId == snapshot.conversationId) {
                                                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                        is ExternalAiChatResult.NoProvider -> {
                                                            refreshExternalProviderState()
                                                            if (chatViewModel.registry.isActive(s.conversationId, s.requestId) && currentConversationId == snapshot.conversationId) {
                                                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                        is ExternalAiChatResult.Empty -> {
                                                            if (chatViewModel.registry.isActive(s.conversationId, s.requestId) && currentConversationId == snapshot.conversationId) {
                                                                Toast.makeText(context, "외부 AI API에서 빈 응답을 받았습니다.", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                        is ExternalAiChatResult.Error -> {
                                                            if (chatViewModel.registry.isActive(s.conversationId, s.requestId) && currentConversationId == snapshot.conversationId) {
                                                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    if (e is CancellationException) throw e
                                                    if (chatViewModel.registry.isActive(s.conversationId, s.requestId) && currentConversationId == snapshot.conversationId) {
                                                        Toast.makeText(context, "오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                                                    }
                                                } finally {
                                                    chatViewModel.finishRequestState(s.conversationId, snapshot.requestId)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            chatViewModel.finishRequestState(snapshot.conversationId, snapshot.requestId)
                                            if (e is CancellationException) throw e
                                            if (currentConversationId == snapshot.conversationId) {
                                                Toast.makeText(context, "오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        return@launch
                                    }

                                    chatViewModel.update(activeConversationId) {
                                        com.projectnuke.fusion.chat.ConversationGenerationState(
                                            isGenerating = true,
                                            activeRequestId = requestId,
                                            streamingText = null,
                                            streamingMetricsLine = null,
                                            generationStatus = if (snapshot.webSearchPolicy != GenerationRequestSnapshot.WebSearchPolicy.DISABLED) "인터넷 검색 중..." else "모델 로딩 중...",
                                            actualWebSearchUsed = false,
                                        )
                                    }

                                    chatViewModel.registry.start(chatViewModel.scope, snapshot) { s ->
                                        if (!chatViewModel.registry.isActive(s.conversationId, s.requestId)) return@start
                                        try {
                                            // === WEB SEARCH ===
                                            val actualWebSearchEnabled = snapshot.webSearchPolicy != GenerationRequestSnapshot.WebSearchPolicy.DISABLED
                                            val webSearchResponse = if (actualWebSearchEnabled) {
                                                chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = "인터넷 검색 중...") }
                                                val searchIntent = FusionWebSearch.detectIntent(snapshot.rawUserText)
                                                chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = when (searchIntent) {
                                                    SearchIntent.NEWS -> "뉴스 검색 중..."
                                                    else -> "인터넷 검색 중..."
                                                }) }
                                                val previousUserMessage = snapshot.history
                                                    .asReversed()
                                                    .firstOrNull { it.role == "user" }
                                                    ?.content
                                                    ?.let { parseMessageAttachments(it).body }
                                                    ?.trim()
                                                    .orEmpty()
                                                val response = FusionWebSearch.search(
                                                    userInput = snapshot.rawUserText,
                                                    previousUserMessage = previousUserMessage,
                                                    providerRepository = webSearchProviderRepository
                                                )
                                                chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = "검색 결과 정리 중...") }
                                                response
                                            } else null
                                            val actualWebSearchUsed = webSearchResponse != null
                                            chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(actualWebSearchUsed = actualWebSearchUsed) }
                                            val webSearchResult = webSearchResponse?.toStructuredContext()
                                            recordWebSearchDiagnostics(context, webSearchResponse)

                                            if (actualWebSearchUsed) {
                                                chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = if (snapshot.reasoningEnabled) "더 깊게 생각하는 중..." else "답변 생성 중...") }
                                            }

                                            if (!chatViewModel.registry.isActive(s.conversationId, s.requestId)) return@start

                                            // === MODEL PATH VALIDATION ===
                                            var activeModelPath = snapshot.selectedModelPath?.takeIf { File(it).exists() }
                                                ?: builtInModels
                                                    .firstOrNull {
                                                        it.name == snapshot.selectedModelId && isModelDownloaded(context, it)
                                                    }
                                                    ?.let { getModelFile(context, it).absolutePath }

                                            if (activeModelPath == null) {
                                                snapshot.selectedModelPath?.takeIf { it.isNotBlank() }?.let {
                                                    android.util.Log.e("FusionEngine", "Selected model file missing: $it")
                                                }
                                                if (chatViewModel.registry.isActive(s.conversationId, s.requestId)) {
                                                    dao.insertMessage(MessageEntity(conversationId = s.conversationId, role = "assistant", content = if (!snapshot.selectedModelPath.isNullOrBlank()) "선택한 모델 파일을 찾을 수 없습니다. 모델을 다시 선택해 주세요." else "아직 사용할 모델이 없습니다. 위쪽 모델 칩에서 Gemma 모델을 다운로드하거나 커스텀 모델을 업로드해 주세요.", createdAt = System.currentTimeMillis()))
                                                    dao.updateConversationTime(s.conversationId, System.currentTimeMillis())
                                                }
                                                return@start
                                            }

                                            val missingImage = snapshot.multimodalImagePaths.firstOrNull { !File(it).exists() }
                                            if (missingImage != null) {
                                                if (chatViewModel.registry.isActive(s.conversationId, s.requestId)) {
                                                    Toast.makeText(context, "이미지 파일을 찾을 수 없습니다: $missingImage", Toast.LENGTH_LONG).show()
                                                    dao.insertMessage(MessageEntity(conversationId = s.conversationId, role = "assistant", content = "이미지 입력 처리 실패: 이미지 파일을 찾을 수 없습니다.\n$missingImage", createdAt = System.currentTimeMillis()))
                                                    dao.updateConversationTime(s.conversationId, System.currentTimeMillis())
                                                }
                                                return@start
                                            }

                                            if (snapshot.multimodalImagePaths.isNotEmpty() && !isMultimodalCapableModel(snapshot.selectedModelId!!, activeModelPath!!)) {
                                                if (chatViewModel.registry.isActive(s.conversationId, s.requestId)) {
                                                    dao.insertMessage(MessageEntity(conversationId = s.conversationId, role = "assistant", content = "이 모델은 이미지 입력을 지원하지 않습니다.", createdAt = System.currentTimeMillis()))
                                                    dao.updateConversationTime(s.conversationId, System.currentTimeMillis())
                                                }
                                                return@start
                                            }

                                            if (!chatViewModel.registry.isActive(s.conversationId, s.requestId)) return@start

                                            // === PROMPT CONSTRUCTION ===
                                            val requestSettings = snapshot.settings.copy(
                                                speculativeDecodingEnabled = resolveSpeculativeDecodingEnabled(
                                                    modelName = snapshot.selectedModelId!!,
                                                    settings = snapshot.settings
                                                )
                                            )
                                            val fusionSystemPrompt = buildFusionSystemPrompt(
                                                reasoningEnabled = snapshot.reasoningEnabled,
                                                webSearchEnabled = actualWebSearchUsed,
                                                webContext = webSearchResult,
                                                promptLabInstruction = snapshot.promptLabInstruction
                                            )
                                            val currentMessages = buildList<ChatMessage> {
                                                add(ChatMessage(role = "system", content = """
                FUSION_GENERATION_SETTINGS
                maxTokens=${requestSettings.maxTokens}
                topK=${requestSettings.topK}
                topP=${requestSettings.topP}
                temperature=${requestSettings.temperature}
                accelerator=${requestSettings.accelerator.name}
                reasoningBudgetTokens=${requestSettings.reasoningBudgetTokens}
                speculativeDecoding=${requestSettings.speculativeDecodingEnabled == true}
            """.trimIndent()))
                                                add(ChatMessage(role = "system", content = fusionSystemPrompt))
                                                snapshot.selectedModelPath?.let { add(ChatMessage(role = "system", content = "FUSION_SELECTED_MODEL_PATH=$it")) }
                                                add(ChatMessage(role = "system", content = "FUSION_MODEL_FAMILY=${FusionModelCatalog.inferFamily(context, snapshot.selectedModelId!!).name}"))
                                                buildSavedMemoryContext(context, settingsPrefs, s.conversationId, snapshot.selectedModelId!!).text?.let { add(ChatMessage(role = "system", content = it)) }
                                                buildConversationSummaryContextText(loadConversationSummary(context, s.conversationId))?.let { add(ChatMessage(role = "system", content = it)) }
                                                addAll(snapshot.history.map { ChatMessage(role = it.role, content = if (it.role == "assistant") visibleAssistantHistoryText(it.content) else it.content) })
                                                add(ChatMessage(role = "user", content = buildFinalUserContent(
                                                    body = snapshot.promptText,
                                                    attachments = if (imageAttachments.isNotEmpty()) nonImageAttachments else attachmentsToSend,
                                                    webSearchEnabled = actualWebSearchUsed,
                                                    webSearchResult = webSearchResult
                                                )))
                                            }

                                            chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = "모델 로딩 중...") }

                                            // === GENERATION ===
                                            val generationStartMs = SystemClock.elapsedRealtime()
                                            val firstTokenLatency = java.util.concurrent.atomic.AtomicLong(-1L)

                                            val rawOutcome = FusionRuntimeLock.withChatGeneration {
                                                generateWithLiteRtRecovery(
                                                    engine = engine,
                                                    onBeforeRetry = {
                                                        chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = "모델 로딩 중...", streamingText = null) }
                                                    },
                                                    generateOnce = {
                                                        val requestScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.currentCoroutineContext())
                                                        if (snapshot.multimodalImagePaths.isNotEmpty() && !snapshot.reasoningEnabled) {
                                                            chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = "이미지 분석 준비 중...") }
                                                            val coalescer = TokenCoalescer(
                                                                scope = requestScope,
                                                                shouldPublish = { chatViewModel.registry.isActive(s.conversationId, snapshot.requestId) },
                                                                onPublish = { text ->
                                                                    chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(streamingText = text) }
                                                                }
                                                            )
                                                            chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(streamingText = "") }
                                                            chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = "이미지 분석 중...") }
                                                            try {
                                                                val outcome = engine.generateMultimodalStreaming(
                                                                    messages = currentMessages,
                                                                    modelPath = activeModelPath!!,
                                                                    settings = requestSettings,
                                                                    imagePaths = snapshot.multimodalImagePaths,
                                                                    onToken = { token ->
                                                                        if (token.isNotEmpty()) firstTokenLatency.compareAndSet(-1L, SystemClock.elapsedRealtime() - generationStartMs)
                                                                        coalescer.append(token)
                                                                    }
                                                                )
                                                                when (outcome) {
                                                                    is GenerationOutcome.Success -> coalescer.finish()
                                                                    else -> coalescer.abort()
                                                                }
                                                                outcome
                                                            } catch (e: Exception) {
                                                                coalescer.abort()
                                                                throw e
                                                            }
                                                        } else if (snapshot.multimodalImagePaths.isNotEmpty()) {
                                                            chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = "이미지 분석 준비 중...") }
                                                            chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = "이미지 분석 중...") }
                                                            engine.generateMultimodalStreaming(
                                                                messages = currentMessages,
                                                                modelPath = activeModelPath!!,
                                                                settings = requestSettings,
                                                                imagePaths = snapshot.multimodalImagePaths,
                                                                onToken = { token ->
                                                                    if (token.isNotEmpty()) firstTokenLatency.compareAndSet(-1L, SystemClock.elapsedRealtime() - generationStartMs)
                                                                }
                                                            )
                                                        } else if (snapshot.reasoningEnabled) {
                                                            chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = "더 깊게 생각하는 중...") }
                                                            engine.generate(messages = currentMessages, modelPath = activeModelPath!!, settings = requestSettings)
                                                        } else {
                                                            val coalescer = TokenCoalescer(
                                                                scope = requestScope,
                                                                shouldPublish = { chatViewModel.registry.isActive(s.conversationId, snapshot.requestId) },
                                                                onPublish = { text ->
                                                                    chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(streamingText = text) }
                                                                }
                                                            )
                                                            chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(streamingText = "") }
                                                            chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(generationStatus = "답변 생성 중...") }
                                                            try {
                                                                val outcome = engine.generateStreaming(
                                                                    messages = currentMessages,
                                                                    modelPath = activeModelPath!!,
                                                                    settings = requestSettings,
                                                                    onToken = { token ->
                                                                        if (token.isNotEmpty()) firstTokenLatency.compareAndSet(-1L, SystemClock.elapsedRealtime() - generationStartMs)
                                                                        coalescer.append(token)
                                                                    }
                                                                )
                                                                when (outcome) {
                                                                    is GenerationOutcome.Success -> coalescer.finish()
                                                                    else -> coalescer.abort()
                                                                }
                                                                outcome
                                                            } catch (e: Exception) {
                                                                coalescer.abort()
                                                                throw e
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                            
                                            val firstTokenLatencyMs = firstTokenLatency.get().let { if (it < 0L) null else it }

                                            if (!chatViewModel.registry.isActive(s.conversationId, s.requestId)) return@start

                                            val rawReply = when (rawOutcome) {
                                                is GenerationOutcome.Success -> rawOutcome.text
                                                is GenerationOutcome.Cancelled -> {
                                                    DeveloperLogStore.record(context, "chat", "답변 생성 취소", "cancelled")
                                                    return@start
                                                }
                                                GenerationOutcome.Empty -> {
                                                    DeveloperLogStore.record(context, "chat", "답변 생성 실패", "empty")
                                                    return@start
                                                }
                                                is GenerationOutcome.Failure -> {
                                                    DeveloperLogStore.record(context, "chat", "답변 생성 실패", rawOutcome.kind.name)
                                                    if (chatViewModel.registry.isActive(s.conversationId, s.requestId)) {
                                                        Toast.makeText(context, rawOutcome.message, Toast.LENGTH_LONG).show()
                                                    }
                                                    return@start
                                                }
                                            }

                                            if (!chatViewModel.registry.isActive(s.conversationId, s.requestId)) return@start

                                            val totalGenerationMs = SystemClock.elapsedRealtime() - generationStartMs
                                            val metricsLine = buildFusionMetricsLine(
                                                modelName = shortModelName(snapshot.selectedModelId!!),
                                                acceleratorName = buildAcceleratorLabel(acceleratorName = requestSettings.accelerator.name, speculativeDecodingEnabled = requestSettings.speculativeDecodingEnabled == true),
                                                generatedText = rawReply,
                                                totalGenerationMs = totalGenerationMs,
                                                firstTokenLatencyMs = firstTokenLatencyMs,
                                                settingsLine = buildEffectiveSettingsLine(buildEffectiveRuntimeSettings(modelName = snapshot.selectedModelId!!, modelPath = activeModelPath!!, settings = requestSettings, reasoningEnabled = snapshot.reasoningEnabled, webSearchEnabled = actualWebSearchUsed, mtpStatus = engine.lastMtpStatus))
                                            )
                                            chatViewModel.updateRequestState(s.conversationId, snapshot.requestId, requireActiveSession = true) { it.copy(streamingMetricsLine = metricsLine, generationStatus = "답변 저장 중...") }
                                            if (!chatViewModel.registry.isActive(s.conversationId, s.requestId)) return@start
                                            dao.insertMessage(MessageEntity(conversationId = s.conversationId, role = "assistant", content = appendSearchSourcesMetadata(appendFusionMetrics(rawReply, metricsLine), webSearchResponse?.sources.orEmpty()), createdAt = System.currentTimeMillis()))
                                            dao.updateConversationTime(s.conversationId, System.currentTimeMillis())
                                        } catch (e: Exception) {
                                            Log.e("FusionEngine", "Chat generation failed", e)
                                            if (e is BenchmarkRunningException) return@start
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            if (chatViewModel.registry.isActive(s.conversationId, s.requestId)) {
                                                Toast.makeText(context, if (isLiteRtModelLoadException(e)) "모델을 불러올 수 없습니다. 모델 설정을 확인한 뒤 다시 시도해 주세요." else "오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                                            }
                                        } finally {
                                            chatViewModel.finishRequestState(s.conversationId, snapshot.requestId)
                                        }
                                    }
                                } catch (e: Exception) {
                                    if (!userMessageInserted && attachmentsToSend.isNotEmpty()) {
                                        attachmentsToSend.forEach { attachment ->
                                            val deleted = AttachmentStorageManager.deletePendingAttachmentFile(context = context, path = attachment.localPath)
                                            if (!deleted) {
                                                AttachmentStorageManager.unregisterPendingAttachment(attachment.localPath)
                                            }
                                        }
                                        DeveloperLogStore.record(context, "attachment", "첨부 파일 저장 롤백", "count=${attachmentsToSend.size}, error=${e::class.java.simpleName}")
                                    }
                                    Log.e("FusionEngine", "Chat generation failed", e)
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    isGenerating = false
                                    generationStatus = null
                                    streamingAssistantText = null
                                    streamingMetricsLine = null
                                }
                            }
                        }
                    }
                )
            }
            }
        },
        content = { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BlackBg)
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                if (inChatSearchMode) {
                    InChatSearchBar(
                        query = inChatSearchQuery,
                        onQueryChange = { inChatSearchQuery = it },
                        onClose = {
                            inChatSearchMode = false
                            inChatSearchQuery = ""
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "검색 결과",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (messageEntities.isEmpty() && !isGenerating) {
                    EmptyChatBody(bottomPadding = chatContentBottomPadding)
                } else if (inChatSearchMode) {
                    if (inChatSearchQuery.trim().isNotEmpty() && inChatSearchResults.isEmpty()) {
                        EmptyInChatSearchResults()
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(top = 6.dp, bottom = chatContentBottomPadding)
                        ) {
                            items(
                                items = inChatSearchResults,
                                key = { it.id }
                            ) { result ->
                                InChatSearchResultRow(
                                    role = if (result.role == "user") "사용자" else "Fusion",
                                    preview = visibleSearchText(result.content),
                                    onClick = {
                                        scope.launch {
                                            val idx = activeMessageEntities.indexOfFirst { it.id == result.id }
                                            if (idx >= 0) {
                                                listState.animateScrollToItem(idx)
                                                inChatSearchMode = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = chatContentBottomPadding)
                    ) {
                        items(
                            items = activeMessageEntities,
                            key = { it.id }
                        ) { message ->
                            when (message.role) {
                                "user" -> UserMessageBubble(message.content)
                                else -> {
                                    val versionGroup = chatTimeline
                                        .filterIsInstance<ChatTimelineItem.AssistantVersions>()
                                        .first { group -> group.activeMessage.id == message.id }
                                    AssistantMessage(
                                    content = message.content,
                                    createdAt = message.createdAt,
                                    selectedModel = selectedModel,
                                    webSearchEnabled = webSearchEnabled,
                                    reasoningEnabled = reasoningEnabled,
                                    isRegenerating = regeneratingMessageId == message.id,
                                    versionIndex = versionGroup.activeIndex,
                                    versionCount = versionGroup.versions.size,
                                    onPreviousVersion = {
                                        val selected = versionGroup.versions[versionGroup.activeIndex - 1]
                                        val updated = responseVersionState.copy(
                                            activeMessageIdByGroup = responseVersionState.activeMessageIdByGroup +
                                                (versionGroup.groupId to selected.id)
                                        )
                                        saveResponseVersionState(context, conversationId, updated)
                                        responseVersionState = updated
                                    },
                                    onNextVersion = {
                                        val selected = versionGroup.versions[versionGroup.activeIndex + 1]
                                        val updated = responseVersionState.copy(
                                            activeMessageIdByGroup = responseVersionState.activeMessageIdByGroup +
                                                (versionGroup.groupId to selected.id)
                                        )
                                        saveResponseVersionState(context, conversationId, updated)
                                        responseVersionState = updated
                                    },
                                    onRetry = { startRegenerateResponse(message, ResponseRegenerationAction.Retry) },
                                onRegenerate = { action -> startRegenerateResponse(message, action) },
                                onBranch = {
                                    Toast.makeText(
                                        context,
                                        "새 채팅으로 가지치기 기능은 다음 단계에서 연결하겠습니다.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onToggleWebSearch = {
                                    webSearchEnabled = !webSearchEnabled
                                    saveFusionSettings(
                                        prefs = settingsPrefs,
                                        settings = generationSettings,
                                        reasoningEnabled = reasoningEnabled,
                                        webSearchEnabled = webSearchEnabled,
                                        selectedModel = selectedModel,
                                        selectedModelPath = selectedModelPath
                                    )
                                }
                                    )
                                }
                        }
                    }

                    if (isGenerating && regeneratingMessageId == null) {
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
                                    showActions = false,
                                    onRetry = {},
                                    onRegenerate = {},
                                    onBranch = {},
                                    onToggleWebSearch = {}
                                )
                            } else {
                                ModelLoadingBubble(
                                    status = generationStatus ?: "모델 로딩 중..."
                                )
                            }
                        }
                    }
                }
            }
            }
            AnimatedVisibility(
                visible = fusionPulseState != FusionPulseState.Idle,
                enter = fadeIn(animationSpec = tween(durationMillis = 450)),
                exit = fadeOut(animationSpec = tween(durationMillis = 750)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 132.dp)
            ) {
                FusionPulseAmbientLight(
                    state = displayedPulseState,
                    modifier = Modifier.fillMaxWidth()
                )
                }
            }
        }
    )

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
                var didSelectModel = false
                when {
                    model.customPath != null && File(model.customPath).exists() -> {
                        selectedModel = model.name
                        selectedModelPath = model.customPath
                        didSelectModel = true
                    }

                    isModelDownloaded(context, model) -> {
                        selectedModel = model.name
                        selectedModelPath = getModelFile(context, model).absolutePath
                        saveFusionSettings(
                            prefs = settingsPrefs,
                            settings = generationSettings,
                            reasoningEnabled = reasoningEnabled,
                            webSearchEnabled = webSearchEnabled,
                            selectedModel = selectedModel,
                            selectedModelPath = selectedModelPath
                        )
                        didSelectModel = true
                    }

                    else -> {
                        pendingDownloadModel = model
                    }
                }

                if (didSelectModel) {
                    val catalog = FusionModelCatalog.all(context)
                    val selectedId = catalog.firstOrNull {
                        it.displayName == selectedModel && (selectedModelPath == null || it.localPath == selectedModelPath)
                    }?.id ?: ("custom:" + (selectedModelPath ?: selectedModel))
                    recordRecentModel(settingsPrefs, selectedId, selectedModel)
                    saveFusionSettings(
                        prefs = settingsPrefs,
                        settings = generationSettings,
                        reasoningEnabled = reasoningEnabled,
                        webSearchEnabled = webSearchEnabled,
                        selectedModel = selectedModel,
                        selectedModelPath = selectedModelPath
                    )
                }
            },
            onUploadCustomModel = {
                modelPickerLauncher.launch(arrayOf("*/*"))
            },
            onLinkExternalModel = {
                externalModelPickerLauncher.launch(arrayOf("*/*"))
            },
            onOpenStorageManager = {
                showModelStorageManager = true
            },
            onOpenAdvancedSettings = {
                showAdvancedSettingsDialog = true
            },
            onOpenBenchmark = { modelName, openHistory ->
                showModelDialog = false
                onOpenBenchmark(modelName, openHistory)
            },
            onToggleReasoning = {
                reasoningEnabled = !reasoningEnabled
                saveFusionSettings(
                    prefs = settingsPrefs,
                    settings = generationSettings,
                    reasoningEnabled = reasoningEnabled,
                    webSearchEnabled = webSearchEnabled,
                    selectedModel = selectedModel,
                    selectedModelPath = selectedModelPath
                )
            },
            onToggleWebSearch = {
                webSearchEnabled = !webSearchEnabled
                saveFusionSettings(
                    prefs = settingsPrefs,
                    settings = generationSettings,
                    reasoningEnabled = reasoningEnabled,
                    webSearchEnabled = webSearchEnabled,
                    selectedModel = selectedModel,
                    selectedModelPath = selectedModelPath
                )
            }
        )
    }

    pendingModelImport?.let { pending ->
        ModelImportWizardDialog(
            pending = pending,
            onDismiss = { pendingModelImport = null },
            onLink = { family ->
                val spec = FusionModelCatalog.externalLinkedSpec(
                    displayName = pending.displayName,
                    originalFileName = pending.originalFileName,
                    uriString = pending.uri.toString(),
                    fileSizeBytes = pending.fileSizeBytes,
                    family = family
                )
                FusionModelCatalog.saveImported(context, spec)
                storageRefreshKey += 1
                pendingModelImport = null
                Toast.makeText(context, "외부 모델 파일을 연결했습니다.", Toast.LENGTH_SHORT).show()
                when {
                    !pending.permissionPersisted -> Toast.makeText(context, "모델 파일 권한을 유지할 수 없습니다.", Toast.LENGTH_SHORT).show()
                    spec.availability == ModelAvailability.CUSTOM_IMPORTED -> Toast.makeText(context, "이 모델은 실행 전에 Fusion 내부 저장소로 복사해야 할 수 있습니다.", Toast.LENGTH_LONG).show()
                    spec.availability == ModelAvailability.NEEDS_CONVERSION -> Toast.makeText(context, "이 모델은 변환 후 사용할 수 있습니다.", Toast.LENGTH_SHORT).show()
                    else -> Toast.makeText(context, "이 형식은 현재 직접 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            },
            onCopyForRun = { family ->
                scope.launch {
                    val copiedFile = copyUriToModelFile(
                        context = context,
                        uri = pending.uri,
                        displayName = pending.displayName
                    )
                    if (copiedFile == null) {
                        Toast.makeText(context, "모델 파일을 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val spec = FusionModelCatalog.importedSpec(pending.displayName, copiedFile.absolutePath, family)
                    FusionModelCatalog.saveImported(context, spec)
                    if (spec.availability == ModelAvailability.CUSTOM_IMPORTED &&
                        customModels.none { it.customPath == copiedFile.absolutePath }
                    ) {
                        customModels.add(
                            LocalModel(
                                name = spec.displayName,
                                fileName = spec.fileName ?: pending.displayName,
                                customPath = copiedFile.absolutePath
                            )
                        )
                    }
                    storageRefreshKey += 1
                    pendingModelImport = null
                    when (spec.availability) {
                        ModelAvailability.CUSTOM_IMPORTED -> Toast.makeText(context, "모델 파일을 가져왔습니다.", Toast.LENGTH_SHORT).show()
                        ModelAvailability.NEEDS_CONVERSION -> Toast.makeText(context, "이 모델은 변환 후 사용할 수 있습니다.", Toast.LENGTH_SHORT).show()
                        else -> Toast.makeText(context, "이 형식은 현재 직접 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showModelStorageManager) {
        ModelStorageManagerDialog(
            refreshKey = storageRefreshKey,
            currentModel = selectedModel,
            onDismiss = { showModelStorageManager = false },
            onSelect = { spec ->
                val localPath = spec.localPath
                when {
                    spec.availability == ModelAvailability.NEEDS_CONVERSION -> {
                        Toast.makeText(context, "이 모델은 변환 후 사용할 수 있습니다.", Toast.LENGTH_SHORT).show()
                    }
                    spec.availability != ModelAvailability.CUSTOM_IMPORTED && !spec.externallyReferenced -> {
                        Toast.makeText(context, "이 형식은 현재 직접 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                    spec.externallyReferenced && localPath.isNullOrBlank() -> {
                        if (spec.availability == ModelAvailability.CUSTOM_IMPORTED) {
                            Toast.makeText(context, "이 모델은 실행 전에 Fusion 내부 저장소로 복사해야 합니다.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "이 파일은 현재 직접 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    localPath != null && File(localPath).exists() && spec.availability == ModelAvailability.CUSTOM_IMPORTED -> {
                        selectedModel = spec.displayName
                        selectedModelPath = localPath
                        saveFusionSettings(
                            prefs = settingsPrefs,
                            settings = generationSettings,
                            reasoningEnabled = reasoningEnabled,
                            webSearchEnabled = webSearchEnabled,
                            selectedModel = selectedModel,
                            selectedModelPath = selectedModelPath
                        )
                    }
                    else -> Toast.makeText(context, "모델 파일에 접근할 수 없습니다. 파일을 다시 연결해 주세요.", Toast.LENGTH_SHORT).show()
                }
            },
            onChanged = { storageRefreshKey += 1 }
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
                        "아직 다운로드 URL이 등록되지 않은 모델입니다",
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
                        saveFusionSettings(
                            prefs = settingsPrefs,
                            settings = generationSettings,
                            reasoningEnabled = reasoningEnabled,
                            webSearchEnabled = webSearchEnabled,
                            selectedModel = selectedModel,
                            selectedModelPath = selectedModelPath
                        )
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
            reasoningEnabled = reasoningEnabled,
            webSearchEnabled = webSearchEnabled,
            webSearchProviderRepository = webSearchProviderRepository,
            onDismiss = {
                showAdvancedSettingsDialog = false
            },
            onApply = { newSettings, newReasoningEnabled, newWebSearchEnabled ->
                generationSettings = newSettings
                reasoningEnabled = newReasoningEnabled
                webSearchEnabled = newWebSearchEnabled
                saveFusionSettings(
                    prefs = settingsPrefs,
                    settings = newSettings,
                    reasoningEnabled = newReasoningEnabled,
                    webSearchEnabled = newWebSearchEnabled,
                    selectedModel = selectedModel,
                    selectedModelPath = selectedModelPath
                )
                showAdvancedSettingsDialog = false

                Toast.makeText(
                    context,
                    "고급 설정 적용됨: ${newSettings.accelerator.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    if (showDeleteChatDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteChatDialog = false },
            title = {
                Text("채팅 삭제")
            },
            text = {
                Text("이 채팅을 삭제할까요?")
            },
            dismissButton = {
                TextButton(onClick = { showDeleteChatDialog = false }) {
                    Text("취소")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteChatDialog = false
                        scope.launch {
                            val targetId = conversationId
                            if (targetId != 0L) {
                                chatViewModel.cancelAndAwait(targetId, reason = "delete-conversation")
                                // Re-check existence: a race could have removed it.
                                if (dao.getConversationById(targetId) != null) {
                                    dao.deleteConversation(targetId)
                                }
                            }
                            input = ""
                            clearPendingAttachmentDrafts(pendingAttachments)
                            streamingAssistantText = null
                            streamingMetricsLine = null
                            generationStatus = null
                            onNewChat()
                        }
                    }
                ) {
                    Text("삭제", color = DangerRed)
                }
            },
            containerColor = PanelBg,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary
        )
    }

    if (showConversationSummaryDialog) {
        ConversationSummaryDialog(
            summaryMemory = conversationSummary,
            onGenerate = {
                input = "현재 대화의 핵심 내용을 나중에 참고할 수 있도록 간결하게 요약해 주세요. 사용자의 선호, 진행 중인 작업, 중요한 결정 사항을 중심으로 정리해 주세요. 불필요한 잡담은 제외해 주세요."
                showConversationSummaryDialog = false
                Toast.makeText(context, "요약 요청을 입력창에 추가했습니다.", Toast.LENGTH_SHORT).show()
                DeveloperLogStore.record(context, "summary", "요약 생성 요청", "conversationId=$conversationId")
            },
            onEdit = {
                conversationSummaryDraft = conversationSummary?.summary.orEmpty()
                showConversationSummaryEditor = true
            },
            onCopy = {
                val text = conversationSummary?.summary.orEmpty()
                if (text.isBlank()) {
                    Toast.makeText(context, "저장된 요약이 없습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    clipboardManager.setText(AnnotatedString(text))
                    Toast.makeText(context, "요약을 복사했습니다.", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = {
                showConversationSummaryDeleteConfirm = true
            },
            onDismiss = {
                showConversationSummaryDialog = false
            }
        )
    }

    if (showMemoryCandidateDialog) {
        MemoryCandidateDialog(
            candidateText = memoryCandidateText,
            isExtracting = extractingMemoryCandidates,
            savedCount = savedMemoryCandidates.size,
            onExtract = { startMemoryCandidateExtraction() },
            onCopy = {
                val text = memoryCandidateText.trim()
                if (text.isBlank()) {
                    Toast.makeText(context, "복사할 메모리 후보가 없습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    clipboardManager.setText(AnnotatedString(text))
                    Toast.makeText(context, "메모리 후보를 복사했습니다.", Toast.LENGTH_SHORT).show()
                }
            },
            onSaveSelected = { selectedItems ->
                val savedCount = saveConversationMemoryCandidates(context, conversationId, selectedItems)
                if (savedCount <= 0) {
                    Toast.makeText(context, "저장할 메모리 후보가 없습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "메모리 후보를 저장했습니다.", Toast.LENGTH_SHORT).show()
                    DeveloperLogStore.record(context, "memory", "메모리 후보 저장", "conversationId=$conversationId, count=$savedCount")
                }
            },
            onSaveAll = {
                val savedCount = saveConversationMemoryCandidates(context, conversationId, parseMemoryCandidateLines(memoryCandidateText))
                if (savedCount <= 0) {
                    Toast.makeText(context, "저장할 메모리 후보가 없습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "메모리 후보를 저장했습니다.", Toast.LENGTH_SHORT).show()
                    DeveloperLogStore.record(context, "memory", "메모리 후보 저장", "conversationId=$conversationId, count=$savedCount")
                }
            },
            onDismiss = { showMemoryCandidateDialog = false }
        )
    }

    if (showConversationSummaryEditor) {
        ConversationSummaryEditorDialog(
            value = conversationSummaryDraft,
            onValueChange = { conversationSummaryDraft = it },
            onSave = {
                val saved = saveConversationSummary(context, conversationId, conversationSummaryDraft)
                if (saved == null) {
                    Toast.makeText(context, "대화가 저장된 후 사용할 수 있습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    conversationSummaryRefreshKey++
                    showConversationSummaryEditor = false
                    Toast.makeText(context, "대화 요약을 저장했습니다.", Toast.LENGTH_SHORT).show()
                    DeveloperLogStore.record(context, "summary", "대화 요약 저장", "conversationId=$conversationId, length=${saved.summary.length}")
                }
            },
            onDismiss = {
                showConversationSummaryEditor = false
            }
        )
    }

    if (showConversationSummaryDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showConversationSummaryDeleteConfirm = false },
            title = { Text("대화 요약을 삭제하시겠습니까?") },
            text = { Text("저장된 요약만 삭제되며 채팅 기록은 삭제되지 않습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteConversationSummary(context, conversationId)
                    conversationSummaryRefreshKey++
                    showConversationSummaryDeleteConfirm = false
                    Toast.makeText(context, "대화 요약을 삭제했습니다.", Toast.LENGTH_SHORT).show()
                    DeveloperLogStore.record(context, "summary", "대화 요약 삭제", "conversationId=$conversationId")
                }) {
                    Text("삭제", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConversationSummaryDeleteConfirm = false }) {
                    Text("취소")
                }
            },
            containerColor = PanelBg,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary
        )
    }

}

@Composable
private fun ConversationSummaryDialog(
    summaryMemory: ConversationSummaryMemory?,
    onGenerate: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val updatedAtText = remember(summaryMemory?.updatedAt) {
        summaryMemory?.updatedAt
            ?.takeIf { it > 0L }
            ?.let { SimpleDateFormat("yyyy.MM.dd a h:mm", Locale.KOREAN).format(Date(it)) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("대화 요약") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "현재 대화의 핵심 내용을 저장해 긴 대화에서도 맥락을 유지합니다.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = ReleaseCardBgOrPanel()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (summaryMemory == null) {
                            Text("저장된 요약이 없습니다.", color = TextSecondary, fontSize = 14.sp)
                        } else {
                            updatedAtText?.let {
                                Text("업데이트: $it", color = TextSecondary, fontSize = 12.sp)
                            }
                            Text(
                                text = summaryMemory.summary,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onGenerate) { Text("요약 생성", color = AccentBlue) }
                TextButton(onClick = onEdit) { Text("직접 편집", color = AccentBlue) }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onCopy, enabled = summaryMemory != null) { Text("요약 복사", color = AccentBlue) }
                TextButton(onClick = onDelete, enabled = summaryMemory != null) { Text("요약 삭제", color = DangerRed) }
                TextButton(onClick = onDismiss) { Text("닫기", color = TextSecondary) }
            }
        },
        containerColor = PanelBg,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary
    )
}

@Composable
private fun ConversationSummaryEditorDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("대화 요약 편집") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                placeholder = { Text("이 대화에서 계속 참고할 핵심 내용을 입력해 주세요.") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = LineColor,
                    focusedContainerColor = PanelBg,
                    unfocusedContainerColor = PanelBg,
                    focusedPlaceholderColor = TextSecondary,
                    unfocusedPlaceholderColor = TextSecondary
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("저장", color = AccentBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = TextSecondary)
            }
        },
        containerColor = PanelBg,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary
    )
}

@Composable
private fun MemoryCandidateDialog(
    candidateText: String,
    isExtracting: Boolean,
    savedCount: Int,
    onExtract: () -> Unit,
    onCopy: () -> Unit,
    onSaveSelected: (List<String>) -> Unit,
    onSaveAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val parsedCandidates = remember(candidateText) { parseMemoryCandidateLines(candidateText) }
    var selectedCandidates by remember(candidateText) { mutableStateOf(parsedCandidates.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("메모리 후보 추출") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "이 대화에서 나중에 참고할 만한 정보를 정리합니다.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Text(
                    text = "자동 저장하지 않습니다. 필요한 항목만 확인해 주세요.",
                    color = AccentBlue,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                if (savedCount > 0) {
                    Text(
                        text = "저장된 후보 ${savedCount}개",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = ReleaseCardBgOrPanel()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isExtracting) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = AccentBlue,
                                    strokeWidth = 2.dp
                                )
                                Text("메모리 후보를 추출하는 중입니다.", color = TextPrimary, fontSize = 13.sp)
                            }
                        } else if (candidateText.isBlank()) {
                            Text("추출된 메모리 후보가 없습니다.", color = TextSecondary, fontSize = 14.sp)
                        } else if (parsedCandidates.isEmpty()) {
                            Text(
                                text = candidateText,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        } else {
                            parsedCandidates.forEach { candidate ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Checkbox(
                                        checked = selectedCandidates.contains(candidate),
                                        onCheckedChange = { checked ->
                                            selectedCandidates = if (checked) {
                                                selectedCandidates + candidate
                                            } else {
                                                selectedCandidates - candidate
                                            }
                                        }
                                    )
                                    Text(
                                        text = candidate,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onExtract, enabled = !isExtracting) { Text("후보 추출", color = AccentBlue) }
                TextButton(onClick = { onSaveSelected(selectedCandidates.toList()) }, enabled = parsedCandidates.isNotEmpty() && !isExtracting) {
                    Text("선택 항목 저장", color = AccentBlue, maxLines = 1)
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSaveAll, enabled = parsedCandidates.isNotEmpty() && !isExtracting) {
                    Text("전체 저장", color = AccentBlue)
                }
                TextButton(onClick = onCopy, enabled = candidateText.isNotBlank() && !isExtracting) {
                    Text("후보 복사", color = AccentBlue)
                }
                TextButton(onClick = onDismiss) { Text("닫기", color = TextSecondary) }
            }
        },
        containerColor = PanelBg,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary
    )
}

private fun ReleaseCardBgOrPanel(): Color = Color(0xFF111111)

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
    onDeleteChat: () -> Unit,
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
                        text = { Text("채팅 고정", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onChatOption("채팅 고정") }
                    )
                    DropdownMenuItem(
                        text = { Text("새 프로젝트", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onChatOption("새 프로젝트") }
                    )
                    DropdownMenuItem(
                        text = { Text("프로젝트에 추가", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onChatOption("프로젝트에 추가") }
                    )
                    DropdownMenuItem(
                        text = { Text("업로드한 파일", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onChatOption("업로드한 파일") }
                    )
                    DropdownMenuItem(
                        text = { Text("홈 화면에 추가", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onChatOption("홈 화면에 추가") }
                    )
                    DropdownMenuItem(
                        text = { Text("아카이브에 보관", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onChatOption("아카이브에 보관") }
                    )
                    DropdownMenuItem(
                        text = { Text("대화 요약", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onChatOption(ChatOptionConversationSummary) }
                    )
                    DropdownMenuItem(
                        text = { Text("메모리 후보 추출", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onChatOption(ChatOptionMemoryCandidateExtraction) }
                    )
                    DropdownMenuItem(
                        text = { Text("삭제", color = DangerRed, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = onDeleteChat
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
private fun EmptyChatBody(bottomPadding: androidx.compose.ui.unit.Dp) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomPadding),
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
            text = "새 채팅을 시작해 보세요.",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "모델과 모드는 위쪽 칩에서 바꿀 수 있습니다.",
            color = TextSecondary,
            fontSize = 15.sp
        )
    }

}

@Composable
private fun InChatSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = PanelBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "대화 내 검색",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                decorationBox = { inner ->
                    if (query.isBlank()) {
                        Text("메시지를 검색합니다.", color = TextSecondary, fontSize = 14.sp)
                    }
                    inner()
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "닫기",
                color = AccentBlue,
                fontSize = 13.sp,
                modifier = Modifier.clickable { onClose() }
            )
        }
    }
}

@Composable
private fun InChatSearchResultRow(
    role: String,
    preview: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = PanelBg
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(text = role, color = AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = preview.ifBlank { "(내용 없음)" },
                color = TextPrimary,
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyInChatSearchResults() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "검색 결과가 없습니다.",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
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

                Text(
                    text = status,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
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
    showActions: Boolean = true,
    isRegenerating: Boolean = false,
    versionIndex: Int = 0,
    versionCount: Int = 1,
    onPreviousVersion: () -> Unit = {},
    onNextVersion: () -> Unit = {},
    onRetry: () -> Unit,
    onRegenerate: (ResponseRegenerationAction) -> Unit,
    onBranch: () -> Unit,
    onToggleWebSearch: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var moreExpanded by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    val searchSources = remember(content) {
        parseSearchSourcesMetadata(content)
    }
    val visibleContent = remember(content) {
        stripSearchSourcesMetadata(content)
    }
    val metricsSplit = remember(visibleContent) {
        splitFusionMetrics(visibleContent)
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

        if (searchSources.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            SearchSourceCapsules(
                sources = searchSources,
                onClick = { showSources = true }
            )
        }

        if (isRegenerating) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "답변을 다시 생성하는 중입니다.",
                color = AccentBlue,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        if (showActions) {
        if (versionCount > 1) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ActionIcon(
                    icon = Icons.Rounded.ChevronLeft,
                    contentDescription = "이전 답변",
                    enabled = versionIndex > 0,
                    onClick = onPreviousVersion
                )
                Text(
                    text = "${versionIndex + 1} / $versionCount",
                    color = AccentBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                ActionIcon(
                    icon = Icons.Rounded.ChevronRight,
                    contentDescription = "다음 답변",
                    enabled = versionIndex < versionCount - 1,
                    onClick = onNextVersion
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
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
                    Toast.makeText(context, "복사했습니다.", Toast.LENGTH_SHORT).show()
                }
            )

            ActionIcon(
                icon = Icons.Rounded.VolumeUp,
                contentDescription = "음성으로 읽기",
                onClick = {
                    Toast.makeText(context, "음성 읽기는 다음 단계에서 연결하겠습니다.", Toast.LENGTH_SHORT).show()
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
                    isRegenerating = isRegenerating,
                    onBranch = {
                        moreExpanded = false
                        onBranch()
                    },
                    onRetry = {
                        moreExpanded = false
                        onRetry()
                    },
                    onRegenerate = { action ->
                        moreExpanded = false
                        onRegenerate(action)
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

    if (showSources) {
        SearchSourcesDialog(
            sources = searchSources,
            onDismiss = { showSources = false }
        )
    }
}
@Composable
private fun SearchSourceCapsules(
    sources: List<WebSearchSource>,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SourceCapsule("출처 ${sources.size}개", onClick)
        sources.take(3).forEach { source ->
            val label = source.source ?: source.providerDisplayName
            SourceCapsule(label.take(22), onClick)
        }
    }
}

@Composable
private fun SourceCapsule(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFF132334),
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            color = AccentBlue,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SearchSourcesDialog(
    sources: List<WebSearchSource>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("출처", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                sources.forEach { source ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF101318),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            SourceDetailLine("검색 제공자", source.providerDisplayName)
                            SourceDetailLine("검색어", source.queryUsed)
                            SourceDetailLine("자료 출처", source.source.orEmpty())
                            SourceDetailLine("제목", source.title)
                            source.publishedAt?.takeIf { it.isNotBlank() }?.let { SourceDetailLine("게시일", it) }
                            source.snippet?.takeIf { it.isNotBlank() }?.let { SourceDetailLine("요약", it) }
                            source.url?.takeIf { it.isNotBlank() }?.let { url ->
                                TextButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }.onFailure {
                                            Toast.makeText(context, "원문을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text("원문 열기", color = AccentBlue)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기", color = AccentBlue)
            }
        },
        containerColor = PanelBg,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary
    )
}

@Composable
private fun SourceDetailLine(
    label: String,
    value: String
) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
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
    isRegenerating: Boolean,
    onBranch: () -> Unit,
    onRetry: () -> Unit,
    onRegenerate: (ResponseRegenerationAction) -> Unit,
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
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
            text = { Text("↗  새 채팅으로 가지치기", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            onClick = onBranch
        )

        DropdownMenuItem(
            text = { Text("$selectedModel 사용함", color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            onClick = {},
            enabled = false
        )

        DropdownMenuItem(
            text = { Text("답변 다시 생성", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            onClick = onRetry,
            enabled = !isRegenerating
        )

        DropdownMenuItem(
            text = { Text("더 짧게", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            onClick = { onRegenerate(ResponseRegenerationAction.Shorter) },
            enabled = !isRegenerating
        )

        DropdownMenuItem(
            text = { Text("더 자세히", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            onClick = { onRegenerate(ResponseRegenerationAction.MoreDetailed) },
            enabled = !isRegenerating
        )

        DropdownMenuItem(
            text = { Text("표로 정리", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            onClick = { onRegenerate(ResponseRegenerationAction.Table) },
            enabled = !isRegenerating
        )

        DropdownMenuItem(
            text = { Text("전문가 톤", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            onClick = { onRegenerate(ResponseRegenerationAction.ExpertTone) },
            enabled = !isRegenerating
        )

        DropdownMenuItem(
            text = {
                Text(
                    text = if (webSearchEnabled) "🌐  웹 검색 끄기" else "🌐  웹 검색",
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.35f),
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
    onStopClick: () -> Unit,
    pendingAttachments: List<LocalAttachment>,
    onRemoveAttachment: (LocalAttachment) -> Unit,
    onAppendQuickPrompt: (String) -> Unit,
) {
    var quickPromptExpanded by remember { mutableStateOf(false) }
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
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, 1) }
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PanelBg,
                    modifier = Modifier.clickable { quickPromptExpanded = !quickPromptExpanded }
                ) {
                    Text(
                        text = if (quickPromptExpanded) "빠른 입력 닫기" else "빠른 입력",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            if (quickPromptExpanded) {
                QuickPromptChips(
                    presets = QuickPromptPresets,
                    onChipClick = onAppendQuickPrompt
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
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
                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
                            if (isGenerating) {
                                Surface(
                                    modifier = Modifier.size(42.dp),
                                    shape = CircleShape,
                                    color = DangerRed
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable { onStopClick() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "■",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
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

}
@Composable
private fun QuickPromptChips(
    presets: List<String>,
    onChipClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { preset ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PanelBg,
                modifier = Modifier.clickable { onChipClick(preset) }
            ) {
                Text(
                    text = preset,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
        }
    }
}

private fun appendQuickPrompt(currentInput: String, preset: String): String {
    val trimmed = currentInput.trimEnd()
    if (trimmed.isEmpty()) return preset
    val separator = if (trimmed.endsWith(".") || trimmed.endsWith("?") || trimmed.endsWith("!")) "\n" else " "
    return trimmed + separator + preset
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

private fun isImageAttachment(
    attachment: LocalAttachment
): Boolean {
    val name = attachment.name.lowercase(Locale.ROOT)
    return attachment.mimeType.startsWith("image/") ||
        name.endsWith(".png") ||
        name.endsWith(".jpg") ||
        name.endsWith(".jpeg") ||
        name.endsWith(".webp") ||
        name.endsWith(".gif") ||
        name.endsWith(".bmp")
}

private fun buildImageUserInstruction(
    userInput: String
): String {
    return userInput.trim().ifBlank {
        "이 이미지를 자세히 설명해 주세요."
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
    onLinkExternalModel: () -> Unit,
    onOpenStorageManager: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onOpenBenchmark: (modelName: String?, openHistory: Boolean) -> Unit,
    onToggleReasoning: () -> Unit,
    onToggleWebSearch: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(FusionPrefsName, Context.MODE_PRIVATE) }
    val benchmarkDao = remember { AppDatabase.getInstance(context).benchmarkDao() }
    val benchmarkResults by benchmarkDao.observeAll().collectAsState(initial = emptyList())
    var favoriteModelIds by remember { mutableStateOf(prefs.getStringSet(PrefFavoriteModelIds, emptySet())?.toSet() ?: emptySet()) }
    var hiddenModelIds by remember { mutableStateOf(prefs.getStringSet(PrefHiddenModelIds, emptySet())?.toSet() ?: emptySet()) }
    var showHiddenModels by remember { mutableStateOf(prefs.getBoolean(PrefShowHiddenModels, false)) }
    var sortMode by remember { mutableStateOf(ModelLibrarySortMode.fromKey(prefs.getString(PrefModelLibrarySortMode, null))) }
    var searchQuery by remember { mutableStateOf("") }
    var activeStatusFilter by remember { mutableStateOf("전체") }
    var activeFamilyFilter by remember { mutableStateOf("전체") }
    var modelViewMode by remember { mutableStateOf("전체 모델") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showClearRecentConfirm by remember { mutableStateOf(false) }
    var recentHistory by remember { mutableStateOf(loadRecentModels(prefs)) }
    var modelNotes by remember { mutableStateOf(loadModelNotes(prefs)) }
    val models = builtInModels.toList() + customModels.toList()
    val catalogModels = FusionModelCatalog.all(context)
    val selectedSpecId = remember(currentModel, catalogModels) {
        catalogModels.firstOrNull { it.displayName == currentModel }?.id
    }
    val selectedHiddenSpec = remember(selectedSpecId, hiddenModelIds, catalogModels) {
        selectedSpecId?.takeIf { it in hiddenModelIds }?.let { id -> catalogModels.firstOrNull { it.id == id } }
    }
    val visibleCatalogModels = remember(catalogModels, hiddenModelIds, showHiddenModels) {
        if (showHiddenModels) catalogModels else catalogModels.filterNot { it.id in hiddenModelIds }
    }
    val baseModels = remember(catalogModels, visibleCatalogModels, activeStatusFilter, hiddenModelIds) {
        when (activeStatusFilter) {
            "숨긴 모델" -> catalogModels.filter { it.id in hiddenModelIds }
            else -> visibleCatalogModels
        }
    }
    val statusFilteredModels = remember(baseModels, activeStatusFilter, context, favoriteModelIds) {
        when (activeStatusFilter) {
            "전체" -> baseModels
            "즐겨찾기" -> baseModels.filter { it.id in favoriteModelIds }
            "로컬 가능" -> baseModels.filter { isCatalogModelAvailable(context, it) || it.availability == ModelAvailability.CUSTOM_IMPORTED }
            "변환 필요" -> baseModels.filter { it.availability == ModelAvailability.NEEDS_CONVERSION || it.availability == ModelAvailability.UNSUPPORTED_ON_DEVICE }
            "원격 전용" -> baseModels.filter { it.availability == ModelAvailability.REMOTE_ONLY }
            "8GB 권장" -> baseModels.filter { (it.recommendedRamGb ?: 0) <= 8 && (it.recommendedRamGb ?: 0) > 0 }
            "숨긴 모델" -> baseModels
            else -> baseModels
        }
    }
    val familyFilteredModels = remember(statusFilteredModels, activeFamilyFilter) {
        if (activeFamilyFilter == "전체") statusFilteredModels
        else statusFilteredModels.filter { it.family.name.equals(activeFamilyFilter, ignoreCase = true) }
    }
    val normalizedSearch = remember(searchQuery) { searchQuery.trim().lowercase(Locale.getDefault()) }
    val filteredCatalogModels = remember(familyFilteredModels, normalizedSearch) {
        if (normalizedSearch.isBlank()) {
            familyFilteredModels
        } else {
            familyFilteredModels.filter { spec ->
                listOf(
                    spec.displayName,
                    spec.family.name,
                    spec.parameterLabel,
                    spec.runtimeFormat.name,
                    spec.sourceLabel ?: "",
                    spec.notes,
                    modelNotes[spec.id].orEmpty()
                ).any { it.lowercase(Locale.getDefault()).contains(normalizedSearch) }
            }
        }
    }
    val deviceSummary = remember(catalogModels) {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(info)
        val total = info.totalMem / (1024f * 1024f * 1024f)
        val avail = info.availMem / (1024f * 1024f * 1024f)
        Triple(total, avail, info.lowMemory)
    }
    val recommendedEvaluations = remember(filteredCatalogModels, deviceSummary, currentModel) {
        filteredCatalogModels.map { spec ->
            evaluateModelRecommendation(
                spec = spec,
                totalRamGb = deviceSummary.first,
                availableRamGb = deviceSummary.second,
                lowMemory = deviceSummary.third,
                isCurrentSelected = spec.displayName == currentModel,
                isFavorite = spec.id in favoriteModelIds,
                isLocalAvailable = isCatalogModelAvailable(context, spec),
                currentMaxTokens = prefs.getInt(PrefMaxTokens, 4000)
            )
        }
    }
    val benchmarkSummaryByModelId = remember(catalogModels, benchmarkResults) {
        catalogModels.associate { spec ->
            spec.id to buildModelBenchmarkSummary(spec, benchmarkResults)
        }
    }
    recommendedEvaluations.forEach { evaluation ->
        val spec = evaluation.spec
        Log.d(
            "FusionModelRecommend",
            "id=${spec.id}, name=${spec.displayName}, availability=${spec.availability}, memoryClass=${spec.memoryClass}, " +
                "recommendedDeviceClass=${spec.recommendedDeviceClass}, minRam=${spec.minRecommendedRamGb}, " +
                "recommendedRam=${spec.recommendedRamGb}, sizeGb=${spec.modelSizeEstimateGb}, totalRam=${deviceSummary.first}, " +
                "availableRam=${deviceSummary.second}, hidden=${spec.id in hiddenModelIds}, favorite=${spec.id in favoriteModelIds}, " +
                "current=${spec.displayName == currentModel}, tier=${evaluation.tier}, ramClass=${evaluation.deviceRamClass}, included=${evaluation.includedInRecommendedLocal}, includeReason=${evaluation.includeReason}, reason=${evaluation.reason}"
        )
    }
    val recommendedBest = remember(recommendedEvaluations) { recommendedEvaluations.filter { it.includedInRecommendedLocal && it.tier == "권장" } }
    val recommendedExperimental = remember(recommendedEvaluations) { recommendedEvaluations.filter { it.includedInRecommendedLocal && it.tier == "실험 가능" } }
    val recommendedTop = remember(recommendedBest, recommendedExperimental) { recommendedBest + recommendedExperimental }
    val recommendedCaution = remember(recommendedEvaluations) { recommendedEvaluations.filter { it.includedInRecommendedLocal && it.tier == "주의 필요" } }
    val recommendedNot = remember(recommendedEvaluations) { recommendedEvaluations.filter { it.tier == "권장하지 않음" || it.tier == "원격 전용" } }
    val favoriteVisibleSpecs = remember(visibleCatalogModels, favoriteModelIds) {
        visibleCatalogModels.filter { it.id in favoriteModelIds }
    }
    val sortedFavoriteVisibleSpecs = remember(favoriteVisibleSpecs, sortMode, currentModel, favoriteModelIds) {
        sortModelSpecs(favoriteVisibleSpecs, sortMode, currentModel, favoriteModelIds)
    }
    val recommendedEvaluationMap = remember(recommendedEvaluations) { recommendedEvaluations.associateBy { it.spec.id } }
    val sortedRecommendedBestSpecs = remember(recommendedBest, sortMode, currentModel, favoriteModelIds) {
        sortModelSpecs(recommendedBest.map { it.spec }, sortMode, currentModel, favoriteModelIds)
    }
    val sortedRecommendedExperimentalSpecs = remember(recommendedExperimental, sortMode, currentModel, favoriteModelIds) {
        sortModelSpecs(recommendedExperimental.map { it.spec }, sortMode, currentModel, favoriteModelIds)
    }
    val sortedRecommendedCautionSpecs = remember(recommendedCaution, sortMode, currentModel, favoriteModelIds) {
        sortModelSpecs(recommendedCaution.map { it.spec }, sortMode, currentModel, favoriteModelIds)
    }
    val recentModels = remember(recentHistory, catalogModels, hiddenModelIds, showHiddenModels) {
        recentHistory.mapNotNull { entry ->
            val spec = catalogModels.firstOrNull { it.id == entry.modelId } ?: return@mapNotNull null
            if (!showHiddenModels && spec.id in hiddenModelIds) return@mapNotNull null
            spec to entry
        }
    }
    val showRecentSection = remember(searchQuery, activeStatusFilter, activeFamilyFilter, modelViewMode, recentModels) {
        searchQuery.isBlank() && activeStatusFilter == "전체" && activeFamilyFilter == "전체" && modelViewMode == "전체 모델" && recentModels.isNotEmpty()
    }
    fun persistFavorite(ids: Set<String>) {
        favoriteModelIds = ids
        prefs.edit().putStringSet(PrefFavoriteModelIds, ids).apply()
    }
    fun persistHidden(ids: Set<String>) {
        hiddenModelIds = ids
        prefs.edit().putStringSet(PrefHiddenModelIds, ids).apply()
    }
    fun setShowHidden(enabled: Boolean) {
        showHiddenModels = enabled
        prefs.edit().putBoolean(PrefShowHiddenModels, enabled).apply()
    }
    fun persistSort(mode: ModelLibrarySortMode) {
        sortMode = mode
        prefs.edit().putString(PrefModelLibrarySortMode, mode.key).apply()
    }
    fun persistModelNote(modelId: String, note: String?) {
        val next = modelNotes.toMutableMap()
        val trimmed = note?.trim().orEmpty()
        if (trimmed.isBlank()) next.remove(modelId) else next[modelId] = trimmed
        modelNotes = next
        saveModelNotes(prefs, next)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("모델 라이브러리")
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "사용할 모델을 선택하거나 새 모델을 가져옵니다.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FusionTextButton(onClick = onLinkExternalModel) { Text("외부 모델 파일 연결", fontSize = 13.sp) }
                        FusionTextButton(onClick = onOpenStorageManager) { Text("모델 저장공간", fontSize = 13.sp) }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("전체 모델", "내 기기에 추천").forEach { label ->
                            CompactFilterChip(
                                label = label,
                                selected = modelViewMode == label,
                                onClick = { modelViewMode = label }
                            )
                        }
                    }
                    Box {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PanelBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, LineColor),
                            modifier = Modifier.clickable { sortMenuExpanded = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("정렬: ${sortMode.label}", color = TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                            containerColor = PanelBg
                        ) {
                            val menuItemColors = MenuDefaults.itemColors(
                                textColor = TextPrimary,
                                leadingIconColor = TextPrimary,
                                trailingIconColor = TextPrimary,
                                disabledTextColor = TextSecondary,
                                disabledLeadingIconColor = TextSecondary,
                                disabledTrailingIconColor = TextSecondary
                            )
                            ModelLibrarySortMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors = menuItemColors,
                                    onClick = {
                                        sortMenuExpanded = false
                                        persistSort(mode)
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("모델을 검색합니다.", color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = LineColor,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AccentBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("전체", "즐겨찾기", "로컬 가능", "변환 필요", "원격 전용", "8GB 권장", "숨긴 모델").forEach { label ->
                            CompactFilterChip(
                                label = label,
                                selected = activeStatusFilter == label,
                                onClick = {
                                    activeStatusFilter = label
                                    if (label == "전체") activeFamilyFilter = "전체"
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "전체" to "전체",
                            "GEMMA" to "Gemma",
                            "QWEN" to "Qwen",
                            "LLAMA" to "Llama",
                            "PHI" to "Phi",
                            "DEEPSEEK" to "DeepSeek",
                            "MISTRAL" to "Mistral",
                            "KIMI" to "Kimi",
                            "CUSTOM" to "Custom"
                        ).forEach { (key, label) ->
                            CompactFilterChip(
                                label = label,
                                selected = activeFamilyFilter == key,
                                onClick = { activeFamilyFilter = key }
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = PanelBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { setShowHidden(!showHiddenModels) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("숨긴 모델 표시", color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (showHiddenModels) "켜짐" else "꺼짐", color = AccentBlue, fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    selectedHiddenSpec?.let { selectedSpec ->
                        if (!showHiddenModels) {
                            ModelZooSection(
                                title = "현재 사용 중",
                                specs = listOf(selectedSpec),
                                currentModel = currentModel,
                                onSelect = { spec ->
                                    val model = LocalModel(spec.displayName, spec.fileName ?: spec.displayName, customPath = spec.localPath, downloadUrl = spec.downloadUrl)
                                    onSelect(model)
                                },
                                onUploadCustomModel = onUploadCustomModel,
                                onApplyRecommendedSettings = { spec ->
                                    applyDeviceAwareRecommendedSettings(context, prefs.edit(), spec)
                                    Toast.makeText(context, "권장 설정을 적용했습니다.", Toast.LENGTH_SHORT).show()
                                },
                                favoriteModelIds = favoriteModelIds,
                                hiddenModelIds = hiddenModelIds,
                                showHiddenBadge = true,
                                onToggleFavorite = { spec ->
                                    val next = if (spec.id in favoriteModelIds) favoriteModelIds - spec.id else favoriteModelIds + spec.id
                                    persistFavorite(next)
                                    Toast.makeText(context, if (spec.id in favoriteModelIds) "즐겨찾기에서 제거했습니다." else "즐겨찾기에 추가했습니다.", Toast.LENGTH_SHORT).show()
                                },
                                onHideModel = { spec ->
                                    persistHidden(hiddenModelIds + spec.id)
                                    Toast.makeText(context, "모델을 숨겼습니다.", Toast.LENGTH_SHORT).show()
                                },
                                onUnhideModel = { spec ->
                                    persistHidden(hiddenModelIds - spec.id)
                                    Toast.makeText(context, "모델 숨김을 해제했습니다.", Toast.LENGTH_SHORT).show()
                                },
                                modelNotes = modelNotes,
                                onSaveModelNote = ::persistModelNote,
                                benchmarkSummaryByModelId = benchmarkSummaryByModelId,
                                onOpenBenchmark = onOpenBenchmark
                            )
                        }
                    }

                    val favoriteVisibleSpecs = filteredCatalogModels.filter { it.id in favoriteModelIds }
                    val recentSpecs = recentModels.map { it.first }
                    val recentSpecIds = recentSpecs.map { it.id }.toSet()
                    if (showRecentSection) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "최근 사용 모델",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            FusionTextButton(onClick = { showClearRecentConfirm = true }) {
                                Text("최근 사용 기록 지우기", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        ModelZooSection(
                            title = "최근 사용 모델",
                            specs = sortModelSpecs(recentSpecs, sortMode, currentModel, favoriteModelIds),
                            currentModel = currentModel,
                            onSelect = { spec ->
                                val model = LocalModel(spec.displayName, spec.fileName ?: spec.displayName, customPath = spec.localPath, downloadUrl = spec.downloadUrl)
                                onSelect(model)
                            },
                            onUploadCustomModel = onUploadCustomModel,
                            onApplyRecommendedSettings = { spec ->
                                applyDeviceAwareRecommendedSettings(context, prefs.edit(), spec)
                                Toast.makeText(context, "권장 설정을 적용했습니다.", Toast.LENGTH_SHORT).show()
                            },
                            favoriteModelIds = favoriteModelIds,
                            hiddenModelIds = hiddenModelIds,
                            showHiddenBadge = showHiddenModels,
                            onToggleFavorite = { spec ->
                                val next = if (spec.id in favoriteModelIds) favoriteModelIds - spec.id else favoriteModelIds + spec.id
                                persistFavorite(next)
                                Toast.makeText(context, if (spec.id in favoriteModelIds) "즐겨찾기에서 제거했습니다." else "즐겨찾기에 추가했습니다.", Toast.LENGTH_SHORT).show()
                            },
                            onHideModel = { spec ->
                                persistHidden(hiddenModelIds + spec.id)
                                Toast.makeText(context, "모델을 숨겼습니다.", Toast.LENGTH_SHORT).show()
                            },
                            onUnhideModel = { spec ->
                                persistHidden(hiddenModelIds - spec.id)
                                Toast.makeText(context, "모델 숨김을 해제했습니다.", Toast.LENGTH_SHORT).show()
                            }
                        )
                        val latestRecent = recentModels.firstOrNull()?.second
                        latestRecent?.let {
                            Text(formatRecentUsedLabel(it.lastUsedAt), color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    if (modelViewMode == "내 기기에 추천") {
                        Surface(shape = RoundedCornerShape(10.dp), color = PanelBg, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("기기 메모리: 약 ${formatGb(deviceSummary.first)}GB", color = TextSecondary, fontSize = 12.sp)
                                Text("현재 사용 가능: 약 ${formatGb(deviceSummary.second)}GB", color = TextSecondary, fontSize = 12.sp)
                                if (deviceSummary.third) Text("저메모리 모드를 권장합니다.", color = DangerRed, fontSize = 12.sp)
                            }
                        }
                        val selectedEval = recommendedEvaluations.firstOrNull { it.spec.displayName == currentModel && it.tier !in listOf("권장", "실험 가능") }
                        selectedEval?.let {
                            ModelZooSection(
                                title = "현재 사용 중",
                                specs = listOf(it.spec),
                                currentModel = currentModel,
                                onSelect = { spec -> onSelect(LocalModel(spec.displayName, spec.fileName ?: spec.displayName, customPath = spec.localPath, downloadUrl = spec.downloadUrl)) },
                                onUploadCustomModel = onUploadCustomModel,
                                onApplyRecommendedSettings = { spec -> applyDeviceAwareRecommendedSettings(context, prefs.edit(), spec); Toast.makeText(context, "권장 설정을 적용했습니다.", Toast.LENGTH_SHORT).show() },
                                favoriteModelIds = favoriteModelIds,
                                hiddenModelIds = hiddenModelIds,
                                showHiddenBadge = showHiddenModels,
                                onToggleFavorite = { spec -> val next = if (spec.id in favoriteModelIds) favoriteModelIds - spec.id else favoriteModelIds + spec.id; persistFavorite(next); Toast.makeText(context, if (spec.id in favoriteModelIds) "즐겨찾기에서 제거했습니다." else "즐겨찾기에 추가했습니다.", Toast.LENGTH_SHORT).show() },
                                onHideModel = { spec -> persistHidden(hiddenModelIds + spec.id); Toast.makeText(context, "모델을 숨겼습니다.", Toast.LENGTH_SHORT).show() },
                                onUnhideModel = { spec -> persistHidden(hiddenModelIds - spec.id); Toast.makeText(context, "모델 숨김을 해제했습니다.", Toast.LENGTH_SHORT).show() },
                                recommendationMap = recommendedEvaluations.associate { ev -> ev.spec.id to ev },
                                modelNotes = modelNotes,
                                onSaveModelNote = ::persistModelNote,
                                benchmarkSummaryByModelId = benchmarkSummaryByModelId,
                                onOpenBenchmark = onOpenBenchmark
                            )
                            Text("현재 선택된 모델은 이 기기에서 안정적으로 실행되지 않을 수 있습니다.", color = DangerRed, fontSize = 12.sp)
                        }
                        if (recommendedBest.isNotEmpty()) {
                            ModelZooSection(
                                title = "권장 모델",
                                specs = sortedRecommendedBestSpecs,
                                currentModel = currentModel,
                                onSelect = { spec -> onSelect(LocalModel(spec.displayName, spec.fileName ?: spec.displayName, customPath = spec.localPath, downloadUrl = spec.downloadUrl)) },
                                onUploadCustomModel = onUploadCustomModel,
                                onApplyRecommendedSettings = { spec -> applyDeviceAwareRecommendedSettings(context, prefs.edit(), spec); Toast.makeText(context, "권장 설정을 적용했습니다.", Toast.LENGTH_SHORT).show() },
                                favoriteModelIds = favoriteModelIds,
                                hiddenModelIds = hiddenModelIds,
                                showHiddenBadge = showHiddenModels,
                                onToggleFavorite = { spec -> val next = if (spec.id in favoriteModelIds) favoriteModelIds - spec.id else favoriteModelIds + spec.id; persistFavorite(next); Toast.makeText(context, if (spec.id in favoriteModelIds) "즐겨찾기에서 제거했습니다." else "즐겨찾기에 추가했습니다.", Toast.LENGTH_SHORT).show() },
                                onHideModel = { spec -> persistHidden(hiddenModelIds + spec.id); Toast.makeText(context, "모델을 숨겼습니다.", Toast.LENGTH_SHORT).show() },
                                onUnhideModel = { spec -> persistHidden(hiddenModelIds - spec.id); Toast.makeText(context, "모델 숨김을 해제했습니다.", Toast.LENGTH_SHORT).show() },
                                recommendationMap = recommendedEvaluationMap,
                                modelNotes = modelNotes,
                                onSaveModelNote = ::persistModelNote,
                                benchmarkSummaryByModelId = benchmarkSummaryByModelId,
                                onOpenBenchmark = onOpenBenchmark
                            )
                        }
                        if (recommendedExperimental.isNotEmpty()) {
                            ModelZooSection(
                                title = "실험 가능한 모델",
                                specs = sortedRecommendedExperimentalSpecs,
                                currentModel = currentModel,
                                onSelect = { spec -> onSelect(LocalModel(spec.displayName, spec.fileName ?: spec.displayName, customPath = spec.localPath, downloadUrl = spec.downloadUrl)) },
                                onUploadCustomModel = onUploadCustomModel,
                                onApplyRecommendedSettings = { spec -> applyDeviceAwareRecommendedSettings(context, prefs.edit(), spec); Toast.makeText(context, "권장 설정을 적용했습니다.", Toast.LENGTH_SHORT).show() },
                                favoriteModelIds = favoriteModelIds,
                                hiddenModelIds = hiddenModelIds,
                                showHiddenBadge = showHiddenModels,
                                onToggleFavorite = { spec -> val next = if (spec.id in favoriteModelIds) favoriteModelIds - spec.id else favoriteModelIds + spec.id; persistFavorite(next); Toast.makeText(context, if (spec.id in favoriteModelIds) "즐겨찾기에서 제거했습니다." else "즐겨찾기에 추가했습니다.", Toast.LENGTH_SHORT).show() },
                                onHideModel = { spec -> persistHidden(hiddenModelIds + spec.id); Toast.makeText(context, "모델을 숨겼습니다.", Toast.LENGTH_SHORT).show() },
                                onUnhideModel = { spec -> persistHidden(hiddenModelIds - spec.id); Toast.makeText(context, "모델 숨김을 해제했습니다.", Toast.LENGTH_SHORT).show() },
                                recommendationMap = recommendedEvaluationMap,
                                modelNotes = modelNotes,
                                onSaveModelNote = ::persistModelNote,
                                benchmarkSummaryByModelId = benchmarkSummaryByModelId,
                                onOpenBenchmark = onOpenBenchmark
                            )
                        }
                        if (recommendedCaution.isNotEmpty()) {
                            ModelZooSection(
                                title = "주의가 필요한 모델",
                                specs = sortedRecommendedCautionSpecs,
                                currentModel = currentModel,
                                onSelect = { spec -> onSelect(LocalModel(spec.displayName, spec.fileName ?: spec.displayName, customPath = spec.localPath, downloadUrl = spec.downloadUrl)) },
                                onUploadCustomModel = onUploadCustomModel,
                                onApplyRecommendedSettings = { spec -> applyDeviceAwareRecommendedSettings(context, prefs.edit(), spec); Toast.makeText(context, "권장 설정을 적용했습니다.", Toast.LENGTH_SHORT).show() },
                                favoriteModelIds = favoriteModelIds,
                                hiddenModelIds = hiddenModelIds,
                                showHiddenBadge = showHiddenModels,
                                onToggleFavorite = { spec -> val next = if (spec.id in favoriteModelIds) favoriteModelIds - spec.id else favoriteModelIds + spec.id; persistFavorite(next); Toast.makeText(context, if (spec.id in favoriteModelIds) "즐겨찾기에서 제거했습니다." else "즐겨찾기에 추가했습니다.", Toast.LENGTH_SHORT).show() },
                                onHideModel = { spec -> persistHidden(hiddenModelIds + spec.id); Toast.makeText(context, "모델을 숨겼습니다.", Toast.LENGTH_SHORT).show() },
                                onUnhideModel = { spec -> persistHidden(hiddenModelIds - spec.id); Toast.makeText(context, "모델 숨김을 해제했습니다.", Toast.LENGTH_SHORT).show() },
                                recommendationMap = recommendedEvaluationMap,
                                modelNotes = modelNotes,
                                onSaveModelNote = ::persistModelNote,
                                benchmarkSummaryByModelId = benchmarkSummaryByModelId,
                                onOpenBenchmark = onOpenBenchmark
                            )
                        }
                        if (recommendedTop.isEmpty() && recommendedCaution.isEmpty()) {
                            Text("현재 기기에 추천할 수 있는 로컬 모델이 없습니다.", color = TextSecondary, fontSize = 13.sp)
                            Text("전체 모델에서 변환 필요 또는 원격 모델을 확인해 주세요.", color = TextSecondary, fontSize = 12.sp)
                        }
                        FusionTextButton(onClick = { activeStatusFilter = "전체"; activeFamilyFilter = "전체"; modelViewMode = "전체 모델" }) {
                            Text("전체 모델로 보기", fontSize = 12.sp)
                        }
                    } else if (favoriteVisibleSpecs.isNotEmpty() && activeStatusFilter == "전체") {
                        ModelZooSection(
                            title = "즐겨찾기",
                            specs = sortedFavoriteVisibleSpecs.filterNot { it.id in recentSpecIds },
                            currentModel = currentModel,
                            onSelect = { spec ->
                                val model = LocalModel(spec.displayName, spec.fileName ?: spec.displayName, customPath = spec.localPath, downloadUrl = spec.downloadUrl)
                                onSelect(model)
                            },
                            onUploadCustomModel = onUploadCustomModel,
                            onApplyRecommendedSettings = { spec ->
                                applyDeviceAwareRecommendedSettings(context, prefs.edit(), spec)
                                Toast.makeText(context, "권장 설정을 적용했습니다.", Toast.LENGTH_SHORT).show()
                            },
                            favoriteModelIds = favoriteModelIds,
                            hiddenModelIds = hiddenModelIds,
                            showHiddenBadge = showHiddenModels,
                            onToggleFavorite = { spec ->
                                val next = if (spec.id in favoriteModelIds) favoriteModelIds - spec.id else favoriteModelIds + spec.id
                                persistFavorite(next)
                                Toast.makeText(context, if (spec.id in favoriteModelIds) "즐겨찾기에서 제거했습니다." else "즐겨찾기에 추가했습니다.", Toast.LENGTH_SHORT).show()
                            },
                            onHideModel = { spec ->
                                persistHidden(hiddenModelIds + spec.id)
                                Toast.makeText(context, "모델을 숨겼습니다.", Toast.LENGTH_SHORT).show()
                            },
                            onUnhideModel = { spec ->
                                persistHidden(hiddenModelIds - spec.id)
                                Toast.makeText(context, "모델 숨김을 해제했습니다.", Toast.LENGTH_SHORT).show()
                            },
                            modelNotes = modelNotes,
                            onSaveModelNote = ::persistModelNote,
                            benchmarkSummaryByModelId = benchmarkSummaryByModelId,
                            onOpenBenchmark = onOpenBenchmark
                        )
                    }

                    if (modelViewMode != "내 기기에 추천") {
                    ModelZooSection(
                        title = "사용 가능한 모델",
                        specs = sortModelSpecs(filteredCatalogModels.filter { isCatalogModelAvailable(context, it) && (activeStatusFilter != "전체" || it.id !in favoriteModelIds) && (it.id !in recentSpecIds || !showRecentSection) }, sortMode, currentModel, favoriteModelIds),
                        currentModel = currentModel,
                        onSelect = { spec ->
                            val model = LocalModel(
                                name = spec.displayName,
                                fileName = spec.fileName ?: spec.displayName,
                                customPath = spec.localPath,
                                downloadUrl = spec.downloadUrl
                            )
                            onSelect(model)
                        },
                        onUploadCustomModel = onUploadCustomModel,
                        onApplyRecommendedSettings = { spec ->
                            applyDeviceAwareRecommendedSettings(context, prefs.edit(), spec)
                            Toast.makeText(context, "권장 설정을 적용했습니다.", Toast.LENGTH_SHORT).show()
                        },
                        favoriteModelIds = favoriteModelIds,
                        hiddenModelIds = hiddenModelIds,
                        showHiddenBadge = showHiddenModels,
                        onToggleFavorite = { spec ->
                            val next = if (spec.id in favoriteModelIds) favoriteModelIds - spec.id else favoriteModelIds + spec.id
                            persistFavorite(next)
                            Toast.makeText(context, if (spec.id in favoriteModelIds) "즐겨찾기에서 제거했습니다." else "즐겨찾기에 추가했습니다.", Toast.LENGTH_SHORT).show()
                        },
                        onHideModel = { spec ->
                            persistHidden(hiddenModelIds + spec.id)
                            Toast.makeText(context, "모델을 숨겼습니다.", Toast.LENGTH_SHORT).show()
                        },
                        onUnhideModel = { spec ->
                            persistHidden(hiddenModelIds - spec.id)
                            Toast.makeText(context, "모델 숨김을 해제했습니다.", Toast.LENGTH_SHORT).show()
                        },
                        recommendationMap = emptyMap(),
                        modelNotes = modelNotes,
                        onSaveModelNote = ::persistModelNote,
                        benchmarkSummaryByModelId = benchmarkSummaryByModelId,
                        onOpenBenchmark = onOpenBenchmark
                    )
                    ModelZooSection(
                        title = "변환이 필요한 모델",
                        specs = sortModelSpecs(filteredCatalogModels.filter { (it.availability == ModelAvailability.NEEDS_CONVERSION || it.availability == ModelAvailability.UNSUPPORTED_ON_DEVICE) && (activeStatusFilter != "전체" || it.id !in favoriteModelIds) && (it.id !in recentSpecIds || !showRecentSection) }, sortMode, currentModel, favoriteModelIds),
                        currentModel = currentModel,
                        onSelect = {},
                        onUploadCustomModel = onUploadCustomModel,
                        onApplyRecommendedSettings = {},
                        favoriteModelIds = favoriteModelIds,
                        hiddenModelIds = hiddenModelIds,
                        showHiddenBadge = showHiddenModels,
                        onToggleFavorite = { spec ->
                            val next = if (spec.id in favoriteModelIds) favoriteModelIds - spec.id else favoriteModelIds + spec.id
                            persistFavorite(next)
                            Toast.makeText(context, if (spec.id in favoriteModelIds) "즐겨찾기에서 제거했습니다." else "즐겨찾기에 추가했습니다.", Toast.LENGTH_SHORT).show()
                        },
                        onHideModel = { spec ->
                            persistHidden(hiddenModelIds + spec.id)
                            Toast.makeText(context, "모델을 숨겼습니다.", Toast.LENGTH_SHORT).show()
                        },
                        onUnhideModel = { spec ->
                            persistHidden(hiddenModelIds - spec.id)
                            Toast.makeText(context, "모델 숨김을 해제했습니다.", Toast.LENGTH_SHORT).show()
                        },
                        modelNotes = modelNotes,
                        onSaveModelNote = ::persistModelNote,
                        benchmarkSummaryByModelId = benchmarkSummaryByModelId,
                        onOpenBenchmark = onOpenBenchmark
                    )
                    ModelZooSection(
                        title = "원격 모델",
                        specs = sortModelSpecs(filteredCatalogModels.filter { it.availability == ModelAvailability.REMOTE_ONLY && (activeStatusFilter != "전체" || it.id !in favoriteModelIds) && (it.id !in recentSpecIds || !showRecentSection) }, sortMode, currentModel, favoriteModelIds),
                        currentModel = currentModel,
                        onSelect = {},
                        onUploadCustomModel = onUploadCustomModel,
                        onApplyRecommendedSettings = {},
                        favoriteModelIds = favoriteModelIds,
                        hiddenModelIds = hiddenModelIds,
                        showHiddenBadge = showHiddenModels,
                        onToggleFavorite = { spec ->
                            val next = if (spec.id in favoriteModelIds) favoriteModelIds - spec.id else favoriteModelIds + spec.id
                            persistFavorite(next)
                            Toast.makeText(context, if (spec.id in favoriteModelIds) "즐겨찾기에서 제거했습니다." else "즐겨찾기에 추가했습니다.", Toast.LENGTH_SHORT).show()
                        },
                        onHideModel = { spec ->
                            persistHidden(hiddenModelIds + spec.id)
                            Toast.makeText(context, "모델을 숨겼습니다.", Toast.LENGTH_SHORT).show()
                        },
                        onUnhideModel = { spec ->
                            persistHidden(hiddenModelIds - spec.id)
                            Toast.makeText(context, "모델 숨김을 해제했습니다.", Toast.LENGTH_SHORT).show()
                        },
                        modelNotes = modelNotes,
                        onSaveModelNote = ::persistModelNote,
                        benchmarkSummaryByModelId = benchmarkSummaryByModelId,
                        onOpenBenchmark = onOpenBenchmark
                    )
                    ModelZooSection(
                        title = "가져온 모델",
                        specs = sortModelSpecs(filteredCatalogModels.filter { (it.id.startsWith("custom-") || it.id.startsWith("external-")) && (activeStatusFilter != "전체" || it.id !in favoriteModelIds) && (it.id !in recentSpecIds || !showRecentSection) }, sortMode, currentModel, favoriteModelIds),
                        currentModel = currentModel,
                        onSelect = { spec ->
                            when {
                                spec.availability == ModelAvailability.NEEDS_CONVERSION -> {
                                    Toast.makeText(context, "이 모델은 변환 후 사용할 수 있습니다.", Toast.LENGTH_SHORT).show()
                                }
                                spec.availability != ModelAvailability.CUSTOM_IMPORTED -> {
                                    Toast.makeText(context, "이 형식은 현재 직접 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
                                }
                                spec.localPath.isNullOrBlank() -> {
                                    Toast.makeText(context, "이 모델은 실행 전에 Fusion 내부 저장소로 복사해야 할 수 있습니다.", Toast.LENGTH_LONG).show()
                                }
                                else -> onSelect(LocalModel(spec.displayName, spec.fileName ?: spec.displayName, customPath = spec.localPath))
                            }
                        },
                        onUploadCustomModel = onUploadCustomModel,
                        onApplyRecommendedSettings = {},
                        favoriteModelIds = favoriteModelIds,
                        hiddenModelIds = hiddenModelIds,
                        showHiddenBadge = showHiddenModels,
                        onToggleFavorite = { spec ->
                            val next = if (spec.id in favoriteModelIds) favoriteModelIds - spec.id else favoriteModelIds + spec.id
                            persistFavorite(next)
                            Toast.makeText(context, if (spec.id in favoriteModelIds) "즐겨찾기에서 제거했습니다." else "즐겨찾기에 추가했습니다.", Toast.LENGTH_SHORT).show()
                        },
                        onHideModel = { spec ->
                            persistHidden(hiddenModelIds + spec.id)
                            Toast.makeText(context, "모델을 숨겼습니다.", Toast.LENGTH_SHORT).show()
                        },
                        onUnhideModel = { spec ->
                            persistHidden(hiddenModelIds - spec.id)
                            Toast.makeText(context, "모델 숨김을 해제했습니다.", Toast.LENGTH_SHORT).show()
                        },
                        modelNotes = modelNotes,
                        onSaveModelNote = ::persistModelNote,
                        benchmarkSummaryByModelId = benchmarkSummaryByModelId,
                        onOpenBenchmark = onOpenBenchmark
                    )
                    if (searchQuery.isNotBlank() && filteredCatalogModels.isEmpty()) {
                        Text(
                            text = "검색 결과가 없습니다.",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("다른 키워드로 검색해 보세요.", color = TextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .height(1.dp)
                                .background(LineColor)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "전체 모델",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    }

                    Text(
                        text = "기존 Gemma 다운로드",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    models.forEach { model: LocalModel ->
                        val downloaded = model.customPath != null || isDownloaded(model)
                        val downloading = downloadingModelName == model.name

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (model.name == currentModel) BubbleBg else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(model) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = model.name,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

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
                                        Spacer(modifier = Modifier.height(5.dp))

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
                        shape = RoundedCornerShape(12.dp),
                        color = PanelBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUploadCustomModel() }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "+ 커스텀 모델 업로드",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "다운로드한 .litertlm / .task / 모델 파일 선택",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                ModelLibrarySettingsDock(
                    reasoningEnabled = reasoningEnabled,
                    webSearchEnabled = webSearchEnabled,
                    generationSettings = generationSettings,
                    onToggleReasoning = onToggleReasoning,
                    onToggleWebSearch = onToggleWebSearch,
                    onOpenAdvancedSettings = onOpenAdvancedSettings,
                    onDismiss = onDismiss
                )
            }
        },
        confirmButton = {},
        containerColor = PanelBg,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary
    )

    if (showClearRecentConfirm) {
        AlertDialog(
            onDismissRequest = { showClearRecentConfirm = false },
            title = { Text("최근 사용 기록을 지우시겠습니까?") },
            text = { Text("모델 파일이나 설정은 삭제되지 않습니다.") },
            confirmButton = {
                FusionTextButton(
                    onClick = {
                        showClearRecentConfirm = false
                        saveRecentModels(prefs, emptyList())
                        recentHistory = emptyList()
                        Toast.makeText(context, "최근 사용 기록을 지웠습니다.", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("지우기", maxLines = 1) }
            },
            dismissButton = {
                FusionTextButton(onClick = { showClearRecentConfirm = false }) {
                    Text("취소", maxLines = 1)
                }
            },
            containerColor = PanelBg,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary
        )
    }
}

@Composable
private fun ModelLibrarySettingsDock(
    reasoningEnabled: Boolean,
    webSearchEnabled: Boolean,
    generationSettings: GenerationSettings,
    onToggleReasoning: () -> Unit,
    onToggleWebSearch: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BubbleBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactToggleChip(
                    title = "Reasoning",
                    checked = reasoningEnabled,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleReasoning
                )
                CompactToggleChip(
                    title = "Web search",
                    checked = webSearchEnabled,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleWebSearch
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PanelBg,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenAdvancedSettings() }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text("고급 설정", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "maxTokens ${generationSettings.maxTokens} · TopK ${generationSettings.topK}",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                FusionTextButton(onClick = onDismiss) {
                    Text("닫기", fontSize = 13.sp)
                }
            }
        }
    }

}

@Composable
private fun CompactToggleChip(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (checked) AccentBlue.copy(alpha = 0.18f) else PanelBg,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (checked) AccentBlue.copy(alpha = 0.65f) else LineColor
        ),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (checked) AccentBlue else TextSecondary.copy(alpha = 0.5f))
            )
            Text(
                text = "$title ${if (checked) "켜짐" else "꺼짐"}",
                color = if (checked) AccentBlue else TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) AccentBlue.copy(alpha = 0.18f) else PanelBg,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) AccentBlue.copy(alpha = 0.65f) else LineColor
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = if (selected) AccentBlue else TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ModelZooSection(
    title: String,
    specs: List<FusionModelSpec>,
    currentModel: String,
    onSelect: (FusionModelSpec) -> Unit,
    onUploadCustomModel: () -> Unit,
    onApplyRecommendedSettings: (FusionModelSpec) -> Unit,
    favoriteModelIds: Set<String>,
    hiddenModelIds: Set<String>,
    showHiddenBadge: Boolean,
    onToggleFavorite: (FusionModelSpec) -> Unit,
    onHideModel: (FusionModelSpec) -> Unit,
    onUnhideModel: (FusionModelSpec) -> Unit,
    recommendationMap: Map<String, ModelRecommendationEvaluation> = emptyMap(),
    modelNotes: Map<String, String> = emptyMap(),
    onSaveModelNote: (String, String?) -> Unit = { _, _ -> },
    benchmarkSummaryByModelId: Map<String, ModelBenchmarkSummary?> = emptyMap(),
    onOpenBenchmark: (modelName: String?, openHistory: Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var selectedSpec by remember { mutableStateOf<FusionModelSpec?>(null) }
    var showDirectDownloadConfirm by remember { mutableStateOf(false) }
    var selectionDecision by remember { mutableStateOf<ModelSelectionDecision?>(null) }
    if (specs.isEmpty()) return
    Text(title, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    specs.forEach { spec ->
        val available = isCatalogModelAvailable(context, spec)
        val memoryInfo = remember(spec.id) { buildModelMemoryInfo(context, spec) }
        val localSelectionMessage = buildLocalSelectionMessage(spec, available)
        val tokenRecommendation = remember(spec.id, memoryInfo.totalRamGb, memoryInfo.availableRamGb) {
            buildDeviceAwareTokenRecommendation(spec, memoryInfo.totalRamGb, memoryInfo.availableRamGb)
        }
        val hasNote = !modelNotes[spec.id].isNullOrBlank()
        val benchmarkSummary = benchmarkSummaryByModelId[spec.id]
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (spec.displayName == currentModel) BubbleBg else Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedSpec = spec }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(spec.displayName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("${spec.sourceLabel ?: spec.family.name} · ${spec.parameterLabel} · ${modelFootprintLabel(spec)}", color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(2.dp))
                    val rec = recommendationMap[spec.id]
                    Text(
                        rec?.reason ?: (localSelectionMessage ?: compactCompatibilityLine(memoryInfo, tokenRecommendation)),
                        color = if (rec?.tier == "주의 필요" || memoryInfo.warning != null) DangerRed else TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    rec?.let {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("권장 설정: maxTokens ${it.recommendedTokens} · ${it.hint}", color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    benchmarkSummary?.let {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(buildCardBenchmarkLine(it), color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (spec.id in favoriteModelIds) {
                    Text("★", color = AccentBlue, fontSize = 12.sp, maxLines = 1)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (hasNote) {
                    Text("메모", color = AccentBlue, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (showHiddenBadge && spec.id in hiddenModelIds) {
                    Text("숨김", color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (spec.displayName == currentModel) {
                    Text("사용 중", color = AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    selectedSpec?.let { spec ->
        val available = isCatalogModelAvailable(context, spec)
        val memoryInfo = buildModelMemoryInfo(context, spec)
        val tokenRecommendation = buildDeviceAwareTokenRecommendation(spec, memoryInfo.totalRamGb, memoryInfo.availableRamGb)
        ModelZooDetailDialog(
            spec = spec,
            available = available,
            memoryInfo = memoryInfo,
            tokenRecommendation = tokenRecommendation,
            localSelectionMessage = buildLocalSelectionMessage(spec, available),
            onDismiss = { selectedSpec = null },
            onSelect = {
                val decision = evaluateModelSelectionDecision(
                    spec = spec,
                    currentModel = currentModel,
                    memoryInfo = memoryInfo,
                    recommendation = recommendationMap[spec.id]
                )
                logModelSelectionDecision(spec, memoryInfo, recommendationMap[spec.id], decision)
                when (decision.level) {
                    ModelSelectRiskLevel.DIRECT -> {
                        onSelect(spec)
                        Toast.makeText(context, "모델을 선택했습니다.", Toast.LENGTH_SHORT).show()
                        selectedSpec = null
                    }
                    ModelSelectRiskLevel.CAUTION -> {
                        selectionDecision = decision
                    }
                    ModelSelectRiskLevel.BLOCKED -> {
                        if (decision.reason == "already selected") {
                            Toast.makeText(context, "이미 선택된 모델입니다.", Toast.LENGTH_SHORT).show()
                            selectedSpec = null
                        } else {
                            selectionDecision = decision
                        }
                    }
                }
            },
            onUploadCustomModel = onUploadCustomModel,
            onApplyRecommendedSettings = {
                onApplyRecommendedSettings(spec)
                selectedSpec = null
            },
            onOpenOfficial = { openModelLink(context, spec.officialUrl) },
            onOpenModelPage = {
                val pageUrl = spec.modelPageUrl ?: spec.downloadUrl ?: spec.officialUrl
                openModelLink(context, pageUrl)
            },
            onDirectDownload = {
                showDirectDownloadConfirm = true
            },
            isFavorite = spec.id in favoriteModelIds,
            isHidden = spec.id in hiddenModelIds,
            onToggleFavorite = {
                onToggleFavorite(spec)
            },
            onHideModel = {
                onHideModel(spec)
                selectedSpec = null
            },
            onUnhideModel = {
                onUnhideModel(spec)
            },
            onCopyLink = {
                val link = spec.directDownloadUrl ?: spec.modelPageUrl ?: spec.downloadUrl ?: spec.officialUrl
                if (link == null) {
                    Toast.makeText(context, "등록된 링크가 없습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    clipboard.setText(AnnotatedString(link))
                    Toast.makeText(context, "모델 링크를 복사했습니다.", Toast.LENGTH_SHORT).show()
                }
            },
            modelNote = modelNotes[spec.id].orEmpty(),
            onSaveModelNote = { note -> onSaveModelNote(spec.id, note) },
            benchmarkSummary = benchmarkSummaryByModelId[spec.id],
            recommendation = recommendationMap[spec.id],
            onOpenBenchmark = { openHistory ->
                selectedSpec = null
                onOpenBenchmark(spec.displayName, openHistory)
            }
        )
        selectionDecision?.let { decision ->
            if (decision.level == ModelSelectRiskLevel.CAUTION) {
                AlertDialog(
                    onDismissRequest = { selectionDecision = null },
                    title = { Text("이 모델을 선택하시겠습니까?") },
                    text = {
                        Text(
                            "현재 기기에서 메모리 부담이 발생할 수 있습니다. 긴 응답이나 멀티태스킹 환경에서는 앱이 종료될 수 있습니다.\n\n" +
                                "모델: ${decision.spec.displayName}\n" +
                                "예상 크기: ${modelFootprintLabel(decision.spec)}\n" +
                                "기기 RAM: ${decision.deviceRamClass}\n" +
                                "권장 maxTokens: ${decision.recommendedMaxTokens}\n" +
                                "권장 등급: ${decision.recommendationTier}"
                        )
                    },
                    confirmButton = {
                        FusionTextButton(onClick = {
                            onSelect(decision.spec)
                            applyDeviceAwareRecommendedSettings(context, context.getSharedPreferences(FusionPrefsName, Context.MODE_PRIVATE).edit(), decision.spec)
                            Toast.makeText(context, "모델과 권장 설정을 적용했습니다.", Toast.LENGTH_SHORT).show()
                            logModelSelectionDecision(decision.spec, memoryInfo, recommendationMap[decision.spec.id], decision.copy(level = ModelSelectRiskLevel.DIRECT, reason = "selected with recommended settings"))
                            selectionDecision = null
                            selectedSpec = null
                        }) { Text("권장 설정과 함께 선택") }
                    },
                    dismissButton = {
                        Row {
                            FusionTextButton(onClick = {
                                onSelect(decision.spec)
                                Toast.makeText(context, "모델을 선택했습니다.", Toast.LENGTH_SHORT).show()
                                logModelSelectionDecision(decision.spec, memoryInfo, recommendationMap[decision.spec.id], decision.copy(level = ModelSelectRiskLevel.DIRECT, reason = "selected directly"))
                                selectionDecision = null
                                selectedSpec = null
                            }) { Text("모델만 선택") }
                            FusionTextButton(onClick = { selectionDecision = null }) { Text("취소") }
                        }
                    },
                    containerColor = PanelBg,
                    titleContentColor = TextPrimary,
                    textContentColor = TextPrimary
                )
            } else {
                AlertDialog(
                    onDismissRequest = { selectionDecision = null },
                    title = { Text("로컬 실행을 권장하지 않습니다.") },
                    text = { Text(decision.blockMessage ?: "이 모델은 현재 기기에서 로컬 실행하기에 너무 클 수 있습니다. 서버 또는 원격 실행을 권장합니다.") },
                    confirmButton = { FusionTextButton(onClick = { selectionDecision = null }) { Text("확인") } },
                    containerColor = PanelBg,
                    titleContentColor = TextPrimary,
                    textContentColor = TextPrimary
                )
            }
        }
        if (showDirectDownloadConfirm) {
            DirectDownloadConfirmDialog(
                onDismiss = { showDirectDownloadConfirm = false },
                onConfirm = {
                    showDirectDownloadConfirm = false
                    startModelDirectDownload(context, spec)
                }
            )
        }
    }
}

@Composable
private fun ModelZooDetailDialog(
    spec: FusionModelSpec,
    available: Boolean,
    memoryInfo: ModelMemoryUiInfo,
    tokenRecommendation: TokenRecommendation,
    localSelectionMessage: String?,
    onDismiss: () -> Unit,
    onSelect: () -> Unit,
    onUploadCustomModel: () -> Unit,
    onApplyRecommendedSettings: () -> Unit,
    onOpenOfficial: () -> Unit,
    onOpenModelPage: () -> Unit,
    onDirectDownload: () -> Unit,
    isFavorite: Boolean,
    isHidden: Boolean,
    onToggleFavorite: () -> Unit,
    onHideModel: () -> Unit,
    onUnhideModel: () -> Unit,
    onCopyLink: () -> Unit,
    modelNote: String,
    onSaveModelNote: (String?) -> Unit,
    benchmarkSummary: ModelBenchmarkSummary?,
    recommendation: ModelRecommendationEvaluation?,
    onOpenBenchmark: (openHistory: Boolean) -> Unit
) {
    val context = LocalContext.current
    val socInfo = remember { collectFusionSocInfo() }
    var noteEditorOpen by remember { mutableStateOf(false) }
    var noteDeleteConfirmOpen by remember { mutableStateOf(false) }
    var noteDraft by remember(modelNote) { mutableStateOf(modelNote) }
    LaunchedEffect(spec.id, memoryInfo.totalRamGb, memoryInfo.availableRamGb, memoryInfo.tier, memoryInfo.warning) {
        val ramClass = when {
            memoryInfo.totalRamGb in 7.0f..8.5f -> "8GB"
            memoryInfo.totalRamGb <= 12.5f -> "12GB"
            memoryInfo.totalRamGb <= 16.5f -> "16GB"
            else -> "HIGH"
        }
        val warningCategory = when {
            memoryInfo.warning.isNullOrBlank() -> "none"
            memoryInfo.warning.contains("원격", ignoreCase = false) -> "remote"
            memoryInfo.warning.contains("권장하지 않습니다") -> "not_recommended"
            memoryInfo.warning.contains("변환") -> "conversion"
            memoryInfo.warning.contains("메모리", ignoreCase = false) -> "memory"
            else -> "general"
        }
        Log.d(
            "FusionModelRecommend",
            "detail id=${spec.id}, name=${spec.displayName}, sizeGb=${spec.modelSizeEstimateGb}, memoryClass=${spec.memoryClass}, " +
                "recommendedDeviceClass=${spec.recommendedDeviceClass}, availability=${spec.availability}, minRam=${spec.minRecommendedRamGb}, " +
                "recommendedRam=${spec.recommendedRamGb}, totalRam=${memoryInfo.totalRamGb}, availableRam=${memoryInfo.availableRamGb}, " +
                "ramClass=$ramClass, tier=${memoryInfo.tier}, warningCategory=$warningCategory, warning=${memoryInfo.warning}"
        )
    }
    var overflowExpanded by remember { mutableStateOf(false) }
    var compatibilityReport by remember { mutableStateOf<FusionModelCompatibilityReport?>(null) }
    var showConversionGuide by remember { mutableStateOf(false) }
    var showModelPassport by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = PanelBg,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(spec.displayName, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${spec.sourceLabel ?: spec.family.name} • ${spec.family.name} • ${spec.runtimeFormat.name}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Column(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DetailMetaRow("파일/가중치 크기", modelFootprintLabel(spec))
                    DetailMetaRow("기기 메모리", "약 ${formatGb(memoryInfo.totalRamGb)}GB")
                    DetailMetaRow("현재 사용 가능", "약 ${formatGb(memoryInfo.availableRamGb)}GB")
                    DetailMetaRow("권장 메모리", spec.recommendedRamGb?.let { "${it}GB 이상" } ?: "정보 없음")
                    DetailMetaRow("권장 등급", memoryInfo.tier)
                    DetailMetaRow("권장 토큰 수", tokenRecommendation.label.removePrefix("권장 토큰 수 ").trim())
                    if (spec.notes.isNotBlank()) Text(spec.notes, color = TextPrimary, fontSize = 13.sp)
                    Text(tokenRecommendation.explanation, color = TextSecondary, fontSize = 12.sp)
                    memoryInfo.warning?.let {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DangerRed.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "$it 긴 응답 또는 멀티태스크 환경에서 종료될 수 있습니다. 가능하면 더 작은 모델 또는 더 낮은 최대 토큰 수를 권장합니다.",
                                color = DangerRed,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                    spec.localExecutionWarning?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
                    localSelectionMessage?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
                    Text("모델 메모", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (modelNote.isBlank()) {
                        Text("저장된 메모가 없습니다.", color = TextSecondary, fontSize = 12.sp)
                    } else {
                        Text(modelNote, color = TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FusionTextButton(onClick = {
                            noteDraft = modelNote
                            noteEditorOpen = true
                        }) {
                            Text(if (modelNote.isBlank()) "메모 작성" else "메모 수정", fontSize = 12.sp, maxLines = 1)
                        }
                        if (modelNote.isNotBlank()) {
                            FusionTextButton(onClick = { noteDeleteConfirmOpen = true }) {
                                Text("메모 삭제", fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                    Text("벤치마크 요약", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (benchmarkSummary == null) {
                        Text("아직 측정된 벤치마크가 없습니다.", color = TextSecondary, fontSize = 12.sp)
                    } else {
                        DetailMetaRow("측정 횟수", "${benchmarkSummary.count}회")
                        DetailMetaRow(
                            "중앙값 디코딩 속도",
                            benchmarkSummary.medianDecodeTps?.let { "${formatSpeed(it)} tok/s" } ?: "정보 없음"
                        )
                        DetailMetaRow(
                            "최고 디코딩 속도",
                            benchmarkSummary.bestDecodeTps?.let { "${formatSpeed(it)} tok/s" } ?: "정보 없음"
                        )
                        DetailMetaRow("최근 측정", formatTimestamp(benchmarkSummary.latestAt))
                        DetailMetaRow("최근 가속기", benchmarkSummary.recentAccelerator ?: "정보 없음")
                        DetailMetaRow("MTP 추천", benchmarkSummary.mtpRecommendation)
                        if (benchmarkSummary.failedCount > 0) {
                            DetailMetaRow("실패 횟수", "${benchmarkSummary.failedCount}회")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FusionTextButton(onClick = {
                            onOpenBenchmark(false)
                        }) {
                            Text("벤치마크 실행", fontSize = 12.sp, maxLines = 1)
                        }
                        FusionTextButton(onClick = {
                            onOpenBenchmark(true)
                        }) {
                            Text("기록 보기", fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    Text(fusionNpuNoteTitle(socInfo.detectedSocVendor), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    DetailMetaRow("감지된 AP", socInfo.vendorLabel)
                    DetailMetaRow("SoC", socInfo.compactSocLabel)
                    Text(buildRuntimeNote(spec), color = TextSecondary, fontSize = 12.sp)
                    Text(fusionNpuNoteText(socInfo.detectedSocVendor), color = TextSecondary, fontSize = 12.sp)
                    Text(
                        fusionNpuCandidateLabel(socInfo.detectedSocVendor, spec.supportsNpuCandidate),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    if (spec.officialUrl == null && spec.downloadUrl == null) {
                        Text("등록된 링크가 없습니다.", color = TextSecondary, fontSize = 12.sp)
                    }
                    if (!available) {
                        Text(
                            "이 모델은 현재 로컬 모델로 바로 선택할 수 없습니다.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val menuItemColors = MenuDefaults.itemColors(
                        textColor = TextPrimary,
                        leadingIconColor = TextPrimary,
                        trailingIconColor = TextPrimary,
                        disabledTextColor = TextSecondary,
                        disabledLeadingIconColor = TextSecondary,
                        disabledTrailingIconColor = TextSecondary
                    )
                    FusionTextButton(enabled = available, onClick = onSelect) {
                        Text("선택", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    FusionTextButton(onClick = onApplyRecommendedSettings) {
                        Text("권장 설정", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Box {
                        FusionTextButton(onClick = { overflowExpanded = true }) {
                            Text("⋯", fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Clip)
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                            containerColor = PanelBg
                        ) {
                            DropdownMenuItem(
                                text = { Text("모델 여권", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = menuItemColors,
                                onClick = {
                                    overflowExpanded = false
                                    showModelPassport = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isFavorite) "즐겨찾기에서 제거" else "즐겨찾기에 추가",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = menuItemColors,
                                onClick = {
                                    overflowExpanded = false
                                    onToggleFavorite()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isHidden) "숨김 해제" else "모델 숨기기",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = menuItemColors,
                                onClick = {
                                    overflowExpanded = false
                                    if (isHidden) {
                                        onUnhideModel()
                                    } else {
                                        onHideModel()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("파일 가져오기", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = menuItemColors,
                                onClick = {
                                    overflowExpanded = false
                                    onUploadCustomModel()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("다운로드 페이지 열기", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                enabled = (spec.modelPageUrl ?: spec.downloadUrl ?: spec.officialUrl) != null,
                                colors = menuItemColors,
                                onClick = {
                                    overflowExpanded = false
                                    onOpenModelPage()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("세부 정보", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = menuItemColors,
                                onClick = {
                                    overflowExpanded = false
                                    if (spec.officialUrl != null) {
                                        onOpenOfficial()
                                    } else {
                                        Toast.makeText(context, "등록된 링크가 없습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("링크 복사", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = menuItemColors,
                                onClick = {
                                    overflowExpanded = false
                                    if (spec.downloadUrl != null || spec.officialUrl != null || spec.modelPageUrl != null) {
                                        onCopyLink()
                                    } else {
                                        Toast.makeText(context, "등록된 링크가 없습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("모델 호환성 검사", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = menuItemColors,
                                onClick = {
                                    overflowExpanded = false
                                    compatibilityReport = FusionModelCompatibility.check(context, spec)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("모델 변환 안내", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = menuItemColors,
                                onClick = {
                                    overflowExpanded = false
                                    if (spec.runtimeFormat == ModelRuntimeFormat.NEEDS_CONVERSION || spec.availability == ModelAvailability.NEEDS_CONVERSION) {
                                        showConversionGuide = true
                                    } else {
                                        Toast.makeText(context, "이 모델은 현재 변환 안내 대상이 아닙니다.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("닫기", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = menuItemColors,
                                onClick = {
                                    overflowExpanded = false
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    compatibilityReport?.let { report ->
        AlertDialog(
            onDismissRequest = { compatibilityReport = null },
            title = { Text("모델 호환성 검사") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(report.summary)
                    DetailMetaRow("상태", report.localExecutionStatus)
                    DetailMetaRow("형식", report.formatLabel)
                    DetailMetaRow("계열", report.familyLabel)
                    DetailMetaRow("권장 토큰", report.recommendedMaxTokens.takeIf { it > 0 }?.toString() ?: "해당 없음")
                    DetailMetaRow("가속기", report.recommendedAccelerator.name)
                    DetailMetaRow("MTP", report.mtpRecommendation)
                    DetailMetaRow("NPU", report.npuCandidateStatus)
                    report.memoryWarning?.let { Text(it, color = DangerRed, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                FusionTextButton(onClick = { compatibilityReport = null }) { Text("확인", maxLines = 1) }
            },
            containerColor = PanelBg,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary
        )
    }

    if (showConversionGuide) {
        AlertDialog(
            onDismissRequest = { showConversionGuide = false },
            title = { Text("모델 변환 안내") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailMetaRow("현재 형식", spec.runtimeFormat.name)
                    DetailMetaRow("권장 형식", ".litertlm 또는 .task")
                    DetailMetaRow("상태", if (spec.supportsNpuCandidate) "NPU 후보" else "변환 필요")
                    Text("현재 앱에서 자동 변환은 지원하지 않습니다.", color = TextSecondary, fontSize = 12.sp)
                    Text("변환 후 파일 가져오기로 모델 파일을 선택해 주세요.", color = TextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                FusionTextButton(onClick = { showConversionGuide = false }) { Text("확인", maxLines = 1) }
            },
            containerColor = PanelBg,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary
        )
    }

    if (noteEditorOpen) {
        AlertDialog(
            onDismissRequest = { noteEditorOpen = false },
            title = { Text("모델 메모") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("이 모델에 대한 개인 메모를 저장합니다.", color = TextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        placeholder = { Text("예: S24에서는 MTP 끔이 더 빠릅니다.", color = TextSecondary) },
                        textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = LineColor,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AccentBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                FusionTextButton(onClick = {
                    onSaveModelNote(noteDraft)
                    noteEditorOpen = false
                    Toast.makeText(context, "모델 메모를 저장했습니다.", Toast.LENGTH_SHORT).show()
                }) { Text("저장", maxLines = 1) }
            },
            dismissButton = {
                FusionTextButton(onClick = { noteEditorOpen = false }) { Text("취소", maxLines = 1) }
            },
            containerColor = PanelBg,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary
        )
    }

    if (noteDeleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { noteDeleteConfirmOpen = false },
            title = { Text("모델 메모를 삭제하시겠습니까?") },
            text = { Text("이 모델에 저장된 메모만 삭제됩니다.") },
            confirmButton = {
                FusionTextButton(onClick = {
                    onSaveModelNote(null)
                    noteDeleteConfirmOpen = false
                    Toast.makeText(context, "모델 메모를 삭제했습니다.", Toast.LENGTH_SHORT).show()
                }) { Text("삭제", maxLines = 1) }
            },
            dismissButton = {
                FusionTextButton(onClick = { noteDeleteConfirmOpen = false }) { Text("취소", maxLines = 1) }
            },
            containerColor = PanelBg,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary
        )
    }
    if (showModelPassport) {
        ModelPassportDialog(
            spec = spec,
            available = available,
            memoryInfo = memoryInfo,
            tokenRecommendation = tokenRecommendation,
            recommendation = recommendation,
            benchmarkSummary = benchmarkSummary,
            modelNote = modelNote,
            onDismiss = { showModelPassport = false },
            onSelect = onSelect,
            onApplyRecommendedSettings = onApplyRecommendedSettings,
            onOpenBenchmark = {
                showModelPassport = false
                onOpenBenchmark(false)
            },
            onOpenModelPage = onOpenModelPage,
            onCopyLink = onCopyLink,
            onUploadCustomModel = onUploadCustomModel,
            onEditNote = {
                noteDraft = modelNote
                noteEditorOpen = true
            }
        )
    }
}

private enum class ModelSelectRiskLevel { DIRECT, CAUTION, BLOCKED }

private data class ModelSelectionDecision(
    val spec: FusionModelSpec,
    val level: ModelSelectRiskLevel,
    val reason: String,
    val recommendationTier: String,
    val recommendedMaxTokens: Int,
    val deviceRamClass: String,
    val blockMessage: String? = null
)

private fun evaluateModelSelectionDecision(
    spec: FusionModelSpec,
    currentModel: String,
    memoryInfo: ModelMemoryUiInfo,
    recommendation: ModelRecommendationEvaluation?
): ModelSelectionDecision {
    if (spec.displayName == currentModel) {
        return ModelSelectionDecision(spec, ModelSelectRiskLevel.BLOCKED, "already selected", recommendation?.tier ?: memoryInfo.tier, 0, ramClassLabel(memoryInfo.totalRamGb), "이미 선택된 모델입니다.")
    }
    val tier = recommendation?.tier ?: memoryInfo.tier
    val available = isSelectableLocally(spec)
    if (!available) {
        val msg = when (spec.availability) {
            ModelAvailability.NEEDS_DOWNLOAD -> "모델 파일을 먼저 가져와야 합니다."
            ModelAvailability.NEEDS_CONVERSION -> "이 모델은 변환 후 사용할 수 있습니다."
            ModelAvailability.REMOTE_ONLY -> "이 모델은 원격 실행이 필요합니다."
            ModelAvailability.UNSUPPORTED_ON_DEVICE -> "로컬 실행을 권장하지 않습니다.\n\n이 모델은 현재 기기에서 로컬 실행하기에 너무 클 수 있습니다. 서버 또는 원격 실행을 권장합니다."
            else -> "로컬 실행을 권장하지 않습니다.\n\n이 모델은 현재 기기에서 로컬 실행하기에 너무 클 수 있습니다. 서버 또는 원격 실행을 권장합니다."
        }
        return ModelSelectionDecision(spec, ModelSelectRiskLevel.BLOCKED, "not locally available", tier, recommendation?.recommendedTokens ?: 0, ramClassLabel(memoryInfo.totalRamGb), msg)
    }
    val risk = FusionModelMemoryPreflight.evaluate(
        spec = spec,
        totalRamBytes = gbToBytes(memoryInfo.totalRamGb),
        availableRamBytes = gbToBytes(memoryInfo.availableRamGb),
        currentMaxTokens = recommendation?.recommendedTokens ?: 2048
    )
    val effectiveRiskLabel = recommendation?.tier ?: risk.label
    val dynamicHighRisk = effectiveRiskLabel in setOf(
        FusionModelMemoryRiskLevel.CAUTION.label,
        FusionModelMemoryRiskLevel.HEAVY.label,
        FusionModelMemoryRiskLevel.NOT_RECOMMENDED.label
    )
    return ModelSelectionDecision(
        spec = spec,
        level = if (dynamicHighRisk) ModelSelectRiskLevel.CAUTION else ModelSelectRiskLevel.DIRECT,
        reason = if (dynamicHighRisk) "dynamic risk confirmation" else "selected directly",
        recommendationTier = effectiveRiskLabel,
        recommendedMaxTokens = risk.recommendedMaxTokens,
        deviceRamClass = risk.ramClass.label
    )
    @Suppress("UNREACHABLE_CODE")
    val sizeGb = spec.modelSizeEstimateGb ?: 0f
    val eightGbClass = memoryInfo.totalRamGb in 7.0f..8.5f
    val lowAvailRam = memoryInfo.availableRamGb < 1.25f
    val highRisk = tier == "주의 필요" || tier == "권장하지 않음" ||
        spec.memoryClass == ModelMemoryClass.HIGH ||
        spec.recommendedDeviceClass in listOf(ModelRecommendedDeviceClass.RAM_12GB_RECOMMENDED, ModelRecommendedDeviceClass.RAM_16GB_RECOMMENDED, ModelRecommendedDeviceClass.SERVER_ONLY) && eightGbClass ||
        (eightGbClass && sizeGb >= 4.0f) || lowAvailRam || !memoryInfo.warning.isNullOrBlank()
    return ModelSelectionDecision(
        spec = spec,
        level = if (highRisk) ModelSelectRiskLevel.CAUTION else ModelSelectRiskLevel.DIRECT,
        reason = if (highRisk) "caution confirmation" else "selected directly",
        recommendationTier = tier,
        recommendedMaxTokens = recommendation?.recommendedTokens ?: buildDeviceAwareTokenRecommendation(spec, memoryInfo.totalRamGb, memoryInfo.availableRamGb).value,
        deviceRamClass = ramClassLabel(memoryInfo.totalRamGb)
    )
}

private fun isSelectableLocally(spec: FusionModelSpec): Boolean {
    return spec.availability == ModelAvailability.READY || spec.availability == ModelAvailability.CUSTOM_IMPORTED
}

private fun ramClassLabel(totalRamGb: Float): String = when {
    totalRamGb in 7.0f..8.5f -> "8GB급"
    totalRamGb <= 12.5f -> "12GB급"
    totalRamGb <= 16.5f -> "16GB급"
    else -> "고메모리"
}

private fun logModelSelectionDecision(
    spec: FusionModelSpec,
    memoryInfo: ModelMemoryUiInfo,
    recommendation: ModelRecommendationEvaluation?,
    decision: ModelSelectionDecision
) {
    Log.d(
        "FusionModelSelect",
        "id=${spec.id}, name=${spec.displayName}, risk=${decision.level}, recommendationTier=${recommendation?.tier ?: memoryInfo.tier}, " +
            "availability=${spec.availability}, modelSizeEstimateGb=${spec.modelSizeEstimateGb}, totalRamGb=${memoryInfo.totalRamGb}, " +
            "availableRamGb=${memoryInfo.availableRamGb}, decision=${decision.reason}"
    )
}

@Composable
private fun ModelPassportDialog(
    spec: FusionModelSpec,
    available: Boolean,
    memoryInfo: ModelMemoryUiInfo,
    tokenRecommendation: TokenRecommendation,
    recommendation: ModelRecommendationEvaluation?,
    benchmarkSummary: ModelBenchmarkSummary?,
    modelNote: String,
    onDismiss: () -> Unit,
    onSelect: () -> Unit,
    onApplyRecommendedSettings: () -> Unit,
    onOpenBenchmark: () -> Unit,
    onOpenModelPage: () -> Unit,
    onCopyLink: () -> Unit,
    onUploadCustomModel: () -> Unit,
    onEditNote: () -> Unit
) {
    val socInfo = remember { collectFusionSocInfo() }
    val isImported = spec.availability == ModelAvailability.CUSTOM_IMPORTED ||
        spec.externallyReferenced ||
        spec.copiedInternally
    val source = spec.sourceLabel
        ?: spec.huggingFaceModelId
        ?: spec.officialUrl
        ?: "출처 정보 없음"
    val tier = recommendation?.tier ?: memoryInfo.tier
    val reason = recommendation?.reason
        ?: memoryInfo.warning
        ?: spec.localExecutionWarning
        ?: "현재 기기 정보를 기준으로 모델 적합도를 계산했습니다."

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(20.dp),
            color = PanelBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("모델 여권", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "모델의 상태, 권장 설정, 벤치마크, 메모를 한눈에 확인합니다.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PassportSection("프로필") {
                        Text(
                            spec.displayName,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${spec.family.name} · ${spec.parameterLabel} · ${passportRuntimeLabel(spec.runtimeFormat)}",
                            color = AccentBlue,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        DetailMetaRow("패밀리", spec.family.name)
                        DetailMetaRow("크기", modelFootprintLabel(spec))
                        DetailMetaRow("실행 형식", passportRuntimeLabel(spec.runtimeFormat))
                        DetailMetaRow("상태", passportAvailabilityLabel(spec.availability, available))
                        DetailMetaRow("권장 기기", passportDeviceClassLabel(spec.recommendedDeviceClass))
                        DetailMetaRow("모델 출처", source)
                    }

                    PassportSection("기기 적합도") {
                        DetailMetaRow("적합도", tier)
                        Text(reason, color = TextSecondary, fontSize = 12.sp)
                        DetailMetaRow("기기 RAM", ramClassLabel(memoryInfo.totalRamGb))
                        DetailMetaRow("사용 가능 RAM", "약 ${formatGb(memoryInfo.availableRamGb)}GB")
                        DetailMetaRow("권장 maxTokens", tokenRecommendation.value.takeIf { it > 0 }?.toString() ?: "원격 실행 권장")
                        DetailMetaRow("MTP", if (spec.recommendedMtpEnabled) "켬 권장" else "끔 권장")
                        DetailMetaRow("Reasoning", if (spec.recommendedReasoningEnabled) "켬 권장" else "끔 권장")
                    }

                    PassportSection("NPU 및 가속 안내") {
                        DetailMetaRow("감지된 AP", socInfo.vendorLabel)
                        DetailMetaRow("SoC", socInfo.compactSocLabel)
                        Text(
                            if (spec.supportsNpuCandidate) {
                                fusionNpuCandidateLabel(socInfo.detectedSocVendor, true)
                            } else {
                                "현재 모델은 전용 NPU 실행 후보로 확인되지 않았습니다."
                            },
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(fusionNpuNoteText(socInfo.detectedSocVendor), color = TextSecondary, fontSize = 12.sp)
                    }

                    PassportSection("벤치마크 요약") {
                        if (benchmarkSummary == null) {
                            Text("아직 측정된 벤치마크가 없습니다.", color = TextSecondary, fontSize = 12.sp)
                        } else {
                            DetailMetaRow("측정 횟수", "${benchmarkSummary.count}회")
                            DetailMetaRow("중앙값", benchmarkSummary.medianDecodeTps?.let { "${formatSpeed(it)} tok/s" } ?: "정보 없음")
                            DetailMetaRow("최고 속도", benchmarkSummary.bestDecodeTps?.let { "${formatSpeed(it)} tok/s" } ?: "정보 없음")
                            DetailMetaRow("최근 측정", formatTimestamp(benchmarkSummary.latestAt))
                            DetailMetaRow("최근 가속기", benchmarkSummary.recentAccelerator ?: "정보 없음")
                            DetailMetaRow("MTP 추천", benchmarkSummary.mtpRecommendation)
                        }
                    }

                    PassportSection("사용자 메모") {
                        Text(
                            modelNote.ifBlank { "저장된 메모가 없습니다." },
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        FusionTextButton(onClick = onEditNote) {
                            Text(if (modelNote.isBlank()) "메모 작성" else "메모 수정", fontSize = 12.sp)
                        }
                    }

                    PassportSection("파일 및 연결 상태") {
                        if (isImported) {
                            DetailMetaRow(
                                "저장 위치",
                                if (spec.externallyReferenced) "외부 파일 연결됨" else "Fusion 내부 저장소"
                            )
                            DetailMetaRow("파일 이름", spec.originalFileName ?: spec.fileName ?: "정보 없음")
                            DetailMetaRow("파일 크기", spec.fileSizeBytes?.let { formatBytes(it) } ?: "정보 없음")
                            DetailMetaRow("실행 상태", passportExecutionStatus(spec, available))
                        } else {
                            Text(
                                if (available) "Fusion에서 사용할 수 있는 모델 파일입니다."
                                else "모델 파일이 아직 연결되지 않았습니다.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FusionTextButton(enabled = available, onClick = onSelect) { Text("선택", fontSize = 12.sp) }
                    FusionTextButton(onClick = onApplyRecommendedSettings) { Text("권장 설정", fontSize = 12.sp) }
                    FusionTextButton(onClick = onOpenBenchmark) { Text("벤치마크", fontSize = 12.sp) }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FusionTextButton(onClick = onOpenModelPage) { Text("모델 페이지", fontSize = 12.sp) }
                    FusionTextButton(onClick = onCopyLink) { Text("링크 복사", fontSize = 12.sp) }
                    FusionTextButton(onClick = onUploadCustomModel) { Text("파일 가져오기", fontSize = 12.sp) }
                    FusionTextButton(onClick = onDismiss) { Text("닫기", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun PassportSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF151515)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(title, color = AccentBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

private fun passportRuntimeLabel(format: ModelRuntimeFormat): String = when (format) {
    ModelRuntimeFormat.LITERT_LM -> "LiteRT-LM"
    ModelRuntimeFormat.MEDIAPIPE_LLM -> "MediaPipe LLM"
    ModelRuntimeFormat.GGUF -> "GGUF"
    ModelRuntimeFormat.ONNX -> "ONNX"
    ModelRuntimeFormat.NEEDS_CONVERSION -> "변환 필요"
    ModelRuntimeFormat.EXYNOS_AI_STUDIO -> "Exynos AI Studio 후보"
    ModelRuntimeFormat.REMOTE_API -> "원격 API"
    ModelRuntimeFormat.UNKNOWN -> "지원 확인 필요"
}

private fun passportAvailabilityLabel(availability: ModelAvailability, available: Boolean): String = when {
    available -> "사용 가능"
    availability == ModelAvailability.NEEDS_CONVERSION -> "변환 필요"
    availability == ModelAvailability.NEEDS_DOWNLOAD -> "파일 필요"
    availability == ModelAvailability.REMOTE_ONLY -> "원격 전용"
    availability == ModelAvailability.UNSUPPORTED_ON_DEVICE -> "지원 확인 필요"
    else -> "실행 준비 필요"
}

private fun passportDeviceClassLabel(deviceClass: ModelRecommendedDeviceClass): String = when (deviceClass) {
    ModelRecommendedDeviceClass.RAM_8GB_SAFE -> "8GB 기기"
    ModelRecommendedDeviceClass.RAM_12GB_RECOMMENDED -> "12GB 이상 기기"
    ModelRecommendedDeviceClass.RAM_16GB_RECOMMENDED -> "16GB 이상 기기"
    ModelRecommendedDeviceClass.SERVER_ONLY -> "서버 또는 원격 실행"
}

private fun passportExecutionStatus(spec: FusionModelSpec, available: Boolean): String = when {
    available -> "실행 후보"
    spec.runtimeFormat == ModelRuntimeFormat.NEEDS_CONVERSION ||
        spec.availability == ModelAvailability.NEEDS_CONVERSION -> "변환 필요"
    spec.runtimeFormat == ModelRuntimeFormat.UNKNOWN ||
        spec.availability == ModelAvailability.UNSUPPORTED_ON_DEVICE -> "지원 확인 필요"
    else -> "실행 준비 필요"
}

@Composable
private fun DetailMetaRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.width(92.dp)
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DirectDownloadConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("모델 파일을 다운로드하시겠습니까?") },
        text = { Text("모델 파일은 용량이 클 수 있습니다. Wi-Fi 연결과 충분한 저장공간을 확인해 주세요.") },
        confirmButton = { FusionTextButton(onClick = onConfirm) { Text("다운로드") } },
        dismissButton = { FusionTextButton(onClick = onDismiss) { Text("취소") } },
        containerColor = PanelBg,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary
    )

}

@Composable
private fun FusionTextButton(
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        enabled = enabled,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.heightIn(min = 30.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            content = {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides if (enabled) AccentBlue else TextSecondary.copy(alpha = 0.45f)
                ) {
                    content()
                }
            }
        )
    }
}

@Composable
private fun CustomModelFamilyDialog(
    onDismiss: () -> Unit,
    onFamilySelected: (ModelFamily) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("모델 패밀리를 선택해 주세요.") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ModelFamily.GEMMA to "Gemma",
                    ModelFamily.QWEN to "Qwen",
                    ModelFamily.LLAMA to "Llama",
                    ModelFamily.PHI to "Phi",
                    ModelFamily.DEEPSEEK to "DeepSeek",
                    ModelFamily.MISTRAL to "Mistral",
                    ModelFamily.KIMI to "Kimi",
                    ModelFamily.CUSTOM to "Custom"
                ).forEach { (family, label) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PanelBg,
                        modifier = Modifier.fillMaxWidth().clickable { onFamilySelected(family) }
                    ) {
                        Text(label, color = TextPrimary, modifier = Modifier.padding(14.dp), fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("나중에") } },
        containerColor = PanelBg,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary
    )
}

@Composable
private fun ModelImportWizardDialog(
    pending: PendingModelImport,
    onDismiss: () -> Unit,
    onLink: (ModelFamily) -> Unit,
    onCopyForRun: (ModelFamily) -> Unit
) {
    var selectedFamily by remember(pending.uri) { mutableStateOf(pending.initialFamily) }
    val format = remember(pending.originalFileName) { FusionModelCatalog.runtimeFormatForFile(pending.originalFileName) }
    val extension = pending.originalFileName.substringAfterLast('.', "").ifBlank { "-" }
    val formatLabel = remember(pending.originalFileName, format) { importFormatLabel(pending.originalFileName, format) }
    val executionStatus = remember(format) { importExecutionStatusMessage(format) }
    val nextAction = remember(format) { importRecommendedActionMessage(format) }
    val canCopyForRun = format == ModelRuntimeFormat.LITERT_LM || format == ModelRuntimeFormat.MEDIAPIPE_LLM

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("모델 파일 확인") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailMetaRow("파일 이름", pending.originalFileName)
                DetailMetaRow("파일 크기", pending.fileSizeBytes?.let { formatBytes(it) } ?: "모델 파일 크기를 확인할 수 없습니다.")
                DetailMetaRow("확장자", extension)
                DetailMetaRow("추정 형식", formatLabel)
                DetailMetaRow("실행 상태", executionStatus)
                Text("모델 패밀리를 선택해 주세요.", color = TextSecondary, fontSize = 12.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        ModelFamily.GEMMA to "Gemma",
                        ModelFamily.QWEN to "Qwen",
                        ModelFamily.LLAMA to "Llama",
                        ModelFamily.PHI to "Phi",
                        ModelFamily.DEEPSEEK to "DeepSeek",
                        ModelFamily.MISTRAL to "Mistral",
                        ModelFamily.KIMI to "Kimi",
                        ModelFamily.CUSTOM to "Custom"
                    ).forEach { (family, label) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedFamily == family) AccentBlue.copy(alpha = 0.18f) else PanelBg,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selectedFamily == family) AccentBlue.copy(alpha = 0.6f) else LineColor
                            ),
                            modifier = Modifier.fillMaxWidth().clickable { selectedFamily = family }
                        ) {
                            Text(label, color = TextPrimary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), fontSize = 14.sp)
                        }
                    }
                }
                if (!pending.permissionPersisted) {
                    Text("모델 파일 권한을 유지할 수 없습니다.", color = DangerRed, fontSize = 12.sp)
                }
                if (format == ModelRuntimeFormat.MEDIAPIPE_LLM) {
                    Text("이 모델은 실행 전에 Fusion 내부 저장소로 복사해야 할 수 있습니다.", color = TextSecondary, fontSize = 12.sp)
                }
                Text(nextAction, color = TextSecondary, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onLink(selectedFamily) }) { Text("연결", color = AccentBlue, maxLines = 1) }
                TextButton(onClick = { onCopyForRun(selectedFamily) }, enabled = canCopyForRun) {
                    Text("실행용으로 복사", color = if (canCopyForRun) AccentBlue else TextSecondary, maxLines = 1)
                }
                TextButton(onClick = onDismiss) { Text("취소", color = TextSecondary, maxLines = 1) }
            }
        },
        containerColor = PanelBg,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary
    )
}

private fun isCatalogModelAvailable(context: Context, spec: FusionModelSpec): Boolean {
    if (spec.externallyReferenced && spec.localPath.isNullOrBlank()) return false
    spec.localPath?.let { return File(it).exists() && spec.canRunLocalRuntime }
    spec.fileName?.let { return File(getModelDirectory(context), it).exists() && spec.canRunLocalRuntime }
    return spec.availability == ModelAvailability.READY
}

private data class ModelMemoryUiInfo(
    val totalRamGb: Float,
    val availableRamGb: Float,
    val warning: String?,
    val tier: String
)

private data class TokenRecommendation(
    val value: Int,
    val label: String,
    val explanation: String
)

private data class ModelRecommendationEvaluation(
    val spec: FusionModelSpec,
    val tier: String,
    val reason: String,
    val recommendedTokens: Int,
    val hint: String,
    val deviceRamClass: String,
    val includedInRecommendedLocal: Boolean,
    val includeReason: String
)

private fun evaluateModelRecommendation(
    spec: FusionModelSpec,
    totalRamGb: Float,
    availableRamGb: Float,
    lowMemory: Boolean,
    isCurrentSelected: Boolean,
    isFavorite: Boolean,
    isLocalAvailable: Boolean,
    currentMaxTokens: Int = 2048
): ModelRecommendationEvaluation {
    val recommendedTokens = FusionModelMemoryPreflight.recommendedTokens(spec, gbToBytes(totalRamGb), gbToBytes(availableRamGb))
    val risk = FusionModelMemoryPreflight.evaluate(
        spec = spec,
        totalRamBytes = gbToBytes(totalRamGb),
        availableRamBytes = gbToBytes(availableRamGb),
        currentMaxTokens = currentMaxTokens,
        lowMemory = lowMemory
    )
    val dynamicIncluded = risk.level in setOf(
        FusionModelMemoryRiskLevel.RECOMMENDED,
        FusionModelMemoryRiskLevel.CHECK_REQUIRED,
        FusionModelMemoryRiskLevel.CAUTION
    )
    return ModelRecommendationEvaluation(
        spec = spec,
        tier = risk.label,
        reason = risk.summary,
        recommendedTokens = risk.recommendedMaxTokens,
        hint = if (spec.recommendedReasoningEnabled) "MTP 끔" else "MTP 끔 · Reasoning 끔",
        deviceRamClass = risk.ramClass.label,
        includedInRecommendedLocal = dynamicIncluded,
        includeReason = if (dynamicIncluded) "included" else "risk_excluded"
    )
    @Suppress("UNREACHABLE_CODE")
    val token = buildDeviceAwareTokenRecommendation(spec, totalRamGb, availableRamGb).value.coerceAtLeast(1024)
    val hintParts = mutableListOf("MTP 끔")
    if (!spec.recommendedReasoningEnabled || totalRamGb <= 8.5f) hintParts += "Reasoning 끔"
    val hint = hintParts.distinct().joinToString(" · ")
    val effectiveTotalRamGb = when {
        totalRamGb >= 7.0f && totalRamGb <= 8.5f -> 8.0f
        totalRamGb <= 12.5f -> 12.0f
        totalRamGb <= 16.5f -> 16.0f
        else -> totalRamGb
    }
    val ramClass = when {
        totalRamGb in 7.0f..8.5f -> "8GB"
        totalRamGb <= 12.5f -> "12GB"
        totalRamGb <= 16.5f -> "16GB"
        else -> "HIGH"
    }
    val minRam = spec.minRecommendedRamGb ?: 0
    val recommendedRam = spec.recommendedRamGb ?: minRam
    val isEightGbSafe = spec.recommendedDeviceClass == ModelRecommendedDeviceClass.RAM_8GB_SAFE
    val isSmallModel = spec.memoryClass == ModelMemoryClass.LOW || (spec.modelSizeEstimateGb ?: Float.MAX_VALUE) <= 2.5f
    val meetsMinimum = minRam <= 0 || effectiveTotalRamGb >= minRam || (minRam == 8 && totalRamGb >= 7.0f)
    val slightlyBelowMinimum = minRam > 0 && effectiveTotalRamGb + 2.0f >= minRam
    val sizeGb = spec.modelSizeEstimateGb ?: when (spec.memoryClass) {
        ModelMemoryClass.LOW -> 1.5f
        ModelMemoryClass.MEDIUM -> 3.5f
        ModelMemoryClass.HIGH -> 7.0f
        ModelMemoryClass.SERVER -> 32.0f
    }
    val sizeTier = when (ramClass) {
        "8GB" -> when {
            sizeGb >= 5.0f -> "권장하지 않음"
            sizeGb >= 4.0f -> "주의 필요"
            sizeGb > 3.0f -> "주의 필요"
            sizeGb > 1.5f -> "실험 가능"
            else -> "권장"
        }
        "12GB" -> when {
            sizeGb > 7.0f -> "권장하지 않음"
            sizeGb > 5.0f -> "주의 필요"
            sizeGb > 3.0f -> "실험 가능"
            else -> "권장"
        }
        "16GB" -> when {
            sizeGb > 10.0f -> "권장하지 않음"
            sizeGb > 5.0f -> "실험 가능"
            else -> "권장"
        }
        else -> if (sizeGb > 12.0f) "주의 필요" else "권장"
    }
    val tier = when {
        spec.availability == ModelAvailability.REMOTE_ONLY || spec.recommendedDeviceClass == ModelRecommendedDeviceClass.SERVER_ONLY -> "원격 전용"
        spec.memoryClass == ModelMemoryClass.SERVER -> "권장하지 않음"
        spec.availability == ModelAvailability.UNSUPPORTED_ON_DEVICE -> "권장하지 않음"
        sizeTier == "권장하지 않음" -> "권장하지 않음"
        sizeTier == "주의 필요" -> "주의 필요"
        isEightGbSafe && effectiveTotalRamGb >= 8.0f && isSmallModel -> "권장"
        isEightGbSafe && effectiveTotalRamGb >= 8.0f -> "실험 가능"
        spec.availability == ModelAvailability.READY && isSmallModel && meetsMinimum -> "권장"
        spec.availability == ModelAvailability.CUSTOM_IMPORTED && isSmallModel && meetsMinimum -> "권장"
        recommendedRam > 0 && effectiveTotalRamGb >= recommendedRam -> "권장"
        meetsMinimum -> "실험 가능"
        slightlyBelowMinimum -> "주의 필요"
        else -> "권장하지 않음"
    }
    val availableMemoryVeryLow = lowMemory || availableRamGb < 1.25f
    val shouldDowngradeForAvailableMemory = availableMemoryVeryLow && !isCurrentSelected && !(isEightGbSafe && isSmallModel)
    val downgradedTier = when {
        shouldDowngradeForAvailableMemory && tier == "권장" -> "실험 가능"
        shouldDowngradeForAvailableMemory && tier == "실험 가능" -> "주의 필요"
        else -> tier
    }
    val reason = when (downgradedTier) {
        "권장" -> if (availableRamGb < 2.0f) "현재 사용 가능한 메모리가 낮아 실행 전에 다른 앱을 정리하는 것이 좋습니다." else "현재 기기 메모리 기준으로 안정적인 소형 모델입니다."
        "실험 가능" -> if (spec.availability == ModelAvailability.NEEDS_CONVERSION) "변환이 필요하지만 메모리 기준은 충족합니다." else "8GB 기기에서 실험하기 적합합니다."
        "주의 필요" -> if (availableRamGb < 2.0f) "현재 사용 가능한 메모리가 낮아 실행 전에 다른 앱을 정리하는 것이 좋습니다." else "긴 응답 또는 멀티태스킹 환경에서 종료될 수 있습니다. 가능하면 낮은 최대 토큰 수를 권장합니다."
        "권장하지 않음" -> "현재 기기 메모리 대비 모델 요구사항이 높습니다."
        else -> "이 모델은 원격 실행을 권장합니다."
    }
    val includeBySizeRule = when (ramClass) {
        "8GB" -> when {
            sizeGb >= 5.0f -> false
            sizeGb >= 4.0f -> isCurrentSelected || isFavorite || isLocalAvailable
            else -> true
        }
        "12GB" -> sizeGb <= 7.0f
        "16GB" -> sizeGb <= 10.0f
        else -> true
    }
    val includeByAvailability = downgradedTier in listOf("권장", "실험 가능", "주의 필요")
    val included = includeByAvailability && includeBySizeRule
    val includeReason = if (included) "included" else if (!includeByAvailability) "tier_excluded" else "size_cutoff"
    return ModelRecommendationEvaluation(spec, downgradedTier, reason, token, hint, ramClass, included, includeReason)
}

private fun getTotalRamGb(context: Context): Float {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager?.getMemoryInfo(memoryInfo)
    return bytesToGb(memoryInfo.totalMem)
}

private fun getAvailableRamGb(context: Context): Float {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager?.getMemoryInfo(memoryInfo)
    return bytesToGb(memoryInfo.availMem)
}

private fun gbToBytes(value: Float): Long {
    if (value <= 0f) return 0L
    return (value * 1024f * 1024f * 1024f).toLong()
}

private fun buildModelMemoryInfo(context: Context, spec: FusionModelSpec): ModelMemoryUiInfo {
    val memoryInfo = FusionModelMemoryPreflight.snapshot(context)
    val totalRamGb = bytesToGb(memoryInfo.totalMem)
    val availableRamGb = bytesToGb(memoryInfo.availMem)
    val evaluation = evaluateModelRecommendation(
        spec = spec,
        totalRamGb = totalRamGb,
        availableRamGb = availableRamGb,
        lowMemory = memoryInfo.lowMemory,
        isCurrentSelected = false,
        isFavorite = false,
        isLocalAvailable = isCatalogModelAvailable(context, spec)
    )
    return ModelMemoryUiInfo(
        totalRamGb = totalRamGb,
        availableRamGb = availableRamGb,
        warning = buildModelMemoryWarning(spec, totalRamGb, availableRamGb, evaluation),
        tier = evaluation.tier
    )
}

private fun buildModelMemoryWarning(
    spec: FusionModelSpec,
    totalRamGb: Float,
    availableRamGb: Float,
    evaluation: ModelRecommendationEvaluation
): String? {
    return evaluation.reason.takeUnless {
        evaluation.tier == FusionModelMemoryRiskLevel.RECOMMENDED.label
    }
    @Suppress("UNREACHABLE_CODE")
    if (evaluation.tier == "원격 전용") {
        return "이 모델은 모바일 로컬 실행용이 아닙니다."
    }
    if (spec.availability == ModelAvailability.NEEDS_CONVERSION) {
        return if (evaluation.tier == "권장하지 않음") {
            "이 모델은 변환이 필요하며 현재 기기 메모리로는 로컬 실행을 권장하지 않습니다."
        } else {
            "이 모델은 변환 후 사용할 수 있습니다. ${evaluation.reason}"
        }
    }
    if (evaluation.tier == "권장하지 않음") {
        if (evaluation.deviceRamClass == "8GB" && (spec.modelSizeEstimateGb ?: 0f) >= 5.0f) {
            return "현재 기기에서는 이 모델의 로컬 실행을 권장하지 않습니다."
        }
        return "현재 기기 메모리로는 이 모델의 로컬 실행을 권장하지 않습니다."
    }
    if (availableRamGb > 0f && availableRamGb < 2f && evaluation.tier == "권장") {
        return "현재 사용 가능한 메모리가 낮아 실행 전에 다른 앱을 정리하는 것이 좋습니다."
    }
    if (totalRamGb in 7.0f..8.5f && (spec.memoryClass == ModelMemoryClass.LOW || (spec.modelSizeEstimateGb ?: 99f) <= 1.5f)) {
        return "현재 기기에서 실험하기 적합한 소형 모델입니다."
    }
    return evaluation.reason
}

private fun buildDeviceAwareTokenRecommendation(
    spec: FusionModelSpec,
    totalRamGb: Float,
    availableRamGb: Float
): TokenRecommendation {
    val recommended = FusionModelMemoryPreflight.recommendedTokens(spec, gbToBytes(totalRamGb), gbToBytes(availableRamGb))
    return TokenRecommendation(
        value = recommended,
        label = if (recommended > 0) "권장 토큰 수: 약 $recommended" else "권장 토큰 수: 원격 실행 권장",
        explanation = "현재 기기의 총 메모리와 사용 가능한 메모리를 기준으로 권장값을 계산했습니다."
    )
    @Suppress("UNREACHABLE_CODE")
    val sizeGb = spec.modelSizeEstimateGb ?: when (spec.memoryClass) {
        ModelMemoryClass.LOW -> 1.5f
        ModelMemoryClass.MEDIUM -> 3.5f
        ModelMemoryClass.HIGH -> 7.0f
        ModelMemoryClass.SERVER -> 32.0f
    }
    val pressure = sizeGb + 1.0f
    val base = when {
        spec.memoryClass == ModelMemoryClass.SERVER || spec.availability == ModelAvailability.REMOTE_ONLY -> 0
        totalRamGb <= 8.5f -> when {
            pressure >= 8f -> 1024
            pressure >= 5f -> 1536
            else -> 2048
        }
        totalRamGb < 15.5f -> when {
            pressure >= 10f -> 2048
            pressure >= 6f -> 3072
            else -> 4096
        }
        else -> when {
            pressure >= 16f -> 4096
            else -> 6144
        }
    }
    val adjusted = if (availableRamGb in 0.1f..2.5f && base > 1024) {
        (base / 2).coerceAtLeast(1024)
    } else {
        base
    }
    val label = if (adjusted <= 0) {
        "권장 토큰 수: 원격 실행 권장"
    } else if (totalRamGb >= 12f && adjusted >= 4096) {
        "권장 토큰 수: 약 2048~$adjusted"
    } else {
        "권장 토큰 수: 약 $adjusted"
    }
    val explanation = "현재 기기의 메모리를 기준으로 권장값을 계산했습니다. 메모리가 부족한 기기에서는 긴 출력에서 속도 저하 또는 종료가 발생할 수 있습니다."
    return TokenRecommendation(value = adjusted, label = label, explanation = explanation)
}

private fun applyDeviceAwareRecommendedSettings(
    context: Context,
    editor: SharedPreferences.Editor,
    spec: FusionModelSpec
) {
    val memoryInfo = buildModelMemoryInfo(context, spec)
    val tokenRecommendation = buildDeviceAwareTokenRecommendation(spec, memoryInfo.totalRamGb, memoryInfo.availableRamGb)
    if (tokenRecommendation.value > 0) {
        editor.putInt(PrefMaxTokens, tokenRecommendation.value)
    }
    editor.putBoolean(PrefSpeculativeDecoding, spec.recommendedMtpEnabled)
    editor.putBoolean(PrefReasoningEnabled, spec.recommendedReasoningEnabled)
    if (spec.runtimeFormat == ModelRuntimeFormat.EXYNOS_AI_STUDIO) {
        editor.putString(PrefAccelerator, AcceleratorMode.AUTO.name)
    }
    editor.apply()
}

private fun modelFootprintLabel(spec: FusionModelSpec): String {
    return spec.modelSizeEstimateGb?.let { "약 ${formatGb(it)}GB" } ?: "메모리 ${spec.memoryClass.name}"
}

private fun compactCompatibilityLine(
    memoryInfo: ModelMemoryUiInfo,
    tokenRecommendation: TokenRecommendation
): String {
    return memoryInfo.warning ?: tokenRecommendation.label
}

private fun buildRuntimeNote(spec: FusionModelSpec): String {
    return when (spec.availability) {
        ModelAvailability.REMOTE_ONLY -> "이 항목은 원격 실행 후보입니다. 로컬 GPU/CPU 실행을 전제로 하지 않습니다."
        ModelAvailability.NEEDS_CONVERSION -> "로컬 실행 전 변환과 실제 기기 호환성 확인이 필요합니다."
        ModelAvailability.NEEDS_DOWNLOAD -> "로컬 파일을 가져온 뒤 현재 LiteRT/Gemma 실행 경로에서 확인해야 합니다."
        ModelAvailability.UNSUPPORTED_ON_DEVICE -> "현재 앱의 로컬 실행 형식으로는 권장하지 않습니다."
        else -> "로컬 파일이 준비된 경우 현재 설정의 GPU/CPU 경로에서 실행할 수 있습니다."
    }
}

private fun buildLocalSelectionMessage(spec: FusionModelSpec, available: Boolean): String? {
    if (available) return null
    if (spec.externallyReferenced && spec.localPath.isNullOrBlank() && spec.availability == ModelAvailability.CUSTOM_IMPORTED) {
        return "실행 준비 필요"
    }
    return when (spec.availability) {
        ModelAvailability.NEEDS_DOWNLOAD -> "모델 파일을 먼저 가져와야 합니다."
        ModelAvailability.NEEDS_CONVERSION -> "이 모델은 변환 후 사용할 수 있습니다."
        ModelAvailability.REMOTE_ONLY -> "이 모델은 원격 실행이 필요합니다."
        ModelAvailability.UNSUPPORTED_ON_DEVICE -> "현재 기기에서는 로컬 실행을 권장하지 않습니다."
        else -> "모델 파일을 먼저 가져와야 합니다."
    }
}

private fun sortModelSpecs(
    specs: List<FusionModelSpec>,
    sortMode: ModelLibrarySortMode,
    currentModel: String,
    favoriteModelIds: Set<String>
): List<FusionModelSpec> {
    val nameKey: (FusionModelSpec) -> String = { it.displayName.lowercase(Locale.getDefault()) }
    val sizeKey: (FusionModelSpec) -> Float = { it.modelSizeEstimateGb ?: Float.MAX_VALUE }
    val minRamKey: (FusionModelSpec) -> Float = { it.minRecommendedRamGb?.toFloat() ?: Float.MAX_VALUE }
    val recRamKey: (FusionModelSpec) -> Float = { it.recommendedRamGb?.toFloat() ?: Float.MAX_VALUE }
    val favoriteKey: (FusionModelSpec) -> Int = { if (it.id in favoriteModelIds) 0 else 1 }
    val localReadyKey: (FusionModelSpec) -> Int = {
        if (it.availability == ModelAvailability.READY || it.availability == ModelAvailability.CUSTOM_IMPORTED) 0 else 1
    }
    val currentKey: (FusionModelSpec) -> Int = { if (it.displayName == currentModel) 0 else 1 }

    return when (sortMode) {
        ModelLibrarySortMode.NAME -> specs.sortedBy(nameKey)
        ModelLibrarySortMode.MEMORY_LOW -> specs.sortedWith(
            compareBy<FusionModelSpec>(minRamKey)
                .thenBy(recRamKey)
                .thenBy(sizeKey)
                .thenBy(nameKey)
        )
        ModelLibrarySortMode.LIGHTWEIGHT -> specs.sortedWith(
            compareBy<FusionModelSpec>(sizeKey)
                .thenBy(minRamKey)
                .thenBy(recRamKey)
                .thenBy(nameKey)
        )
        ModelLibrarySortMode.RECOMMENDATION -> specs.sortedWith(
            compareBy<FusionModelSpec> { recommendationSortRank(it, favoriteModelIds) }
                .thenBy(favoriteKey)
                .thenBy(localReadyKey)
                .thenBy(sizeKey)
                .thenBy(recRamKey)
                .thenBy(nameKey)
        )
        ModelLibrarySortMode.LOCAL_EXECUTION -> specs.sortedWith(
            compareBy<FusionModelSpec>(::localExecutionSortRank)
                .thenBy(favoriteKey)
                .thenBy(sizeKey)
                .thenBy(recRamKey)
                .thenBy(nameKey)
        )
        ModelLibrarySortMode.FAVORITES_FIRST -> specs.sortedWith(
            compareBy<FusionModelSpec>(favoriteKey)
                .thenBy(currentKey)
                .thenBy { recommendationSortRank(it, favoriteModelIds) }
                .thenBy(sizeKey)
                .thenBy(nameKey)
        )
    }
}

private fun loadRecentModels(prefs: SharedPreferences): List<RecentModelEntry> {
    val raw = prefs.getString(PrefRecentModels, null) ?: return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                if (id.isBlank()) continue
                add(
                    RecentModelEntry(
                        modelId = id,
                        displayName = obj.optString("name"),
                        lastUsedAt = obj.optLong("lastUsedAt", 0L),
                        useCount = obj.optInt("useCount", 1)
                    )
                )
            }
        }.sortedByDescending { it.lastUsedAt }.take(MaxRecentModels)
    }.getOrDefault(emptyList())
}

private fun saveRecentModels(prefs: SharedPreferences, items: List<RecentModelEntry>) {
    val arr = JSONArray()
    items.take(MaxRecentModels).forEach { item ->
        arr.put(
            JSONObject().apply {
                put("id", item.modelId)
                put("name", item.displayName)
                put("lastUsedAt", item.lastUsedAt)
                put("useCount", item.useCount)
            }
        )
    }
    prefs.edit().putString(PrefRecentModels, arr.toString()).apply()
}

private fun recordRecentModel(prefs: SharedPreferences, modelId: String, displayName: String) {
    if (modelId.isBlank()) return
    val now = System.currentTimeMillis()
    val current = loadRecentModels(prefs)
    val existing = current.firstOrNull { it.modelId == modelId }
    val updated = RecentModelEntry(
        modelId = modelId,
        displayName = displayName.ifBlank { existing?.displayName ?: modelId },
        lastUsedAt = now,
        useCount = (existing?.useCount ?: 0) + 1
    )
    val next = listOf(updated) + current.filterNot { it.modelId == modelId }
    saveRecentModels(prefs, next)
}

private fun formatRecentUsedLabel(lastUsedAt: Long): String {
    if (lastUsedAt <= 0L) return "최근 사용: 정보 없음"
    val now = System.currentTimeMillis()
    val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(now))
    val usedDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(lastUsedAt))
    return if (today == usedDay) {
        "최근 사용: 오늘"
    } else {
        "최근 사용: ${SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(lastUsedAt))}"
    }
}

private fun loadModelNotes(prefs: SharedPreferences): Map<String, String> {
    val raw = prefs.getString(PrefModelNotes, null) ?: return emptyMap()
    return runCatching {
        val obj = JSONObject(raw)
        buildMap {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.optString(key).trim()
                if (key.isNotBlank() && value.isNotBlank()) {
                    put(key, value)
                }
            }
        }
    }.getOrDefault(emptyMap())
}

private fun saveModelNotes(prefs: SharedPreferences, notes: Map<String, String>) {
    val obj = JSONObject()
    notes.forEach { (id, note) ->
        val trimmed = note.trim()
        if (id.isNotBlank() && trimmed.isNotBlank()) {
            obj.put(id, trimmed)
        }
    }
    prefs.edit().putString(PrefModelNotes, obj.toString()).apply()
}

private fun buildModelBenchmarkSummary(
    spec: FusionModelSpec,
    results: List<BenchmarkResultEntity>
): ModelBenchmarkSummary? {
    val matched = results.filter { result ->
        val pathMatch = !spec.localPath.isNullOrBlank() && !result.modelPath.isNullOrBlank() && result.modelPath == spec.localPath
        val nameMatch = result.modelName.equals(spec.displayName, ignoreCase = true)
        pathMatch || nameMatch
    }
    if (matched.isEmpty()) return null

    val success = matched.filter { it.success }
    val failedCount = matched.count { !it.success }
    val latest = matched.maxByOrNull { it.createdAt } ?: return null
    val speedSeries = success.mapNotNull { it.decodeTokensPerSecond?.takeIf { v -> v > 0f } ?: it.totalTokensPerSecond.takeIf { v -> v > 0f } }
    val sortedSpeed = speedSeries.sorted()
    val median = if (sortedSpeed.isEmpty()) null else {
        val mid = sortedSpeed.size / 2
        if (sortedSpeed.size % 2 == 0) (sortedSpeed[mid - 1] + sortedSpeed[mid]) / 2f else sortedSpeed[mid]
    }
    val avg = if (speedSeries.isEmpty()) null else speedSeries.average().toFloat()
    val best = speedSeries.maxOrNull()
    val worst = speedSeries.minOrNull()
    val averageTotal = success.map { it.totalTokensPerSecond }.filter { it > 0f }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    val mtpRecommendation = buildMtpRecommendation(success)
    return ModelBenchmarkSummary(
        count = matched.size,
        latestAt = latest.createdAt,
        medianDecodeTps = median,
        bestDecodeTps = best,
        worstDecodeTps = worst,
        averageDecodeTps = avg,
        averageTotalTps = averageTotal,
        recentAccelerator = latest.actualBackend ?: latest.accelerator,
        failedCount = failedCount,
        mtpRecommendation = mtpRecommendation
    )
}

private fun buildMtpRecommendation(success: List<BenchmarkResultEntity>): String {
    val on = success.filter { it.mtpEnabled }
    val off = success.filter { !it.mtpEnabled }
    if (on.size < 3 || off.size < 3) return "측정 부족"
    val onMedian = on.mapNotNull { it.decodeTokensPerSecond?.takeIf { v -> v > 0f } ?: it.totalTokensPerSecond.takeIf { v -> v > 0f } }.sorted().let { arr ->
        if (arr.isEmpty()) return "측정 부족"
        val mid = arr.size / 2
        if (arr.size % 2 == 0) (arr[mid - 1] + arr[mid]) / 2f else arr[mid]
    }
    val offMedian = off.mapNotNull { it.decodeTokensPerSecond?.takeIf { v -> v > 0f } ?: it.totalTokensPerSecond.takeIf { v -> v > 0f } }.sorted().let { arr ->
        if (arr.isEmpty()) return "측정 부족"
        val mid = arr.size / 2
        if (arr.size % 2 == 0) (arr[mid - 1] + arr[mid]) / 2f else arr[mid]
    }
    return when {
        onMedian >= offMedian * 1.05f -> "켬"
        offMedian >= onMedian * 1.05f -> "끔"
        else -> "차이 작음"
    }
}

private fun buildCardBenchmarkLine(summary: ModelBenchmarkSummary): String {
    val speed = summary.medianDecodeTps ?: summary.averageTotalTps
    return if (speed != null) {
        "벤치마크 ${summary.count}회 · 중앙값 ${formatSpeed(speed)} tok/s"
    } else {
        "벤치마크 ${summary.count}회"
    }
}

private fun formatSpeed(value: Float): String = String.format(Locale.US, "%.1f", value)

private fun formatTimestamp(ts: Long): String {
    if (ts <= 0L) return "정보 없음"
    return SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(ts))
}

private fun recommendationSortRank(model: FusionModelSpec, favoriteModelIds: Set<String>): Int {
    return when {
        model.availability == ModelAvailability.READY || model.availability == ModelAvailability.CUSTOM_IMPORTED -> 1
        (model.memoryClass == ModelMemoryClass.LOW || (model.modelSizeEstimateGb ?: Float.MAX_VALUE) <= 1.5f || model.recommendedDeviceClass == ModelRecommendedDeviceClass.RAM_8GB_SAFE) -> 2
        model.id in favoriteModelIds -> 3
        model.availability == ModelAvailability.NEEDS_DOWNLOAD || model.availability == ModelAvailability.NEEDS_CONVERSION -> 4
        model.availability == ModelAvailability.REMOTE_ONLY || model.availability == ModelAvailability.UNSUPPORTED_ON_DEVICE || model.memoryClass == ModelMemoryClass.SERVER -> 5
        else -> 6
    }
}

private fun localExecutionSortRank(model: FusionModelSpec): Int {
    return when (model.availability) {
        ModelAvailability.READY -> 1
        ModelAvailability.CUSTOM_IMPORTED -> 2
        ModelAvailability.NEEDS_DOWNLOAD -> 3
        ModelAvailability.NEEDS_CONVERSION -> 4
        ModelAvailability.REMOTE_ONLY -> 5
        ModelAvailability.UNSUPPORTED_ON_DEVICE -> 6
    }
}

private fun openModelLink(context: Context, url: String?) {
    if (url.isNullOrBlank()) {
        Toast.makeText(context, "등록된 링크가 없습니다.", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "링크를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, "링크를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

private fun startModelDirectDownload(context: Context, spec: FusionModelSpec) {
    val url = spec.directDownloadUrl
    if (url.isNullOrBlank()) {
        Toast.makeText(context, "다운로드 페이지를 열어 주세요.", Toast.LENGTH_SHORT).show()
        openModelLink(context, spec.modelPageUrl ?: spec.downloadUrl ?: spec.officialUrl)
        return
    }
    try {
        val request = DownloadManager.Request(url.toUri()).apply {
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setTitle(spec.directDownloadFileName ?: "${spec.displayName} 모델 파일")
            setDescription("${spec.directDownloadFormat ?: "모델"} 파일 다운로드")
            setAllowedOverMetered(false)
            setAllowedOverRoaming(false)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                spec.directDownloadFileName ?: Uri.parse(url).lastPathSegment ?: "${spec.id}.bin"
            )
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (manager == null) {
            Toast.makeText(context, "모델 파일 다운로드를 시작할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        manager.enqueue(request)
        Toast.makeText(context, "모델 파일 다운로드를 시작했습니다.", Toast.LENGTH_SHORT).show()
        Toast.makeText(context, "다운로드가 완료되면 파일 가져오기로 모델 파일을 선택해 주세요.", Toast.LENGTH_LONG).show()
    } catch (_: Exception) {
        Toast.makeText(context, "모델 파일 다운로드를 시작할 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

private fun bytesToGb(bytes: Long): Float {
    if (bytes <= 0L) return 0f
    return bytes / (1024f * 1024f * 1024f)
}

private fun formatGb(value: Float): String {
    return String.format(Locale.US, "%.1f", value.coerceAtLeast(0f))
}

@Composable
private fun ModelStorageManagerDialog(
    refreshKey: Int,
    currentModel: String,
    onDismiss: () -> Unit,
    onSelect: (FusionModelSpec) -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var models by remember(refreshKey) { mutableStateOf(FusionModelCatalog.loadImported(context)) }
    var deleteTarget by remember { mutableStateOf<FusionModelSpec?>(null) }
    val internalFiles = remember(refreshKey) {
        getModelDirectory(context).listFiles()?.filter { it.isFile }.orEmpty()
    }
    val totalSize = internalFiles.sumOf { it.length() } + models.sumOf { it.fileSizeBytes ?: 0L }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = PanelBg,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("모델 저장공간", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("가져온 모델과 외부 연결 파일을 관리합니다.", color = TextSecondary, fontSize = 13.sp)
                Text("총 용량 약 ${formatBytes(totalSize)}", color = TextSecondary, fontSize = 12.sp)

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("Fusion 내부 모델 파일", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    items(internalFiles) { file ->
                        val spec = models.firstOrNull { it.localPath == file.absolutePath }
                        StorageModelRow(
                            name = spec?.displayName ?: file.name,
                            source = "Fusion 내부 저장소",
                            size = formatBytes(file.length()),
                            runtimeFormat = FusionModelCatalog.runtimeFormatForFile(file.name).name,
                            family = spec?.family?.name ?: ModelFamily.CUSTOM.name,
                            status = if (file.exists()) "사용 가능" else "파일을 찾을 수 없습니다.",
                            current = spec?.displayName == currentModel,
                            actions = {
                                FusionTextButton(onClick = {
                                    onSelect(spec ?: FusionModelCatalog.importedSpec(file.name, file.absolutePath, ModelFamily.CUSTOM))
                                }) { Text("선택", fontSize = 12.sp) }
                                FusionTextButton(onClick = {
                                    openModelFile(context, file)
                                }) { Text("파일 열기", fontSize = 12.sp) }
                                FusionTextButton(onClick = {
                                    deleteTarget = spec ?: FusionModelCatalog.importedSpec(file.name, file.absolutePath, ModelFamily.CUSTOM)
                                }) { Text("삭제", fontSize = 12.sp, color = DangerRed) }
                            }
                        )
                    }
                    item {
                        Text("외부 연결 모델 파일", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    items(models.filter { it.externallyReferenced }) { spec ->
                        val available = canOpenUri(context, spec.uriString)
                        StorageModelRow(
                            name = spec.displayName,
                            source = "외부 파일 연결",
                            size = spec.fileSizeBytes?.let { formatBytes(it) } ?: "크기 정보 없음",
                            runtimeFormat = spec.runtimeFormat.name,
                            family = spec.family.name,
                            status = storageStatusLabel(spec, available),
                            current = spec.displayName == currentModel,
                            actions = {
                                FusionTextButton(onClick = { onSelect(spec) }) { Text("선택", fontSize = 12.sp) }
                                FusionTextButton(onClick = { openModelUri(context, spec.uriString) }) { Text("파일 열기", fontSize = 12.sp) }
                                if (spec.availability == ModelAvailability.CUSTOM_IMPORTED && spec.localPath.isNullOrBlank()) {
                                    FusionTextButton(onClick = {
                                        scope.launch {
                                            val copied = copyUriToModelFile(
                                                context = context,
                                                uri = Uri.parse(spec.uriString),
                                                displayName = spec.originalFileName ?: spec.fileName ?: spec.displayName
                                            )
                                            if (copied == null) {
                                                Toast.makeText(context, "모델 파일에 접근할 수 없습니다. 파일을 다시 연결해 주세요.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                FusionModelCatalog.saveImported(
                                                    context,
                                                    spec.copy(
                                                        localPath = copied.absolutePath,
                                                        fileName = copied.name,
                                                        copiedInternally = true,
                                                        externallyReferenced = false,
                                                        sourceLabel = "사용자 가져오기",
                                                        lastCheckedAt = System.currentTimeMillis()
                                                    )
                                                )
                                                models = FusionModelCatalog.loadImported(context)
                                                onChanged()
                                                Toast.makeText(context, "실행용으로 복사했습니다.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }) { Text("실행용으로 복사", fontSize = 12.sp) }
                                }
                                FusionTextButton(onClick = {
                                    FusionModelCatalog.removeImported(context, spec)
                                    models = FusionModelCatalog.loadImported(context)
                                    onChanged()
                                }) { Text("연결 해제", fontSize = 12.sp) }
                            }
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FusionTextButton(onClick = onDismiss) { Text("닫기", fontSize = 13.sp) }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("모델 파일을 삭제하시겠습니까?") },
            text = { Text("Fusion 내부 저장소의 모델 파일이 삭제됩니다.") },
            confirmButton = {
                FusionTextButton(onClick = {
                    target.localPath?.let { File(it).delete() }
                    FusionModelCatalog.removeImported(context, target)
                    models = FusionModelCatalog.loadImported(context)
                    deleteTarget = null
                    onChanged()
                }) { Text("삭제") }
            },
            dismissButton = { FusionTextButton(onClick = { deleteTarget = null }) { Text("취소") } },
            containerColor = PanelBg,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary
        )
    }
}

@Composable
private fun StorageModelRow(
    name: String,
    source: String,
    size: String,
    runtimeFormat: String,
    family: String,
    status: String,
    current: Boolean,
    actions: @Composable RowScope.() -> Unit
) {
    Surface(shape = RoundedCornerShape(12.dp), color = if (current) BubbleBg else Color.Transparent) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$source · $size · $runtimeFormat · $family", color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, color = if (status == "사용 가능") AccentBlue else TextSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), content = actions)
        }
    }
}

private fun storageStatusLabel(spec: FusionModelSpec, uriAvailable: Boolean): String {
    if (!uriAvailable) return "파일을 찾을 수 없습니다."
    if (spec.runtimeFormat == ModelRuntimeFormat.NEEDS_CONVERSION) return "변환 필요"
    if (spec.availability != ModelAvailability.CUSTOM_IMPORTED) return "실행 준비 필요"
    if (spec.externallyReferenced && spec.localPath.isNullOrBlank()) return "실행 준비 필요"
    return "사용 가능"
}

private fun importFormatLabel(fileName: String, format: ModelRuntimeFormat): String {
    return when (fileName.substringAfterLast('.', "").lowercase(Locale.US)) {
        "litertlm" -> "LiteRT-LM"
        "task" -> "MediaPipe LLM"
        "gguf" -> "GGUF"
        "onnx" -> "ONNX"
        "safetensors" -> "변환 필요"
        "bin" -> "확인 필요"
        else -> when (format) {
            ModelRuntimeFormat.LITERT_LM -> "LiteRT-LM"
            ModelRuntimeFormat.MEDIAPIPE_LLM -> "MediaPipe LLM"
            ModelRuntimeFormat.GGUF -> "GGUF"
            ModelRuntimeFormat.ONNX -> "ONNX"
            ModelRuntimeFormat.NEEDS_CONVERSION -> "변환 필요"
            else -> "지원 확인 필요"
        }
    }

}

private fun importExecutionStatusMessage(format: ModelRuntimeFormat): String {
    return when (format) {
        ModelRuntimeFormat.LITERT_LM, ModelRuntimeFormat.MEDIAPIPE_LLM -> "이 모델 파일은 Fusion에서 실행 후보로 사용할 수 있습니다."
        ModelRuntimeFormat.NEEDS_CONVERSION -> "이 모델은 변환 후 사용할 수 있습니다."
        else -> "이 형식은 현재 직접 실행할 수 없습니다."
    }
}

private fun importRecommendedActionMessage(format: ModelRuntimeFormat): String {
    return when (format) {
        ModelRuntimeFormat.LITERT_LM, ModelRuntimeFormat.MEDIAPIPE_LLM -> "권장 동작: 먼저 연결하고, 실행이 필요하면 실행용으로 복사해 주세요."
        ModelRuntimeFormat.NEEDS_CONVERSION -> "권장 동작: 연결로 보관하고 변환 후 다시 가져와 주세요."
        else -> "권장 동작: 연결로 보관하고 지원 여부를 확인해 주세요."
    }
}

private fun isModelLikeFileName(name: String): Boolean {
    val lower = name.lowercase(Locale.US)
    return listOf(".litertlm", ".task", ".gguf", ".onnx", ".bin", ".safetensors").any { lower.endsWith(it) }
}

private fun getFileSizeFromUri(context: Context, uri: Uri): Long? {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (sizeIndex >= 0 && cursor.moveToFirst()) {
            return cursor.getLong(sizeIndex).takeIf { it > 0L }
        }
    }
    return null
}

private fun canOpenUri(context: Context, uriString: String?): Boolean {
    if (uriString.isNullOrBlank()) return false
    return runCatching {
        context.contentResolver.openInputStream(Uri.parse(uriString))?.close()
        true
    }.getOrDefault(false)
}

private fun openModelUri(context: Context, uriString: String?) {
    if (uriString.isNullOrBlank()) {
        Toast.makeText(context, "모델 파일에 접근할 수 없습니다. 파일을 다시 연결해 주세요.", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uriString), "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    } catch (_: Exception) {
        Toast.makeText(context, "파일을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

private fun openModelFile(context: Context, file: File) {
    try {
        val uri = FusionFileProvider.uriFor(context, file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    } catch (_: Exception) {
        Toast.makeText(context, "파일을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val gb = bytes / (1024f * 1024f * 1024f)
    if (gb >= 1f) return "약 ${formatGb(gb)}GB"
    val mb = bytes / (1024f * 1024f)
    return "약 ${String.format(Locale.US, "%.1f", mb)}MB"
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val displayTitle = if (title.contains("MTP")) "MTP" else title
    val displaySubtitle = settingExplanation(displayTitle) ?: subtitle
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = displayTitle,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = displaySubtitle,
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

private fun settingExplanation(title: String): String? {
    return when (title) {
        "Max tokens" -> "\ub2f5\ubcc0\uc758 \ucd5c\ub300 \uae38\uc774\ub97c \uc81c\ud55c\ud569\ub2c8\ub2e4. \uac12\uc774 \ud074\uc218\ub85d \uae34 \ub2f5\ubcc0\uc744 \uc0dd\uc131\ud560 \uc218 \uc788\uc9c0\ub9cc \uba54\ubaa8\ub9ac \uc0ac\uc6a9\ub7c9\uacfc \uc0dd\uc131 \uc2dc\uac04\uc774 \uc99d\uac00\ud569\ub2c8\ub2e4."
        "Temperature" -> "\ub2f5\ubcc0\uc758 \ubb34\uc791\uc704\uc131\uacfc \ucc3d\uc758\uc131\uc744 \uc870\uc808\ud569\ub2c8\ub2e4. \ub0ae\uc744\uc218\ub85d \uc548\uc815\uc801\uc774\uace0, \ub192\uc744\uc218\ub85d \ub2e4\uc591\ud558\uc9c0\ub9cc \ubd80\uc815\ud655\ud574\uc9c8 \uc218 \uc788\uc2b5\ub2c8\ub2e4."
        "TopK" -> "\ub2e4\uc74c \ud1a0\ud070 \ud6c4\ubcf4\ub97c \uc0c1\uc704 \uba87 \uac1c\uae4c\uc9c0 \uace0\ub824\ud560\uc9c0 \uc815\ud569\ub2c8\ub2e4. \ub0ae\uc744\uc218\ub85d \uc548\uc815\uc801\uc774\uace0, \ub192\uc744\uc218\ub85d \ud45c\ud604\uc774 \ub2e4\uc591\ud574\uc9c8 \uc218 \uc788\uc2b5\ub2c8\ub2e4."
        "TopP" -> "\ub204\uc801 \ud655\ub960 \uae30\uc900\uc73c\ub85c \ud6c4\ubcf4 \ud1a0\ud070 \ubc94\uc704\ub97c \uc81c\ud55c\ud569\ub2c8\ub2e4. \ub0ae\uc744\uc218\ub85d \ubcf4\uc218\uc801\uc774\uace0, \ub192\uc744\uc218\ub85d \ub2e4\uc591\ud55c \ud45c\ud604\uc744 \uc0ac\uc6a9\ud560 \uc218 \uc788\uc2b5\ub2c8\ub2e4."
        "Reasoning budget" -> "\ucd94\ub860 \ubaa8\ub4dc\uc5d0\uc11c \uc0ac\uc6a9\ud560 \ubaa9\ud45c \ud1a0\ud070 \ubc94\uc704\uc785\ub2c8\ub2e4. \ud604\uc7ac \ubaa8\ub378\uacfc \ub7f0\ud0c0\uc784\uc5d0 \ub530\ub77c \uc2e4\uc81c \uc801\uc6a9 \uc815\ub3c4\uac00 \ub2ec\ub77c\uc9c8 \uc218 \uc788\uc2b5\ub2c8\ub2e4."
        "MTP" -> "\uc5ec\ub7ec \ud1a0\ud070\uc744 \ubbf8\ub9ac \uc608\uce21\ud574 \uc18d\ub3c4\ub97c \ub192\uc774\ub294 \uc2e4\ud5d8\uc801 \uac00\uc18d \uae30\ub2a5\uc785\ub2c8\ub2e4. \uae30\uae30\uc640 \ubaa8\ub378\uc5d0 \ub530\ub77c \ub354 \ub290\ub824\uc9c8 \uc218 \uc788\uc2b5\ub2c8\ub2e4."
        "Reasoning" -> "\ub2f5\ubcc0\uc744 \uc0dd\uc131\ud558\uae30 \uc804\uc5d0 \ub354 \uad6c\uc870\uc801\uc73c\ub85c \uc0dd\uac01\ud558\ub3c4\ub85d \uc720\ub3c4\ud569\ub2c8\ub2e4. \ud488\uc9c8\uc774 \uc88b\uc544\uc9c8 \uc218 \uc788\uc9c0\ub9cc \uc0dd\uc131 \uc2dc\uac04\uc774 \ub298\uc5b4\ub0a9\ub2c8\ub2e4."
        "Web Search" -> "\ucd5c\uc2e0 \uc815\ubcf4\uac00 \ud544\uc694\ud55c \uc9c8\ubb38\uc5d0\uc11c \uc778\ud130\ub137 \uac80\uc0c9 \uacb0\uacfc\ub97c \ucc38\uace0\ud569\ub2c8\ub2e4. \uac80\uc0c9 \uacb0\uacfc\uac00 \uae38\uba74 \uc751\ub2f5 \uc2dc\uac04\uc774 \ub298\uc5b4\ub0a0 \uc218 \uc788\uc2b5\ub2c8\ub2e4."
        else -> null
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
                        "이 모델은 아직 기기에 없습니다. 다운로드한 뒤 로컬 추론 엔진에 연결할 수 있습니다."
                    } else {
                        "이 모델은 아직 다운로드 URL이 등록되지 않았습니다."
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
    reasoningEnabled: Boolean,
    webSearchEnabled: Boolean,
    webSearchProviderRepository: WebSearchProviderRepository,
    onDismiss: () -> Unit,
    onApply: (GenerationSettings, Boolean, Boolean) -> Unit
) {
    var maxTokens by remember(settings) {
        mutableStateOf(settings.maxTokens.coerceIn(1024, 32000))
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
    var reasoningEnabledLocal by remember(reasoningEnabled) { mutableStateOf(reasoningEnabled) }
    var webSearchEnabledLocal by remember(webSearchEnabled) { mutableStateOf(webSearchEnabled) }

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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "\ucd94\ucc9c \uc124\uc815", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            maxTokens = 1024; maxTokensText = "1024"; temperature = 0.7f; temperatureText = "0.70"; topK = 40; topKText = "40"; topP = 0.9f; topPText = "0.90"; reasoningEnabledLocal = false; speculativeDecodingEnabled = false; speculativeDecodingTouched = true
                        }) { Text("\uc800\uba54\ubaa8\ub9ac", color = TextPrimary) }
                        TextButton(onClick = {
                            maxTokens = 1024; maxTokensText = "1024"; temperature = 0.7f; temperatureText = "0.70"; topK = 40; topKText = "40"; topP = 0.9f; topPText = "0.90"; reasoningEnabledLocal = false; speculativeDecodingEnabled = false; speculativeDecodingTouched = true
                        }) { Text("\ube60\ub978 \uc751\ub2f5", color = TextPrimary) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            maxTokens = 2048; maxTokensText = "2048"; temperature = 0.4f; temperatureText = "0.40"; topK = 40; topKText = "40"; topP = 0.9f; topPText = "0.90"; reasoningEnabledLocal = true; speculativeDecodingEnabled = false; speculativeDecodingTouched = true
                        }) { Text("\uc815\ud655\ud55c \ub2f5\ubcc0", color = TextPrimary) }
                        TextButton(onClick = {
                            maxTokens = 4096; maxTokensText = "4096"; temperature = 0.7f; temperatureText = "0.70"; topK = 64; topKText = "64"; topP = 0.95f; topPText = "0.95"; reasoningEnabledLocal = false
                        }) { Text("\uae34 \uc124\uba85", color = TextPrimary) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            maxTokens = 2048; maxTokensText = "2048"; temperature = 1.1f; temperatureText = "1.10"; topK = 80; topKText = "80"; topP = 0.98f; topPText = "0.98"; reasoningEnabledLocal = false; speculativeDecodingEnabled = false; speculativeDecodingTouched = true
                        }) { Text("\ucc3d\uc758\uc801 \ub2f5\ubcc0", color = TextPrimary) }
                        TextButton(onClick = {
                            maxTokens = 1024; maxTokensText = "1024"; temperature = 0.7f; temperatureText = "0.70"; topK = 40; topKText = "40"; topP = 0.9f; topPText = "0.90"; reasoningEnabledLocal = false; webSearchEnabledLocal = false; speculativeDecodingEnabled = false; speculativeDecodingTouched = true
                        }) { Text("\ubca4\uce58\ub9c8\ud06c \uc548\uc804", color = TextPrimary) }
                    }
                }

                Surface(shape = RoundedCornerShape(12.dp), color = PanelBg, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("\ud604\uc7ac \uc801\uc6a9\uac12", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("\ubaa8\ub378: $selectedModel", color = TextSecondary, fontSize = 12.sp)
                        Text("\uac00\uc18d\uae30: ${accelerator.name} · MTP: ${if (speculativeDecodingEnabled) "\ucf1c\uc9d0" else "\uaebc\uc9d0"}", color = TextSecondary, fontSize = 12.sp)
                        Text("maxTokens=$maxTokensText · temp=$temperatureText · topK=$topKText · topP=$topPText", color = TextSecondary, fontSize = 12.sp)
                        Text("Reasoning: ${if (reasoningEnabledLocal) "\ucf1c\uc9d0" else "\uaebc\uc9d0"} · Web Search: ${if (webSearchEnabledLocal) "\ucf1c\uc9d0" else "\uaebc\uc9d0"}", color = TextSecondary, fontSize = 12.sp)
                    }
                }
                Text(
                    text = "\uc815\ud655\ud55c \ube44\uad50\ub97c \uc704\ud574 \uac19\uc740 \uc870\uac74\uc5d0\uc11c 3\ud68c \uc774\uc0c1 \uce21\uc815\ud574 \uc8fc\uc138\uc694.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                IntSliderField(
                    title = "Max tokens",
                    value = maxTokens,
                    valueText = maxTokensText,
                    min = 1024,
                    max = 32000,
                    onSliderChange = { newValue ->
                        maxTokens = newValue
                        maxTokensText = newValue.toString()
                    },
                    onTextChange = { text ->
                        maxTokensText = text
                        text.toIntOrNull()?.let { parsed ->
                            maxTokens = parsed.coerceIn(1024, 32000)
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

                SettingSwitchRow(
                    title = "Reasoning",
                    subtitle = "",
                    checked = reasoningEnabledLocal,
                    onToggle = { reasoningEnabledLocal = !reasoningEnabledLocal }
                )

                SettingSwitchRow(
                    title = "Web Search",
                    subtitle = "",
                    checked = webSearchEnabledLocal,
                    onToggle = { webSearchEnabledLocal = !webSearchEnabledLocal }
                )

                WebSearchProviderSettingsSection(
                    repository = webSearchProviderRepository,
                    modifier = Modifier.fillMaxWidth()
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
                                ?.coerceIn(1024, 32000)
                                ?: maxTokens.coerceIn(1024, 32000),
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
                        ),
                        reasoningEnabledLocal,
                        webSearchEnabledLocal
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
        settingExplanation(title)?.let { explanation ->
            Text(
                text = explanation,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
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
        settingExplanation(title)?.let { explanation ->
            Text(
                text = explanation,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
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
    val cleanRaw = stripSearchSourcesMetadata(raw)
    val thinkingTagRegex = Regex("""</?fusion_thinking>""", RegexOption.IGNORE_CASE)
    val answerTagRegex = Regex("""</?fusion_answer>""", RegexOption.IGNORE_CASE)

    val thinkingBlockRegex = Regex(
        pattern = """(?is)<fusion_thinking>(.*?)</fusion_thinking>"""
    )
    val answerBlockRegex = Regex(
        pattern = """(?is)<fusion_answer>(.*?)</fusion_answer>"""
    )

    val thinkingBlocks = thinkingBlockRegex.findAll(cleanRaw)
        .map { match -> stripFusionTags(match.groupValues[1]).trim() }
        .filter { it.isNotBlank() }
        .toList()

    val answerBlocks = answerBlockRegex.findAll(cleanRaw)
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

        cleanRaw.contains("</fusion_thinking>", ignoreCase = true) -> cleanRaw
            .substringAfterLast("</fusion_thinking>")
            .let(::stripFusionTags)
            .trim()
            .ifBlank {
                if (thinkingBlocks.isNotEmpty()) {
                    ""
                } else {
                    stripFusionTags(cleanRaw).trim()
                }
            }

        cleanRaw.contains("<fusion_answer>", ignoreCase = true) -> cleanRaw
            .substringAfterLast("<fusion_answer>")
            .let(::stripFusionTags)
            .trim()

        cleanRaw.contains("<fusion_thinking>", ignoreCase = true) -> cleanRaw
            .substringBefore("<fusion_thinking>")
            .let(::stripFusionTags)
            .trim()
            .ifBlank {
                if (thinkingBlocks.isNotEmpty()) {
                    ""
                } else {
                    stripFusionTags(cleanRaw).trim()
                }
            }

        thinkingTagRegex.containsMatchIn(cleanRaw) || answerTagRegex.containsMatchIn(cleanRaw) -> {
            val withoutCompleteThinking = thinkingBlockRegex.replace(cleanRaw, "")
            stripFusionTags(withoutCompleteThinking)
                .trim()
                .ifBlank {
                    if (thinkingBlocks.isNotEmpty()) {
                        ""
                    } else {
                        stripFusionTags(cleanRaw).trim()
                    }
                }
        }

        else -> cleanRaw.trim()
    }.let(::stripFusionTags).trim()

    return ParsedAssistantOutput(
        thinking = thinking,
        answer = answer.ifBlank {
            if (thinkingBlocks.isNotEmpty() || thinkingTagRegex.containsMatchIn(cleanRaw)) {
                "No final answer was generated."
            } else {
                stripFusionTags(cleanRaw).trim()
            }
        }
    )

}

private suspend fun generateWithLiteRtRecovery(
    engine: LiteRtLlmEngine,
    onBeforeRetry: () -> Unit,
    generateOnce: suspend () -> GenerationOutcome
): GenerationOutcome {
    val firstResult = generateOnce()
    if (firstResult is GenerationOutcome.Success || firstResult is GenerationOutcome.Empty) {
        return firstResult
    }

    val firstFailure = firstResult as? GenerationOutcome.Failure
    if (firstFailure != null && firstFailure.kind != FailureKind.MODEL_LOAD_FAILED) {
        return firstResult
    }

    Log.e("FusionEngine", "LiteRT generation failed; unloading engine and retrying once: $firstResult")
    FusionRuntimeManager.unloadSharedEngineForActiveOwner("chat_retry")
    onBeforeRetry()

    val retryResult = generateOnce()
    if (retryResult is GenerationOutcome.Success || retryResult is GenerationOutcome.Empty) {
        return retryResult
    }

    Log.e("FusionEngine", "LiteRT generation retry failed: $retryResult")
    FusionRuntimeManager.unloadSharedEngineForActiveOwner("chat_retry_failed")
    return retryResult
}

private fun isLiteRtModelLoadException(error: Throwable): Boolean {
    val message = error.message.orEmpty()
    return message.contains("Failed to create engine", ignoreCase = true) ||
        message.contains("litert_compiled_model", ignoreCase = true) ||
        message.contains("INTERNAL", ignoreCase = true)
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
    firstTokenLatencyMs: Long? = null,
    settingsLine: String? = null
): String {
    val seconds = (totalGenerationMs / 1000.0).coerceAtLeast(0.1)
    val estimatedTokens = estimateOutputTokens(generatedText)
    val tokensPerSecond = estimatedTokens / seconds

    val totalSecondsText = String.format(Locale.US, "%.1f", seconds)
    val tokensPerSecondText = String.format(Locale.US, "%.1f", tokensPerSecond)

    val mainLine = buildString {
        append(modelName)
        append(" · ")
        append(acceleratorName)
        append(" · ")
        append("${totalSecondsText}s")
        append(" · ")
        append("약 ${tokensPerSecondText} tok/s")

        if (firstTokenLatencyMs != null && firstTokenLatencyMs > 0L) {
            val firstTokenSeconds = firstTokenLatencyMs / 1000.0
            val firstTokenText = String.format(Locale.US, "%.1f", firstTokenSeconds)
            append(" · 첫 토큰 ${firstTokenText}s")
        }
    }

    return listOf(
        mainLine,
        settingsLine?.trim()?.takeIf { it.isNotBlank() }
    )
        .filterNotNull()
        .joinToString("\n")
}

private fun appendFusionMetrics(
    body: String,
    metrics: String,
    settingsLine: String? = null
): String {
    val metricsBlock = listOf(
        metrics,
        settingsLine
    )
        .mapNotNull { it?.trim()?.takeIf { line -> line.isNotBlank() } }
        .joinToString("\n")

    if (metricsBlock.isBlank()) {
        return body
    }

    return body.trimEnd() + "\n\n<fusion_metrics>$metricsBlock</fusion_metrics>"
}

private fun buildFusionSettingsMetricsLine(
    settings: GenerationSettings
): String {
    return "설정 · max ${settings.maxTokens} · temp ${settings.temperature} · topK ${settings.topK} · topP ${settings.topP} · ${settings.accelerator.name}"
}

private fun visibleAssistantHistoryText(content: String): String {
    val withoutSources = stripSearchSourcesMetadata(content)
    val withoutMetrics = Regex("""(?is)<fusion_metrics>.*?</fusion_metrics>""").replace(withoutSources, "")
    val withoutThinking = Regex("""(?is)<fusion_thinking>.*?</fusion_thinking>""").replace(withoutMetrics, "")
    return withoutThinking
        .replace(Regex("""</?fusion_(?:thinking|answer|metrics)>""", RegexOption.IGNORE_CASE), "")
        .trim()
}

private fun splitFusionMetrics(
    content: String
): FusionMetricsSplit {
    val cleanContent = stripSearchSourcesMetadata(content)
    val metricsRegex = Regex("""(?is)<fusion_metrics>(.*?)</fusion_metrics>""")
    val metricsLine = metricsRegex.findAll(cleanContent)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotBlank() }
        .lastOrNull()

    return FusionMetricsSplit(
        content = metricsRegex.replace(cleanContent, "").trimEnd(),
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

        instantResult ?: "검색 결과를 가져오지 못했습니다. 일반 지식과 추론을 구분해서 답변해야 합니다."
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
        val uri = FusionFileProvider.uriFor(context, file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, attachment.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(intent, "파일 열기")
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "이 파일을 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "파일을 열 수 없습니다.",
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

private fun loadSavedGenerationSettings(
    prefs: SharedPreferences
): GenerationSettings {
    val accelerator = runCatching {
        AcceleratorMode.valueOf(
            prefs.getString(PrefAccelerator, AcceleratorMode.GPU.name) ?: AcceleratorMode.GPU.name
        )
    }.getOrDefault(AcceleratorMode.GPU)

    val speculativeDecodingEnabled = if (prefs.contains(PrefSpeculativeDecoding)) {
        prefs.getBoolean(PrefSpeculativeDecoding, false)
    } else {
        null
    }

    return GenerationSettings(
        maxTokens = prefs.getInt(PrefMaxTokens, 4000).coerceIn(2000, 32000),
        topK = prefs.getInt(PrefTopK, 64).coerceIn(5, 100),
        topP = prefs.getFloat(PrefTopP, 0.95f).coerceIn(0f, 1f),
        temperature = prefs.getFloat(PrefTemperature, 1.0f).coerceIn(0f, 2f),
        accelerator = accelerator,
        reasoningBudgetTokens = prefs.getInt(PrefReasoningBudget, 512).coerceIn(128, 8192),
        speculativeDecodingEnabled = speculativeDecodingEnabled
    )
}

private fun saveFusionSettings(
    prefs: SharedPreferences,
    settings: GenerationSettings,
    reasoningEnabled: Boolean,
    webSearchEnabled: Boolean,
    selectedModel: String,
    selectedModelPath: String?
) {
    prefs.edit()
        .putInt(PrefMaxTokens, settings.maxTokens)
        .putInt(PrefTopK, settings.topK)
        .putFloat(PrefTopP, settings.topP)
        .putFloat(PrefTemperature, settings.temperature)
        .putInt(PrefReasoningBudget, settings.reasoningBudgetTokens)
        .putString(PrefAccelerator, settings.accelerator.name)
        .putBoolean(PrefReasoningEnabled, reasoningEnabled)
        .putBoolean(PrefWebSearchEnabled, webSearchEnabled)
        .putString(PrefSelectedModel, selectedModel)
        .apply {
            if (selectedModelPath.isNullOrBlank()) {
                remove(PrefSelectedModelPath)
            } else {
                putString(PrefSelectedModelPath, selectedModelPath)
            }

            if (settings.speculativeDecodingEnabled == null) {
                remove(PrefSpeculativeDecoding)
            } else {
                putBoolean(PrefSpeculativeDecoding, settings.speculativeDecodingEnabled)
            }
        }
        .apply()
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

private fun isMultimodalCapableModel(
    modelName: String,
    modelPath: String
): Boolean {
    val combined = "$modelName $modelPath".lowercase(Locale.ROOT)
    return combined.contains("gemma 4") ||
        combined.contains("gemma-4") ||
        combined.contains("multimodal") ||
        combined.contains("vision")
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

private fun recordWebSearchDiagnostics(
    context: Context,
    response: FusionSearchResponse?
) {
    response ?: return
    response.plan?.let { plan ->
        DeveloperLogStore.record(
            context = context,
            category = "web_search",
            message = "웹검색 검색어 계획 생성",
            technicalSummary = "primary=${plan.primaryQuery.take(90)}, alternates=${plan.alternateQueries.size}, preferred=${plan.preferredProviderTypes.joinToString { it.name }}, reason=${plan.reason.take(80)}"
        )
    }
    response.traces.forEach { trace ->
        DeveloperLogStore.record(
            context = context,
            category = "web_search",
            message = "웹검색 제공자 시도: ${trace.providerDisplayName}",
            technicalSummary = buildString {
                append("query=${trace.queryUsed.take(80)}, results=${trace.parsedResultCount}")
                trace.httpStatus?.let { append(", status=$it") }
                trace.qualityScore?.let { append(", quality=${String.format(Locale.US, "%.2f", it)}") }
                trace.fallbackReason?.let { append(", fallback=${it.take(80)}") }
                trace.exceptionClass?.let { append(", error=$it") }
            }
        )
    }
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
    webContext: String?,
    promptLabInstruction: String?
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
당신은 기기 내에서 실행되는 AI 비서 Fusion입니다.
한국어로 자연스럽게 대화하며 일관되게 존댓말을 사용합니다.
모르는 내용은 모른다고 명확히 밝힙니다.
불확실한 내용은 추론임을 명확히 구분합니다.
사용자의 질문을 그대로 반복하지 않습니다.
thought, user, model, assistant 같은 내부 태그나 역할명을 출력하지 않습니다.
과장된 제목, 과한 이모지, 마케팅식 표현은 피합니다.
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

    return listOf(basePrompt, promptLabInstruction.orEmpty(), outputRule, webRule)
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
}
private fun getAttachmentDirectory(context: Context): File {
    return AttachmentStorageManager.getAttachmentDirectory(context)
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
                AttachmentStorageManager.registerPendingAttachment(outputFile)
                outputFile
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}

private fun clearPendingAttachmentDrafts(pendingAttachments: MutableList<LocalAttachment>) {
    pendingAttachments.forEach { attachment ->
        AttachmentStorageManager.unregisterPendingAttachment(attachment.localPath)
    }
    pendingAttachments.clear()
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

private fun visibleSearchText(rawMessageContent: String): String {
    val withoutAttachments = parseMessageAttachments(rawMessageContent).body
    val withoutMetrics = splitFusionMetrics(withoutAttachments).content
    return withoutMetrics
        .replace(Regex("""(?is)<fusion_thinking>.*?</fusion_thinking>"""), "")
        .replace(Regex("""(?is)<fusion_answer>(.*?)</fusion_answer>"""), "$1")
        .replace(Regex("""(?is)</?fusion_(?:thinking|answer)>"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

private fun parseMemoryCandidateLines(raw: String): List<String> {
    return raw
        .lines()
        .map { line ->
            line.replace(Regex("""^(?:[\-\*\u2022\u25CF\u25E6]\s*|\d+[\.\)]\s*)"""), "").trim()
        }
        .filter { it.isNotBlank() }
        .distinct()
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
