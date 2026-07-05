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
            if (FusionRuntimeLock.isChatGenerationRunning || FusionRuntimeLock.isBenchmarkRunning) return
            runCatching {
                sharedEngine?.unload()
            }.onFailure {
                Log.e("FusionEngine", "Failed to unload shared engine while idle: $reason", it)
            }
        }
    }

    suspend fun unloadSharedEngineWhenRuntimeIdle(reason: String) {
        if (FusionRuntimeLock.isChatGenerationRunning || FusionRuntimeLock.isBenchmarkRunning) return
        FusionRuntimeLock.withLock {
            if (FusionRuntimeLock.isChatGenerationRunning || FusionRuntimeLock.isBenchmarkRunning) return@withLock
            synchronized(lock) {
                runCatching {
                    sharedEngine?.unload()
                }.onFailure {
                    Log.e("FusionEngine", "Failed to unload shared engine while runtime idle: $reason", it)
                }
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

    fun unloadSharedEngineForActiveOwner(reason: String) {
        synchronized(lock) {
            runCatching {
                sharedEngine?.unload()
            }.onFailure {
                Log.e("FusionEngine", "Failed to unload shared engine for active owner: $reason", it)
            }
        }
    }
}
