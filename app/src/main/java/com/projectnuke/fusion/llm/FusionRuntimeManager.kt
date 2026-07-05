package com.projectnuke.fusion.llm

import android.content.Context
import android.util.Log

object FusionRuntimeManager {
    private val lock = Any()
    private var sharedEngine: LiteRtLlmEngine? = null

    fun sharedEngine(context: Context): LiteRtLlmEngine {
        return synchronized(lock) {
            sharedEngine ?: LiteRtLlmEngine(context.applicationContext).also {
                sharedEngine = it
            }
        }
    }

    fun unloadSharedEngineIfIdle(reason: String) {
        if (FusionRuntimeLock.isChatGenerationRunning || FusionRuntimeLock.isBenchmarkRunning) return
        synchronized(lock) {
            runCatching {
                sharedEngine?.unload()
            }.onFailure {
                Log.e("FusionEngine", "Failed to unload shared engine while idle: $reason", it)
            }
        }
    }

    fun unloadSharedEngineAfterExclusive(reason: String) {
        synchronized(lock) {
            runCatching {
                sharedEngine?.unload()
            }.onFailure {
                Log.e("FusionEngine", "Failed to unload shared engine after exclusive run: $reason", it)
            }
        }
    }
}
