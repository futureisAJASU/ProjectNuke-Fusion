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
import com.projectnuke.fusion.llm.LiteRtLlmEngine
import com.projectnuke.fusion.model.AcceleratorMode
import com.projectnuke.fusion.model.ChatMessage
import com.projectnuke.fusion.model.GenerationSettings
import com.projectnuke.fusion.modelzoo.FusionModelCatalog
import com.projectnuke.fusion.modelzoo.FusionModelCompatibility
import com.projectnuke.fusion.modelzoo.FusionModelCompatibilityReport
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
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.content.ActivityNotFoundException
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.FileProvider
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
    Retry("?듬? ?ㅼ떆 ?앹꽦", null),
    Shorter("??吏㏐쾶", "?댁쟾 ?듬????듭떖 ?댁슜???좎??섎㈃????吏㏐퀬 媛꾧껐?섍쾶 ?ㅼ떆 ?묒꽦??二쇱꽭??"),
    MoreDetailed("???먯꽭??, "?댁쟾 ?듬????듭떖 ?댁슜???좎??섎㈃?????먯꽭????댁꽌 ?ㅻ챸??二쇱꽭??"),
    Table("?쒕줈 ?뺣━", "?댁쟾 ?듬????듭떖 ?댁슜???쒕줈 ?뺣━??二쇱꽭?? ?꾩슂??寃쎌슦 吏㏃? ?붿빟???④퍡 ?ы븿??二쇱꽭??"),
    ExpertTone("?꾨Ц媛 ??, "?댁쟾 ?듬????듭떖 ?댁슜???좎??섎㈃?????꾨Ц?곸씠怨??뺥솗???ㅼ쑝濡??ㅼ떆 ?묒꽦??二쇱꽭??")
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

private const val ChatOptionConversationSummary = "????붿빟"
private const val ChatOptionMemoryCandidateExtraction = "硫붾え由??꾨낫 異붿텧"
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
    RECOMMENDATION("recommendation", "異붿쿇??),
    NAME("name", "?대쫫??),
    LIGHTWEIGHT("lightweight", "紐⑤뜽 ?ш린 ?묒? ??),
    MEMORY_LOW("memory_low", "沅뚯옣 ?ъ뼇 ??? ??),
    LOCAL_EXECUTION("local_execution", "濡쒖뺄 ?ㅽ뻾 媛???곗꽑"),
    FAVORITES_FIRST("favorites_first", "利먭꺼李얘린 ?곗꽑");

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
    "?먯꽭???ㅻ챸??二쇱꽭??",
    "?듭떖留??붿빟??二쇱꽭??",
    "臾몄젣?먯쓣 遺꾩꽍??二쇱꽭??",
    "?쒕줈 ?뺣━??二쇱꽭??",
    "諛섎컯??二쇱꽭??"
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
        hasAttachments: Boolean = false
    ): ExternalAiChatResult {
        return externalAiChatRunner.generateFromMessages(
            messages = buildExternalAiMessages(
                messages = currentMessages,
                stripAttachments = { stripSearchSourcesMetadata(parseMessageAttachments(it).body) }
            ),
            hasAttachments = hasAttachments
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
            isGenerating -> Unit
        }
    }

    val scope = rememberCoroutineScope()
    val engine = remember { FusionRuntimeManager.sharedEngine(context) }
    DisposableEffect(engine) {
        val unregister = FusionRuntimeLock.registerChatEngineUnloadCallback {
            Log.i("FusionEngine", "Unloading chat engine for exclusive benchmark mode")
            FusionRuntimeManager.unloadSharedEngineAfterExclusive("exclusive_prepare")
        }
        onDispose {
            unregister()
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
        generationStatus?.contains("紐⑤뜽 濡쒕뵫") == true ||
            generationStatus?.contains("?대?吏 遺꾩꽍 以鍮?) == true -> FusionPulseState.ModelLoading
        generationStatus?.contains("?명꽣??寃??) == true ||
            generationStatus?.contains("寃??寃곌낵") == true -> FusionPulseState.WebSearching
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
            Toast.makeText(context, "?꾩옱 ?묐떟???앹꽦?섎뒗 以묒엯?덈떎.", Toast.LENGTH_SHORT).show()
            return
        }
        if (FusionRuntimeLock.isBenchmarkRunning) {
            Toast.makeText(context, "?꾩옱 ?묐떟???앹꽦?섎뒗 以묒엯?덈떎.", Toast.LENGTH_SHORT).show()
            return
        }

        val snapshot = messageEntities.toList()
        val targetIndex = snapshot.indexOfFirst { it.id == targetMessage.id }
        if (targetIndex <= 0 || targetMessage.role == "user") {
            Toast.makeText(context, "?듬????ㅼ떆 ?앹꽦?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
            return
        }

        val storedGroupId = responseVersionState.groupByMessageId[targetMessage.id]
        val previousUserIndex = storedGroupId
            ?.let { groupId -> snapshot.indexOfFirst { it.id == groupId && it.role == "user" } }
            ?.takeIf { it >= 0 }
            ?: (targetIndex - 1 downTo 0).firstOrNull { snapshot[it].role == "user" }
        if (previousUserIndex == null) {
            Toast.makeText(context, "?듬????ㅼ떆 ?앹꽦?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
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

            ?댁쟾 ?ъ슜???붿껌:
            $previousUserText

            ?댁쟾 ?듬?:
            $originalAssistantText

            ???듬?留??묒꽦??二쇱꽭??
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

        isGenerating = true
        regeneratingMessageId = targetMessage.id
        streamingAssistantText = null
        streamingMetricsLine = null
        generationStatus = "?듬????ㅼ떆 ?앹꽦?섎뒗 以묒엯?덈떎."
        DeveloperLogStore.record(context, "regeneration", "?듬? ?ㅼ떆 ?앹꽦 ?쒖옉", action.menuLabel)

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
                            generationStatus = "?듬? ???以?.."
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
                            DeveloperLogStore.record(context, "regeneration", "?듬? ?ㅼ떆 ?앹꽦 ?깃났", "versions=$versionCount")
                            Toast.makeText(context, "?듬????ㅼ떆 ?앹꽦?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                        }

                        is ExternalAiChatResult.BlockedAttachment -> {
                            generationStatus = "?듬? ???以?.."
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
                            generationStatus = "?듬? ???以?.."
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

                        is ExternalAiChatResult.Error -> {
                            generationStatus = "?듬? ???以?.."
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
                    DeveloperLogStore.record(context, "regeneration", "?듬? ?ㅼ떆 ?앹꽦 ?ㅽ뙣", "model file missing")
                    Toast.makeText(context, "?듬????ㅼ떆 ?앹꽦?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val missingImage = imageAttachments.firstOrNull { !File(it.localPath).exists() }
                if (missingImage != null) {
                    DeveloperLogStore.record(context, "regeneration", "?듬? ?ㅼ떆 ?앹꽦 ?ㅽ뙣", "image file missing")
                    Toast.makeText(context, "?듬????ㅼ떆 ?앹꽦?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val useMultimodalImages = imageAttachments.isNotEmpty()
                if (useMultimodalImages && !isMultimodalCapableModel(selectedModel, activeModelPath!!)) {
                    DeveloperLogStore.record(context, "regeneration", "?듬? ?ㅼ떆 ?앹꽦 ?ㅽ뙣", "multimodal unsupported")
                    Toast.makeText(context, "?듬????ㅼ떆 ?앹꽦?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val generationStartMs = SystemClock.elapsedRealtime()
                val rawReply = FusionRuntimeLock.withChatGeneration {
                    generateWithLiteRtRecovery(
                        engine = engine,
                        onBeforeRetry = {
                            generationStatus = "?듬????ㅼ떆 ?앹꽦?섎뒗 以묒엯?덈떎."
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
                DeveloperLogStore.record(context, "regeneration", "?듬? ?ㅼ떆 ?앹꽦 ?깃났", "versions=$versionCount")
                Toast.makeText(context, "?듬????ㅼ떆 ?앹꽦?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("FusionEngine", "Response regeneration failed", e)
                DeveloperLogStore.record(context, "regeneration", "?듬? ?ㅼ떆 ?앹꽦 ?ㅽ뙣", e::class.java.simpleName)
                Toast.makeText(context, "?듬????ㅼ떆 ?앹꽦?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "?꾩옱 ?묐떟???앹꽦?섎뒗 以묒엯?덈떎.", Toast.LENGTH_SHORT).show()
            return
        }
        if (FusionRuntimeLock.isBenchmarkRunning) {
            Toast.makeText(context, "?꾩옱 ?묐떟???앹꽦?섎뒗 以묒엯?덈떎.", Toast.LENGTH_SHORT).show()
            return
        }

        val snapshot = messageEntities.toList()
        if (conversationId == 0L || snapshot.isEmpty()) {
            Toast.makeText(context, "硫붾え由??꾨낫瑜?異붿텧?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "硫붾え由??꾨낫瑜?異붿텧?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
            return
        }

        val mtpEnabledForRequest = resolveSpeculativeDecodingEnabled(
            modelName = selectedModel,
            settings = generationSettings
        )
        val requestSettings = generationSettings.copy(
            speculativeDecodingEnabled = mtpEnabledForRequest
        )
        val extractionPrompt = "?꾩옱 ??붿뿉???섏쨷??李멸퀬??留뚰븳 硫붾え由??꾨낫瑜?異붿텧??二쇱꽭?? ?ъ슜?먯쓽 ?κ린?곸씤 ?좏샇, 吏꾪뻾 以묒씤 ?꾨줈?앺듃, 諛섎났?곸쑝濡?李멸퀬???ㅼ젙, 以묒슂??寃곗젙 ?ы빆留?吏㏃? bullet濡??뺣━??二쇱꽭?? ?쇱떆?곸씤 媛먯젙, ?ъ냼???〓떞, 誘쇨컧??媛쒖씤?뺣낫, 異붿륫???댁슜? ?쒖쇅??二쇱꽭??"
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
            generationStatus = "硫붾え由??꾨낫瑜?異붿텧?섎뒗 以묒엯?덈떎."
            DeveloperLogStore.record(context, "memory", "硫붾え由??꾨낫 異붿텧 ?쒖옉", "conversationId=$conversationId")

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
                                "硫붾え由??꾨낫 異붿텧 ?깃났",
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

                        is ExternalAiChatResult.Error -> {
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FusionMemory", "Memory candidate extraction failed", e)
                    DeveloperLogStore.record(context, "memory", "硫붾え由??꾨낫 異붿텧 ?ㅽ뙣", e::class.java.simpleName)
                    Toast.makeText(context, "硫붾え由??꾨낫瑜?異붿텧?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
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
            DeveloperLogStore.record(context, "memory", "硫붾え由??꾨낫 異붿텧 ?ㅽ뙣", "model file missing")
            Toast.makeText(context, "硫붾え由??꾨낫瑜?異붿텧?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
            return
        }

        isGenerating = true
        extractingMemoryCandidates = true
        generationStatus = "硫붾え由??꾨낫瑜?異붿텧?섎뒗 以묒엯?덈떎."
        DeveloperLogStore.record(context, "memory", "硫붾え由??꾨낫 異붿텧 ?쒖옉", "conversationId=$conversationId")

        scope.launch {
            try {
                val rawReply = FusionRuntimeLock.withChatGeneration {
                    generateWithLiteRtRecovery(
                        engine = engine,
                        onBeforeRetry = {
                            generationStatus = "硫붾え由??꾨낫瑜?異붿텧?섎뒗 以묒엯?덈떎."
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
                memoryCandidateText = rawReply.trim()
                DeveloperLogStore.record(
                    context,
                    "memory",
                    "硫붾え由??꾨낫 異붿텧 ?깃났",
                    "conversationId=$conversationId, length=${memoryCandidateText.length}, count=${parseMemoryCandidateLines(memoryCandidateText).size}"
                )
            } catch (e: Exception) {
                Log.e("FusionMemory", "Memory candidate extraction failed", e)
                DeveloperLogStore.record(context, "memory", "硫붾え由??꾨낫 異붿텧 ?ㅽ뙣", e::class.java.simpleName)
                Toast.makeText(context, "硫붾え由??꾨낫瑜?異붿텧?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "?듬????ㅼ떆 ?앹꽦?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "???뚯씪? ?꾩옱 吏곸젒 ?ㅽ뻾?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "紐⑤뜽 ?뚯씪 沅뚰븳???좎??????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
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

        Toast.makeText(context, "泥⑤? ?뚯씪 蹂듭궗 以?..", Toast.LENGTH_SHORT).show()

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

            Toast.makeText(context, "泥⑤? ?꾨즺", Toast.LENGTH_SHORT).show()
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
                        if (option == "?????寃??) {
                            inChatSearchMode = true
                        } else {
                            Toast.makeText(context, "$option 湲곕뒫? ?ㅼ쓬 ?④퀎?먯꽌 ?곌껐?섍쿋?듬땲??", Toast.LENGTH_SHORT).show()
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
                        pendingAttachments.remove(attachment)
                    },
                    onAppendQuickPrompt = { preset ->
                        input = appendQuickPrompt(input, preset)
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
                        Toast.makeText(context, "?뚯꽦 ?낅젰? ?ㅼ쓬 ?④퀎?먯꽌 ?곌껐?섍쿋?듬땲??", Toast.LENGTH_SHORT).show()
                    },
                    onVoiceModeClick = {
                        Toast.makeText(context, "蹂댁씠??紐⑤뱶???ㅼ쓬 ?④퀎?먯꽌 ?곌껐?섍쿋?듬땲??", Toast.LENGTH_SHORT).show()
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

                        if (userInput.isNotEmpty() || attachmentsToSend.isNotEmpty()) {
                            if (FusionRuntimeLock.isBenchmarkRunning) {
                                Toast.makeText(context, "踰ㅼ튂留덊겕媛 吏꾪뻾 以묒엯?덈떎. ?꾨즺 ???ㅼ떆 ?쒕룄??二쇱꽭??", Toast.LENGTH_SHORT).show()
                                return@sendClick
                            }

                            input = ""
                            pendingAttachments.clear()

                            isGenerating = true
                            streamingAssistantText = null
                            streamingMetricsLine = null
                            generationStatus = if (shouldUseWebSearch) "?명꽣??寃??以?.." else "紐⑤뜽 濡쒕뵫 以?.."

                            val userMessageContent = buildMessageContentWithAttachments(
                                body = userInput,
                                attachments = attachmentsToSend
                            )

                            scope.launch {
                                var activeConversationId = conversationId

                                try {
                                    val now = System.currentTimeMillis()

                                    if (activeConversationId == 0L) {
                                        val title = userInput.take(24).ifBlank { "????? }

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

                                    if (generationMode == ChatGenerationMode.EXTERNAL_AI_API && attachmentsToSend.isNotEmpty()) {
                                        val message = "?꾩옱 ?몃? AI API 紐⑤뱶?먯꽌??泥⑤? ?뚯씪???꾩넚?????놁뒿?덈떎."
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        dao.insertMessage(
                                            MessageEntity(
                                                conversationId = activeConversationId,
                                                role = "assistant",
                                                content = message,
                                                createdAt = System.currentTimeMillis()
                                            )
                                        )
                                        dao.updateConversationTime(activeConversationId, System.currentTimeMillis())
                                        return@launch
                                    }

                                    val previousUserMessage = messages
                                        .asReversed()
                                        .firstOrNull { it.role == "user" }
                                        ?.content
                                        ?.let { parseMessageAttachments(it).body }
                                        ?.trim()
                                        .orEmpty()

                                    val webSearchResponse = if (shouldUseWebSearch) {
                                        generationStatus = "?명꽣??寃??以?.."
                                        val searchIntent = FusionWebSearch.detectIntent(userInput)
                                        generationStatus = when (searchIntent) {
                                            SearchIntent.NEWS -> "?댁뒪 寃??以?.."
                                            else -> "?명꽣??寃??以?.."
                                        }

                                        val response = FusionWebSearch.search(
                                            userInput = userInput,
                                            previousUserMessage = previousUserMessage,
                                            providerRepository = webSearchProviderRepository
                                        )

                                        generationStatus = "寃??寃곌낵 ?뺣━ 以?.."
                                        response
                                    } else {
                                        null
                                    }
                                    val webSearchResult = webSearchResponse?.toStructuredContext()
                                    recordWebSearchDiagnostics(context, webSearchResponse)

                                    if (shouldUseWebSearch) {
                                        generationStatus = if (reasoningEnabled) "??源딄쾶 ?앷컖?섎뒗 以?.." else "?듬? ?앹꽦 以?.."
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

                                        add(
                                            ChatMessage(
                                                role = "system",
                                                content = fusionSystemPrompt
                                            )
                                        )

                                        if (generationMode != ChatGenerationMode.EXTERNAL_AI_API) {
                                            selectedModelPath?.let { modelPath ->
                                                add(
                                                    ChatMessage(
                                                        role = "system",
                                                        content = "FUSION_SELECTED_MODEL_PATH=$modelPath"
                                                    )
                                                )
                                            }
                                        }

                                        add(
                                            ChatMessage(
                                                role = "system",
                                                content = "FUSION_MODEL_FAMILY=${FusionModelCatalog.inferFamily(context, selectedModel).name}"
                                            )
                                        )

                                        buildSavedMemoryContext(context, settingsPrefs, activeConversationId, selectedModel).text?.let { memoryContext ->
                                            add(ChatMessage(role = "system", content = memoryContext))
                                        }

                                        buildConversationSummaryContextText(loadConversationSummary(context, activeConversationId))?.let { summaryContext ->
                                            add(ChatMessage(role = "system", content = summaryContext))
                                        }

                                        addAll(messages.map { message -> ChatMessage(role = message.role, content = if (message.role == "assistant") visibleAssistantHistoryText(message.content) else message.content) })

                                        val finalUserContent = buildFinalUserContent(
                                            body = userInstruction,
                                            attachments = if (imageAttachments.isNotEmpty()) nonImageAttachments else attachmentsToSend,
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

                                    if (generationMode == ChatGenerationMode.EXTERNAL_AI_API) {
                                        generationStatus = "?몃? AI API ?묐떟??湲곕떎由щ뒗 以?.."

                                        when (
                                            val result = runExternalAiRequest(
                                                currentMessages = currentMessages,
                                                hasAttachments = attachmentsToSend.isNotEmpty()
                                            )
                                        ) {
                                            is ExternalAiChatResult.Success -> {
                                                refreshExternalProviderState()
                                                generationStatus = "?듬? ???以?.."
                                                dao.insertMessage(
                                                    MessageEntity(
                                                        conversationId = activeConversationId,
                                                        role = "assistant",
                                                        content = appendSearchSourcesMetadata(result.content, webSearchResponse?.sources.orEmpty()),
                                                        createdAt = System.currentTimeMillis()
                                                    )
                                                )
                                                dao.updateConversationTime(activeConversationId, System.currentTimeMillis())
                                            }

                                            is ExternalAiChatResult.BlockedAttachment -> {
                                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                                generationStatus = "?듬? ???以?.."
                                                dao.insertMessage(
                                                    MessageEntity(
                                                        conversationId = activeConversationId,
                                                        role = "assistant",
                                                        content = result.message,
                                                        createdAt = System.currentTimeMillis()
                                                    )
                                                )
                                                dao.updateConversationTime(activeConversationId, System.currentTimeMillis())
                                            }

                                            is ExternalAiChatResult.NoProvider -> {
                                                refreshExternalProviderState()
                                                generationStatus = "?듬? ???以?.."
                                                dao.insertMessage(
                                                    MessageEntity(
                                                        conversationId = activeConversationId,
                                                        role = "assistant",
                                                        content = result.message,
                                                        createdAt = System.currentTimeMillis()
                                                    )
                                                )
                                                dao.updateConversationTime(activeConversationId, System.currentTimeMillis())
                                            }

                                            is ExternalAiChatResult.Error -> {
                                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                                generationStatus = "?듬? ???以?.."
                                                dao.insertMessage(
                                                    MessageEntity(
                                                        conversationId = activeConversationId,
                                                        role = "assistant",
                                                        content = result.message,
                                                        createdAt = System.currentTimeMillis()
                                                    )
                                                )
                                                dao.updateConversationTime(activeConversationId, System.currentTimeMillis())
                                            }
                                        }
                                        return@launch
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
                                        selectedModelPath?.takeIf { it.isNotBlank() }?.let {
                                            android.util.Log.e("FusionEngine", "Selected model file missing: $it")
                                        }
                                        dao.insertMessage(
                                            MessageEntity(
                                                conversationId = activeConversationId,
                                                role = "assistant",
                                                content = if (!selectedModelPath.isNullOrBlank()) {
                                                    "?좏깮??紐⑤뜽 ?뚯씪??李얠쓣 ???놁뒿?덈떎. 紐⑤뜽???ㅼ떆 ?좏깮??二쇱꽭??"
                                                } else {
                                                    "?꾩쭅 ?ъ슜??紐⑤뜽???놁뒿?덈떎. ?꾩そ 紐⑤뜽 移⑹뿉??Gemma 紐⑤뜽???ㅼ슫濡쒕뱶?섍굅??而ㅼ뒪? 紐⑤뜽???낅줈?쒗빐 二쇱꽭??"
                                                },
                                                createdAt = System.currentTimeMillis()
                                            )
                                        )

                                        dao.updateConversationTime(
                                            activeConversationId,
                                            System.currentTimeMillis()
                                        )

                                        return@launch
                                    }

                                    val missingImage = imageAttachments.firstOrNull { !File(it.localPath).exists() }
                                    if (missingImage != null) {
                                        Toast.makeText(
                                            context,
                                            "?대?吏 ?뚯씪??李얠쓣 ???놁뒿?덈떎: ${missingImage.localPath}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        dao.insertMessage(
                                            MessageEntity(
                                                conversationId = activeConversationId,
                                                role = "assistant",
                                                content = "?대?吏 ?낅젰 泥섎━ ?ㅽ뙣: ?대?吏 ?뚯씪??李얠쓣 ???놁뒿?덈떎.\n${missingImage.localPath}",
                                                createdAt = System.currentTimeMillis()
                                            )
                                        )
                                        dao.updateConversationTime(
                                            activeConversationId,
                                            System.currentTimeMillis()
                                        )
                                        return@launch
                                    }

                                    val useMultimodalImages = imageAttachments.isNotEmpty()
                                    if (useMultimodalImages && !isMultimodalCapableModel(selectedModel, activeModelPath!!)) {
                                        dao.insertMessage(
                                            MessageEntity(
                                                conversationId = activeConversationId,
                                                role = "assistant",
                                                content = "??紐⑤뜽? ?대?吏 ?낅젰??吏?먰븯吏 ?딅뒗 寃?媛숈븘.",
                                                createdAt = System.currentTimeMillis()
                                            )
                                        )
                                        dao.updateConversationTime(
                                            activeConversationId,
                                            System.currentTimeMillis()
                                        )
                                        return@launch
                                    }

                                    generationStatus = "紐⑤뜽 濡쒕뵫 以?.."

                                    val generationStartMs = SystemClock.elapsedRealtime()
                                    var firstTokenLatencyMs: Long? = null

                                    val rawReply = FusionRuntimeLock.withChatGeneration {
                                        generateWithLiteRtRecovery(
                                            engine = engine,
                                            onBeforeRetry = {
                                                generationStatus = "紐⑤뜽 濡쒕뵫 以?.."
                                                streamingAssistantText = null
                                            },
                                            generateOnce = {
                                                if (useMultimodalImages) {
                                        generationStatus = "?대?吏 遺꾩꽍 以鍮?以?.."
                                        val streamedOutput = StringBuilder()
                                        if (!reasoningEnabled) {
                                            streamingAssistantText = ""
                                        }
                                        generationStatus = "?대?吏 遺꾩꽍 以?.."

                                        engine.generateMultimodalStreaming(
                                            messages = currentMessages,
                                            modelPath = activeModelPath!!,
                                            settings = requestSettings,
                                            imagePaths = imageAttachments.map { it.localPath },
                                            onToken = { token ->
                                                if (firstTokenLatencyMs == null && token.isNotEmpty()) {
                                                    firstTokenLatencyMs = SystemClock.elapsedRealtime() - generationStartMs
                                                }
                                                streamedOutput.append(token)
                                                if (!reasoningEnabled) {
                                                    val visibleText = streamedOutput.toString()
                                                    scope.launch {
                                                        if (isGenerating) {
                                                            streamingAssistantText = visibleText
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    } else if (reasoningEnabled) {
                                        generationStatus = "??源딄쾶 ?앷컖?섎뒗 以?.."
                                        engine.generate(
                                            messages = currentMessages,
                                            modelPath = activeModelPath!!,
                                            settings = requestSettings
                                        )
                                    } else {
                                        val streamedOutput = StringBuilder()
                                        streamingAssistantText = ""
                                        generationStatus = "?듬? ?앹꽦 以?.."

                                        engine.generateStreaming(
                                            messages = currentMessages,
                                            modelPath = activeModelPath!!,
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
                                        firstTokenLatencyMs = firstTokenLatencyMs,
                                        settingsLine = buildEffectiveSettingsLine(
                                            buildEffectiveRuntimeSettings(
                                                modelName = selectedModel,
                                                modelPath = activeModelPath!!,
                                                settings = requestSettings,
                                                reasoningEnabled = reasoningEnabled,
                                                webSearchEnabled = webSearchEnabled,
                                                mtpStatus = engine.lastMtpStatus
                                            )
                                        )
                                    )
                                    streamingMetricsLine = metricsLine

                                    generationStatus = "?듬? ???以?.."

                                    dao.insertMessage(
                                        MessageEntity(
                                            conversationId = activeConversationId,
                                            role = "assistant",
                                            content = appendSearchSourcesMetadata(appendFusionMetrics(rawReply, metricsLine), webSearchResponse?.sources.orEmpty()),
                                            createdAt = System.currentTimeMillis()
                                        )
                                    )

                                    dao.updateConversationTime(
                                        activeConversationId,
                                        System.currentTimeMillis()
                                    )
                                    pendingAttachments.clear()
                                } catch (e: Exception) {
                                    Log.e("FusionEngine", "Chat generation failed", e)
                                    if (e is BenchmarkRunningException) {
                                        Toast.makeText(context, "踰ㅼ튂留덊겕媛 吏꾪뻾 以묒엯?덈떎. ?꾨즺 ???ㅼ떆 ?쒕룄??二쇱꽭??", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    if (activeConversationId != 0L) {
                                        dao.insertMessage(
                                            MessageEntity(
                                                conversationId = activeConversationId,
                                                role = "assistant",
                                                content = if (isLiteRtModelLoadException(e)) {
                                                    "紐⑤뜽??遺덈윭?????놁뒿?덈떎. 紐⑤뜽 ?ㅼ젙???뺤씤?????ㅼ떆 ?쒕룄??二쇱꽭??"
                                                } else {
                                                    "?ㅻ쪟媛 諛쒖깮?덉뒿?덈떎. ?좎떆 ???ㅼ떆 ?쒕룄??二쇱꽭??"
                                                },
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
                        text = "寃??寃곌낵",
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
                                    role = if (result.role == "user") "?ъ슜?? else "Fusion",
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
                                        "??梨꾪똿?쇰줈 媛吏移섍린 湲곕뒫? ?ㅼ쓬 ?④퀎?먯꽌 ?곌껐?섍쿋?듬땲??",
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
                                    status = generationStatus ?: "紐⑤뜽 濡쒕뵫 以?.."
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
                Toast.makeText(context, "?몃? 紐⑤뜽 ?뚯씪???곌껐?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                when {
                    !pending.permissionPersisted -> Toast.makeText(context, "紐⑤뜽 ?뚯씪 沅뚰븳???좎??????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
                    spec.availability == ModelAvailability.CUSTOM_IMPORTED -> Toast.makeText(context, "??紐⑤뜽? ?ㅽ뻾 ?꾩뿉 Fusion ?대? ??μ냼濡?蹂듭궗?댁빞 ?????덉뒿?덈떎.", Toast.LENGTH_LONG).show()
                    spec.availability == ModelAvailability.NEEDS_CONVERSION -> Toast.makeText(context, "??紐⑤뜽? 蹂?????ъ슜?????덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                    else -> Toast.makeText(context, "???뺤떇? ?꾩옱 吏곸젒 ?ㅽ뻾?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, "紐⑤뜽 ?뚯씪??媛?몄삱 ???놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
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
                        ModelAvailability.CUSTOM_IMPORTED -> Toast.makeText(context, "紐⑤뜽 ?뚯씪??媛?몄솕?듬땲??", Toast.LENGTH_SHORT).show()
                        ModelAvailability.NEEDS_CONVERSION -> Toast.makeText(context, "??紐⑤뜽? 蹂?????ъ슜?????덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                        else -> Toast.makeText(context, "???뺤떇? ?꾩옱 吏곸젒 ?ㅽ뻾?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, "??紐⑤뜽? 蹂?????ъ슜?????덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                    }
                    spec.availability != ModelAvailability.CUSTOM_IMPORTED && !spec.externallyReferenced -> {
                        Toast.makeText(context, "???뺤떇? ?꾩옱 吏곸젒 ?ㅽ뻾?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
                    }
                    spec.externallyReferenced && localPath.isNullOrBlank() -> {
                        if (spec.availability == ModelAvailability.CUSTOM_IMPORTED) {
                            Toast.makeText(context, "??紐⑤뜽? ?ㅽ뻾 ?꾩뿉 Fusion ?대? ??μ냼濡?蹂듭궗?댁빞 ?⑸땲??", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "???뚯씪? ?꾩옱 吏곸젒 ?ㅽ뻾?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
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
                    else -> Toast.makeText(context, "紐⑤뜽 ?뚯씪???묎렐?????놁뒿?덈떎. ?뚯씪???ㅼ떆 ?곌껐??二쇱꽭??", Toast.LENGTH_SHORT).show()
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
                        "?꾩쭅 ?ㅼ슫濡쒕뱶 URL???깅줉?섏? ?딆? 紐⑤뜽?댁빞",
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
                        Toast.makeText(context, "${model.name} ?ㅼ슫濡쒕뱶 ?꾨즺", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "${model.name} ?ㅼ슫濡쒕뱶 ?ㅽ뙣", Toast.LENGTH_SHORT).show()
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
                    "怨좉툒 ?ㅼ젙 ?곸슜?? ${newSettings.accelerator.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    if (showDeleteChatDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteChatDialog = false },
            title = {
                Text("梨꾪똿 ??젣")
            },
            text = {
                Text("??梨꾪똿????젣?좉퉴??")
            },
            dismissButton = {
                TextButton(onClick = { showDeleteChatDialog = false }) {
                    Text("痍⑥냼")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteChatDialog = false
                        scope.launch {
                            if (conversationId != 0L) {
                                dao.deleteConversation(conversationId)
                            }
                            input = ""
                            pendingAttachments.clear()
                            streamingAssistantText = null
                            streamingMetricsLine = null
                            generationStatus = null
                            onNewChat()
                        }
                    }
                ) {
                    Text("??젣", color = DangerRed)
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
                input = "?꾩옱 ??붿쓽 ?듭떖 ?댁슜???섏쨷??李멸퀬?????덈룄濡?媛꾧껐?섍쾶 ?붿빟??二쇱꽭?? ?ъ슜?먯쓽 ?좏샇, 吏꾪뻾 以묒씤 ?묒뾽, 以묒슂??寃곗젙 ?ы빆??以묒떖?쇰줈 ?뺣━??二쇱꽭?? 遺덊븘?뷀븳 ?〓떞? ?쒖쇅??二쇱꽭??"
                showConversationSummaryDialog = false
                Toast.makeText(context, "?붿빟 ?붿껌???낅젰李쎌뿉 異붽??덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                DeveloperLogStore.record(context, "summary", "?붿빟 ?앹꽦 ?붿껌", "conversationId=$conversationId")
            },
            onEdit = {
                conversationSummaryDraft = conversationSummary?.summary.orEmpty()
                showConversationSummaryEditor = true
            },
            onCopy = {
                val text = conversationSummary?.summary.orEmpty()
                if (text.isBlank()) {
                    Toast.makeText(context, "??λ맂 ?붿빟???놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
                } else {
                    clipboardManager.setText(AnnotatedString(text))
                    Toast.makeText(context, "?붿빟??蹂듭궗?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "蹂듭궗??硫붾え由??꾨낫媛 ?놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
                } else {
                    clipboardManager.setText(AnnotatedString(text))
                    Toast.makeText(context, "硫붾え由??꾨낫瑜?蹂듭궗?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                }
            },
            onSaveSelected = { selectedItems ->
                val savedCount = saveConversationMemoryCandidates(context, conversationId, selectedItems)
                if (savedCount <= 0) {
                    Toast.makeText(context, "??ν븷 硫붾え由??꾨낫媛 ?놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "硫붾え由??꾨낫瑜???ν뻽?듬땲??", Toast.LENGTH_SHORT).show()
                    DeveloperLogStore.record(context, "memory", "硫붾え由??꾨낫 ???, "conversationId=$conversationId, count=$savedCount")
                }
            },
            onSaveAll = {
                val savedCount = saveConversationMemoryCandidates(context, conversationId, parseMemoryCandidateLines(memoryCandidateText))
                if (savedCount <= 0) {
                    Toast.makeText(context, "??ν븷 硫붾え由??꾨낫媛 ?놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "硫붾え由??꾨낫瑜???ν뻽?듬땲??", Toast.LENGTH_SHORT).show()
                    DeveloperLogStore.record(context, "memory", "硫붾え由??꾨낫 ???, "conversationId=$conversationId, count=$savedCount")
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
                    Toast.makeText(context, "??붽? ??λ맂 ???ъ슜?????덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                } else {
                    conversationSummaryRefreshKey++
                    showConversationSummaryEditor = false
                    Toast.makeText(context, "????붿빟????ν뻽?듬땲??", Toast.LENGTH_SHORT).show()
                    DeveloperLogStore.record(context, "summary", "????붿빟 ???, "conversationId=$conversationId, length=${saved.summary.length}")
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
            title = { Text("????붿빟????젣?섏떆寃좎뒿?덇퉴?") },
            text = { Text("??λ맂 ?붿빟留???젣?섎ŉ 梨꾪똿 湲곕줉? ??젣?섏? ?딆뒿?덈떎.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteConversationSummary(context, conversationId)
                    conversationSummaryRefreshKey++
                    showConversationSummaryDeleteConfirm = false
                    Toast.makeText(context, "????붿빟????젣?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                    DeveloperLogStore.record(context, "summary", "????붿빟 ??젣", "conversationId=$conversationId")
                }) {
                    Text("??젣", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConversationSummaryDeleteConfirm = false }) {
                    Text("痍⑥냼")
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
        title = { Text("????붿빟") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "?꾩옱 ??붿쓽 ?듭떖 ?댁슜????ν빐 湲???붿뿉?쒕룄 留λ씫???좎??⑸땲??",
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
                            Text("??λ맂 ?붿빟???놁뒿?덈떎.", color = TextSecondary, fontSize = 14.sp)
                        } else {
                            updatedAtText?.let {
                                Text("?낅뜲?댄듃: $it", color = TextSecondary, fontSize = 12.sp)
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
                TextButton(onClick = onGenerate) { Text("?붿빟 ?앹꽦", color = AccentBlue) }
                TextButton(onClick = onEdit) { Text("吏곸젒 ?몄쭛", color = AccentBlue) }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onCopy, enabled = summaryMemory != null) { Text("?붿빟 蹂듭궗", color = AccentBlue) }
                TextButton(onClick = onDelete, enabled = summaryMemory != null) { Text("?붿빟 ??젣", color = DangerRed) }
                TextButton(onClick = onDismiss) { Text("?リ린", color = TextSecondary) }
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
        title = { Text("????붿빟 ?몄쭛") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                placeholder = { Text("????붿뿉??怨꾩냽 李멸퀬???듭떖 ?댁슜???낅젰??二쇱꽭??") },
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
                Text("???, color = AccentBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("痍⑥냼", color = TextSecondary)
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
        title = { Text("硫붾え由??꾨낫 異붿텧") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "????붿뿉???섏쨷??李멸퀬??留뚰븳 ?뺣낫瑜??뺣━?⑸땲??",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Text(
                    text = "?먮룞 ??ν븯吏 ?딆뒿?덈떎. ?꾩슂????ぉ留??뺤씤??二쇱꽭??",
                    color = AccentBlue,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                if (savedCount > 0) {
                    Text(
                        text = "??λ맂 ?꾨낫 ${savedCount}媛?,
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
                                Text("硫붾え由??꾨낫瑜?異붿텧?섎뒗 以묒엯?덈떎.", color = TextPrimary, fontSize = 13.sp)
                            }
                        } else if (candidateText.isBlank()) {
                            Text("異붿텧??硫붾え由??꾨낫媛 ?놁뒿?덈떎.", color = TextSecondary, fontSize = 14.sp)
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
                TextButton(onClick = onExtract, enabled = !isExtracting) { Text("?꾨낫 異붿텧", color = AccentBlue) }
                TextButton(onClick = { onSaveSelected(selectedCandidates.toList()) }, enabled = parsedCandidates.isNotEmpty() && !isExtracting) {
                    Text("?좏깮 ??ぉ ???, color = AccentBlue, maxLines = 1)
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSaveAll, enabled = parsedCandidates.isNotEmpty() && !isExtracting) {
                    Text("?꾩껜 ???, color = AccentBlue)
                }
                TextButton(onClick = onCopy, enabled = candidateText.isNotBlank() && !isExtracting) {
                    Text("?꾨낫 蹂듭궗", color = AccentBlue)
                }
                TextButton(onClick = onDismiss) { Text("?リ린", color = TextSecondary) }
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
                text = "??,
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
                contentDescription = "??梨꾪똿",
                onClick = onComposeClick
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                CircleTextButton(
                    text = "??,
                    onClick = onChatMenuClick
                )

                DropdownMenu(
                    expanded = chatMenuExpanded,
                    onDismissRequest = onDismissChatMenu,
                    containerColor = MenuBg
                ) {
                    DropdownMenuItem(
                        text = { Text("梨꾪똿 怨좎젙", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onChatOption("梨꾪똿 怨좎젙") }
                    )
                    DropdownMenuItem(
                        text = { Text("???꾨줈?앺듃", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onChatOption("???꾨줈?앺듃") }
                    )
                    DropdownMenuItem(
                        text = { Text("?꾨줈?앺듃??異붽?", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onChatOption("?꾨줈?앺듃??異붽?") }
                    )
                    DropdownMenuItem(
                        text = { Text("?낅줈?쒗븳 ?뚯씪", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onChatOption("?낅줈?쒗븳 ?뚯씪") }
                    )
                    DropdownMenuItem(
                        text = { Text("???붾㈃??異붽?", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onChatOption("???붾㈃??異붽?") }
                    )
                    DropdownMenuItem(
                        text = { Text("?꾩뭅?대툕??蹂닿?", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onChatOption("?꾩뭅?대툕??蹂닿?") }
                    )
                    DropdownMenuItem(
                        text = { Text("????붿빟", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onChatOption(ChatOptionConversationSummary) }
                    )
                    DropdownMenuItem(
                        text = { Text("硫붾え由??꾨낫 異붿텧", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onChatOption(ChatOptionMemoryCandidateExtraction) }
                    )
                    DropdownMenuItem(
                        text = { Text("??젣", color = DangerRed, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
    }.joinToString(" 쨌 ")
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
                text = "??,
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
            text = "??梨꾪똿???쒖옉?대낫?몄슂.",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "紐⑤뜽怨?紐⑤뱶???꾩そ 移⑹뿉??諛붽? ???덉뒿?덈떎.",
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
                text = "?????寃??,
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
                        Text("硫붿떆吏瑜?寃?됲빀?덈떎.", color = TextSecondary, fontSize = 14.sp)
                    }
                    inner()
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "?リ린",
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
                text = preview.ifBlank { "(?댁슜 ?놁쓬)" },
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
            text = "寃??寃곌낵媛 ?놁뒿?덈떎.",
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
                text = "?듬????ㅼ떆 ?앹꽦?섎뒗 以묒엯?덈떎.",
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
                    contentDescription = "?댁쟾 ?듬?",
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
                    contentDescription = "?ㅼ쓬 ?듬?",
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
                contentDescription = "蹂듭궗",
                onClick = {
                    clipboardManager.setText(AnnotatedString(parsed.answer))
                    Toast.makeText(context, "蹂듭궗?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                }
            )

            ActionIcon(
                icon = Icons.Rounded.VolumeUp,
                contentDescription = "?뚯꽦?쇰줈 ?쎄린",
                onClick = {
                    Toast.makeText(context, "?뚯꽦 ?쎄린???ㅼ쓬 ?④퀎?먯꽌 ?곌껐?섍쿋?듬땲??", Toast.LENGTH_SHORT).show()
                }
            )

            Box {
                ActionIcon(
                    icon = Icons.Rounded.MoreVert,
                    contentDescription = "?붾낫湲?,
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
        SourceCapsule("異쒖쿂 ${sources.size}媛?, onClick)
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
        title = { Text("異쒖쿂", color = TextPrimary) },
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
                            SourceDetailLine("寃???쒓났??, source.providerDisplayName)
                            SourceDetailLine("寃?됱뼱", source.queryUsed)
                            SourceDetailLine("?먮즺 異쒖쿂", source.source.orEmpty())
                            SourceDetailLine("?쒕ぉ", source.title)
                            source.publishedAt?.takeIf { it.isNotBlank() }?.let { SourceDetailLine("寃뚯떆??, it) }
                            source.snippet?.takeIf { it.isNotBlank() }?.let { SourceDetailLine("?붿빟", it) }
                            source.url?.takeIf { it.isNotBlank() }?.let { url ->
                                TextButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }.onFailure {
                                            Toast.makeText(context, "?먮Ц???????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text("?먮Ц ?닿린", color = AccentBlue)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("?リ린", color = AccentBlue)
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
                    text = if (expanded) "?앷컖 怨쇱젙 ?묎린" else "?앷컖 怨쇱젙 蹂닿린",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = if (expanded) "?? else "??,
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
        SimpleDateFormat("?ㅻ뒛, a h:mm", Locale.KOREAN)
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
            text = { Text("?? ??梨꾪똿?쇰줈 媛吏移섍린", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            onClick = onBranch
        )

        DropdownMenuItem(
            text = { Text("$selectedModel ?ъ슜??, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            onClick = {},
            enabled = false
        )

        DropdownMenuItem(
            text = { Text("?듬? ?ㅼ떆 ?앹꽦", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            onClick = onRetry,
            enabled = !isRegenerating
        )

        DropdownMenuItem(
            text = { Text("??吏㏐쾶", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            onClick = { onRegenerate(ResponseRegenerationAction.Shorter) },
            enabled = !isRegenerating
        )

        DropdownMenuItem(
            text = { Text("???먯꽭??, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            onClick = { onRegenerate(ResponseRegenerationAction.MoreDetailed) },
            enabled = !isRegenerating
        )

        DropdownMenuItem(
            text = { Text("?쒕줈 ?뺣━", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            onClick = { onRegenerate(ResponseRegenerationAction.Table) },
            enabled = !isRegenerating
        )

        DropdownMenuItem(
            text = { Text("?꾨Ц媛 ??, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            onClick = { onRegenerate(ResponseRegenerationAction.ExpertTone) },
            enabled = !isRegenerating
        )

        DropdownMenuItem(
            text = {
                Text(
                    text = if (webSearchEnabled) "?뙋  ??寃???꾧린" else "?뙋  ??寃??,
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
                        text = if (quickPromptExpanded) "鍮좊Ⅸ ?낅젰 ?リ린" else "鍮좊Ⅸ ?낅젰",
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
                                        text = "?낅젰?섏뿬 梨꾪똿",
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
                                    contentDescription = "?뚯꽦 ?낅젰",
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
                                            text = "??,
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
                                        text = if (isGenerating) "?? else "??,
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
                    text = "횞",
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
        mimeType.startsWith("image/") -> "?뼹"
        mimeType == "application/pdf" -> "?뱞"
        mimeType.startsWith("text/") -> "?뱷"
        else -> "?뱨"
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
        "???대?吏瑜??먯꽭???ㅻ챸??二쇱꽭??"
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
    var activeStatusFilter by remember { mutableStateOf("?꾩껜") }
    var activeFamilyFilter by remember { mutableStateOf("?꾩껜") }
    var modelViewMode by remember { mutableStateOf("?꾩껜 紐⑤뜽") }
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
            "?④릿 紐⑤뜽" -> catalogModels.filter { it.id in hiddenModelIds }
            else -> visibleCatalogModels
        }
    }
    val statusFilteredModels = remember(baseModels, activeStatusFilter, context, favoriteModelIds) {
        when (activeStatusFilter) {
            "?꾩껜" -> baseModels
            "利먭꺼李얘린" -> baseModels.filter { it.id in favoriteModelIds }
            "濡쒖뺄 媛?? -> baseModels.filter { isCatalogModelAvailable(context, it) || it.availability == ModelAvailability.CUSTOM_IMPORTED }
            "蹂???꾩슂" -> baseModels.filter { it.availability == ModelAvailability.NEEDS_CONVERSION || it.availability == ModelAvailability.UNSUPPORTED_ON_DEVICE }
            "?먭꺽 ?꾩슜" -> baseModels.filter { it.availability == ModelAvailability.REMOTE_ONLY }
            "8GB 沅뚯옣" -> baseModels.filter { (it.recommendedRamGb ?: 0) <= 8 && (it.recommendedRamGb ?: 0) > 0 }
            "?④릿 紐⑤뜽" -> baseModels
            else -> baseModels
        }
    }
    val familyFilteredModels = remember(statusFilteredModels, activeFamilyFilter) {
        if (activeFamilyFilter == "?꾩껜") statusFilteredModels
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
    val recommendedBest = remember(recommendedEvaluations) { recommendedEvaluations.filter { it.includedInRecommendedLocal && it.tier == "沅뚯옣" } }
    val recommendedExperimental = remember(recommendedEvaluations) { recommendedEvaluations.filter { it.includedInRecommendedLocal && it.tier == "?ㅽ뿕 媛?? } }
    val recommendedTop = remember(recommendedBest, recommendedExperimental) { recommendedBest + recommendedExperimental }
    val recommendedCaution = remember(recommendedEvaluations) { recommendedEvaluations.filter { it.includedInRecommendedLocal && it.tier == "二쇱쓽 ?꾩슂" } }
    val recommendedNot = remember(recommendedEvaluations) { recommendedEvaluations.filter { it.tier == "沅뚯옣?섏? ?딆쓬" || it.tier == "?먭꺽 ?꾩슜" } }
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
        searchQuery.isBlank() && activeStatusFilter == "?꾩껜" && activeFamilyFilter == "?꾩껜" && modelViewMode == "?꾩껜 紐⑤뜽" && recentModels.isNotEmpty()
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
            Text("紐⑤뜽 ?쇱씠釉뚮윭由?)
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
                        text = "?ъ슜??紐⑤뜽???좏깮?섍굅????紐⑤뜽??媛?몄샃?덈떎.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FusionTextButton(onClick = onLinkExternalModel) { Text("?몃? 紐⑤뜽 ?뚯씪 ?곌껐", fontSize = 13.sp) }
                        FusionTextButton(onClick = onOpenStorageManager) { Text("紐⑤뜽 ??κ났媛?, fontSize = 13.sp) }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("?꾩껜 紐⑤뜽", "??湲곌린??異붿쿇").forEach { label ->
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
                                Text("?뺣젹: ${sortMode.label}", color = TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        placeholder = { Text("紐⑤뜽??寃?됲빀?덈떎.", color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                        listOf("?꾩껜", "利먭꺼李얘린", "濡쒖뺄 媛??, "蹂???꾩슂", "?먭꺽 ?꾩슜", "8GB 沅뚯옣", "?④릿 紐⑤뜽").forEach { label ->
                            CompactFilterChip(
                                label = label,
                                selected = activeStatusFilter == label,
                                onClick = {
                                    activeStatusFilter = label
                                    if (label == "?꾩껜") activeFamilyFilter = "?꾩껜"
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
                            "?꾩껜" to "?꾩껜",
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
                            Text("?④릿 紐⑤뜽 ?쒖떆", color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (showHiddenModels) "耳쒖쭚" else "爰쇱쭚", color = AccentBlue, fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    selectedHiddenSpec?.let { selectedSpec ->
                        if (!showHiddenModels) {
                            ModelZooSection(
                                title = "?꾩옱 ?ъ슜 以?,
                                specs = listOf(selectedSpec),
                                currentModel = currentModel,
                                onSelect = { spec ->
                                    val model = LocalModel(spec.displayName, spec.fileName ?: spec.displayName, customPath = spec.localPath, downloadUrl = spec.downloadUrl)
                                    onSelect(model)
                                },
                                onUploadCustomModel = onUploadCustomModel,
                                onApplyRecommendedSettings = { spec ->
                                    applyDeviceAwareRecommendedSettings(context, prefs.edit(), spec)
                                    Toast.makeText(context, "沅뚯옣 ?ㅼ젙???곸슜?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                                },
                                favoriteModelIds = favoriteModelIds,
                                hiddenModelIds = hiddenModelIds,
                                showHiddenBadge = true,
                                onToggleFavorite = { spec ->
                                    val next = if (spec.id in favoriteModelIds) favoriteModelIds - spec.id else favoriteModelIds + spec.id
                                    persistFavorite(next)
                                    Toast.makeText(context, if (spec.id in favoriteModelIds) "利먭꺼李얘린?먯꽌 ?쒓굅?덉뒿?덈떎." else "利먭꺼李얘린??異붽??덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                                },
                                onHideModel = { spec ->
                                    persistHidden(hiddenModelIds + spec.id)
                                    Toast.makeText(context, "紐⑤뜽???④꼈?듬땲??", Toast.LENGTH_SHORT).show()
                                },
                                onUnhideModel = { spec ->
                                    persistHidden(hiddenModelIds - spec.id)
                                    Toast.makeText(context, "紐⑤뜽 ?④????댁젣?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
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
                                text = "理쒓렐 ?ъ슜 紐⑤뜽",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            FusionTextButton(onClick = { showClearRecentConfirm = true }) {
                                Text("理쒓렐 ?ъ슜 湲곕줉 吏?곌린", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        ModelZooSection(
                            title = "理쒓렐 ?ъ슜 紐⑤뜽",
                            specs = sortModelSpecs(recentSpecs, sortMode, currentModel, favoriteModelIds),
                            currentModel = currentModel,
                            onSelect = { spec ->
                                val model = LocalModel(spec.displayName, spec.fileName ?: spec.displayName, customPath = spec.localPath, downloadUrl = spec.downloadUrl)
                                onSelect(model)
                            },
                            onUploadCustomModel = onUploadCustomModel,
                            onApplyRecommendedSettings = { spec ->
                                applyDeviceAwareRecommendedSettings(context, prefs.edit(), spec)
                                Toast.makeText(context, "沅뚯옣 ?ㅼ젙???곸슜?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                            },
                            favoriteModelIds = favoriteModelIds,
                            hiddenModelIds = hiddenModelIds,
                            showHiddenBadge = showHiddenModels,
                            onToggleFavorite = { spec ->
                                val next = if (spec.id in favoriteModelIds) favoriteModelIds - spec.id else favoriteModelIds + spec.id
                                persistFavorite(next)
                                Toast.makeText(context, if (spec.id in favoriteModelIds) "利먭꺼李얘린?먯꽌 ?쒓굅?덉뒿?덈떎." else "利먭꺼李얘린??異붽??덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                            },
                            onHideModel = { spec ->
                                persistHidden(hiddenModelIds + spec.id)
                                Toast.makeText(context, "紐⑤뜽???④꼈?듬땲??", Toast.LENGTH_SHORT).show()
                            },
                            onUnhideModel = { spec ->
                                persistHidden(hiddenModelIds - spec.id)
                                Toast.makeText(context, "紐⑤뜽 ?④????댁젣?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                            }
                        )
                        val latestRecent = recentModels.firstOrNull()?.second
                        latestRecent?.let {
                            Text(formatRecentUsedLabel(it.lastUsedAt), color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    if (modelViewMode == "??湲곌린??異붿쿇") {
                        Surface(shape = RoundedCornerShape(10.dp), color = PanelBg, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("湲곌린 硫붾え由? ??${formatGb(deviceSummary.first)}GB", color = TextSecondary, fontSize = 12.sp)
                                Text("?꾩옱 ?ъ슜 媛?? ??${formatGb(deviceSummary.second)}GB", color = TextSecondary, fontSize = 12.sp)
                                if (deviceSummary.third) Text("?硫붾え由?紐⑤뱶瑜?沅뚯옣?⑸땲??", color = DangerRed, fontSize = 12.sp)
                            }
                        }
                        val selectedEval = recommendedEvaluations.firstOrNull { it.spec.displayName == currentModel && it.tier !in listOf("沅뚯옣", "?ㅽ뿕 媛??) }
                        selectedEval?.let {
                            ModelZooSection(
                                title = "?꾩옱 ?ъ슜 以?,
                                specs = listOf(it.spec),
                                currentModel = currentModel,
                                onSelect = { spec -> onSelect(LocalModel(spec.displayName, spec.fileName ?: spec.displayName, customPath = spec.localPath, downloadUrl = spec.downloadUrl)) },
                                onUploadCustomModel = onUploadCustomModel,
                                onApplyRecommendedSettings = { spec -> applyDeviceAwareRecommendedSettings(context, prefs.edit(), spec); Toast.makeText(context, "沅뚯옣 ?ㅼ젙???곸슜?덉뒿?덈떎.", Toast.LENGTH_SHORT).show() },
                                favoriteModelIds = favoriteModelIds,
                                hiddenModelIds = hiddenModelIds,
                                showHiddenBadge = showHiddenModels,
                                onToggleFavorite = { spec -> val next = if (spec.id in favoriteModelIds) favoriteModelIds - spec.id else favoriteModelIds + spec.id; persistFavorite(next); Toast.makeText(context, if (spec.id in favoriteModelIds) "利먭꺼李얘린?먯꽌 ?쒓굅?덉뒿?덈떎." else "利먭꺼李얘린??異붽??덉뒿?덈떎.", Toast.LENGTH_SHORT).show() },
                                onHideModel = { spec -> persistHidden(hiddenModelIds + spec.id); Toast.makeText(context, "紐⑤뜽???④꼈?듬땲??", Toast.LENGTH_SHORT).show() },
                                onUnhideModel = { spec -> persistHidden(hiddenModelIds - spec.id); Toast.makeText(context, "紐⑤뜽 ?④????댁젣?덉뒿?덈떎.", Toast.LENGTH_SHORT).show() },
                                recommendationMap = recommendedEvaluations.associate { ev -> ev.spec.id to ev },
                                modelNotes = modelNotes,
                                onSaveModelNote = ::persistModelNote,
                                benchmarkSummaryByModelId = benchmarkSummaryByModelId,
                                onOpenBenchmark = onOpenBenchmark
                            )
                            Text("?꾩옱 ?좏깮??紐⑤뜽? ??湲곌린?먯꽌 ?덉젙?곸쑝濡??ㅽ뻾?섏? ?딆쓣 ???덉뒿?덈떎.", color = DangerRed, fontSize = 12.sp)
                        }
                        if (recommendedBest.isNotEmpty()) {
                            ModelZooSection(
                                title = "沅뚯옣 紐⑤뜽",
                                specs = sortedRecommendedBestSpecs,
                                currentModel = currentModel,
                                onSelect = { spec -> onSelect(LocalModel(spec.displayName, spec.fileName ?: spec.displayName, customPath = spec.localPath, downloadUrl = spec.downloadUrl)) },
                                onUploadCustomModel = onUploadCustomModel,
                                onApplyRecommendedSettings = { spec -> applyDeviceAwareRecommendedSettings(context, prefs.edit(), spec); Toast.makeText(context, "沅뚯옣 ?ㅼ젙???곸슜?덉뒿?덈떎.", Toast.LENGTH_SHORT).show() },
                                favoriteModelIds = favoriteModelIds,
                                hiddenModelIds = hiddenModelIds,
                                showHiddenBadge = showHiddenModels,
                                onToggleFavorite = { spec -> val next = if (spec.id in favoriteModelIds) favoriteModelIds - spec.id else favoriteModelIds + spec.id; persistFavorite(next); Toast.makeText(context, if (spec.id in favoriteModelIds) "利먭꺼李얘린?먯꽌 ?쒓굅?덉뒿?덈떎." else "利먭꺼李얘린??異붽??덉뒿?덈떎.", Toast.LENGTH_SHORT).show() },
                                onHideModel = { spec -> persistHidden(hiddenModelIds + spec.id); Toast.makeText(context, "紐⑤뜽???④꼈?듬땲??", Toast.LENGTH_SHORT).show() },
                                onUnhideModel = { spec -> persistHidden(hiddenModelIds - spec.id); Toast.makeText(context, "紐⑤뜽 ?④????댁젣?덉뒿?덈떎.", Toast.LENGTH_SHORT).show() },
                                recommendationMap = recommendedEvaluationMap,
                                modelNotes = modelNotes,
                                onSaveModelNote = ::persistModelNote,
                                benchmarkSummaryByModelId = benchmarkSummaryByModelId,
                                onOpenBenchmark = onOpenBenchmark
                            )
                        }
                        if (recommendedExperimental.isNotEmpty()) {
                            ModelZooSection(
                                title = "?ㅽ뿕 媛?ν븳 紐⑤뜽",
                                specs = sortedRecommendedExperimentalSpecs,
                                currentModel = currentModel,
                                onSelect = { spec -> onSelect(LocalModel(spec.displayName, spec.fileName ?: spec.displayName, customPath = spec.localPath, downloadUrl = spec.downloadUrl)) },
                                onUploadCustomModel = onUploadCustomModel,
                                onApplyRecommendedSettings = { spec -> applyDeviceAwareRecommendedSettings(context, prefs.edit(), spec); Toast.makeText(context, "沅뚯옣 ?ㅼ젙???곸슜?덉뒿?덈떎.", Toast.LENGTH_SHORT).show() },
                                favoriteModelIds = favoriteModelIds,
                                hiddenModelIds = hiddenModelIds,
                                showHiddenBadge = showHiddenModels,
                                onToggleFavorite = { spec -> val next = if (spec.id in favoriteModelIds) favoriteModelIds - spec.id else favoriteModelIds + spec.id; persistFavorite(next); Toast.makeText(context, if (spec.id in favoriteModelIds) "利먭꺼李얘린?먯꽌 ?쒓굅?덉뒿?덈떎." else "利먭꺼李얘린??異붽??덉뒿?덈떎.", Toast.LENGTH_SHORT).show() },
                                onHideModel = { spec -> persistHidden(hiddenModelIds + spec.id); Toast.makeText(context, "紐⑤뜽???④꼈?듬땲??", Toast.LENGTH_SHORT).show() },
                                onUnhideModel = { spec -> persistHidden(hiddenModelIds - spec.id); Toast.makeText(context, "紐⑤뜽 ?④????댁젣?덉뒿?덈떎.", Toast.LENGTH_SHORT).show() },
                                recommendationMap = recommendedEvaluationMap,
                                modelNotes = modelNotes,
                                onSaveModelNote = ::persistModelNote,
                                benchmarkSummaryByModelId = benchmarkSummaryByModelId,
                                onOpenBenchmark = onOpenBenchmark
                            )
                        }
                        if (recommendedCaution.isNotEmpty()) {
                            ModelZooSection(
                                title = "二쇱쓽媛 ?꾩슂??紐⑤뜽",
                                specs = sortedRecommendedCautionSpecs,
                                currentModel = currentModel,
                                onSelect = { spec -> onSelect(LocalModel(spec.displayName, spec.fileName ?: spec.displayName, customPath = spec.localPath, downloadUrl = spec.downloadUrl)) },
                                onUploadCustomModel = onUploadCustomModel,
                                onApplyRecommendedSettings = { spec -> applyDeviceAwareRecommendedSettings(context, prefs.edit(), spec); Toast.makeText(context, "沅뚯옣 ?ㅼ젙???곸슜?덉뒿?덈떎.", Toast.LENGTH_SHORT).show() },
                                favoriteModelIds = favoriteModelIds,
                                hiddenModelIds = hiddenModelIds,
                                showHiddenBadge = showHiddenModels,
                                onToggleFavorite = { spec -> val next = if (spec.id in favoriteModelIds) favoriteModelIds - spec.id else favoriteModelIds + spec.id; persistFavorite(next); Toast.makeText(context, if (spec.id in favoriteModelIds) "利먭꺼李얘린?먯꽌 ?쒓굅?덉뒿?덈떎." else "利먭꺼李얘린??異붽??덉뒿?덈떎.", Toast.LENGTH_SHORT).show() },
                                onHideModel = { spec -> persistHidden(hiddenModelIds + spec.id); Toast.makeText(context, "紐⑤뜽???④꼈?듬땲??", Toast.LENGTH_SHORT).show() },
                                onUnhideModel = { spec -> persistHidden(hiddenModelIds - spec.id); Toast.makeText(context, "紐⑤뜽 ?④????댁젣?덉뒿?덈떎.", Toast.LENGTH_SHORT).show() },
                                recommendationMap = recommendedEvaluationMap,
                                modelNotes = modelNotes,
                                onSaveModelNote = ::persistModelNote,
                                benchmarkSummaryByModelId = benchmarkSummaryByModelId,
                                onOpenBenchmark = onOpenBenchmark
                            )
                        }
                        if (recommendedTop.isEmpty() && recommendedCaution.isEmpty()) {
                            Text("?꾩옱 湲곌린??異붿쿇?????덈뒗 濡쒖뺄 紐⑤뜽???놁뒿?덈떎.", color = TextSecondary, fontSize = 13.sp)
                            Text("?꾩껜 紐⑤뜽?먯꽌 蹂???꾩슂 ?먮뒗 ?먭꺽 紐⑤뜽???뺤씤??二쇱꽭??", color = TextSecondary, fontSize = 12.sp)
                        }
                        FusionTextButton(onClick = { activeStatusFilter = "?꾩껜"; activeFamilyFilter = "?꾩껜"; modelViewMode = "?꾩껜 紐⑤뜽" }) {
                            Text("?꾩껜 紐⑤뜽濡?蹂닿린", fontSize = 12.sp)
                        }
                    } else if (favoriteVisibleSpecs.isNotEmpty() && activeStatusFilter == "?꾩껜") {
                        ModelZooSection(
                            title = "利먭꺼李얘린",
                            specs = sortedFavoriteVisibleSpecs.filterNot { it.id in recentSpecIds },
                            currentModel = currentModel,
                            onSelect = { spec ->
                                val model = LocalModel(spec.displayName, spec.fileName ?: spec.displayName, customPath = spec.localPath, downloadUrl = spec.downloadUrl)
                                onSelect(model)
                            },
                            onUploadCustomModel = onUploadCustomModel,
                            onApplyRecommendedSettings = { spec ->
                                applyDeviceAwareRecommendedSettings(context, prefs.edit(), spec)
                                Toast.makeText(context, "沅뚯옣 ?ㅼ젙???곸슜?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                            },
                            favoriteModelIds = favoriteModelIds,
                            hiddenModelIds = hiddenModelIds,
                            showHiddenBadge = showHiddenModels,
                            onToggleFavorite = { spec ->
                                val next = if (spec.id in favoriteModelIds) favoriteModelIds - spec.id else favoriteModelIds + spec.id
                                persistFavorite(next)
                                Toast.makeText(context, if (spec.id in favoriteModelIds) "利먭꺼李얘린?먯꽌 ?쒓굅?덉뒿?덈떎." else "利먭꺼李얘린??異붽??덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                            },
                            onHideModel = { spec ->
                                persistHidden(hiddenModelIds + spec.id)
                                Toast.makeText(context, "紐⑤뜽???④꼈?듬땲??", Toast.LENGTH_SHORT).show()
                            },
                            onUnhideModel = { spec ->
                                persistHidden(hiddenModelIds - spec.id)
                                Toast.makeText(context, "紐⑤뜽 ?④????댁젣?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                            },
                            modelNotes = modelNotes,
                            onSaveModelNote = ::persistModelNote,
                            benchmarkSummaryByModelId = benchmarkSummaryByModelId,
                            onOpenBenchmark = onOpenBenchmark
                        )
                    }

                    if (modelViewMode != "??湲곌린??異붿쿇") {
                    ModelZooSection(
                        title = "?ъ슜 媛?ν븳 紐⑤뜽",
                        specs = sortModelSpecs(filteredCatalogModels.filter { isCatalogModelAvailable(context, it) && (activeStatusFilter != "?꾩껜" || it.id !in favoriteModelIds) && (it.id !in recentSpecIds || !showRecentSection) }, sortMode, currentModel, favoriteModelIds),
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
                            Toast.makeText(context, "沅뚯옣 ?ㅼ젙???곸슜?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                        },
                        favoriteModelIds = favoriteModelIds,
                        hiddenModelIds = hiddenModelIds,
                        showHiddenBadge = showHiddenModels,
                        onToggleFavorite = { spec ->
                            val next = if (spec.id in favoriteModelIds) favoriteModelIds - spec.id else favoriteModelIds + spec.id
                            persistFavorite(next)
                            Toast.makeText(context, if (spec.id in favoriteModelIds) "利먭꺼李얘린?먯꽌 ?쒓굅?덉뒿?덈떎." else "利먭꺼李얘린??異붽??덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                        },
                        onHideModel = { spec ->
                            persistHidden(hiddenModelIds + spec.id)
                            Toast.makeText(context, "紐⑤뜽???④꼈?듬땲??", Toast.LENGTH_SHORT).show()
                        },
                        onUnhideModel = { spec ->
                            persistHidden(hiddenModelIds - spec.id)
                            Toast.makeText(context, "紐⑤뜽 ?④????댁젣?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                        },
                        recommendationMap = emptyMap(),
                        modelNotes = modelNotes,
                        onSaveModelNote = ::persistModelNote,
                        benchmarkSummaryByModelId = benchmarkSummaryByModelId,
                        onOpenBenchmark = onOpenBenchmark
                    )
                    ModelZooSection(
                        title = "蹂?섏씠 ?꾩슂??紐⑤뜽",
                        specs = sortModelSpecs(filteredCatalogModels.filter { (it.availability == ModelAvailability.NEEDS_CONVERSION || it.availability == ModelAvailability.UNSUPPORTED_ON_DEVICE) && (activeStatusFilter != "?꾩껜" || it.id !in favoriteModelIds) && (it.id !in recentSpecIds || !showRecentSection) }, sortMode, currentModel, favoriteModelIds),
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
                            Toast.makeText(context, if (spec.id in favoriteModelIds) "利먭꺼李얘린?먯꽌 ?쒓굅?덉뒿?덈떎." else "利먭꺼李얘린??異붽??덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                        },
                        onHideModel = { spec ->
                            persistHidden(hiddenModelIds + spec.id)
                            Toast.makeText(context, "紐⑤뜽???④꼈?듬땲??", Toast.LENGTH_SHORT).show()
                        },
                        onUnhideModel = { spec ->
                            persistHidden(hiddenModelIds - spec.id)
                            Toast.makeText(context, "紐⑤뜽 ?④????댁젣?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                        },
                        modelNotes = modelNotes,
                        onSaveModelNote = ::persistModelNote,
                        benchmarkSummaryByModelId = benchmarkSummaryByModelId,
                        onOpenBenchmark = onOpenBenchmark
                    )
                    ModelZooSection(
                        title = "?먭꺽 紐⑤뜽",
                        specs = sortModelSpecs(filteredCatalogModels.filter { it.availability == ModelAvailability.REMOTE_ONLY && (activeStatusFilter != "?꾩껜" || it.id !in favoriteModelIds) && (it.id !in recentSpecIds || !showRecentSection) }, sortMode, currentModel, favoriteModelIds),
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
                            Toast.makeText(context, if (spec.id in favoriteModelIds) "利먭꺼李얘린?먯꽌 ?쒓굅?덉뒿?덈떎." else "利먭꺼李얘린??異붽??덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                        },
                        onHideModel = { spec ->
                            persistHidden(hiddenModelIds + spec.id)
                            Toast.makeText(context, "紐⑤뜽???④꼈?듬땲??", Toast.LENGTH_SHORT).show()
                        },
                        onUnhideModel = { spec ->
                            persistHidden(hiddenModelIds - spec.id)
                            Toast.makeText(context, "紐⑤뜽 ?④????댁젣?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                        },
                        modelNotes = modelNotes,
                        onSaveModelNote = ::persistModelNote,
                        benchmarkSummaryByModelId = benchmarkSummaryByModelId,
                        onOpenBenchmark = onOpenBenchmark
                    )
                    ModelZooSection(
                        title = "媛?몄삩 紐⑤뜽",
                        specs = sortModelSpecs(filteredCatalogModels.filter { (it.id.startsWith("custom-") || it.id.startsWith("external-")) && (activeStatusFilter != "?꾩껜" || it.id !in favoriteModelIds) && (it.id !in recentSpecIds || !showRecentSection) }, sortMode, currentModel, favoriteModelIds),
                        currentModel = currentModel,
                        onSelect = { spec ->
                            when {
                                spec.availability == ModelAvailability.NEEDS_CONVERSION -> {
                                    Toast.makeText(context, "??紐⑤뜽? 蹂?????ъ슜?????덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                                }
                                spec.availability != ModelAvailability.CUSTOM_IMPORTED -> {
                                    Toast.makeText(context, "???뺤떇? ?꾩옱 吏곸젒 ?ㅽ뻾?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
                                }
                                spec.localPath.isNullOrBlank() -> {
                                    Toast.makeText(context, "??紐⑤뜽? ?ㅽ뻾 ?꾩뿉 Fusion ?대? ??μ냼濡?蹂듭궗?댁빞 ?????덉뒿?덈떎.", Toast.LENGTH_LONG).show()
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
                            Toast.makeText(context, if (spec.id in favoriteModelIds) "利먭꺼李얘린?먯꽌 ?쒓굅?덉뒿?덈떎." else "利먭꺼李얘린??異붽??덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                        },
                        onHideModel = { spec ->
                            persistHidden(hiddenModelIds + spec.id)
                            Toast.makeText(context, "紐⑤뜽???④꼈?듬땲??", Toast.LENGTH_SHORT).show()
                        },
                        onUnhideModel = { spec ->
                            persistHidden(hiddenModelIds - spec.id)
                            Toast.makeText(context, "紐⑤뜽 ?④????댁젣?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                        },
                        modelNotes = modelNotes,
                        onSaveModelNote = ::persistModelNote,
                        benchmarkSummaryByModelId = benchmarkSummaryByModelId,
                        onOpenBenchmark = onOpenBenchmark
                    )
                    if (searchQuery.isNotBlank() && filteredCatalogModels.isEmpty()) {
                        Text(
                            text = "寃??寃곌낵媛 ?놁뒿?덈떎.",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("?ㅻⅨ ?ㅼ썙?쒕줈 寃?됲빐 蹂댁꽭??", color = TextSecondary, fontSize = 12.sp)
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
                            text = "?꾩껜 紐⑤뜽",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    }

                    Text(
                        text = "湲곗〈 Gemma ?ㅼ슫濡쒕뱶",
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
                                            model.customPath != null -> "而ㅼ뒪? 紐⑤뜽"
                                            downloading -> "?ㅼ슫濡쒕뱶 以?${downloadProgressPercent ?: 0}%"
                                            downloaded -> "?ㅼ슫濡쒕뱶??
                                            model.downloadUrl != null -> "??빐???ㅼ슫濡쒕뱶"
                                            else -> "?ㅼ슫濡쒕뱶 URL 誘몃벑濡?
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
                                        text = "?ъ슜 以?,
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
                                text = "+ 而ㅼ뒪? 紐⑤뜽 ?낅줈??,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "?ㅼ슫濡쒕뱶??.litertlm / .task / 紐⑤뜽 ?뚯씪 ?좏깮",
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
            title = { Text("理쒓렐 ?ъ슜 湲곕줉??吏?곗떆寃좎뒿?덇퉴?") },
            text = { Text("紐⑤뜽 ?뚯씪?대굹 ?ㅼ젙? ??젣?섏? ?딆뒿?덈떎.") },
            confirmButton = {
                FusionTextButton(
                    onClick = {
                        showClearRecentConfirm = false
                        saveRecentModels(prefs, emptyList())
                        recentHistory = emptyList()
                        Toast.makeText(context, "理쒓렐 ?ъ슜 湲곕줉??吏?좎뒿?덈떎.", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("吏?곌린", maxLines = 1) }
            },
            dismissButton = {
                FusionTextButton(onClick = { showClearRecentConfirm = false }) {
                    Text("痍⑥냼", maxLines = 1)
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
                        Text("怨좉툒 ?ㅼ젙", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "maxTokens ${generationSettings.maxTokens} 쨌 TopK ${generationSettings.topK}",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                FusionTextButton(onClick = onDismiss) {
                    Text("?リ린", fontSize = 13.sp)
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
                text = "$title ${if (checked) "耳쒖쭚" else "爰쇱쭚"}",
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
                    Text("${spec.sourceLabel ?: spec.family.name} 쨌 ${spec.parameterLabel} 쨌 ${modelFootprintLabel(spec)}", color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(2.dp))
                    val rec = recommendationMap[spec.id]
                    Text(
                        rec?.reason ?: (localSelectionMessage ?: compactCompatibilityLine(memoryInfo, tokenRecommendation)),
                        color = if (rec?.tier == "二쇱쓽 ?꾩슂" || memoryInfo.warning != null) DangerRed else TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    rec?.let {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("沅뚯옣 ?ㅼ젙: maxTokens ${it.recommendedTokens} 쨌 ${it.hint}", color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    benchmarkSummary?.let {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(buildCardBenchmarkLine(it), color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (spec.id in favoriteModelIds) {
                    Text("??, color = AccentBlue, fontSize = 12.sp, maxLines = 1)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (hasNote) {
                    Text("硫붾え", color = AccentBlue, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (showHiddenBadge && spec.id in hiddenModelIds) {
                    Text("?④?", color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (spec.displayName == currentModel) {
                    Text("?ъ슜 以?, color = AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                        Toast.makeText(context, "紐⑤뜽???좏깮?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                        selectedSpec = null
                    }
                    ModelSelectRiskLevel.CAUTION -> {
                        selectionDecision = decision
                    }
                    ModelSelectRiskLevel.BLOCKED -> {
                        if (decision.reason == "already selected") {
                            Toast.makeText(context, "?대? ?좏깮??紐⑤뜽?낅땲??", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "?깅줉??留곹겕媛 ?놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
                } else {
                    clipboard.setText(AnnotatedString(link))
                    Toast.makeText(context, "紐⑤뜽 留곹겕瑜?蹂듭궗?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
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
                    title = { Text("??紐⑤뜽???좏깮?섏떆寃좎뒿?덇퉴?") },
                    text = {
                        Text(
                            "?꾩옱 湲곌린?먯꽌 硫붾え由?遺?댁씠 諛쒖깮?????덉뒿?덈떎. 湲??묐떟?대굹 硫?고깭?ㅽ궧 ?섍꼍?먯꽌???깆씠 醫낅즺?????덉뒿?덈떎.\n\n" +
                                "紐⑤뜽: ${decision.spec.displayName}\n" +
                                "?덉긽 ?ш린: ${modelFootprintLabel(decision.spec)}\n" +
                                "湲곌린 RAM: ${decision.deviceRamClass}\n" +
                                "沅뚯옣 maxTokens: ${decision.recommendedMaxTokens}\n" +
                                "沅뚯옣 ?깃툒: ${decision.recommendationTier}"
                        )
                    },
                    confirmButton = {
                        FusionTextButton(onClick = {
                            onSelect(decision.spec)
                            applyDeviceAwareRecommendedSettings(context, context.getSharedPreferences(FusionPrefsName, Context.MODE_PRIVATE).edit(), decision.spec)
                            Toast.makeText(context, "紐⑤뜽怨?沅뚯옣 ?ㅼ젙???곸슜?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                            logModelSelectionDecision(decision.spec, memoryInfo, recommendationMap[decision.spec.id], decision.copy(level = ModelSelectRiskLevel.DIRECT, reason = "selected with recommended settings"))
                            selectionDecision = null
                            selectedSpec = null
                        }) { Text("沅뚯옣 ?ㅼ젙怨??④퍡 ?좏깮") }
                    },
                    dismissButton = {
                        Row {
                            FusionTextButton(onClick = {
                                onSelect(decision.spec)
                                Toast.makeText(context, "紐⑤뜽???좏깮?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                                logModelSelectionDecision(decision.spec, memoryInfo, recommendationMap[decision.spec.id], decision.copy(level = ModelSelectRiskLevel.DIRECT, reason = "selected directly"))
                                selectionDecision = null
                                selectedSpec = null
                            }) { Text("紐⑤뜽留??좏깮") }
                            FusionTextButton(onClick = { selectionDecision = null }) { Text("痍⑥냼") }
                        }
                    },
                    containerColor = PanelBg,
                    titleContentColor = TextPrimary,
                    textContentColor = TextPrimary
                )
            } else {
                AlertDialog(
                    onDismissRequest = { selectionDecision = null },
                    title = { Text("濡쒖뺄 ?ㅽ뻾??沅뚯옣?섏? ?딆뒿?덈떎.") },
                    text = { Text(decision.blockMessage ?: "??紐⑤뜽? ?꾩옱 湲곌린?먯꽌 濡쒖뺄 ?ㅽ뻾?섍린???덈Т ?????덉뒿?덈떎. ?쒕쾭 ?먮뒗 ?먭꺽 ?ㅽ뻾??沅뚯옣?⑸땲??") },
                    confirmButton = { FusionTextButton(onClick = { selectionDecision = null }) { Text("?뺤씤") } },
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
            memoryInfo.warning.contains("?먭꺽", ignoreCase = false) -> "remote"
            memoryInfo.warning.contains("沅뚯옣?섏? ?딆뒿?덈떎") -> "not_recommended"
            memoryInfo.warning.contains("蹂??) -> "conversion"
            memoryInfo.warning.contains("硫붾え由?, ignoreCase = false) -> "memory"
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
                    text = "${spec.sourceLabel ?: spec.family.name} ??${spec.family.name} ??${spec.runtimeFormat.name}",
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
                    DetailMetaRow("?뚯씪/媛以묒튂 ?ш린", modelFootprintLabel(spec))
                    DetailMetaRow("湲곌린 硫붾え由?, "??${formatGb(memoryInfo.totalRamGb)}GB")
                    DetailMetaRow("?꾩옱 ?ъ슜 媛??, "??${formatGb(memoryInfo.availableRamGb)}GB")
                    DetailMetaRow("沅뚯옣 硫붾え由?, spec.recommendedRamGb?.let { "${it}GB ?댁긽" } ?: "?뺣낫 ?놁쓬")
                    DetailMetaRow("沅뚯옣 ?깃툒", memoryInfo.tier)
                    DetailMetaRow("沅뚯옣 ?좏겙 ??, tokenRecommendation.label.removePrefix("沅뚯옣 ?좏겙 ??").trim())
                    if (spec.notes.isNotBlank()) Text(spec.notes, color = TextPrimary, fontSize = 13.sp)
                    Text(tokenRecommendation.explanation, color = TextSecondary, fontSize = 12.sp)
                    memoryInfo.warning?.let {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DangerRed.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "$it 湲??묐떟 ?먮뒗 硫?고깭?ㅽ겕 ?섍꼍?먯꽌 醫낅즺?????덉뒿?덈떎. 媛?ν븯硫????묒? 紐⑤뜽 ?먮뒗 ????? 理쒕? ?좏겙 ?섎? 沅뚯옣?⑸땲??",
                                color = DangerRed,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                    spec.localExecutionWarning?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
                    localSelectionMessage?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
                    Text("紐⑤뜽 硫붾え", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (modelNote.isBlank()) {
                        Text("??λ맂 硫붾え媛 ?놁뒿?덈떎.", color = TextSecondary, fontSize = 12.sp)
                    } else {
                        Text(modelNote, color = TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FusionTextButton(onClick = {
                            noteDraft = modelNote
                            noteEditorOpen = true
                        }) {
                            Text(if (modelNote.isBlank()) "硫붾え ?묒꽦" else "硫붾え ?섏젙", fontSize = 12.sp, maxLines = 1)
                        }
                        if (modelNote.isNotBlank()) {
                            FusionTextButton(onClick = { noteDeleteConfirmOpen = true }) {
                                Text("硫붾え ??젣", fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                    Text("踰ㅼ튂留덊겕 ?붿빟", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (benchmarkSummary == null) {
                        Text("?꾩쭅 痢≪젙??踰ㅼ튂留덊겕媛 ?놁뒿?덈떎.", color = TextSecondary, fontSize = 12.sp)
                    } else {
                        DetailMetaRow("痢≪젙 ?잛닔", "${benchmarkSummary.count}??)
                        DetailMetaRow(
                            "以묒븰媛??붿퐫???띾룄",
                            benchmarkSummary.medianDecodeTps?.let { "${formatSpeed(it)} tok/s" } ?: "?뺣낫 ?놁쓬"
                        )
                        DetailMetaRow(
                            "理쒓퀬 ?붿퐫???띾룄",
                            benchmarkSummary.bestDecodeTps?.let { "${formatSpeed(it)} tok/s" } ?: "?뺣낫 ?놁쓬"
                        )
                        DetailMetaRow("理쒓렐 痢≪젙", formatTimestamp(benchmarkSummary.latestAt))
                        DetailMetaRow("理쒓렐 媛?띻린", benchmarkSummary.recentAccelerator ?: "?뺣낫 ?놁쓬")
                        DetailMetaRow("MTP 異붿쿇", benchmarkSummary.mtpRecommendation)
                        if (benchmarkSummary.failedCount > 0) {
                            DetailMetaRow("?ㅽ뙣 ?잛닔", "${benchmarkSummary.failedCount}??)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FusionTextButton(onClick = {
                            onOpenBenchmark(false)
                        }) {
                            Text("踰ㅼ튂留덊겕 ?ㅽ뻾", fontSize = 12.sp, maxLines = 1)
                        }
                        FusionTextButton(onClick = {
                            onOpenBenchmark(true)
                        }) {
                            Text("湲곕줉 蹂닿린", fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    Text(fusionNpuNoteTitle(socInfo.detectedSocVendor), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    DetailMetaRow("媛먯???AP", socInfo.vendorLabel)
                    DetailMetaRow("SoC", socInfo.compactSocLabel)
                    Text(buildRuntimeNote(spec), color = TextSecondary, fontSize = 12.sp)
                    Text(fusionNpuNoteText(socInfo.detectedSocVendor), color = TextSecondary, fontSize = 12.sp)
                    Text(
                        fusionNpuCandidateLabel(socInfo.detectedSocVendor, spec.supportsNpuCandidate),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    if (spec.officialUrl == null && spec.downloadUrl == null) {
                        Text("?깅줉??留곹겕媛 ?놁뒿?덈떎.", color = TextSecondary, fontSize = 12.sp)
                    }
                    if (!available) {
                        Text(
                            "??紐⑤뜽? ?꾩옱 濡쒖뺄 紐⑤뜽濡?諛붾줈 ?좏깮?????놁뒿?덈떎.",
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
                        Text("?좏깮", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    FusionTextButton(onClick = onApplyRecommendedSettings) {
                        Text("沅뚯옣 ?ㅼ젙", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Box {
                        FusionTextButton(onClick = { overflowExpanded = true }) {
                            Text("??, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Clip)
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                            containerColor = PanelBg
                        ) {
                            DropdownMenuItem(
                                text = { Text("紐⑤뜽 ?ш텒", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = menuItemColors,
                                onClick = {
                                    overflowExpanded = false
                                    showModelPassport = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isFavorite) "利먭꺼李얘린?먯꽌 ?쒓굅" else "利먭꺼李얘린??異붽?",
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
                                        if (isHidden) "?④? ?댁젣" else "紐⑤뜽 ?④린湲?,
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
                                text = { Text("?뚯씪 媛?몄삤湲?, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = menuItemColors,
                                onClick = {
                                    overflowExpanded = false
                                    onUploadCustomModel()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("?ㅼ슫濡쒕뱶 ?섏씠吏 ?닿린", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                enabled = (spec.modelPageUrl ?: spec.downloadUrl ?: spec.officialUrl) != null,
                                colors = menuItemColors,
                                onClick = {
                                    overflowExpanded = false
                                    onOpenModelPage()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("?몃? ?뺣낫", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = menuItemColors,
                                onClick = {
                                    overflowExpanded = false
                                    if (spec.officialUrl != null) {
                                        onOpenOfficial()
                                    } else {
                                        Toast.makeText(context, "?깅줉??留곹겕媛 ?놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("留곹겕 蹂듭궗", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = menuItemColors,
                                onClick = {
                                    overflowExpanded = false
                                    if (spec.downloadUrl != null || spec.officialUrl != null || spec.modelPageUrl != null) {
                                        onCopyLink()
                                    } else {
                                        Toast.makeText(context, "?깅줉??留곹겕媛 ?놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("紐⑤뜽 ?명솚??寃??, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = menuItemColors,
                                onClick = {
                                    overflowExpanded = false
                                    compatibilityReport = FusionModelCompatibility.check(context, spec)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("紐⑤뜽 蹂???덈궡", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = menuItemColors,
                                onClick = {
                                    overflowExpanded = false
                                    if (spec.runtimeFormat == ModelRuntimeFormat.NEEDS_CONVERSION || spec.availability == ModelAvailability.NEEDS_CONVERSION) {
                                        showConversionGuide = true
                                    } else {
                                        Toast.makeText(context, "??紐⑤뜽? ?꾩옱 蹂???덈궡 ??곸씠 ?꾨떃?덈떎.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("?リ린", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
            title = { Text("紐⑤뜽 ?명솚??寃??) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(report.summary)
                    DetailMetaRow("?곹깭", report.localExecutionStatus)
                    DetailMetaRow("?뺤떇", report.formatLabel)
                    DetailMetaRow("怨꾩뿴", report.familyLabel)
                    DetailMetaRow("沅뚯옣 ?좏겙", report.recommendedMaxTokens.takeIf { it > 0 }?.toString() ?: "?대떦 ?놁쓬")
                    DetailMetaRow("媛?띻린", report.recommendedAccelerator.name)
                    DetailMetaRow("MTP", report.mtpRecommendation)
                    DetailMetaRow("NPU", report.npuCandidateStatus)
                    report.memoryWarning?.let { Text(it, color = DangerRed, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                FusionTextButton(onClick = { compatibilityReport = null }) { Text("?뺤씤", maxLines = 1) }
            },
            containerColor = PanelBg,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary
        )
    }

    if (showConversionGuide) {
        AlertDialog(
            onDismissRequest = { showConversionGuide = false },
            title = { Text("紐⑤뜽 蹂???덈궡") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailMetaRow("?꾩옱 ?뺤떇", spec.runtimeFormat.name)
                    DetailMetaRow("沅뚯옣 ?뺤떇", ".litertlm ?먮뒗 .task")
                    DetailMetaRow("?곹깭", if (spec.supportsNpuCandidate) "NPU ?꾨낫" else "蹂???꾩슂")
                    Text("?꾩옱 ?깆뿉???먮룞 蹂?섏? 吏?먰븯吏 ?딆뒿?덈떎.", color = TextSecondary, fontSize = 12.sp)
                    Text("蹂?????뚯씪 媛?몄삤湲곕줈 紐⑤뜽 ?뚯씪???좏깮??二쇱꽭??", color = TextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                FusionTextButton(onClick = { showConversionGuide = false }) { Text("?뺤씤", maxLines = 1) }
            },
            containerColor = PanelBg,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary
        )
    }

    if (noteEditorOpen) {
        AlertDialog(
            onDismissRequest = { noteEditorOpen = false },
            title = { Text("紐⑤뜽 硫붾え") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("??紐⑤뜽?????媛쒖씤 硫붾え瑜???ν빀?덈떎.", color = TextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        placeholder = { Text("?? S24?먯꽌??MTP ?붿씠 ??鍮좊쫭?덈떎.", color = TextSecondary) },
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
                    Toast.makeText(context, "紐⑤뜽 硫붾え瑜???ν뻽?듬땲??", Toast.LENGTH_SHORT).show()
                }) { Text("???, maxLines = 1) }
            },
            dismissButton = {
                FusionTextButton(onClick = { noteEditorOpen = false }) { Text("痍⑥냼", maxLines = 1) }
            },
            containerColor = PanelBg,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary
        )
    }

    if (noteDeleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { noteDeleteConfirmOpen = false },
            title = { Text("紐⑤뜽 硫붾え瑜???젣?섏떆寃좎뒿?덇퉴?") },
            text = { Text("??紐⑤뜽????λ맂 硫붾え留???젣?⑸땲??") },
            confirmButton = {
                FusionTextButton(onClick = {
                    onSaveModelNote(null)
                    noteDeleteConfirmOpen = false
                    Toast.makeText(context, "紐⑤뜽 硫붾え瑜???젣?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                }) { Text("??젣", maxLines = 1) }
            },
            dismissButton = {
                FusionTextButton(onClick = { noteDeleteConfirmOpen = false }) { Text("痍⑥냼", maxLines = 1) }
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
        return ModelSelectionDecision(spec, ModelSelectRiskLevel.BLOCKED, "already selected", recommendation?.tier ?: memoryInfo.tier, 0, ramClassLabel(memoryInfo.totalRamGb), "?대? ?좏깮??紐⑤뜽?낅땲??")
    }
    val tier = recommendation?.tier ?: memoryInfo.tier
    val available = isSelectableLocally(spec)
    if (!available) {
        val msg = when (spec.availability) {
            ModelAvailability.NEEDS_DOWNLOAD -> "紐⑤뜽 ?뚯씪??癒쇱? 媛?몄????⑸땲??"
            ModelAvailability.NEEDS_CONVERSION -> "??紐⑤뜽? 蹂?????ъ슜?????덉뒿?덈떎."
            ModelAvailability.REMOTE_ONLY -> "??紐⑤뜽? ?먭꺽 ?ㅽ뻾???꾩슂?⑸땲??"
            ModelAvailability.UNSUPPORTED_ON_DEVICE -> "濡쒖뺄 ?ㅽ뻾??沅뚯옣?섏? ?딆뒿?덈떎.\n\n??紐⑤뜽? ?꾩옱 湲곌린?먯꽌 濡쒖뺄 ?ㅽ뻾?섍린???덈Т ?????덉뒿?덈떎. ?쒕쾭 ?먮뒗 ?먭꺽 ?ㅽ뻾??沅뚯옣?⑸땲??"
            else -> "濡쒖뺄 ?ㅽ뻾??沅뚯옣?섏? ?딆뒿?덈떎.\n\n??紐⑤뜽? ?꾩옱 湲곌린?먯꽌 濡쒖뺄 ?ㅽ뻾?섍린???덈Т ?????덉뒿?덈떎. ?쒕쾭 ?먮뒗 ?먭꺽 ?ㅽ뻾??沅뚯옣?⑸땲??"
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
    val highRisk = tier == "二쇱쓽 ?꾩슂" || tier == "沅뚯옣?섏? ?딆쓬" ||
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
    totalRamGb in 7.0f..8.5f -> "8GB湲?
    totalRamGb <= 12.5f -> "12GB湲?
    totalRamGb <= 16.5f -> "16GB湲?
    else -> "怨좊찓紐⑤━"
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
        ?: "異쒖쿂 ?뺣낫 ?놁쓬"
    val tier = recommendation?.tier ?: memoryInfo.tier
    val reason = recommendation?.reason
        ?: memoryInfo.warning
        ?: spec.localExecutionWarning
        ?: "?꾩옱 湲곌린 ?뺣낫瑜?湲곗??쇰줈 紐⑤뜽 ?곹빀?꾨? 怨꾩궛?덉뒿?덈떎."

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
                Text("紐⑤뜽 ?ш텒", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "紐⑤뜽???곹깭, 沅뚯옣 ?ㅼ젙, 踰ㅼ튂留덊겕, 硫붾え瑜??쒕늿???뺤씤?⑸땲??",
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
                    PassportSection("?꾨줈??) {
                        Text(
                            spec.displayName,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${spec.family.name} 쨌 ${spec.parameterLabel} 쨌 ${passportRuntimeLabel(spec.runtimeFormat)}",
                            color = AccentBlue,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        DetailMetaRow("?⑤?由?, spec.family.name)
                        DetailMetaRow("?ш린", modelFootprintLabel(spec))
                        DetailMetaRow("?ㅽ뻾 ?뺤떇", passportRuntimeLabel(spec.runtimeFormat))
                        DetailMetaRow("?곹깭", passportAvailabilityLabel(spec.availability, available))
                        DetailMetaRow("沅뚯옣 湲곌린", passportDeviceClassLabel(spec.recommendedDeviceClass))
                        DetailMetaRow("紐⑤뜽 異쒖쿂", source)
                    }

                    PassportSection("湲곌린 ?곹빀??) {
                        DetailMetaRow("?곹빀??, tier)
                        Text(reason, color = TextSecondary, fontSize = 12.sp)
                        DetailMetaRow("湲곌린 RAM", ramClassLabel(memoryInfo.totalRamGb))
                        DetailMetaRow("?ъ슜 媛??RAM", "??${formatGb(memoryInfo.availableRamGb)}GB")
                        DetailMetaRow("沅뚯옣 maxTokens", tokenRecommendation.value.takeIf { it > 0 }?.toString() ?: "?먭꺽 ?ㅽ뻾 沅뚯옣")
                        DetailMetaRow("MTP", if (spec.recommendedMtpEnabled) "耳?沅뚯옣" else "??沅뚯옣")
                        DetailMetaRow("Reasoning", if (spec.recommendedReasoningEnabled) "耳?沅뚯옣" else "??沅뚯옣")
                    }

                    PassportSection("NPU 諛?媛???덈궡") {
                        DetailMetaRow("媛먯???AP", socInfo.vendorLabel)
                        DetailMetaRow("SoC", socInfo.compactSocLabel)
                        Text(
                            if (spec.supportsNpuCandidate) {
                                fusionNpuCandidateLabel(socInfo.detectedSocVendor, true)
                            } else {
                                "?꾩옱 紐⑤뜽? ?꾩슜 NPU ?ㅽ뻾 ?꾨낫濡??뺤씤?섏? ?딆븯?듬땲??"
                            },
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(fusionNpuNoteText(socInfo.detectedSocVendor), color = TextSecondary, fontSize = 12.sp)
                    }

                    PassportSection("踰ㅼ튂留덊겕 ?붿빟") {
                        if (benchmarkSummary == null) {
                            Text("?꾩쭅 痢≪젙??踰ㅼ튂留덊겕媛 ?놁뒿?덈떎.", color = TextSecondary, fontSize = 12.sp)
                        } else {
                            DetailMetaRow("痢≪젙 ?잛닔", "${benchmarkSummary.count}??)
                            DetailMetaRow("以묒븰媛?, benchmarkSummary.medianDecodeTps?.let { "${formatSpeed(it)} tok/s" } ?: "?뺣낫 ?놁쓬")
                            DetailMetaRow("理쒓퀬 ?띾룄", benchmarkSummary.bestDecodeTps?.let { "${formatSpeed(it)} tok/s" } ?: "?뺣낫 ?놁쓬")
                            DetailMetaRow("理쒓렐 痢≪젙", formatTimestamp(benchmarkSummary.latestAt))
                            DetailMetaRow("理쒓렐 媛?띻린", benchmarkSummary.recentAccelerator ?: "?뺣낫 ?놁쓬")
                            DetailMetaRow("MTP 異붿쿇", benchmarkSummary.mtpRecommendation)
                        }
                    }

                    PassportSection("?ъ슜??硫붾え") {
                        Text(
                            modelNote.ifBlank { "??λ맂 硫붾え媛 ?놁뒿?덈떎." },
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        FusionTextButton(onClick = onEditNote) {
                            Text(if (modelNote.isBlank()) "硫붾え ?묒꽦" else "硫붾え ?섏젙", fontSize = 12.sp)
                        }
                    }

                    PassportSection("?뚯씪 諛??곌껐 ?곹깭") {
                        if (isImported) {
                            DetailMetaRow(
                                "????꾩튂",
                                if (spec.externallyReferenced) "?몃? ?뚯씪 ?곌껐?? else "Fusion ?대? ??μ냼"
                            )
                            DetailMetaRow("?뚯씪 ?대쫫", spec.originalFileName ?: spec.fileName ?: "?뺣낫 ?놁쓬")
                            DetailMetaRow("?뚯씪 ?ш린", spec.fileSizeBytes?.let { formatBytes(it) } ?: "?뺣낫 ?놁쓬")
                            DetailMetaRow("?ㅽ뻾 ?곹깭", passportExecutionStatus(spec, available))
                        } else {
                            Text(
                                if (available) "Fusion?먯꽌 ?ъ슜?????덈뒗 紐⑤뜽 ?뚯씪?낅땲??"
                                else "紐⑤뜽 ?뚯씪???꾩쭅 ?곌껐?섏? ?딆븯?듬땲??",
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
                    FusionTextButton(enabled = available, onClick = onSelect) { Text("?좏깮", fontSize = 12.sp) }
                    FusionTextButton(onClick = onApplyRecommendedSettings) { Text("沅뚯옣 ?ㅼ젙", fontSize = 12.sp) }
                    FusionTextButton(onClick = onOpenBenchmark) { Text("踰ㅼ튂留덊겕", fontSize = 12.sp) }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FusionTextButton(onClick = onOpenModelPage) { Text("紐⑤뜽 ?섏씠吏", fontSize = 12.sp) }
                    FusionTextButton(onClick = onCopyLink) { Text("留곹겕 蹂듭궗", fontSize = 12.sp) }
                    FusionTextButton(onClick = onUploadCustomModel) { Text("?뚯씪 媛?몄삤湲?, fontSize = 12.sp) }
                    FusionTextButton(onClick = onDismiss) { Text("?リ린", fontSize = 12.sp) }
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
    ModelRuntimeFormat.NEEDS_CONVERSION -> "蹂???꾩슂"
    ModelRuntimeFormat.EXYNOS_AI_STUDIO -> "Exynos AI Studio ?꾨낫"
    ModelRuntimeFormat.REMOTE_API -> "?먭꺽 API"
    ModelRuntimeFormat.UNKNOWN -> "吏???뺤씤 ?꾩슂"
}

private fun passportAvailabilityLabel(availability: ModelAvailability, available: Boolean): String = when {
    available -> "?ъ슜 媛??
    availability == ModelAvailability.NEEDS_CONVERSION -> "蹂???꾩슂"
    availability == ModelAvailability.NEEDS_DOWNLOAD -> "?뚯씪 ?꾩슂"
    availability == ModelAvailability.REMOTE_ONLY -> "?먭꺽 ?꾩슜"
    availability == ModelAvailability.UNSUPPORTED_ON_DEVICE -> "吏???뺤씤 ?꾩슂"
    else -> "?ㅽ뻾 以鍮??꾩슂"
}

private fun passportDeviceClassLabel(deviceClass: ModelRecommendedDeviceClass): String = when (deviceClass) {
    ModelRecommendedDeviceClass.RAM_8GB_SAFE -> "8GB 湲곌린"
    ModelRecommendedDeviceClass.RAM_12GB_RECOMMENDED -> "12GB ?댁긽 湲곌린"
    ModelRecommendedDeviceClass.RAM_16GB_RECOMMENDED -> "16GB ?댁긽 湲곌린"
    ModelRecommendedDeviceClass.SERVER_ONLY -> "?쒕쾭 ?먮뒗 ?먭꺽 ?ㅽ뻾"
}

private fun passportExecutionStatus(spec: FusionModelSpec, available: Boolean): String = when {
    available -> "?ㅽ뻾 ?꾨낫"
    spec.runtimeFormat == ModelRuntimeFormat.NEEDS_CONVERSION ||
        spec.availability == ModelAvailability.NEEDS_CONVERSION -> "蹂???꾩슂"
    spec.runtimeFormat == ModelRuntimeFormat.UNKNOWN ||
        spec.availability == ModelAvailability.UNSUPPORTED_ON_DEVICE -> "吏???뺤씤 ?꾩슂"
    else -> "?ㅽ뻾 以鍮??꾩슂"
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
        title = { Text("紐⑤뜽 ?뚯씪???ㅼ슫濡쒕뱶?섏떆寃좎뒿?덇퉴?") },
        text = { Text("紐⑤뜽 ?뚯씪? ?⑸웾???????덉뒿?덈떎. Wi-Fi ?곌껐怨?異⑸텇????κ났媛꾩쓣 ?뺤씤??二쇱꽭??") },
        confirmButton = { FusionTextButton(onClick = onConfirm) { Text("?ㅼ슫濡쒕뱶") } },
        dismissButton = { FusionTextButton(onClick = onDismiss) { Text("痍⑥냼") } },
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
        title = { Text("紐⑤뜽 ?⑤?由щ? ?좏깮??二쇱꽭??") },
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("?섏쨷??) } },
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
        title = { Text("紐⑤뜽 ?뚯씪 ?뺤씤") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailMetaRow("?뚯씪 ?대쫫", pending.originalFileName)
                DetailMetaRow("?뚯씪 ?ш린", pending.fileSizeBytes?.let { formatBytes(it) } ?: "紐⑤뜽 ?뚯씪 ?ш린瑜??뺤씤?????놁뒿?덈떎.")
                DetailMetaRow("?뺤옣??, extension)
                DetailMetaRow("異붿젙 ?뺤떇", formatLabel)
                DetailMetaRow("?ㅽ뻾 ?곹깭", executionStatus)
                Text("紐⑤뜽 ?⑤?由щ? ?좏깮??二쇱꽭??", color = TextSecondary, fontSize = 12.sp)
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
                    Text("紐⑤뜽 ?뚯씪 沅뚰븳???좎??????놁뒿?덈떎.", color = DangerRed, fontSize = 12.sp)
                }
                if (format == ModelRuntimeFormat.MEDIAPIPE_LLM) {
                    Text("??紐⑤뜽? ?ㅽ뻾 ?꾩뿉 Fusion ?대? ??μ냼濡?蹂듭궗?댁빞 ?????덉뒿?덈떎.", color = TextSecondary, fontSize = 12.sp)
                }
                Text(nextAction, color = TextSecondary, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onLink(selectedFamily) }) { Text("?곌껐", color = AccentBlue, maxLines = 1) }
                TextButton(onClick = { onCopyForRun(selectedFamily) }, enabled = canCopyForRun) {
                    Text("?ㅽ뻾?⑹쑝濡?蹂듭궗", color = if (canCopyForRun) AccentBlue else TextSecondary, maxLines = 1)
                }
                TextButton(onClick = onDismiss) { Text("痍⑥냼", color = TextSecondary, maxLines = 1) }
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
        hint = if (spec.recommendedReasoningEnabled) "MTP ?? else "MTP ??쨌 Reasoning ??,
        deviceRamClass = risk.ramClass.label,
        includedInRecommendedLocal = dynamicIncluded,
        includeReason = if (dynamicIncluded) "included" else "risk_excluded"
    )
    @Suppress("UNREACHABLE_CODE")
    val token = buildDeviceAwareTokenRecommendation(spec, totalRamGb, availableRamGb).value.coerceAtLeast(1024)
    val hintParts = mutableListOf("MTP ??)
    if (!spec.recommendedReasoningEnabled || totalRamGb <= 8.5f) hintParts += "Reasoning ??
    val hint = hintParts.distinct().joinToString(" 쨌 ")
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
            sizeGb >= 5.0f -> "沅뚯옣?섏? ?딆쓬"
            sizeGb >= 4.0f -> "二쇱쓽 ?꾩슂"
            sizeGb > 3.0f -> "二쇱쓽 ?꾩슂"
            sizeGb > 1.5f -> "?ㅽ뿕 媛??
            else -> "沅뚯옣"
        }
        "12GB" -> when {
            sizeGb > 7.0f -> "沅뚯옣?섏? ?딆쓬"
            sizeGb > 5.0f -> "二쇱쓽 ?꾩슂"
            sizeGb > 3.0f -> "?ㅽ뿕 媛??
            else -> "沅뚯옣"
        }
        "16GB" -> when {
            sizeGb > 10.0f -> "沅뚯옣?섏? ?딆쓬"
            sizeGb > 5.0f -> "?ㅽ뿕 媛??
            else -> "沅뚯옣"
        }
        else -> if (sizeGb > 12.0f) "二쇱쓽 ?꾩슂" else "沅뚯옣"
    }
    val tier = when {
        spec.availability == ModelAvailability.REMOTE_ONLY || spec.recommendedDeviceClass == ModelRecommendedDeviceClass.SERVER_ONLY -> "?먭꺽 ?꾩슜"
        spec.memoryClass == ModelMemoryClass.SERVER -> "沅뚯옣?섏? ?딆쓬"
        spec.availability == ModelAvailability.UNSUPPORTED_ON_DEVICE -> "沅뚯옣?섏? ?딆쓬"
        sizeTier == "沅뚯옣?섏? ?딆쓬" -> "沅뚯옣?섏? ?딆쓬"
        sizeTier == "二쇱쓽 ?꾩슂" -> "二쇱쓽 ?꾩슂"
        isEightGbSafe && effectiveTotalRamGb >= 8.0f && isSmallModel -> "沅뚯옣"
        isEightGbSafe && effectiveTotalRamGb >= 8.0f -> "?ㅽ뿕 媛??
        spec.availability == ModelAvailability.READY && isSmallModel && meetsMinimum -> "沅뚯옣"
        spec.availability == ModelAvailability.CUSTOM_IMPORTED && isSmallModel && meetsMinimum -> "沅뚯옣"
        recommendedRam > 0 && effectiveTotalRamGb >= recommendedRam -> "沅뚯옣"
        meetsMinimum -> "?ㅽ뿕 媛??
        slightlyBelowMinimum -> "二쇱쓽 ?꾩슂"
        else -> "沅뚯옣?섏? ?딆쓬"
    }
    val availableMemoryVeryLow = lowMemory || availableRamGb < 1.25f
    val shouldDowngradeForAvailableMemory = availableMemoryVeryLow && !isCurrentSelected && !(isEightGbSafe && isSmallModel)
    val downgradedTier = when {
        shouldDowngradeForAvailableMemory && tier == "沅뚯옣" -> "?ㅽ뿕 媛??
        shouldDowngradeForAvailableMemory && tier == "?ㅽ뿕 媛?? -> "二쇱쓽 ?꾩슂"
        else -> tier
    }
    val reason = when (downgradedTier) {
        "沅뚯옣" -> if (availableRamGb < 2.0f) "?꾩옱 ?ъ슜 媛?ν븳 硫붾え由ш? ??븘 ?ㅽ뻾 ?꾩뿉 ?ㅻⅨ ?깆쓣 ?뺣━?섎뒗 寃껋씠 醫뗭뒿?덈떎." else "?꾩옱 湲곌린 硫붾え由?湲곗??쇰줈 ?덉젙?곸씤 ?뚰삎 紐⑤뜽?낅땲??"
        "?ㅽ뿕 媛?? -> if (spec.availability == ModelAvailability.NEEDS_CONVERSION) "蹂?섏씠 ?꾩슂?섏?留?硫붾え由?湲곗?? 異⑹”?⑸땲??" else "8GB 湲곌린?먯꽌 ?ㅽ뿕?섍린 ?곹빀?⑸땲??"
        "二쇱쓽 ?꾩슂" -> if (availableRamGb < 2.0f) "?꾩옱 ?ъ슜 媛?ν븳 硫붾え由ш? ??븘 ?ㅽ뻾 ?꾩뿉 ?ㅻⅨ ?깆쓣 ?뺣━?섎뒗 寃껋씠 醫뗭뒿?덈떎." else "湲??묐떟 ?먮뒗 硫?고깭?ㅽ궧 ?섍꼍?먯꽌 醫낅즺?????덉뒿?덈떎. 媛?ν븯硫???? 理쒕? ?좏겙 ?섎? 沅뚯옣?⑸땲??"
        "沅뚯옣?섏? ?딆쓬" -> "?꾩옱 湲곌린 硫붾え由??鍮?紐⑤뜽 ?붽뎄?ы빆???믪뒿?덈떎."
        else -> "??紐⑤뜽? ?먭꺽 ?ㅽ뻾??沅뚯옣?⑸땲??"
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
    val includeByAvailability = downgradedTier in listOf("沅뚯옣", "?ㅽ뿕 媛??, "二쇱쓽 ?꾩슂")
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
    if (evaluation.tier == "?먭꺽 ?꾩슜") {
        return "??紐⑤뜽? 紐⑤컮??濡쒖뺄 ?ㅽ뻾?⑹씠 ?꾨떃?덈떎."
    }
    if (spec.availability == ModelAvailability.NEEDS_CONVERSION) {
        return if (evaluation.tier == "沅뚯옣?섏? ?딆쓬") {
            "??紐⑤뜽? 蹂?섏씠 ?꾩슂?섎ŉ ?꾩옱 湲곌린 硫붾え由щ줈??濡쒖뺄 ?ㅽ뻾??沅뚯옣?섏? ?딆뒿?덈떎."
        } else {
            "??紐⑤뜽? 蹂?????ъ슜?????덉뒿?덈떎. ${evaluation.reason}"
        }
    }
    if (evaluation.tier == "沅뚯옣?섏? ?딆쓬") {
        if (evaluation.deviceRamClass == "8GB" && (spec.modelSizeEstimateGb ?: 0f) >= 5.0f) {
            return "?꾩옱 湲곌린?먯꽌????紐⑤뜽??濡쒖뺄 ?ㅽ뻾??沅뚯옣?섏? ?딆뒿?덈떎."
        }
        return "?꾩옱 湲곌린 硫붾え由щ줈????紐⑤뜽??濡쒖뺄 ?ㅽ뻾??沅뚯옣?섏? ?딆뒿?덈떎."
    }
    if (availableRamGb > 0f && availableRamGb < 2f && evaluation.tier == "沅뚯옣") {
        return "?꾩옱 ?ъ슜 媛?ν븳 硫붾え由ш? ??븘 ?ㅽ뻾 ?꾩뿉 ?ㅻⅨ ?깆쓣 ?뺣━?섎뒗 寃껋씠 醫뗭뒿?덈떎."
    }
    if (totalRamGb in 7.0f..8.5f && (spec.memoryClass == ModelMemoryClass.LOW || (spec.modelSizeEstimateGb ?: 99f) <= 1.5f)) {
        return "?꾩옱 湲곌린?먯꽌 ?ㅽ뿕?섍린 ?곹빀???뚰삎 紐⑤뜽?낅땲??"
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
        label = if (recommended > 0) "沅뚯옣 ?좏겙 ?? ??$recommended" else "沅뚯옣 ?좏겙 ?? ?먭꺽 ?ㅽ뻾 沅뚯옣",
        explanation = "?꾩옱 湲곌린??珥?硫붾え由ъ? ?ъ슜 媛?ν븳 硫붾え由щ? 湲곗??쇰줈 沅뚯옣媛믪쓣 怨꾩궛?덉뒿?덈떎."
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
        "沅뚯옣 ?좏겙 ?? ?먭꺽 ?ㅽ뻾 沅뚯옣"
    } else if (totalRamGb >= 12f && adjusted >= 4096) {
        "沅뚯옣 ?좏겙 ?? ??2048~$adjusted"
    } else {
        "沅뚯옣 ?좏겙 ?? ??$adjusted"
    }
    val explanation = "?꾩옱 湲곌린??硫붾え由щ? 湲곗??쇰줈 沅뚯옣媛믪쓣 怨꾩궛?덉뒿?덈떎. 硫붾え由ш? 遺議깊븳 湲곌린?먯꽌??湲?異쒕젰?먯꽌 ?띾룄 ????먮뒗 醫낅즺媛 諛쒖깮?????덉뒿?덈떎."
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
    return spec.modelSizeEstimateGb?.let { "??${formatGb(it)}GB" } ?: "硫붾え由?${spec.memoryClass.name}"
}

private fun compactCompatibilityLine(
    memoryInfo: ModelMemoryUiInfo,
    tokenRecommendation: TokenRecommendation
): String {
    return memoryInfo.warning ?: tokenRecommendation.label
}

private fun buildRuntimeNote(spec: FusionModelSpec): String {
    return when (spec.availability) {
        ModelAvailability.REMOTE_ONLY -> "????ぉ? ?먭꺽 ?ㅽ뻾 ?꾨낫?낅땲?? 濡쒖뺄 GPU/CPU ?ㅽ뻾???꾩젣濡??섏? ?딆뒿?덈떎."
        ModelAvailability.NEEDS_CONVERSION -> "濡쒖뺄 ?ㅽ뻾 ??蹂?섍낵 ?ㅼ젣 湲곌린 ?명솚???뺤씤???꾩슂?⑸땲??"
        ModelAvailability.NEEDS_DOWNLOAD -> "濡쒖뺄 ?뚯씪??媛?몄삩 ???꾩옱 LiteRT/Gemma ?ㅽ뻾 寃쎈줈?먯꽌 ?뺤씤?댁빞 ?⑸땲??"
        ModelAvailability.UNSUPPORTED_ON_DEVICE -> "?꾩옱 ?깆쓽 濡쒖뺄 ?ㅽ뻾 ?뺤떇?쇰줈??沅뚯옣?섏? ?딆뒿?덈떎."
        else -> "濡쒖뺄 ?뚯씪??以鍮꾨맂 寃쎌슦 ?꾩옱 ?ㅼ젙??GPU/CPU 寃쎈줈?먯꽌 ?ㅽ뻾?????덉뒿?덈떎."
    }
}

private fun buildLocalSelectionMessage(spec: FusionModelSpec, available: Boolean): String? {
    if (available) return null
    if (spec.externallyReferenced && spec.localPath.isNullOrBlank() && spec.availability == ModelAvailability.CUSTOM_IMPORTED) {
        return "?ㅽ뻾 以鍮??꾩슂"
    }
    return when (spec.availability) {
        ModelAvailability.NEEDS_DOWNLOAD -> "紐⑤뜽 ?뚯씪??癒쇱? 媛?몄????⑸땲??"
        ModelAvailability.NEEDS_CONVERSION -> "??紐⑤뜽? 蹂?????ъ슜?????덉뒿?덈떎."
        ModelAvailability.REMOTE_ONLY -> "??紐⑤뜽? ?먭꺽 ?ㅽ뻾???꾩슂?⑸땲??"
        ModelAvailability.UNSUPPORTED_ON_DEVICE -> "?꾩옱 湲곌린?먯꽌??濡쒖뺄 ?ㅽ뻾??沅뚯옣?섏? ?딆뒿?덈떎."
        else -> "紐⑤뜽 ?뚯씪??癒쇱? 媛?몄????⑸땲??"
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
    if (lastUsedAt <= 0L) return "理쒓렐 ?ъ슜: ?뺣낫 ?놁쓬"
    val now = System.currentTimeMillis()
    val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(now))
    val usedDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(lastUsedAt))
    return if (today == usedDay) {
        "理쒓렐 ?ъ슜: ?ㅻ뒛"
    } else {
        "理쒓렐 ?ъ슜: ${SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(lastUsedAt))}"
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
    if (on.size < 3 || off.size < 3) return "痢≪젙 遺議?
    val onMedian = on.mapNotNull { it.decodeTokensPerSecond?.takeIf { v -> v > 0f } ?: it.totalTokensPerSecond.takeIf { v -> v > 0f } }.sorted().let { arr ->
        if (arr.isEmpty()) return "痢≪젙 遺議?
        val mid = arr.size / 2
        if (arr.size % 2 == 0) (arr[mid - 1] + arr[mid]) / 2f else arr[mid]
    }
    val offMedian = off.mapNotNull { it.decodeTokensPerSecond?.takeIf { v -> v > 0f } ?: it.totalTokensPerSecond.takeIf { v -> v > 0f } }.sorted().let { arr ->
        if (arr.isEmpty()) return "痢≪젙 遺議?
        val mid = arr.size / 2
        if (arr.size % 2 == 0) (arr[mid - 1] + arr[mid]) / 2f else arr[mid]
    }
    return when {
        onMedian >= offMedian * 1.05f -> "耳?
        offMedian >= onMedian * 1.05f -> "??
        else -> "李⑥씠 ?묒쓬"
    }
}

private fun buildCardBenchmarkLine(summary: ModelBenchmarkSummary): String {
    val speed = summary.medianDecodeTps ?: summary.averageTotalTps
    return if (speed != null) {
        "踰ㅼ튂留덊겕 ${summary.count}??쨌 以묒븰媛?${formatSpeed(speed)} tok/s"
    } else {
        "踰ㅼ튂留덊겕 ${summary.count}??
    }
}

private fun formatSpeed(value: Float): String = String.format(Locale.US, "%.1f", value)

private fun formatTimestamp(ts: Long): String {
    if (ts <= 0L) return "?뺣낫 ?놁쓬"
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
        Toast.makeText(context, "?깅줉??留곹겕媛 ?놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "留곹겕瑜??????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, "留곹겕瑜??????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
    }
}

private fun startModelDirectDownload(context: Context, spec: FusionModelSpec) {
    val url = spec.directDownloadUrl
    if (url.isNullOrBlank()) {
        Toast.makeText(context, "?ㅼ슫濡쒕뱶 ?섏씠吏瑜??댁뼱 二쇱꽭??", Toast.LENGTH_SHORT).show()
        openModelLink(context, spec.modelPageUrl ?: spec.downloadUrl ?: spec.officialUrl)
        return
    }
    try {
        val request = DownloadManager.Request(url.toUri()).apply {
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setTitle(spec.directDownloadFileName ?: "${spec.displayName} 紐⑤뜽 ?뚯씪")
            setDescription("${spec.directDownloadFormat ?: "紐⑤뜽"} ?뚯씪 ?ㅼ슫濡쒕뱶")
            setAllowedOverMetered(false)
            setAllowedOverRoaming(false)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                spec.directDownloadFileName ?: Uri.parse(url).lastPathSegment ?: "${spec.id}.bin"
            )
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (manager == null) {
            Toast.makeText(context, "紐⑤뜽 ?뚯씪 ?ㅼ슫濡쒕뱶瑜??쒖옉?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
            return
        }
        manager.enqueue(request)
        Toast.makeText(context, "紐⑤뜽 ?뚯씪 ?ㅼ슫濡쒕뱶瑜??쒖옉?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
        Toast.makeText(context, "?ㅼ슫濡쒕뱶媛 ?꾨즺?섎㈃ ?뚯씪 媛?몄삤湲곕줈 紐⑤뜽 ?뚯씪???좏깮??二쇱꽭??", Toast.LENGTH_LONG).show()
    } catch (_: Exception) {
        Toast.makeText(context, "紐⑤뜽 ?뚯씪 ?ㅼ슫濡쒕뱶瑜??쒖옉?????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
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
                Text("紐⑤뜽 ??κ났媛?, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("媛?몄삩 紐⑤뜽怨??몃? ?곌껐 ?뚯씪??愿由ы빀?덈떎.", color = TextSecondary, fontSize = 13.sp)
                Text("珥??⑸웾 ??${formatBytes(totalSize)}", color = TextSecondary, fontSize = 12.sp)

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("Fusion ?대? 紐⑤뜽 ?뚯씪", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    items(internalFiles) { file ->
                        val spec = models.firstOrNull { it.localPath == file.absolutePath }
                        StorageModelRow(
                            name = spec?.displayName ?: file.name,
                            source = "Fusion ?대? ??μ냼",
                            size = formatBytes(file.length()),
                            runtimeFormat = FusionModelCatalog.runtimeFormatForFile(file.name).name,
                            family = spec?.family?.name ?: ModelFamily.CUSTOM.name,
                            status = if (file.exists()) "?ъ슜 媛?? else "?뚯씪??李얠쓣 ???놁뒿?덈떎.",
                            current = spec?.displayName == currentModel,
                            actions = {
                                FusionTextButton(onClick = {
                                    onSelect(spec ?: FusionModelCatalog.importedSpec(file.name, file.absolutePath, ModelFamily.CUSTOM))
                                }) { Text("?좏깮", fontSize = 12.sp) }
                                FusionTextButton(onClick = {
                                    openModelFile(context, file)
                                }) { Text("?뚯씪 ?닿린", fontSize = 12.sp) }
                                FusionTextButton(onClick = {
                                    deleteTarget = spec ?: FusionModelCatalog.importedSpec(file.name, file.absolutePath, ModelFamily.CUSTOM)
                                }) { Text("??젣", fontSize = 12.sp, color = DangerRed) }
                            }
                        )
                    }
                    item {
                        Text("?몃? ?곌껐 紐⑤뜽 ?뚯씪", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    items(models.filter { it.externallyReferenced }) { spec ->
                        val available = canOpenUri(context, spec.uriString)
                        StorageModelRow(
                            name = spec.displayName,
                            source = "?몃? ?뚯씪 ?곌껐",
                            size = spec.fileSizeBytes?.let { formatBytes(it) } ?: "?ш린 ?뺣낫 ?놁쓬",
                            runtimeFormat = spec.runtimeFormat.name,
                            family = spec.family.name,
                            status = storageStatusLabel(spec, available),
                            current = spec.displayName == currentModel,
                            actions = {
                                FusionTextButton(onClick = { onSelect(spec) }) { Text("?좏깮", fontSize = 12.sp) }
                                FusionTextButton(onClick = { openModelUri(context, spec.uriString) }) { Text("?뚯씪 ?닿린", fontSize = 12.sp) }
                                if (spec.availability == ModelAvailability.CUSTOM_IMPORTED && spec.localPath.isNullOrBlank()) {
                                    FusionTextButton(onClick = {
                                        scope.launch {
                                            val copied = copyUriToModelFile(
                                                context = context,
                                                uri = Uri.parse(spec.uriString),
                                                displayName = spec.originalFileName ?: spec.fileName ?: spec.displayName
                                            )
                                            if (copied == null) {
                                                Toast.makeText(context, "紐⑤뜽 ?뚯씪???묎렐?????놁뒿?덈떎. ?뚯씪???ㅼ떆 ?곌껐??二쇱꽭??", Toast.LENGTH_SHORT).show()
                                            } else {
                                                FusionModelCatalog.saveImported(
                                                    context,
                                                    spec.copy(
                                                        localPath = copied.absolutePath,
                                                        fileName = copied.name,
                                                        copiedInternally = true,
                                                        externallyReferenced = false,
                                                        sourceLabel = "?ъ슜??媛?몄삤湲?,
                                                        lastCheckedAt = System.currentTimeMillis()
                                                    )
                                                )
                                                models = FusionModelCatalog.loadImported(context)
                                                onChanged()
                                                Toast.makeText(context, "?ㅽ뻾?⑹쑝濡?蹂듭궗?덉뒿?덈떎.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }) { Text("?ㅽ뻾?⑹쑝濡?蹂듭궗", fontSize = 12.sp) }
                                }
                                FusionTextButton(onClick = {
                                    FusionModelCatalog.removeImported(context, spec)
                                    models = FusionModelCatalog.loadImported(context)
                                    onChanged()
                                }) { Text("?곌껐 ?댁젣", fontSize = 12.sp) }
                            }
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FusionTextButton(onClick = onDismiss) { Text("?リ린", fontSize = 13.sp) }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("紐⑤뜽 ?뚯씪????젣?섏떆寃좎뒿?덇퉴?") },
            text = { Text("Fusion ?대? ??μ냼??紐⑤뜽 ?뚯씪????젣?⑸땲??") },
            confirmButton = {
                FusionTextButton(onClick = {
                    target.localPath?.let { File(it).delete() }
                    FusionModelCatalog.removeImported(context, target)
                    models = FusionModelCatalog.loadImported(context)
                    deleteTarget = null
                    onChanged()
                }) { Text("??젣") }
            },
            dismissButton = { FusionTextButton(onClick = { deleteTarget = null }) { Text("痍⑥냼") } },
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
            Text("$source 쨌 $size 쨌 $runtimeFormat 쨌 $family", color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, color = if (status == "?ъ슜 媛??) AccentBlue else TextSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), content = actions)
        }
    }
}

private fun storageStatusLabel(spec: FusionModelSpec, uriAvailable: Boolean): String {
    if (!uriAvailable) return "?뚯씪??李얠쓣 ???놁뒿?덈떎."
    if (spec.runtimeFormat == ModelRuntimeFormat.NEEDS_CONVERSION) return "蹂???꾩슂"
    if (spec.availability != ModelAvailability.CUSTOM_IMPORTED) return "?ㅽ뻾 以鍮??꾩슂"
    if (spec.externallyReferenced && spec.localPath.isNullOrBlank()) return "?ㅽ뻾 以鍮??꾩슂"
    return "?ъ슜 媛??
}

private fun importFormatLabel(fileName: String, format: ModelRuntimeFormat): String {
    return when (fileName.substringAfterLast('.', "").lowercase(Locale.US)) {
        "litertlm" -> "LiteRT-LM"
        "task" -> "MediaPipe LLM"
        "gguf" -> "GGUF"
        "onnx" -> "ONNX"
        "safetensors" -> "蹂???꾩슂"
        "bin" -> "?뺤씤 ?꾩슂"
        else -> when (format) {
            ModelRuntimeFormat.LITERT_LM -> "LiteRT-LM"
            ModelRuntimeFormat.MEDIAPIPE_LLM -> "MediaPipe LLM"
            ModelRuntimeFormat.GGUF -> "GGUF"
            ModelRuntimeFormat.ONNX -> "ONNX"
            ModelRuntimeFormat.NEEDS_CONVERSION -> "蹂???꾩슂"
            else -> "吏???뺤씤 ?꾩슂"
        }
    }

}

private fun importExecutionStatusMessage(format: ModelRuntimeFormat): String {
    return when (format) {
        ModelRuntimeFormat.LITERT_LM, ModelRuntimeFormat.MEDIAPIPE_LLM -> "??紐⑤뜽 ?뚯씪? Fusion?먯꽌 ?ㅽ뻾 ?꾨낫濡??ъ슜?????덉뒿?덈떎."
        ModelRuntimeFormat.NEEDS_CONVERSION -> "??紐⑤뜽? 蹂?????ъ슜?????덉뒿?덈떎."
        else -> "???뺤떇? ?꾩옱 吏곸젒 ?ㅽ뻾?????놁뒿?덈떎."
    }
}

private fun importRecommendedActionMessage(format: ModelRuntimeFormat): String {
    return when (format) {
        ModelRuntimeFormat.LITERT_LM, ModelRuntimeFormat.MEDIAPIPE_LLM -> "沅뚯옣 ?숈옉: 癒쇱? ?곌껐?섍퀬, ?ㅽ뻾???꾩슂?섎㈃ ?ㅽ뻾?⑹쑝濡?蹂듭궗??二쇱꽭??"
        ModelRuntimeFormat.NEEDS_CONVERSION -> "沅뚯옣 ?숈옉: ?곌껐濡?蹂닿??섍퀬 蹂?????ㅼ떆 媛?몄? 二쇱꽭??"
        else -> "沅뚯옣 ?숈옉: ?곌껐濡?蹂닿??섍퀬 吏???щ?瑜??뺤씤??二쇱꽭??"
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
        Toast.makeText(context, "紐⑤뜽 ?뚯씪???묎렐?????놁뒿?덈떎. ?뚯씪???ㅼ떆 ?곌껐??二쇱꽭??", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uriString), "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    } catch (_: Exception) {
        Toast.makeText(context, "?뚯씪???????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
    }
}

private fun openModelFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    } catch (_: Exception) {
        Toast.makeText(context, "?뚯씪???????놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val gb = bytes / (1024f * 1024f * 1024f)
    if (gb >= 1f) return "??${formatGb(gb)}GB"
    val mb = bytes / (1024f * 1024f)
    return "??${String.format(Locale.US, "%.1f", mb)}MB"
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
                text = "怨좉툒 ?ㅼ젙",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Max ${settings.maxTokens} 쨌 TopK ${settings.topK} 쨌 TopP ${
                    "%.2f".format(settings.topP)
                } 쨌 Temp ${"%.2f".format(settings.temperature)}",
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${settings.accelerator.name} 쨌 Reason ${settings.reasoningBudgetTokens} 쨌 MTP ${
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
            Text("紐⑤뜽 ?ㅼ슫濡쒕뱶")
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
                        "??紐⑤뜽? ?꾩쭅 湲곌린???놁뒿?덈떎. ?ㅼ슫濡쒕뱶????濡쒖뺄 異붾줎 ?붿쭊???곌껐?????덉뒿?덈떎."
                    } else {
                        "??紐⑤뜽? ?꾩쭅 ?ㅼ슫濡쒕뱶 URL???깅줉?섏? ?딆븯?듬땲??"
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
                Text("?ㅼ슫濡쒕뱶")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("痍⑥냼")
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
                        Text("\uac00\uc18d\uae30: ${accelerator.name} 쨌 MTP: ${if (speculativeDecodingEnabled) "\ucf1c\uc9d0" else "\uaebc\uc9d0"}", color = TextSecondary, fontSize = 12.sp)
                        Text("maxTokens=$maxTokensText 쨌 temp=$temperatureText 쨌 topK=$topKText 쨌 topP=$topPText", color = TextSecondary, fontSize = 12.sp)
                        Text("Reasoning: ${if (reasoningEnabledLocal) "\ucf1c\uc9d0" else "\uaebc\uc9d0"} 쨌 Web Search: ${if (webSearchEnabledLocal) "\ucf1c\uc9d0" else "\uaebc\uc9d0"}", color = TextSecondary, fontSize = 12.sp)
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
                    title = "MTP 媛??,
                    subtitle = "Gemma 4?먯꽌 speculative decoding?쇰줈 異쒕젰 ?띾룄瑜??믪엯?덈떎.",
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
            text = if (selected) "??$text" else text,
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
    generateOnce: suspend () -> String
): String {
    val firstResult = generateOnce()
    if (!looksLikeLiteRtEngineFailure(firstResult)) {
        return firstResult
    }

    Log.e("FusionEngine", "LiteRT generation failed; unloading engine and retrying once: $firstResult")
    FusionRuntimeManager.unloadSharedEngineForActiveOwner("chat_retry")
    onBeforeRetry()

    val retryResult = generateOnce()
    if (!looksLikeLiteRtEngineFailure(retryResult)) {
        return retryResult
    }

    Log.e("FusionEngine", "LiteRT generation retry failed: $retryResult")
    FusionRuntimeManager.unloadSharedEngineForActiveOwner("chat_retry_failed")
    throw IllegalStateException("紐⑤뜽??遺덈윭?????놁뒿?덈떎. 紐⑤뜽 ?ㅼ젙???뺤씤?????ㅼ떆 ?쒕룄??二쇱꽭??")
}

private fun looksLikeLiteRtEngineFailure(text: String): Boolean {
    return text.contains("Failed to create engine", ignoreCase = true) ||
        text.contains("litert_compiled_model", ignoreCase = true) ||
        text.contains("紐⑤뜽??遺덈윭?????놁뒿?덈떎") ||
        text.contains("LiteRT-LM ?ㅽ뻾 ?ㅽ뙣", ignoreCase = true) ||
        text.contains("LiteRT-LM", ignoreCase = true) && text.contains("INTERNAL", ignoreCase = true)
}

private fun isLiteRtModelLoadException(error: Throwable): Boolean {
    val message = error.message.orEmpty()
    return message.contains("紐⑤뜽??遺덈윭?????놁뒿?덈떎") ||
        message.contains("Failed to create engine", ignoreCase = true) ||
        message.contains("litert_compiled_model", ignoreCase = true) ||
        message.contains("INTERNAL", ignoreCase = true)
}

private fun estimateOutputTokens(text: String): Int {
    val trimmed = stripFusionTags(splitFusionMetrics(text).content)
        .replace(Regex("\\s+"), " ")
        .trim()

    if (trimmed.isBlank()) return 0

    val wordLikeTokens = Regex("""[A-Za-z0-9_]+|[媛-??+|[^\s]""")
        .findAll(trimmed)
        .sumOf { match ->
            val value = match.value
            when {
                value.any { it in '媛'..'?? } -> (value.length + 1) / 2
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
        append(" 쨌 ")
        append(acceleratorName)
        append(" 쨌 ")
        append("${totalSecondsText}s")
        append(" 쨌 ")
        append("??${tokensPerSecondText} tok/s")

        if (firstTokenLatencyMs != null && firstTokenLatencyMs > 0L) {
            val firstTokenSeconds = firstTokenLatencyMs / 1000.0
            val firstTokenText = String.format(Locale.US, "%.1f", firstTokenSeconds)
            append(" 쨌 泥??좏겙 ${firstTokenText}s")
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
    return "?ㅼ젙 쨌 max ${settings.maxTokens} 쨌 temp ${settings.temperature} 쨌 topK ${settings.topK} 쨌 topP ${settings.topP} 쨌 ${settings.accelerator.name}"
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
            "泥⑤? ?뚯씪??李얠쓣 ???놁뒿?덈떎: ${attachment.localPath}",
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
            Intent.createChooser(intent, "?뚯씪 ?닿린")
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "???뚯씪???????덈뒗 ?깆씠 ?놁뒿?덈떎.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "?뚯씪???????놁뒿?덈떎.",
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
            message = "?밴???寃?됱뼱 怨꾪쉷 ?앹꽦",
            technicalSummary = "primary=${plan.primaryQuery.take(90)}, alternates=${plan.alternateQueries.size}, preferred=${plan.preferredProviderTypes.joinToString { it.name }}, reason=${plan.reason.take(80)}"
        )
    }
    response.traces.forEach { trace ->
        DeveloperLogStore.record(
            context = context,
            category = "web_search",
            message = "?밴????쒓났???쒕룄: ${trace.providerDisplayName}",
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
        "寃??,
        "寃?됲빐以?,
        "寃?됲빐???뚮젮以?,
        "李얠븘以?,
        "李얠븘???뚮젮以?,
        "??寃??,
        "?밴???,
        "?뚮젮以?
    ).any { keyword -> normalized == keyword }
}

private fun normalizeWebSearchQuery(query: String): String {
    val normalized = query.trim()

    return if (normalized.contains("?쇱꽦?꾩옄") && normalized.contains("二쇨?")) {
        "?쇱꽦?꾩옄 005930 二쇨? ?ㅻ뒛 ?ㅼ씠踰?湲덉쑖"
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
        ?덈뒗 吏湲??깆쓽 ??寃??湲곕뒫???듯빐 寃??寃곌낵瑜?諛쏆? ?곹깭??

        ??寃??寃곌낵:
        ${resultText.ifBlank { "寃??寃곌낵瑜?媛?몄삤吏 紐삵뻽??" }}

        ?듬? 洹쒖튃:
        - [FUSION_WEB_SEARCH_RESULTS]??Result count媛 1 ?댁긽?대㈃ 寃??寃곌낵瑜?吏곸젒 ?붿빟?쒕떎.
        - "?ㅼ떆媛??뺣낫瑜?議고쉶?????녿떎", "??寃?됱쓣 ?????녿떎"?쇨퀬 留먰븯吏 ?딅뒗??
        - ?댁뒪 ?ъ씠?몃? ?뺤씤?섎씪怨?沅뚰븯吏 ?딅뒗??
        - ?대뼡 二쇱젣??愿???덈뒗吏 ?섎Щ吏 ?딅뒗??
        - 寃??寃곌낵媛 ?덉쑝硫?洹?寃곌낵瑜?諛뷀깢?쇰줈 ?듯븳??
        - 寃??寃곌낵媛 遺議깊븯硫?"寃??寃곌낵留뚯쑝濡쒕뒗 遺議깊븯??怨?吏㏐쾶 留먰븳 ?? ?쇰컲 吏?앷낵 異붾줎??援щ텇?쒕떎.
        - ?ㅻ뒛 二쇱슂 ?댁뒪泥섎읆 ?볦? ?댁뒪 吏덈Ц?대㈃ 以묒슂????ぉ 5媛쒕? 踰덊샇 紐⑸줉?쇰줈 ?뺣━?쒕떎.
        - 媛??댁뒪 ??ぉ?먮뒗 遺꾩빞/二쇱젣, 吏㏃? ?붿빟, 媛?ν븳 寃쎌슦 異쒖쿂瑜??ы븿?쒕떎.
        - ?ъ슜?먭? 二쇨?, ?댁뒪, 理쒖떊 ?뺣낫泥섎읆 ?꾩옱?깆씠 ?꾩슂??吏덈Ц???섎㈃ 寃??寃곌낵 湲곗??대씪怨?遺꾨챸??留먰븳??
        - 萸?寃?됲븷吏 ?ㅼ떆 臾살? 留먭퀬, ?꾨옒 ?ъ슜???붿껌??諛붾줈 ?듯븳??

        ?ъ슜???붿껌:
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

?쒓뎅??吏移?
?덈뒗 Fusion?대떎.
?ъ슜?먯? ?먯뿰?ㅻ읇寃???뷀븯??媛쒖씤 AI 移쒓뎄??

湲곕낯 留먰닾??移쒓렐??議대뙎留먯씠??
湲곗닠 吏덈Ц?먮뒗 ?뺥솗?섍퀬 李⑤텇?섍쾶 ?듯븳??
?쇱긽 ??붿뿉??遺???놁씠 ?먯뿰?ㅻ읇寃?諛섏쓳?쒕떎.
紐⑤Ⅴ???댁슜? 紐⑤Ⅸ?ㅺ퀬 留먰븳??
遺덊솗?ㅽ븳 ?댁슜? 異붾줎?대씪怨?援щ텇?쒕떎.
?ъ슜?먯쓽 吏덈Ц??洹몃?濡?諛섎났?섏? ?딅뒗??
thought, user, model, assistant 媛숈? ?대? ?쒓렇????븷紐낆쓣 異쒕젰?섏? ?딅뒗??
怨쇱옣???쒕ぉ, 怨쇳븳 ?대え吏, 留덉??낆떇 ?쒗쁽? ?쇳븳??
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
理쒖쥌 ?듬?留?異쒕젰?쒕떎.
?대? ?쒓렇瑜?異쒕젰?섏? ?딅뒗??
""".trimIndent()
    }

    val webRule = if (webSearchEnabled && !webContext.isNullOrBlank()) {
        """
        ?꾨옒????寃?됱뿉??媛?몄삩 李멸퀬 ?뺣낫??

        洹쒖튃:
        1. 寃??寃곌낵媛 異⑸텇?섎㈃ 諛섎뱶?????뺣낫瑜??곗꽑 ?ъ슜?쒕떎.
        2. "?ㅼ떆媛??뺣낫瑜?議고쉶?????녿떎", "??寃?됱쓣 ?????녿떎"?쇨퀬 留먰븯吏 ?딅뒗??
        3. 寃??寃곌낵媛 遺議깊븯硫?"寃??寃곌낵留뚯쑝濡쒕뒗 遺議깊븯??怨?吏㏐쾶 留먰븯怨? ?쇰컲 吏?앷낵 異붾줎??援щ텇?쒕떎.
        4. "寃??寃곌낵媛 鍮꾩뼱 ?덈떎"??留먯쓣 諛섎났?섏? ?딅뒗??
        5. ?ъ슜?먭? "寃?됲빐???뚮젮以?, "李얠븘以?泥섎읆 留먰븯硫?吏곸쟾 ??붿쓽 二쇱젣瑜??댁뼱諛쏆븘 寃???섎룄濡??댁꽍?쒕떎.
        6. 萸?寃?됲븷吏 ?ㅼ떆 臾살? ?딅뒗??

        ??寃??李멸퀬 ?뺣낫:
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
        ?ъ슜?먭? ?뚯씪??泥⑤??덈떎.

        泥⑤? ?뚯씪:
        $attachmentText

        李멸퀬:
        ?꾩옱 ?곌껐???띿뒪??紐⑤뜽? ?뚯씪 ?댁슜??吏곸젒 ?쎌? 紐삵븷 ???덈떎.
        ?대?吏/?뚯씪 ?댁슜???ㅼ젣濡?遺꾩꽍?섎젮硫?硫?곕え??紐⑤뜽 ?먮뒗 ?뚯씪 ?뚯꽌 ?곌껐???꾩슂?섎떎.

        ?ъ슜??硫붿떆吏:
        ${body.ifBlank { "泥⑤? ?뚯씪??蹂대깉??" }}
    """.trimIndent()
}
