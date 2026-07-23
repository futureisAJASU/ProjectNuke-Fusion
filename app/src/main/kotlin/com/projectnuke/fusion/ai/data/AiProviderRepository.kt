package com.projectnuke.fusion.ai.data

import android.content.Context
import android.content.SharedPreferences
import com.projectnuke.fusion.ai.ExternalAiProviderSource
import com.projectnuke.fusion.ai.model.AiProviderConfig
import com.projectnuke.fusion.ai.model.AiProviderType
import com.projectnuke.fusion.ai.provider.AiProviderPresets
import com.projectnuke.fusion.ai.secure.SecretStore
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

class AiProviderRepository(
    context: Context,
    private val secretStore: SecretStore
) : ExternalAiProviderSource {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    suspend fun getProviders(): List<AiProviderConfig> {
        return withContext(Dispatchers.IO) {
            val raw = prefs.getString(KeyProviders, null)
            val stored = raw?.let { parseProviders(it) }.orEmpty()
            stored.ifEmpty { AiProviderPresets.defaults }
        }
    }

    suspend fun saveProvider(config: AiProviderConfig, rawApiKey: String?) {
        val trimmedKey = rawApiKey?.trim().orEmpty()
        val secretId = when {
            trimmedKey.isNotBlank() -> config.apiKeySecretId ?: "ai_key_${config.id}_${UUID.randomUUID()}"
            else -> config.apiKeySecretId
        }
        if (trimmedKey.isNotBlank() && secretId != null) {
            secretStore.putSecret(secretId, trimmedKey)
        }
        val safeConfig = config.copy(apiKeySecretId = secretId)
        withContext(Dispatchers.IO) {
            val updated = getProviders().filterNot { it.id == safeConfig.id } + safeConfig
            prefs.edit().putString(KeyProviders, providersToJson(updated).toString()).apply()
            if (prefs.getString(KeySelectedProvider, null) == null) {
                prefs.edit().putString(KeySelectedProvider, safeConfig.id).apply()
            }
        }
    }

    suspend fun deleteProvider(id: String) {
        val existing = getProviders()
        existing.firstOrNull { it.id == id }?.apiKeySecretId?.let { secretStore.deleteSecret(it) }
        withContext(Dispatchers.IO) {
            val updated = existing.filterNot { it.id == id }
            prefs.edit()
                .putString(KeyProviders, providersToJson(updated).toString())
                .apply()
            if (prefs.getString(KeySelectedProvider, null) == id) {
                prefs.edit().putString(KeySelectedProvider, updated.firstOrNull()?.id).apply()
            }
        }
    }

    suspend fun getSelectedProvider(): AiProviderConfig? {
        val providers = getProviders()
        val selectedId = withContext(Dispatchers.IO) { prefs.getString(KeySelectedProvider, null) }
        return providers.firstOrNull { it.id == selectedId } ?: providers.firstOrNull()
    }

    override suspend fun getSelectedRunnableProvider(): AiProviderConfig? {
        val providers = getProviders()
        val selectedId = withContext(Dispatchers.IO) { prefs.getString(KeySelectedProvider, null) }
        val runnableProviders = providers.filter(::isRunnableProvider)
        val resolved = runnableProviders.firstOrNull { it.id == selectedId } ?: runnableProviders.firstOrNull()
        withContext(Dispatchers.IO) {
            val normalizedId = resolved?.id
            if (selectedId != normalizedId) {
                prefs.edit().putString(KeySelectedProvider, normalizedId).apply()
            }
        }
        return resolved
    }

    fun observeProviderChanges(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KeyProviders || key == KeySelectedProvider) {
                trySend(Unit)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    suspend fun setSelectedProvider(id: String) {
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KeySelectedProvider, id).apply()
        }
    }

    private fun isRunnableProvider(provider: AiProviderConfig): Boolean {
        return provider.isEnabled &&
            !provider.apiKeySecretId.isNullOrBlank() &&
            provider.baseUrl.isNotBlank() &&
            provider.modelId.isNotBlank()
    }

    private fun parseProviders(raw: String): List<AiProviderConfig> {
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        AiProviderConfig(
                            id = obj.getString("id"),
                            type = AiProviderType.valueOf(obj.getString("type")),
                            displayName = obj.optString("displayName"),
                            baseUrl = obj.optString("baseUrl"),
                            modelId = obj.optString("modelId"),
                            apiKeySecretId = obj.optString("apiKeySecretId").takeIf { it.isNotBlank() },
                            isEnabled = obj.optBoolean("isEnabled", true),
                            temperature = obj.optDouble("temperature", 0.7),
                            maxTokens = if (obj.has("maxTokens") && !obj.isNull("maxTokens")) obj.optInt("maxTokens") else null
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun providersToJson(providers: List<AiProviderConfig>): JSONArray {
        return JSONArray().also { array ->
            providers.forEach { config ->
                array.put(
                    JSONObject()
                        .put("id", config.id)
                        .put("type", config.type.name)
                        .put("displayName", config.displayName)
                        .put("baseUrl", config.baseUrl)
                        .put("modelId", config.modelId)
                        .put("apiKeySecretId", config.apiKeySecretId)
                        .put("isEnabled", config.isEnabled)
                        .put("temperature", config.temperature)
                        .put("maxTokens", config.maxTokens)
                )
            }
        }
    }

    private companion object {
        const val PrefsName = "fusion_ai_provider_configs"
        const val KeyProviders = "providers"
        const val KeySelectedProvider = "selected_provider_id"
    }
}
