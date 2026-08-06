package com.projectnuke.fusion.model

import android.content.SharedPreferences

/**
 * Unified local-generation settings policy consumed by Chat, Benchmark, and A/B.
 *
 * Replaces the duplicated per-screen `load*SettingsFromPrefs` helpers with a
 * single [fromPrefs] entry point so all three surfaces use the same
 * preference keys, legacy migration rules, and KV capacity derivation.
 */
object LocalGenerationSettingsPolicy {

    // ── Preference keys ────────────────────────────────────────────────

    const val KEY_MAX_TOKENS = "generation_max_output_tokens"
    const val KEY_KV_CACHE = "engine_kv_cache_capacity_tokens"
    // Legacy keys for compatibility migration.
    private const val LEGACY_MAX_TOKENS = "max_tokens"
    private const val LEGACY_KV_CACHE = "kv_cache_capacity_tokens"

    private const val KEY_TOP_K = "top_k"
    private const val KEY_TOP_P = "top_p"
    private const val KEY_TEMPERATURE = "temperature"
    private const val KEY_ACCELERATOR = "accelerator"
    private const val KEY_REASONING_BUDGET = "reasoning_budget_tokens"
    private const val KEY_SPECULATIVE_DECODING = "speculative_decoding_enabled"

    // ── Public API ─────────────────────────────────────────────────────

    fun fromPrefs(prefs: SharedPreferences): GenerationSettings {
        val maxTokens = readMaxTokens(prefs)
        val kvCache = readKvCache(prefs, maxTokens)
        val accel = readAccelerator(prefs)

        return GenerationSettings(
            maxTokens = maxTokens,
            kvCacheCapacityTokens = kvCache,
            topK = prefs.getInt(KEY_TOP_K, 64).coerceIn(1, 100),
            topP = prefs.getFloat(KEY_TOP_P, 0.95f).coerceIn(0f, 1f),
            temperature = prefs.getFloat(KEY_TEMPERATURE, 1.0f).coerceIn(0f, 2f),
            accelerator = accel,
            reasoningBudgetTokens = prefs.getInt(KEY_REASONING_BUDGET, 512).coerceIn(1, 8192),
            speculativeDecodingEnabled = if (prefs.contains(KEY_SPECULATIVE_DECODING)) {
                prefs.getBoolean(KEY_SPECULATIVE_DECODING, false)
            } else null
        )
    }

    // ── Output / KV policy ─────────────────────────────────────────────

    fun lowMemoryOutputCap(): Int = 1024

    fun lowMemoryKvCapacity(): Int =
        KvCacheCapacityPolicy.lowMemoryBenchmarkCapacity()

    fun deriveKvCapacityForOutput(outputLimit: Int): Int =
        (outputLimit * 2).coerceIn(KvCacheCapacityPolicy.MIN_CAPACITY, KvCacheCapacityPolicy.MAX_CAPACITY)

    // ── Private helpers ─────────────────────────────────────────────────

    private fun readMaxTokens(prefs: SharedPreferences): Int {
        if (prefs.contains(KEY_MAX_TOKENS)) {
            return prefs.getInt(KEY_MAX_TOKENS, 4000).coerceIn(1, 32000)
        }
        val legacy = prefs.getInt(LEGACY_MAX_TOKENS, 4000).coerceIn(1, 32000)
        prefs.edit().putInt(KEY_MAX_TOKENS, legacy).apply()
        return legacy
    }

    private fun readKvCache(prefs: SharedPreferences, maxTokens: Int): Int {
        if (prefs.contains(KEY_KV_CACHE)) {
            return prefs.getInt(KEY_KV_CACHE, 4096).coerceIn(KvCacheCapacityPolicy.MIN_CAPACITY, KvCacheCapacityPolicy.MAX_CAPACITY)
        }
        if (prefs.contains(LEGACY_KV_CACHE)) {
            val legacy = prefs.getInt(LEGACY_KV_CACHE, 4096).coerceIn(
                KvCacheCapacityPolicy.MIN_CAPACITY, KvCacheCapacityPolicy.MAX_CAPACITY
            )
            prefs.edit().putInt(KEY_KV_CACHE, legacy).apply()
            return legacy
        }
        val derived = deriveKvCapacityForOutput(maxTokens)
        prefs.edit().putInt(KEY_KV_CACHE, derived).apply()
        return derived
    }

    private fun readAccelerator(prefs: SharedPreferences): AcceleratorMode =
        runCatching {
            val raw = prefs.getString(KEY_ACCELERATOR, "GPU") ?: "GPU"
            AcceleratorMode.valueOf(raw)
        }.getOrDefault(AcceleratorMode.GPU)
}