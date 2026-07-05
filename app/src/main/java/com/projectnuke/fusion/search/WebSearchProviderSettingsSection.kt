package com.projectnuke.fusion.search

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun WebSearchProviderSettingsSection(
    repository: WebSearchProviderRepository,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(WebSearchMode.AUTO) }
    var providers by remember { mutableStateOf<List<WebSearchProviderConfig>>(emptyList()) }
    var selectedId by remember { mutableStateOf(WebSearchProviderRepository.DefaultFree.id) }
    var selectedProvider by remember { mutableStateOf<WebSearchProviderConfig?>(null) }
    var apiKey by remember(selectedId) { mutableStateOf("") }
    var clientId by remember(selectedId) { mutableStateOf("") }
    var clientSecret by remember(selectedId) { mutableStateOf("") }
    var baseUrl by remember(selectedProvider?.baseUrl) { mutableStateOf(selectedProvider?.baseUrl.orEmpty()) }
    var statusText by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        mode = repository.selectedMode()
        providers = repository.listProviders()
        selectedProvider = repository.selectedProvider()
        selectedId = selectedProvider?.id ?: WebSearchProviderRepository.DefaultFree.id
        baseUrl = selectedProvider?.baseUrl.orEmpty()
    }

    LaunchedEffect(repository) { refresh() }

    fun saveSelectedProvider(update: WebSearchProviderConfig, message: String) {
        scope.launch {
            repository.saveProvider(
                config = update.copy(baseUrl = baseUrl.ifBlank { update.baseUrl }),
                rawApiKey = apiKey.takeIf { it.isNotBlank() },
                rawClientId = clientId.takeIf { it.isNotBlank() },
                rawClientSecret = clientSecret.takeIf { it.isNotBlank() }
            )
            repository.setSelectedProvider(update.id)
            statusText = message
            apiKey = ""
            clientId = ""
            clientSecret = ""
            refresh()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF151515)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
        ) {
            Text("웹 검색 제공자", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "무료 기본 검색은 별도 API 없이 사용할 수 있지만 검색 결과가 제한적이거나 일시적으로 실패할 수 있습니다.",
                color = Color(0xFFB8C0CC),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Text(
                "자동 모드에서는 검색 결과 품질이 낮을 때 다른 제공자를 시도합니다. 수동 모드에서는 선택한 검색 제공자를 우선 사용합니다.",
                color = Color(0xFFB8C0CC),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                ModeButton("자동", mode == WebSearchMode.AUTO) {
                    scope.launch {
                        repository.setSelectedMode(WebSearchMode.AUTO)
                        refresh()
                    }
                }
                Spacer(Modifier.width(8.dp))
                ModeButton("수동", mode == WebSearchMode.MANUAL) {
                    scope.launch {
                        repository.setSelectedMode(WebSearchMode.MANUAL)
                        refresh()
                    }
                }
            }

            providers.forEach { provider ->
                val selected = provider.id == selectedId
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (selected) Color(0xFF79B8FF) else Color(0xFF30343A), RoundedCornerShape(10.dp))
                        .clickable {
                            scope.launch {
                                repository.setSelectedProvider(provider.id)
                                refresh()
                            }
                        },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) Color(0xFF102233) else Color(0xFF101010)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(provider.displayName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(provider.type.name, color = Color(0xFF9EA7B3), fontSize = 11.sp)
                        }
                        if (provider.type == WebSearchProviderType.FREE_DEFAULT) {
                            Text("항상 사용", color = Color(0xFF9EA7B3), fontSize = 11.sp)
                        } else {
                            Switch(
                                checked = provider.isEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        repository.saveProvider(provider.copy(isEnabled = enabled))
                                        refresh()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            selectedProvider?.let { provider ->
                Text("선택된 제공자: ${provider.displayName}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (provider.type == WebSearchProviderType.FREE_DEFAULT) {
                    Text("무료 기본 검색은 기본 대체 검색으로 항상 사용할 수 있습니다.", color = Color(0xFFB8C0CC), fontSize = 12.sp)
                } else {
                    if (provider.type == WebSearchProviderType.CUSTOM_COMPATIBLE) {
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = { Text("Base URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = provider.noAuth,
                                onCheckedChange = { checked ->
                                    saveSelectedProvider(provider.copy(noAuth = checked), "설정을 저장했습니다.")
                                }
                            )
                            Text("인증 없이 사용", color = Color(0xFFB8C0CC), fontSize = 12.sp)
                        }
                    }
                    if (provider.type == WebSearchProviderType.NAVER) {
                        OutlinedTextField(
                            value = clientId,
                            onValueChange = { clientId = it },
                            label = { Text("Client ID") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = clientSecret,
                            onValueChange = { clientSecret = it },
                            label = { Text("Client Secret") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (!provider.noAuth) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("API Key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = provider.allowFallbackInManualMode,
                            onCheckedChange = { checked ->
                                saveSelectedProvider(provider.copy(allowFallbackInManualMode = checked), "설정을 저장했습니다.")
                            }
                        )
                        Text("수동 모드에서 무료 기본 검색으로 대체를 허용", color = Color(0xFFB8C0CC), fontSize = 12.sp)
                    }
                    TextButton(onClick = { saveSelectedProvider(provider.copy(isEnabled = true, baseUrl = baseUrl.ifBlank { provider.baseUrl }), "검색 제공자 설정을 저장했습니다.") }) {
                        Text("제공자 저장", color = Color(0xFF79B8FF))
                    }
                }
            }

            Text(
                "외부 검색 API는 각 제공자의 요금, 사용량, 호출 정책에 따라 제한될 수 있습니다.",
                color = Color(0xFFFFC774),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            statusText?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, color = Color(0xFF79B8FF), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Color(0xFF1D4E79) else Color(0xFF22252B)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
