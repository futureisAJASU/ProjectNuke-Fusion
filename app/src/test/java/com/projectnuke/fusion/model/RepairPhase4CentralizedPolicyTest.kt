package com.projectnuke.fusion.model

import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import org.junit.Assert.assertEquals
import org.junit.Test

class RepairPhase4CentralizedPolicyTest {

    private class FakePrefs : SharedPreferences {
        internal val map = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = map
        override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = map[key] as? Set<String> ?: defValues
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {}

        inner class FakeEditor(private val map: MutableMap<String, Any?>) : Editor {
            private val changes = mutableMapOf<String, Any?>()
            override fun putString(key: String, value: String?): Editor { changes[key] = value; return this }
            override fun putInt(key: String, value: Int): Editor { changes[key] = value; return this }
            override fun putFloat(key: String, value: Float): Editor { changes[key] = value; return this }
            override fun putLong(key: String, value: Long): Editor { changes[key] = value; return this }
            override fun putBoolean(key: String, value: Boolean): Editor { changes[key] = value; return this }
            override fun putStringSet(key: String, values: Set<String>?): Editor { changes[key] = values; return this }
            override fun remove(key: String): Editor { changes[key] = null; return this }
            override fun clear(): Editor { changes.clear(); return this }
            override fun apply(): Unit { changes.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }; changes.clear() }
            override fun commit(): Boolean = true
        }
    }

    @Test
    fun `fromPrefs loads settings with new keys when present`() {
        val prefs = FakePrefs().apply {
            map["generation_max_output_tokens"] = 2048
            map["engine_kv_cache_capacity_tokens"] = 8192
            map["top_k"] = 32
            map["top_p"] = 0.9f
            map["temperature"] = 0.8f
            map["accelerator"] = "CPU"
            map["reasoning_budget_tokens"] = 1024
            map["speculative_decoding_enabled"] = true
        }
        val settings = LocalGenerationSettingsPolicy.fromPrefs(prefs)
        assertEquals(2048, settings.maxTokens)
        assertEquals(8192, settings.kvCacheCapacityTokens)
        assertEquals(32, settings.topK)
        assertEquals(0.9f, settings.topP, 0.001f)
        assertEquals(0.8f, settings.temperature, 0.001f)
        assertEquals(AcceleratorMode.CPU, settings.accelerator)
        assertEquals(1024, settings.reasoningBudgetTokens)
        assertEquals(true, settings.speculativeDecodingEnabled)
    }

    @Test
    fun `legacy max_tokens migrates to new key silently`() {
        val prefs = FakePrefs().apply {
            map["max_tokens"] = 3000
        }
        val settings = LocalGenerationSettingsPolicy.fromPrefs(prefs)
        assertEquals(3000, settings.maxTokens)
        assertEquals(3000, prefs.map["generation_max_output_tokens"])
    }

    @Test
    fun `legacy kv_cache migrates to new key`() {
        val prefs = FakePrefs().apply {
            map["generation_max_output_tokens"] = 2048
            map["kv_cache_capacity_tokens"] = 6144
        }
        val settings = LocalGenerationSettingsPolicy.fromPrefs(prefs)
        assertEquals(6144, settings.kvCacheCapacityTokens)
        assertEquals(6144, prefs.map["engine_kv_cache_capacity_tokens"])
    }

    @Test
    fun `no legacy KV cache derives from output limit using 2x heuristic`() {
        val prefs = FakePrefs().apply {
            map["generation_max_output_tokens"] = 3000
        }
        val settings = LocalGenerationSettingsPolicy.fromPrefs(prefs)
        assertEquals(6000, settings.kvCacheCapacityTokens)
    }

    @Test
    fun `lowMemoryOutputCap returns 1024`() {
        assertEquals(1024, LocalGenerationSettingsPolicy.lowMemoryOutputCap())
    }

    @Test
    fun `lowMemoryKvCapacity delegates to KvCacheCapacityPolicy`() {
        assertEquals(
            KvCacheCapacityPolicy.lowMemoryBenchmarkCapacity(),
            LocalGenerationSettingsPolicy.lowMemoryKvCapacity()
        )
    }

    @Test
    fun `deriveKvCapacityForOutput uses 2x heuristic clamped to bounds`() {
        assertEquals(KvCacheCapacityPolicy.MIN_CAPACITY, LocalGenerationSettingsPolicy.deriveKvCapacityForOutput(1))
        assertEquals(4000, LocalGenerationSettingsPolicy.deriveKvCapacityForOutput(2000))
        assertEquals(KvCacheCapacityPolicy.MAX_CAPACITY, LocalGenerationSettingsPolicy.deriveKvCapacityForOutput(20000))
    }
}