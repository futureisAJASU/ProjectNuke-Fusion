package com.projectnuke.fusion.ai.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectnuke.fusion.ai.data.AiProviderRepository
import com.projectnuke.fusion.ai.model.AiProviderConfig
import com.projectnuke.fusion.ai.model.AiProviderType
import com.projectnuke.fusion.ai.network.OpenAiCompatibleClient
import com.projectnuke.fusion.ai.provider.AiProviderPresets
import com.projectnuke.fusion.ai.secure.AndroidKeystoreSecretStore
import com.projectnuke.fusion.ai.usecase.TestAiProviderUseCase
import kotlinx.coroutines.launch

private val PanelColor = Color(0xFF151515)
private val CardColor = Color(0xFF202020)
private val CardSelectedColor = Color(0xFF24384A)
private val TextPrimary = Color(0xFFF4F4F4)
private val TextSecondary = Color(0xFFB8B8B8)
private val AccentColor = Color(0xFF8CCBFF)
private val SuccessColor = Color(0xFF9BE2AF)
private val WarningColor = Color(0xFFFFD38A)
private val DangerColor = Color(0xFFFF8A8A)

private data class ProviderStatus(
    val title: String,
    val detail: String,
    val color: Color
)

private data class ProviderDraft(
    val id: String,
    val type: AiProviderType,
    val displayName: String,
    val baseUrl: String,
    val modelId: String,
    val apiKeySecretId: String?,
    val apiKeyInput: String,
    val isEnabled: Boolean,
    val temperatureText: String,
    val maxTokensText: String
)

@Composable
fun AiProviderSettingsScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val secretStore = remember { AndroidKeystoreSecretStore(context) }
    val repository = remember { AiProviderRepository(context, secretStore) }
    val tester = remember { TestAiProviderUseCase(OpenAiCompatibleClient(secretStore)) }
    val scope = rememberCoroutineScope()

    var providers by remember { mutableStateOf<List<AiProviderConfig>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var displayName by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AiProviderType.OPENAI) }
    var apiKey by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var temperatureText by remember { mutableStateOf("0.7") }
    var maxTokensText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun loadEditor(config: AiProviderConfig?) {
        val target = config ?: AiProviderPresets.OpenAi
        selectedId = target.id
        type = target.type
        displayName = target.displayName
        baseUrl = target.baseUrl
        modelId = target.modelId
        apiKey = ""
        enabled = target.isEnabled
        temperatureText = target.temperature.toString()
        maxTokensText = target.maxTokens?.toString().orEmpty()
        statusText = null
    }

    fun currentDraft(): ProviderDraft? {
        val id = selectedId ?: return null
        val previous = providers.firstOrNull { it.id == id }
        return ProviderDraft(
            id = id,
            type = type,
            displayName = displayName.trim(),
            baseUrl = baseUrl.trim(),
            modelId = modelId.trim(),
            apiKeySecretId = previous?.apiKeySecretId,
            apiKeyInput = apiKey.trim(),
            isEnabled = enabled,
            temperatureText = temperatureText.trim(),
            maxTokensText = maxTokensText.trim()
        )
    }

    fun ProviderDraft.toConfig(): AiProviderConfig {
        return AiProviderConfig(
            id = id,
            type = type,
            displayName = displayName,
            baseUrl = baseUrl,
            modelId = modelId,
            apiKeySecretId = apiKeySecretId,
            isEnabled = isEnabled,
            temperature = temperatureText.toDoubleOrNull() ?: 0.7,
            maxTokens = maxTokensText.toIntOrNull()
        )
    }

    fun buildProviderStatus(config: AiProviderConfig, hasApiKey: Boolean): ProviderStatus {
        if (!config.isEnabled) {
            return ProviderStatus("비활성화됨", "이 제공자는 현재 사용하지 않습니다.", TextSecondary)
        }
        val missing = buildList {
            if (!hasApiKey) add("API 키")
            if (config.baseUrl.isBlank()) add("Base URL")
            if (config.modelId.isBlank()) add("모델 ID")
        }
        if (missing.isEmpty()) {
            return ProviderStatus("사용 가능", "즉시 외부 AI API 대화에 사용할 수 있습니다.", SuccessColor)
        }
        return ProviderStatus(
            title = "설정 필요",
            detail = "${missing.joinToString(", ")} 항목을 확인해 주세요.",
            color = WarningColor
        )
    }

    fun validateForSave(draft: ProviderDraft): String? {
        return when {
            draft.displayName.isBlank() -> "표시 이름을 입력해 주세요."
            draft.baseUrl.isBlank() -> "Base URL을 입력해 주세요."
            draft.modelId.isBlank() -> "모델 ID를 입력해 주세요."
            else -> null
        }
    }

    suspend fun refreshProviders() {
        providers = repository.getProviders()
        loadEditor(repository.getSelectedProvider() ?: providers.firstOrNull())
    }

    LaunchedEffect(Unit) {
        refreshProviders()
    }

    val currentDraft = currentDraft()
    val editorStatus = currentDraft?.let {
        buildProviderStatus(
            config = it.toConfig(),
            hasApiKey = it.apiKeyInput.isNotBlank() || !it.apiKeySecretId.isNullOrBlank()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelColor,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = { Text("외부 AI API 설정") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "Fusion은 외부 AI 모델을 직접 제공하지 않습니다. 사용자가 직접 발급한 OpenAI 호환 API 정보를 기기 내에 저장하여 연결합니다. 입력한 API 키는 Android Keystore로 보호되며 Fusion 서버로 전송되지 않습니다.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }

                item { SectionTitle("제공자 목록") }
                items(providers, key = { it.id }) { provider ->
                    ProviderRow(
                        config = provider,
                        selected = provider.id == selectedId,
                        onClick = {
                            loadEditor(provider)
                            scope.launch { repository.setSelectedProvider(provider.id) }
                        }
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PresetButton("OpenAI") { loadEditor(AiProviderPresets.OpenAi) }
                        PresetButton("NVIDIA NIM") { loadEditor(AiProviderPresets.NvidiaNim) }
                    }
                }
                item {
                    PresetButton("Custom OpenAI-Compatible") {
                        loadEditor(AiProviderPresets.CustomOpenAiCompatible)
                    }
                }

                item { SectionTitle("편집") }
                item {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("표시 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = modelId,
                        onValueChange = { modelId = it },
                        label = { Text("모델 ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API 키") },
                        placeholder = { Text("새 API 키를 입력하면 저장된 키를 교체합니다.") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = temperatureText,
                            onValueChange = { temperatureText = it },
                            label = { Text("Temperature") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxTokensText,
                            onValueChange = { maxTokensText = it },
                            label = { Text("Max tokens") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                        Text(
                            text = "이 제공자를 사용합니다.",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
                editorStatus?.let { providerStatus ->
                    item {
                        StatusCard(
                            title = providerStatus.title,
                            detail = providerStatus.detail,
                            color = providerStatus.color
                        )
                    }
                }
                statusText?.let { message ->
                    item { Text(message, color = TextSecondary, fontSize = 12.sp) }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !busy,
                            onClick = {
                                val draft = currentDraft ?: return@Button
                                validateForSave(draft)?.let {
                                    statusText = it
                                    return@Button
                                }
                                busy = true
                                scope.launch {
                                    repository.saveProvider(draft.toConfig(), draft.apiKeyInput.ifBlank { null })
                                    repository.setSelectedProvider(draft.id)
                                    refreshProviders()
                                    apiKey = ""
                                    busy = false
                                    statusText = "설정을 저장했습니다."
                                }
                            }
                        ) { Text("저장") }

                        Button(
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color.Black),
                            onClick = {
                                val draft = currentDraft ?: return@Button
                                validateForSave(draft)?.let {
                                    statusText = it
                                    return@Button
                                }
                                if (draft.apiKeyInput.isBlank() && draft.apiKeySecretId.isNullOrBlank()) {
                                    statusText = "API 키를 입력한 뒤 연결 테스트를 진행해 주세요."
                                    return@Button
                                }
                                busy = true
                                scope.launch {
                                    val savedConfig = if (draft.apiKeyInput.isNotBlank()) {
                                        repository.saveProvider(draft.toConfig(), draft.apiKeyInput)
                                        repository.getProviders().firstOrNull { it.id == draft.id } ?: draft.toConfig()
                                    } else {
                                        draft.toConfig()
                                    }
                                    val result = tester(savedConfig)
                                    statusText = result.getOrElse { it.message ?: "연결 테스트에 실패했습니다." }
                                    apiKey = ""
                                    busy = false
                                }
                            }
                        ) { Text("테스트") }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                val draft = currentDraft ?: return@TextButton
                                val secretId = draft.apiKeySecretId
                                if (secretId.isNullOrBlank()) {
                                    statusText = "삭제할 API 키가 없습니다."
                                    return@TextButton
                                }
                                busy = true
                                scope.launch {
                                    secretStore.deleteSecret(secretId)
                                    repository.saveProvider(draft.toConfig().copy(apiKeySecretId = null), rawApiKey = null)
                                    refreshProviders()
                                    busy = false
                                    statusText = "저장된 API 키를 삭제했습니다."
                                }
                            }
                        ) { Text("API 키 삭제", color = DangerColor) }

                        TextButton(
                            enabled = !busy,
                            onClick = {
                                val id = selectedId ?: return@TextButton
                                if (id in AiProviderPresets.defaults.map { it.id }) {
                                    Toast.makeText(context, "기본 제공자는 삭제할 수 없습니다. 비활성화로 관리해 주세요.", Toast.LENGTH_SHORT).show()
                                    return@TextButton
                                }
                                busy = true
                                scope.launch {
                                    repository.deleteProvider(id)
                                    refreshProviders()
                                    busy = false
                                }
                            }
                        ) { Text("제공자 삭제", color = DangerColor) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기", color = AccentColor)
            }
        }
    )
}

@Composable
private fun ProviderRow(
    config: AiProviderConfig,
    selected: Boolean,
    onClick: () -> Unit
) {
    val status = when {
        !config.isEnabled -> ProviderStatus("비활성화됨", "현재 사용하지 않습니다.", TextSecondary)
        config.apiKeySecretId.isNullOrBlank() && config.baseUrl.isBlank() && config.modelId.isBlank() ->
            ProviderStatus("설정 필요", "API 키, Base URL, 모델 ID가 필요합니다.", WarningColor)
        config.apiKeySecretId.isNullOrBlank() ->
            ProviderStatus("설정 필요", "API 키가 필요합니다.", WarningColor)
        config.baseUrl.isBlank() ->
            ProviderStatus("설정 필요", "Base URL이 필요합니다.", WarningColor)
        config.modelId.isBlank() ->
            ProviderStatus("설정 필요", "모델 ID가 필요합니다.", WarningColor)
        else -> ProviderStatus("사용 가능", "외부 AI API 대화에 사용할 수 있습니다.", SuccessColor)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) CardSelectedColor else CardColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(config.displayName.ifBlank { "이름 없는 제공자" }, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                config.baseUrl.ifBlank { "Base URL이 아직 없습니다." },
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${status.title} · ${status.detail}",
                color = status.color,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    detail: String,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(3.dp))
            Text(detail, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun PresetButton(
    text: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = AccentColor,
        fontSize = 13.sp,
        modifier = Modifier
            .background(Color(0xFF1E2A32), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}
