package com.projectnuke.fusion.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object FusionRuntimeLock {
    private val mutex = Mutex()
    private val callbackLock = Any()
    private var chatEngineUnloadCallback: (() -> Unit)? = null

    @Volatile
    var isBenchmarkRunning: Boolean = false
        private set

    @Volatile
    var isChatGenerationRunning: Boolean = false
        private set

    fun registerChatEngineUnloadCallback(callback: () -> Unit): () -> Unit {
        synchronized(callbackLock) {
            chatEngineUnloadCallback = callback
        }
        return {
            synchronized(callbackLock) {
                if (chatEngineUnloadCallback === callback) {
                    chatEngineUnloadCallback = null
                }
            }
        }
    }

    suspend fun <T> withLock(block: suspend () -> T): T {
        return mutex.withLock { block() }
    }

    suspend fun <T> withChatGeneration(block: suspend () -> T): T {
        if (isBenchmarkRunning) {
            throw BenchmarkRunningException()
        }
        isChatGenerationRunning = true
        return try {
            mutex.withLock {
                if (isBenchmarkRunning) {
                    throw BenchmarkRunningException()
                }
                block()
            }
        } finally {
            isChatGenerationRunning = false
        }
    }

    suspend fun <T> withExclusiveBenchmark(
        onPrepareExclusiveMode: suspend () -> Unit,
        block: suspend () -> T
    ): T {
        if (isChatGenerationRunning) {
            throw ChatGenerationRunningException()
        }
        isBenchmarkRunning = true
        return try {
            mutex.withLock {
                onPrepareExclusiveMode()
                System.gc()
                delay(500L)
                block()
            }
        } finally {
            isBenchmarkRunning = false
            System.gc()
        }
    }

    fun requestChatEngineUnloadForBenchmark() {
        synchronized(callbackLock) {
            chatEngineUnloadCallback
        }?.invoke()
    }
}

class BenchmarkRunningException : IllegalStateException("Benchmark is running")

class ChatGenerationRunningException : IllegalStateException("Chat generation is running")
