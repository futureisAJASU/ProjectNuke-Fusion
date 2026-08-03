package com.projectnuke.fusion.llm

import android.content.Context
import android.util.Log

/**
 * Stable process owner for a runtime wrapper. Unload clears only the wrapper's
 * native state; [acquire] never replaces an already published wrapper.
 */
internal class StableRuntimeOwner<T : Any> {
    private val lock = Any()
    private var instance: T? = null

    fun acquire(factory: () -> T): T = synchronized(lock) {
        instance ?: factory().also { instance = it }
    }

    fun unload(action: (T) -> Unit) {
        synchronized(lock) {
            instance?.let(action)
        }
    }

    internal fun currentIdentity(): T? = synchronized(lock) { instance }
}

object FusionRuntimeManager {
    private val owner = StableRuntimeOwner<LiteRtLlmEngine>()

    /**
     * Returns the one stable process wrapper. UI code may retain this identity:
     * unload never orphans it and the next generation reloads its native engine.
     */
    fun sharedEngine(context: Context): LiteRtLlmEngine =
        owner.acquire {
            LiteRtLlmEngine(
                context.applicationContext,
                failureMemoryStorage = PrefsMtpFailureMemoryStorage(context.applicationContext)
            )
        }

    fun unloadSharedEngineIfIdle(reason: String) {
        if (FusionRuntimeLock.isChatGenerationRunning || FusionRuntimeLock.isBenchmarkRunning) return
        unloadStableWrapper("idle:$reason")
    }

    suspend fun unloadSharedEngineWhenRuntimeIdle(reason: String) {
        if (FusionRuntimeLock.isChatGenerationRunning || FusionRuntimeLock.isBenchmarkRunning) return
        FusionRuntimeLock.withLock {
            if (FusionRuntimeLock.isChatGenerationRunning || FusionRuntimeLock.isBenchmarkRunning) {
                return@withLock
            }
            unloadStableWrapper("runtime-idle:$reason")
        }
    }

    /**
     * Call only while the caller owns [FusionRuntimeLock]'s exclusive runtime
     * section. This keeps benchmark model switches serialized with generation.
     */
    fun unloadSharedEngineAfterExclusive(reason: String) {
        unloadStableWrapper("exclusive:$reason")
    }

    /**
     * Call only from the active chat owner while it holds the runtime lock.
     */
    fun unloadSharedEngineForActiveOwner(reason: String) {
        unloadStableWrapper("active-owner:$reason")
    }

    private fun unloadStableWrapper(reason: String) {
        runCatching {
            owner.unload { it.unload() }
        }.onFailure {
            Log.e("FusionEngine", "Failed to unload shared runtime: $reason", it)
        }
    }
}
