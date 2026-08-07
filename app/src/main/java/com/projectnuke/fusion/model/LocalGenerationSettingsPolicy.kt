package com.projectnuke.fusion.model

import android.content.SharedPreferences

/**
 * Unified local-generation settings policy consumed by Chat, Benchmark, and A/B.
 *
 * Replaces the duplicated per-screen `load*SettingsFromPrefs` helpers with a
 * single [fromPrefs] entry point so all three surfaces use the same
 * preference keys, legacy migration rules, and KV capacity derivation.
 *
 * Authoritative keys (written by all production code):
 * - [KEY_MAX_TOKENS] -> "generation_max_output_tokens"
 * - [KEY_KV_CACHE] -> "engine_kv_cache_capacity_tokens"
 *
 * Legacy keys (read-only for one-time migration):
 * - "max_tokens"
 * - "kv_cache_capacity_tokens"
 *
 * Migration runs once per key when the authoritative key is absent
 * and the legacy key exists. After migration, the legacy key is NOT
 * updated by policy — only the authoritative key is written.
 */
object LocalGenerationSettingsPolicy {

    // ── Authoritative preference keys ────────────────────────────────────

    const val KEY_MAX_TOKENS = "generation_max_output_tokens"
    const val KEY_KV_CACHE = "engine_kv_cache_capacity_tokens"

    // Legacy keys for one-time compatibility migration only.
    private const val LEGACY_MAX_TOKENS = "max_tokens"
    private const val LEGACY_KV_CACHE = "kv_cache_capacity_tokens"

    // Other settings keys (non-migrated, shared).
    const val KEY_TOP_K = "top_k"
    const val KEY_TOP_P = "top_p"
    const val KEY_TEMPERATURE = "temperature"
    const val KEY_ACCELERATOR = "accelerator"
    const val KEY_REASONING_BUDGET = "reasoning_budget_tokens"
    const val KEY_SPECULATIVE_DECODING = "speculative_decoding_enabled"

    // ── Public API ─────────────────────────────────────────────────────

    /**
     * Reads generation settings from shared preferences with the consolidated
     * output/KV policy and legacy-key migration. Should be the single source
     * for all three surfaces (Chat, Benchmark, A/B).
     */
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

    /**
     * Returns the effective output limit for a migration run on a low-memory
     * device. Benchmarks on 8-GB-class devices should use a reduced output cap
     * to avoid OOM during assessment.
     */
    fun lowMemoryOutputCap(): Int = 1024

    /**
     * Returns the reduced KV cache capacity token count for memory assessment.
     * Delegates to [com.projectnuke.fusion.model.KvCacheCapacityPolicy.lowMemoryBenchmarkCapacity] for the default.
     */
    fun lowMemoryKvCapacity(): Int =
        KvCacheCapacityPolicy.lowMemoryBenchmarkCapacity()

    /**
     * Returns the KV cache capacity based on the requested output limit
     * using the standard heuristic: capacity = max(output_limit * 2, MIN_CAPACITY).
     */
    fun deriveKvCapacityForOutput(outputLimit: Int): Int =
        (outputLimit * 2).coerceIn(KvCacheCapacityPolicy.MIN_CAPACITY, KvCacheCapacityPolicy.MAX_CAPACITY)

    // ── Private helpers ─────────────────────────────────────────────────

    private fun readMaxTokens(prefs: SharedPreferences): Int {
        // If authoritative key exists, use it.
        if (prefs.contains(KEY_MAX_TOKENS)) {
            return prefs.getInt(KEY_MAX_TOKENS, 4000).coerceIn(1, 32000)
        }
        // One-time migration from legacy key.
        val legacy = prefs.getInt(LEGACY_MAX_TOKENS, 4000).coerceIn(1, 32000)
        prefs.edit().putInt(KEY_MAX_TOKENS, legacy).apply()
        return legacy
    }

    private fun readKvCache(prefs: SharedPreferences, maxTokens: Int): Int {
        // If authoritative key exists, use it.
        if (prefs.contains(KEY_KV_CACHE)) {
            return prefs.getInt(KEY_KV_CACHE, 4096).coerceIn(KvCacheCapacityPolicy.MIN_CAPACITY, KvCacheCapacityPolicy.MAX_CAPACITY)
        }
        // One-time migration from legacy key.
        if (prefs.contains(LEGACY_KV_CACHE)) {
            val legacy = prefs.getInt(LEGACY_KV_CACHE, 4096).coerceIn(
                KvCacheCapacityPolicy.MIN_CAPACITY, KvCacheCapacityPolicy.MAX_CAPACITY
            )
            prefs.edit().putInt(KEY_KV_CACHE, legacy).apply()
            return legacy
        }
        // Derive from output limit using the heuristic: capacity >= 2 * output.
        val derived = deriveKvCapacityForOutput(maxTokens)
        prefs.edit().putInt(KEY_KV_CACHE, derived).apply()
        return derived
    }

    private fun readAccelerator(prefs: SharedPreferences): AcceleratorMode =
        runCatching {
            val raw = prefs.getString(KEY_ACCELERATOR, "GPU") ?: "GPU"
            AcceleratorMode.valueOf(raw)
        }.getOrDefault(AcceleratorMode.GPU)

    // ── Migration check ──────────────────────────────────────────────────

    /**
     * Returns true if migration has completed for both authoritative keys.
     * Useful for tests to verify migration state.
     */
    fun isMigrationComplete(prefs: SharedPreferences): Boolean =
        prefs.contains(KEY_MAX_TOKENS) && prefs.contains(KEY_KV_CACHE)
}