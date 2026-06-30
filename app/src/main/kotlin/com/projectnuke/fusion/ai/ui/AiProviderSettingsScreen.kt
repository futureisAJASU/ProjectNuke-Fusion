package com.projectnuke.fusion.ai.ui

import android.content.Context
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
private val TextPrimary = Color(0xFFF4F4F4)
private val TextSecondary = Color(0xFFB8B8B8)
private val AccentColor = Color(0xFF8CCBFF)
private val DangerColor = Color(0xFFFF8A8A)

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
        statusText = if (target.apiKeySecretId == null) "API 키가 저장되어 있지 않습니다." else "API 키가 저장되어 있습니다."
    }

    fun currentConfig(): AiProviderConfig? {
        val id = selectedId ?: return null
        val previous = providers.firstOrNull { it.id == id }
        return AiProviderConfig(
            id = id,
            type = type,
            displayName = displayName.trim(),
            baseUrl = baseUrl.trim(),
            modelId = modelId.trim(),
            apiKeySecretId = previous?.apiKeySecretId,
            isEnabled = enabled,
            temperature = temperatureText.toDoubleOrNull() ?: 0.7,
            maxTokens = maxTokensText.toIntOrNull()
        )
    }

    fun refreshProviders() {
        scope.launch {
            providers = repository.getProviders()
            loadEditor(repository.getSelectedProvider() ?: providers.firstOrNull())
        }
    }

    LaunchedEffect(Unit) {
        providers = repository.getProviders()
        loadEditor(repository.getSelectedProvider() ?: providers.firstOrNull())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelColor,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = { Text("AI API 제공자 설정") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "Fusion은 AI 모델을 직접 제공하지 않습니다. 사용자가 직접 발급한 API 키를 사용하여 외부 OpenAI 호환 API 제공자에 연결합니다. API 키, 사용량, 요금, 모델 라이선스, 이용 약관은 각 API 제공자의 정책을 따릅니다. 입력한 API 키는 기기 내에 암호화되어 저장되며 Fusion 서버로 전송되지 않습니다. 단, 프롬프트와 첨부 데이터는 선택한 API 제공자에게 전송됩니다.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }

                item { SectionTitle("제공자") }
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
                        placeholder = { Text("새 API 키를 입력하면 저장된 키가 교체됩니다.") },
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

                statusText?.let { message ->
                    item { Text(message, color = TextSecondary, fontSize = 12.sp) }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !busy,
                            onClick = {
                                val config = currentConfig()
                                if (config == null || config.displayName.isBlank() || config.baseUrl.isBlank() || config.modelId.isBlank()) {
                                    statusText = "표시 이름, Base URL, 모델 ID를 입력해 주세요."
                                    return@Button
                                }
                                busy = true
                                scope.launch {
                                    repository.saveProvider(config, apiKey.takeIf { it.isNotBlank() })
                                    repository.setSelectedProvider(config.id)
                                    providers = repository.getProviders()
                                    apiKey = ""
                                    busy = false
                                    statusText = "저장되었습니다."
                                }
                            }
                        ) { Text("저장") }

                        Button(
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color.Black),
                            onClick = {
                                val config = currentConfig()
                                if (config == null) return@Button
                                busy = true
                                scope.launch {
                                    val savedConfig = if (apiKey.isNotBlank()) {
                                        repository.saveProvider(config, apiKey)
                                        repository.getProviders().firstOrNull { it.id == config.id } ?: config
                                    } else {
                                        config
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
                                val config = currentConfig() ?: return@TextButton
                                val secretId = config.apiKeySecretId
                                if (secretId == null) {
                                    statusText = "삭제할 API 키가 없습니다."
                                    return@TextButton
                                }
                                busy = true
                                scope.launch {
                                    secretStore.deleteSecret(secretId)
                                    repository.saveProvider(config.copy(apiKeySecretId = null), rawApiKey = null)
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
                                    Toast.makeText(context, "기본 제공자는 삭제하지 않고 비활성화해 주세요.", Toast.LENGTH_SHORT).show()
                                    return@TextButton
                                }
                                busy = true
                                scope.launch {
                                    repository.deleteProvider(id)
                                    providers = repository.getProviders()
                                    loadEditor(repository.getSelectedProvider() ?: providers.firstOrNull())
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF24384A) else CardColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(config.displayName, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(3.dp))
            Text(config.modelId.ifBlank { "모델 ID가 필요합니다." }, color = TextSecondary, fontSize = 12.sp)
            Text(
                if (config.apiKeySecretId == null) "API 키 없음" else "API 키 저장됨",
                color = TextSecondary,
                fontSize = 12.sp
            )
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
