package com.projectnuke.fusion.search

import android.content.Context
import android.content.SharedPreferences
import com.projectnuke.fusion.ai.secure.SecretStore
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class WebSearchProviderRepository(
    context: Context,
    private val secretStore: SecretStore
) {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    suspend fun listProviders(): List<WebSearchProviderConfig> = withContext(Dispatchers.IO) {
        val stored = prefs.getString(KeyProviders, null)?.let(::parseProviders).orEmpty()
        mergeDefaults(stored)
    }

    suspend fun selectedMode(): WebSearchMode = withContext(Dispatchers.IO) {
        runCatching {
            WebSearchMode.valueOf(prefs.getString(KeyMode, WebSearchMode.AUTO.name) ?: WebSearchMode.AUTO.name)
        }.getOrDefault(WebSearchMode.AUTO)
    }

    suspend fun setSelectedMode(mode: WebSearchMode) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KeyMode, mode.name).apply()
    }

    suspend fun selectedProvider(): WebSearchProviderConfig {
        val providers = listProviders()
        val selectedId = withContext(Dispatchers.IO) { prefs.getString(KeySelectedProvider, DefaultFree.id) }
        return providers.firstOrNull { it.id == selectedId } ?: providers.first()
    }

    suspend fun setSelectedProvider(id: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KeySelectedProvider, id).apply()
    }

    suspend fun saveProvider(
        config: WebSearchProviderConfig,
        rawApiKey: String? = null,
        rawClientId: String? = null,
        rawClientSecret: String? = null
    ) {
        val safeConfig = saveSecrets(config, rawApiKey, rawClientId, rawClientSecret)
        withContext(Dispatchers.IO) {
            val updated = listProviders().filterNot { it.id == safeConfig.id } + safeConfig
            prefs.edit().putString(KeyProviders, providersToJson(updated).toString()).apply()
            if (prefs.getString(KeySelectedProvider, null) == null) {
                prefs.edit().putString(KeySelectedProvider, safeConfig.id).apply()
            }
        }
    }

    suspend fun deleteProvider(id: String) {
        val existing = listProviders().firstOrNull { it.id == id } ?: return
        listOf(existing.apiKeySecretId, existing.clientIdSecretId, existing.clientSecretSecretId)
            .filterNotNull()
            .forEach { secretStore.deleteSecret(it) }
        withContext(Dispatchers.IO) {
            val updated = listProviders().filterNot { it.id == id }
            prefs.edit().putString(KeyProviders, providersToJson(updated).toString()).apply()
            if (prefs.getString(KeySelectedProvider, null) == id) {
                prefs.edit().putString(KeySelectedProvider, DefaultFree.id).apply()
            }
        }
    }

    suspend fun setProviderEnabled(id: String, enabled: Boolean) {
        val provider = listProviders().firstOrNull { it.id == id } ?: return
        saveProvider(provider.copy(isEnabled = enabled))
    }

    suspend fun isRunnable(config: WebSearchProviderConfig): Boolean {
        if (!config.isEnabled) return false
        return missingRequirements(config).isEmpty()
    }

    suspend fun missingRequirements(config: WebSearchProviderConfig): List<String> {
        suspend fun hasSecret(id: String?): Boolean = !id.isNullOrBlank() && !secretStore.getSecret(id).isNullOrBlank()
        return when (config.type) {
            WebSearchProviderType.FREE_DEFAULT -> emptyList()
            WebSearchProviderType.NAVER -> buildList {
                if (!hasSecret(config.clientIdSecretId)) add("clientId")
                if (!hasSecret(config.clientSecretSecretId)) add("clientSecret")
            }
            WebSearchProviderType.KAKAO_DAUM,
            WebSearchProviderType.EXA,
            WebSearchProviderType.BRAVE -> buildList {
                if (!hasSecret(config.apiKeySecretId)) add("apiKey")
            }
            WebSearchProviderType.CUSTOM_COMPATIBLE -> buildList {
                if (config.baseUrl.isNullOrBlank()) add("baseUrl")
                if (!config.noAuth && !hasSecret(config.apiKeySecretId)) add("apiKey")
            }
        }
    }

    suspend fun getSecretValue(secretId: String?): String? {
        return secretId?.takeIf { it.isNotBlank() }?.let { secretStore.getSecret(it) }
    }

    fun observeChanges(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KeyProviders || key == KeySelectedProvider || key == KeyMode) {
                trySend(Unit)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private suspend fun saveSecrets(
        config: WebSearchProviderConfig,
        rawApiKey: String?,
        rawClientId: String?,
        rawClientSecret: String?
    ): WebSearchProviderConfig {
        suspend fun saveSecret(prefix: String, currentId: String?, value: String?): String? {
            val trimmed = value?.trim().orEmpty()
            if (trimmed.isBlank()) return currentId
            val id = currentId ?: "${prefix}_${config.id}_${UUID.randomUUID()}"
            secretStore.putSecret(id, trimmed)
            return id
        }
        return config.copy(
            apiKeySecretId = saveSecret("web_search_api_key", config.apiKeySecretId, rawApiKey),
            clientIdSecretId = saveSecret("web_search_client_id", config.clientIdSecretId, rawClientId),
            clientSecretSecretId = saveSecret("web_search_client_secret", config.clientSecretSecretId, rawClientSecret)
        )
    }

    private fun mergeDefaults(stored: List<WebSearchProviderConfig>): List<WebSearchProviderConfig> {
        val byId = stored.associateBy { it.id }
        val defaults = defaultProviders.map { byId[it.id] ?: it }
        val extras = stored.filterNot { config -> defaultProviders.any { it.id == config.id } }
        return (defaults + extras).sortedBy { it.priority }
    }

    private fun parseProviders(raw: String): List<WebSearchProviderConfig> {
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        WebSearchProviderConfig(
                            id = obj.getString("id"),
                            displayName = obj.optString("displayName"),
                            type = WebSearchProviderType.valueOf(obj.getString("type")),
                            isEnabled = obj.optBoolean("isEnabled", true),
                            baseUrl = obj.optString("baseUrl").takeIf { it.isNotBlank() },
                            apiKeySecretId = obj.optString("apiKeySecretId").takeIf { it.isNotBlank() },
                            clientIdSecretId = obj.optString("clientIdSecretId").takeIf { it.isNotBlank() },
                            clientSecretSecretId = obj.optString("clientSecretSecretId").takeIf { it.isNotBlank() },
                            defaultSearchCategory = obj.optString("defaultSearchCategory").takeIf { it.isNotBlank() },
                            allowFallbackInManualMode = obj.optBoolean("allowFallbackInManualMode", false),
                            priority = obj.optInt("priority", 100),
                            noAuth = obj.optBoolean("noAuth", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun providersToJson(providers: List<WebSearchProviderConfig>): JSONArray {
        return JSONArray().also { array ->
            providers.forEach { config ->
                array.put(
                    JSONObject()
                        .put("id", config.id)
                        .put("displayName", config.displayName)
                        .put("type", config.type.name)
                        .put("isEnabled", config.isEnabled)
                        .put("baseUrl", config.baseUrl)
                        .put("apiKeySecretId", config.apiKeySecretId)
                        .put("clientIdSecretId", config.clientIdSecretId)
                        .put("clientSecretSecretId", config.clientSecretSecretId)
                        .put("defaultSearchCategory", config.defaultSearchCategory)
                        .put("allowFallbackInManualMode", config.allowFallbackInManualMode)
                        .put("priority", config.priority)
                        .put("noAuth", config.noAuth)
                )
            }
        }
    }

    companion object {
        private const val PrefsName = "fusion_web_search_provider_configs"
        private const val KeyProviders = "providers"
        private const val KeySelectedProvider = "selected_web_search_provider_id"
        private const val KeyMode = "web_search_mode"

        val DefaultFree = WebSearchProviderConfig(
            id = "free_default",
            displayName = "무료 기본 검색",
            type = WebSearchProviderType.FREE_DEFAULT,
            isEnabled = true,
            priority = 0
        )

        val defaultProviders = listOf(
            DefaultFree,
            WebSearchProviderConfig("naver", "Naver", WebSearchProviderType.NAVER, isEnabled = false, priority = 10),
            WebSearchProviderConfig("kakao_daum", "Kakao/Daum", WebSearchProviderType.KAKAO_DAUM, isEnabled = false, priority = 20),
            WebSearchProviderConfig("exa", "Exa", WebSearchProviderType.EXA, isEnabled = false, priority = 30),
            WebSearchProviderConfig("brave", "Brave", WebSearchProviderType.BRAVE, isEnabled = false, priority = 40),
            WebSearchProviderConfig("custom", "Custom Search", WebSearchProviderType.CUSTOM_COMPATIBLE, isEnabled = false, priority = 50)
        )
    }
}
