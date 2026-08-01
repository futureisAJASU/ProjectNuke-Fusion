package com.projectnuke.fusion.ai.data

import android.content.Context
import android.content.SharedPreferences
import com.projectnuke.fusion.ai.ExternalAiProviderSource
import com.projectnuke.fusion.ai.model.AiProviderAuthMode
import com.projectnuke.fusion.ai.model.AiProviderConfig
import com.projectnuke.fusion.ai.model.AiProviderType
import com.projectnuke.fusion.ai.provider.AiProviderPresets
import com.projectnuke.fusion.ai.secure.SecretStore
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class ProviderValidationResult(val config: AiProviderConfig, val warning: String? = null)

internal object AiProviderValidator {
    const val MAX_DISPLAY_NAME = 80
    const val MAX_URL = 2_048
    const val MAX_MODEL_ID = 256
    const val MAX_HEADER_NAME = 128
    const val MAX_SECRET = 16_384
    const val MAX_TEMPERATURE = 2.0
    const val MAX_TOKENS = 1_000_000

    fun validate(config: AiProviderConfig, secret: String?): ProviderValidationResult {
        require(config.id.length in 1..128) { "Invalid provider id" }
        require(config.displayName.length in 1..MAX_DISPLAY_NAME) { "Display name is too long" }
        require(config.modelId.length in 1..MAX_MODEL_ID) { "Model id is invalid" }
        require(config.baseUrl.length in 1..MAX_URL) { "Endpoint is invalid" }
        require(config.temperature.isFinite() && config.temperature in 0.0..MAX_TEMPERATURE) {
            "Temperature is invalid"
        }
        require(config.maxTokens == null || config.maxTokens in 1..MAX_TOKENS) { "Max tokens is invalid" }
        if (config.authMode == AiProviderAuthMode.CUSTOM_HEADER) {
            require(config.authHeaderName.orEmpty().length in 1..MAX_HEADER_NAME) { "Header name is invalid" }
            require(config.authHeaderName!!.matches(Regex("[A-Za-z0-9-]+"))) { "Header name is invalid" }
        }
        val normalized = normalizeEndpoint(config.baseUrl)
        val uri = URI(normalized)
        require(uri.scheme == "https" || uri.scheme == "http") { "Endpoint scheme is invalid" }
        val host = uri.host.orEmpty().lowercase()
        val local = host == "localhost" || host == "127.0.0.1" || host == "::1" ||
            host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("172.16.")
        if (uri.scheme == "http" && !local) require(false) { "Cleartext public endpoints are not allowed" }
        if (config.authMode != AiProviderAuthMode.NONE) {
            require(secret == null || secret.length <= MAX_SECRET) { "Secret is too long" }
        }
        return ProviderValidationResult(
            config = config.copy(baseUrl = normalized),
            warning = if (uri.scheme == "http" && local) "Cleartext local endpoint" else null,
        )
    }

    fun normalizeEndpoint(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
    }
}

class AiProviderRepository(
    context: Context,
    private val secretStore: SecretStore,
) : ExternalAiProviderSource {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    suspend fun getProviders(): List<AiProviderConfig> = withContext(Dispatchers.IO) {
        val stored = prefs.getString(KeyProviders, null)?.let(::parseProviders).orEmpty()
        stored.ifEmpty { AiProviderPresets.defaults }
    }

    suspend fun hasDecryptableSecret(config: AiProviderConfig): Boolean =
        config.authMode == AiProviderAuthMode.NONE ||
            (!config.apiKeySecretId.isNullOrBlank() &&
                !secretStore.getSecret(config.apiKeySecretId!!).isNullOrBlank())

    suspend fun saveProvider(config: AiProviderConfig, rawApiKey: String?): Boolean {
        val previous = getProviders().firstOrNull { it.id == config.id }
        val input = rawApiKey?.trim().orEmpty()
        if (config.authMode != AiProviderAuthMode.NONE && input.isNotEmpty()) {
            require(input.length <= AiProviderValidator.MAX_SECRET) { "Secret is too long" }
        }
        val newSecretId = if (input.isNotEmpty()) {
            "ai_key_${config.id}_${UUID.randomUUID()}"
        } else config.apiKeySecretId
        val secretWritten = if (input.isNotEmpty() && newSecretId != null) {
            secretStore.putSecret(newSecretId, input)
        } else true
        if (!secretWritten) return false
        val validated = try {
            AiProviderValidator.validate(config.copy(apiKeySecretId = newSecretId), input.ifEmpty { null }).config
        } catch (failure: Throwable) {
            if (input.isNotEmpty() && newSecretId != null) runCatching { secretStore.deleteSecret(newSecretId) }
            throw failure
        }
        val committed = withContext(Dispatchers.IO) {
            val updated = getProviders().filterNot { it.id == validated.id } + validated
            prefs.edit().putString(KeyProviders, providersToJson(updated).toString()).commit()
        }
        if (!committed) {
            if (input.isNotEmpty() && newSecretId != null) runCatching { secretStore.deleteSecret(newSecretId) }
            return false
        }
        val oldSecretId = previous?.apiKeySecretId
        val oldCleanupSuccess = if (oldSecretId != null && oldSecretId != newSecretId) {
            runCatching { secretStore.deleteSecret(oldSecretId) }.getOrDefault(false)
        } else true
        if (prefs.getString(KeySelectedProvider, null) == null) {
            withContext(Dispatchers.IO) { prefs.edit().putString(KeySelectedProvider, validated.id).commit() }
        }
        return oldCleanupSuccess
    }

    suspend fun createCustomProvider(): AiProviderConfig {
        val config = AiProviderConfig(
            id = UUID.randomUUID().toString(), type = AiProviderType.CUSTOM_OPENAI_COMPATIBLE,
            displayName = "Custom provider", baseUrl = "https://example.invalid/v1",
            modelId = "model", apiKeySecretId = null,
        )
        check(saveProvider(config, null)) { "Provider metadata could not be saved" }
        return config
    }

    suspend fun deleteProvider(id: String): Boolean {
        val existing = getProviders().firstOrNull { it.id == id } ?: return true
        val updated = existing.let { list -> getProviders().filterNot { it.id == id } }
        val committed = withContext(Dispatchers.IO) {
            prefs.edit().putString(KeyProviders, providersToJson(updated).toString())
                .putString(KeySelectedProvider, updated.firstOrNull()?.id).commit()
        }
        if (!committed) return false
        val secretCleanupSuccess = if (existing.apiKeySecretId != null) {
            runCatching { secretStore.deleteSecret(existing.apiKeySecretId) }.getOrDefault(false)
        } else true
        return secretCleanupSuccess
    }

    suspend fun getSelectedProvider(): AiProviderConfig? {
        val providers = getProviders()
        val selected = withContext(Dispatchers.IO) { prefs.getString(KeySelectedProvider, null) }
        return providers.firstOrNull { it.id == selected } ?: providers.firstOrNull()
    }

    override suspend fun getRunnableProviderById(id: String): AiProviderConfig? =
        getProviders().firstOrNull { it.id == id && it.isEnabled && hasDecryptableSecret(it) && it.modelId.isNotBlank() }

    override suspend fun getSelectedRunnableProvider(): AiProviderConfig? {
        val providers = getProviders()
        val selected = withContext(Dispatchers.IO) { prefs.getString(KeySelectedProvider, null) }
        val runnable = providers.filter { it.isEnabled && it.modelId.isNotBlank() }
            .filter { hasDecryptableSecret(it) }
        val resolved = runnable.firstOrNull { it.id == selected } ?: runnable.firstOrNull()
        if (resolved?.id != selected) withContext(Dispatchers.IO) { prefs.edit().putString(KeySelectedProvider, resolved?.id).commit() }
        return resolved
    }

    fun observeProviderChanges(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KeyProviders || key == KeySelectedProvider) trySend(Unit)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    suspend fun setSelectedProvider(id: String): Boolean = withContext(Dispatchers.IO) {
        prefs.edit().putString(KeySelectedProvider, id).commit()
    }

    private fun parseProviders(raw: String): List<AiProviderConfig> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(AiProviderConfig(
                    id = obj.getString("id"), type = AiProviderType.valueOf(obj.getString("type")),
                    displayName = obj.optString("displayName"), baseUrl = obj.optString("baseUrl"),
                    modelId = obj.optString("modelId"), apiKeySecretId = obj.optString("apiKeySecretId").takeIf { it.isNotBlank() },
                    isEnabled = obj.optBoolean("isEnabled", true), temperature = obj.optDouble("temperature", 0.7),
                    maxTokens = if (obj.has("maxTokens") && !obj.isNull("maxTokens")) obj.optInt("maxTokens") else null,
                    authMode = runCatching { AiProviderAuthMode.valueOf(obj.optString("authMode")) }.getOrDefault(AiProviderAuthMode.BEARER_API_KEY),
                    authHeaderName = obj.optString("authHeaderName").takeIf { it.isNotBlank() },
                    reasoningContentEnabled = obj.optBoolean("reasoningContentEnabled", false),
                ))
            }
        }
    }.getOrDefault(emptyList())

    private fun providersToJson(providers: List<AiProviderConfig>) = JSONArray().also { array ->
        providers.forEach { c -> array.put(JSONObject().put("id", c.id).put("type", c.type.name)
            .put("displayName", c.displayName).put("baseUrl", c.baseUrl).put("modelId", c.modelId)
            .put("apiKeySecretId", c.apiKeySecretId).put("isEnabled", c.isEnabled)
            .put("temperature", c.temperature).put("maxTokens", c.maxTokens)
            .put("authMode", c.authMode.name).put("authHeaderName", c.authHeaderName)
            .put("reasoningContentEnabled", c.reasoningContentEnabled)) }
    }

    private companion object {
        const val PrefsName = "fusion_ai_provider_configs"
        const val KeyProviders = "providers"
        const val KeySelectedProvider = "selected_provider_id"
    }
}
