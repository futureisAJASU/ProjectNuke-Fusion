package com.projectnuke.fusion.ui

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.projectnuke.fusion.model.AcceleratorMode
import com.projectnuke.fusion.util.ManagedModelPathPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val settingsRestoreLock = Mutex()

private const val SettingsBackupSchemaVersion = 1

private const val PrefSelectedModel = "selected_model"
private const val PrefSelectedModelPath = "selected_model_path"
private const val PrefAccelerator = "accelerator"
private const val PrefMaxTokens = "max_tokens"
private const val PrefTemperature = "temperature"
private const val PrefTopK = "top_k"
private const val PrefTopP = "top_p"
private const val PrefSpeculativeDecoding = "speculative_decoding_enabled"
private const val PrefReasoningEnabled = "reasoning_enabled"
private const val PrefReasoningBudget = "reasoning_budget_tokens"
private const val PrefWebSearchEnabled = "web_search_enabled"
private const val PrefFavoriteModelIds = "favorite_model_ids"
private const val PrefHiddenModelIds = "hidden_model_ids"
private const val PrefRecentModels = "recent_models"
private const val PrefModelNotes = "model_notes"
private const val PrefModelLibrarySortMode = "model_library_sort_mode"
private const val PrefFusionAppLanguage = "fusion_app_language"

enum class SettingsRestoreResult {
    Success,
    ModelPathMissing,
    InvalidJson,
    UnsupportedSchema,
    TooLarge,
    InvalidFields,
    CommitFailed,
}

internal const val MaxSettingsBackupBytes = 512 * 1024
private const val MaxBackupObjectFields = 64
private const val MaxBackupArrayItems = 256
private const val MaxBackupStringChars = 8_192

fun buildSettingsBackupJson(context: Context, prefs: SharedPreferences): String {
    val lowMemoryMode = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.let {
        it.isLowRamDevice
    } ?: false
    val root = JSONObject()
        .put("schemaVersion", SettingsBackupSchemaVersion)
        .put("app", "Fusion")
        .put("exportedAt", System.currentTimeMillis())
    val settings = JSONObject()
        .put("selectedModel", prefs.getString(PrefSelectedModel, "Gemma 4 E2B-it"))
        .put("selectedModelPath", prefs.getString(PrefSelectedModelPath, null))
        .put("accelerator", prefs.getString(PrefAccelerator, AcceleratorMode.GPU.name))
        .put("maxTokens", prefs.getInt(PrefMaxTokens, 4000))
        .put("temperature", prefs.getFloat(PrefTemperature, 1.0f).toDouble())
        .put("topK", prefs.getInt(PrefTopK, 64))
        .put("topP", prefs.getFloat(PrefTopP, 0.95f).toDouble())
        .put("mtpEnabled", prefs.getBoolean(PrefSpeculativeDecoding, false))
        .put("reasoningEnabled", prefs.getBoolean(PrefReasoningEnabled, false))
        .put("reasoningBudget", prefs.getInt(PrefReasoningBudget, 512))
        .put("webSearchEnabled", prefs.getBoolean(PrefWebSearchEnabled, false))
        .put("appLanguage", prefs.getString(PrefFusionAppLanguage, FusionAppLanguage.SYSTEM.value))
        .put("savedMemoryContextEnabled", prefs.getBoolean(PrefSavedMemoryContextEnabled, false))
        .put("memoryManagerSortMode", prefs.getString(PrefMemoryManagerSortMode, MemoryManagerSortMode.UPDATED_DESC.name))
        .put("lowMemoryMode", lowMemoryMode)
    val modelLibrary = JSONObject()
        .put("favorites", JSONArray((prefs.getStringSet(PrefFavoriteModelIds, emptySet()) ?: emptySet()).toList()))
        .put("hidden", JSONArray((prefs.getStringSet(PrefHiddenModelIds, emptySet()) ?: emptySet()).toList()))
        .put("recent", prefs.getString(PrefRecentModels, null)?.let { JSONArray(it) } ?: JSONArray())
        .put("notes", prefs.getString(PrefModelNotes, null)?.let { JSONObject(it) } ?: JSONObject())
        .put("sortMode", prefs.getString(PrefModelLibrarySortMode, "recommendation"))
    root.put("settings", settings)
    root.put("modelLibrary", modelLibrary)
    Log.d("FusionModelSelect", "settings_export schema=$SettingsBackupSchemaVersion keys=settings,modelLibrary success=true")
    return root.toString(2)
}

suspend fun restoreSettingsBackupJson(
    context: Context,
    prefs: SharedPreferences,
    raw: String
): SettingsRestoreResult = settingsRestoreLock.withLock {
    if (raw.toByteArray(Charsets.UTF_8).size > MaxSettingsBackupBytes) {
        return@withLock SettingsRestoreResult.TooLarge
    }
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return@withLock SettingsRestoreResult.InvalidJson
    if (root.optString("app") != "Fusion" || root.optInt("schemaVersion", -1) != SettingsBackupSchemaVersion) {
        return@withLock SettingsRestoreResult.UnsupportedSchema
    }
    val settings = root.optJSONObject("settings") ?: JSONObject()
    val modelLibrary = root.optJSONObject("modelLibrary") ?: JSONObject()
    if (root.length() > MaxBackupObjectFields ||
        settings.length() > MaxBackupObjectFields ||
        modelLibrary.length() > MaxBackupObjectFields ||
        containsOversizedValue(root)
    ) {
        return@withLock SettingsRestoreResult.InvalidFields
    }
    val restoredKeys = mutableListOf<String>()
    val editor = prefs.edit()
    if (settings.has("accelerator")) {
        val rawAccelerator = settings.optString("accelerator", AcceleratorMode.GPU.name)
        val accelerator = runCatching { AcceleratorMode.valueOf(rawAccelerator) }.getOrDefault(AcceleratorMode.GPU)
        editor.putString(PrefAccelerator, accelerator.name)
        restoredKeys += PrefAccelerator
    }
    if (settings.has("maxTokens")) { editor.putInt(PrefMaxTokens, settings.optInt("maxTokens", 4000).coerceIn(2000, 32000)); restoredKeys += PrefMaxTokens }
    if (settings.has("temperature")) { editor.putFloat(PrefTemperature, settings.optDouble("temperature", 1.0).toFloat().coerceIn(0f, 2f)); restoredKeys += PrefTemperature }
    if (settings.has("topK")) { editor.putInt(PrefTopK, settings.optInt("topK", 64).coerceIn(5, 100)); restoredKeys += PrefTopK }
    if (settings.has("topP")) { editor.putFloat(PrefTopP, settings.optDouble("topP", 0.95).toFloat().coerceIn(0f, 1f)); restoredKeys += PrefTopP }
    if (settings.has("mtpEnabled")) { editor.putBoolean(PrefSpeculativeDecoding, settings.optBoolean("mtpEnabled", false)); restoredKeys += PrefSpeculativeDecoding }
    if (settings.has("reasoningEnabled")) { editor.putBoolean(PrefReasoningEnabled, settings.optBoolean("reasoningEnabled", false)); restoredKeys += PrefReasoningEnabled }
    if (settings.has("reasoningBudget")) { editor.putInt(PrefReasoningBudget, settings.optInt("reasoningBudget", 512).coerceIn(128, 8192)); restoredKeys += PrefReasoningBudget }
    if (settings.has("webSearchEnabled")) { editor.putBoolean(PrefWebSearchEnabled, settings.optBoolean("webSearchEnabled", false)); restoredKeys += PrefWebSearchEnabled }
    if (settings.has("appLanguage")) {
        val rawLanguage = settings.optString("appLanguage", FusionAppLanguage.SYSTEM.value)
        val language = FusionAppLanguage.fromValue(rawLanguage)
        editor.putString(PrefFusionAppLanguage, language.value)
        restoredKeys += PrefFusionAppLanguage
    }
    if (settings.has("savedMemoryContextEnabled")) { editor.putBoolean(PrefSavedMemoryContextEnabled, settings.optBoolean("savedMemoryContextEnabled", false)); restoredKeys += PrefSavedMemoryContextEnabled }
    if (settings.has("memoryManagerSortMode")) {
        val sortMode = settings.optString("memoryManagerSortMode")
        if (runCatching { MemoryManagerSortMode.valueOf(sortMode) }.isSuccess) {
            editor.putString(PrefMemoryManagerSortMode, sortMode)
            restoredKeys += PrefMemoryManagerSortMode
        }
    }
    if (settings.has("lowMemoryMode")) { restoredKeys += "lowMemoryMode(ignored)" }

    val currentSelectedModel = prefs.getString(PrefSelectedModel, "Gemma 4 E2B-it") ?: "Gemma 4 E2B-it"
    val backupModel = settings.optString("selectedModel", currentSelectedModel)
    val hasBackupPath = settings.has("selectedModelPath")
    val backupPath = if (!hasBackupPath || settings.isNull("selectedModelPath")) {
        null
    } else {
        settings.optString("selectedModelPath")
    }
    var modelPathMissing = false
    if (hasBackupPath && !backupPath.isNullOrBlank()) {
        val managedModel = ManagedModelPathPolicy.resolveRunnableModel(context, backupPath)
        if (managedModel != null) {
            editor.putString(PrefSelectedModel, backupModel)
            editor.putString(PrefSelectedModelPath, managedModel.absolutePath)
            restoredKeys += PrefSelectedModel
            restoredKeys += PrefSelectedModelPath
        } else {
            modelPathMissing = true
        }
    } else if (hasBackupPath || settings.has("selectedModel")) {
        editor.putString(PrefSelectedModel, backupModel)
        editor.remove(PrefSelectedModelPath)
        restoredKeys += PrefSelectedModel
    }
    modelLibrary.optJSONArray("favorites")?.let { arr ->
        editor.putStringSet(PrefFavoriteModelIds, (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }.toSet())
        restoredKeys += PrefFavoriteModelIds
    }
    modelLibrary.optJSONArray("hidden")?.let { arr ->
        editor.putStringSet(PrefHiddenModelIds, (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }.toSet())
        restoredKeys += PrefHiddenModelIds
    }
    modelLibrary.optJSONArray("recent")?.let { arr -> editor.putString(PrefRecentModels, arr.toString()); restoredKeys += PrefRecentModels }
    modelLibrary.optJSONObject("notes")?.let { notes -> editor.putString(PrefModelNotes, notes.toString()); restoredKeys += PrefModelNotes }
    if (modelLibrary.has("sortMode")) {
        val sortMode = modelLibrary.optString("sortMode", "recommendation")
        val allowedSortModes = setOf("recommendation", "name", "lightweight", "memory_low", "local_execution", "favorites_first")
        editor.putString(PrefModelLibrarySortMode, sortMode.takeIf { it in allowedSortModes } ?: "recommendation")
        restoredKeys += PrefModelLibrarySortMode
    }
    val committed = withContext(Dispatchers.IO) { editor.commit() }
    if (!committed) return@withLock SettingsRestoreResult.CommitFailed
    if (modelPathMissing) {
        Log.d("FusionModelSelect", "settings_restore schema=${root.optInt("schemaVersion", -1)} keys=${restoredKeys.joinToString(",")} ModelPathMissing retained_current=true")
        return@withLock SettingsRestoreResult.ModelPathMissing
    }
    Log.d("FusionModelSelect", "settings_restore schema=${root.optInt("schemaVersion", -1)} keys=${restoredKeys.joinToString(",")} success=true")
    return@withLock SettingsRestoreResult.Success
}

private fun containsOversizedValue(value: Any?): Boolean = when (value) {
    is JSONObject -> {
        if (value.length() > MaxBackupObjectFields) true
        else value.keys().asSequence().any { key ->
            key.length > 200 || containsOversizedValue(value.opt(key))
        }
    }
    is JSONArray -> {
        if (value.length() > MaxBackupArrayItems) true
        else (0 until value.length()).any { containsOversizedValue(value.opt(it)) }
    }
    is String -> value.length > MaxBackupStringChars
    else -> false
}
