package com.projectnuke.fusion.util

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

data class FusionMemorySnapshot(
    val totalMem: Long,
    val availMem: Long,
    val lowMemory: Boolean,
    val threshold: Long
)

object FusionMemoryManager {
    private const val LowRamDeviceBytes = 8L * 1024L * 1024L * 1024L
    private const val LowMemoryBenchmarkFloorBytes = 1536L * 1024L * 1024L
    private val lightweightCacheClearers = CopyOnWriteArrayList<() -> Unit>()
    private val idleEngineUnloaders = CopyOnWriteArrayList<() -> Unit>()

    fun getMemorySnapshot(context: Context): FusionMemorySnapshot {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        return FusionMemorySnapshot(
            totalMem = memoryInfo.totalMem,
            availMem = memoryInfo.availMem,
            lowMemory = memoryInfo.lowMemory,
            threshold = memoryInfo.threshold
        )
    }

    fun isLowRamDevice(context: Context): Boolean {
        val snapshot = getMemorySnapshot(context)
        return snapshot.totalMem in 1..LowRamDeviceBytes
    }

    fun recommendedBenchmarkMaxTokens(context: Context, userMaxTokens: Int): Int {
        val snapshot = getMemorySnapshot(context)
        val tightFloor = snapshot.threshold.coerceAtLeast(LowMemoryBenchmarkFloorBytes) * 2
        return when {
            snapshot.lowMemory || snapshot.availMem in 1 until tightFloor -> userMaxTokens.coerceAtMost(1024)
            isLowRamDevice(context) -> userMaxTokens.coerceAtMost(2048)
            else -> userMaxTokens
        }
    }

    fun shouldBlockBenchmark(context: Context): Boolean {
        val snapshot = getMemorySnapshot(context)
        val safeFloor = snapshot.threshold.coerceAtLeast(LowMemoryBenchmarkFloorBytes)
        return snapshot.lowMemory || snapshot.availMem < safeFloor
    }

    fun logMemorySnapshot(tag: String, context: Context, reason: String) {
        val snapshot = getMemorySnapshot(context)
        Log.i(
            tag,
            "reason=$reason totalMem=${snapshot.totalMem} availMem=${snapshot.availMem} lowMemory=${snapshot.lowMemory} threshold=${snapshot.threshold}"
        )
    }

    fun registerLightweightCacheClearer(clearer: () -> Unit): () -> Unit {
        lightweightCacheClearers.add(clearer)
        return { lightweightCacheClearers.remove(clearer) }
    }

    fun registerIdleEngineUnloader(unloader: () -> Unit): () -> Unit {
        idleEngineUnloaders.add(unloader)
        return { idleEngineUnloaders.remove(unloader) }
    }

    fun clearLightweightCaches() {
        lightweightCacheClearers.forEach { clearer ->
            runCatching { clearer() }
                .onFailure { Log.w("FusionMemory", "Failed to clear lightweight cache", it) }
        }
    }

    fun unloadIdleEngines() {
        idleEngineUnloaders.forEach { unloader ->
            runCatching { unloader() }
                .onFailure { Log.w("FusionMemory", "Failed to unload idle engine", it) }
        }
    }
}
