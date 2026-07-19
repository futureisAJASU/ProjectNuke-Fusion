package com.projectnuke.fusion.llm

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Authoritative runtime ownership model. Chat and benchmark can never both
 * own the runtime; a queued or active chat operation must not transiently
 * appear idle; a failed acquisition must not clear another operation's state;
 * cancellation/exceptions always release only the matching owner.
 */
object FusionRuntimeLock {
    private val mutex = Mutex()

    private val ownerLock = Any()
    private var owner: RuntimeOwner = RuntimeOwner.Idle
    private var pendingChatCount: Int = 0

    private val unloadCallbacks = CopyOnWriteArraySet<ChatEngineUnloadCallback>()

    fun interface ChatEngineUnloadCallback {
        fun onUnloadRequested()
    }

    /** UI-observable state derived from the authoritative owner only. */
    sealed interface RuntimeOwnerState {
        data object Idle : RuntimeOwnerState
        data class Chat(val requestId: String) : RuntimeOwnerState
        data class Benchmark(val runId: String) : RuntimeOwnerState
    }

    private sealed interface RuntimeOwner {
        data object Idle : RuntimeOwner
        data class Chat(val requestId: String) : RuntimeOwner
        data class Benchmark(val runId: String) : RuntimeOwner
    }

    val isChatGenerationRunning: Boolean
        get() = synchronized(ownerLock) { owner is RuntimeOwner.Chat }

    val isBenchmarkRunning: Boolean
        get() = synchronized(ownerLock) { owner is RuntimeOwner.Benchmark }

    fun ownerSnapshot(): RuntimeOwnerState = synchronized(ownerLock) {
        when (val current = owner) {
            RuntimeOwner.Idle -> RuntimeOwnerState.Idle
            is RuntimeOwner.Chat -> RuntimeOwnerState.Chat(current.requestId)
            is RuntimeOwner.Benchmark -> RuntimeOwnerState.Benchmark(current.runId)
        }
    }

    fun pendingChatRequests(): Int = synchronized(ownerLock) { pendingChatCount }

    /**
     * Registers [callback] and returns an unregister handle. Multiple
     * observers are supported; registering a second observer no longer
     * discards the first.
     */
    fun registerChatEngineUnloadCallback(callback: () -> Unit): () -> Unit {
        val wrapper = ChatEngineUnloadCallback { callback() }
        unloadCallbacks += wrapper
        return { unloadCallbacks -= wrapper }
    }

    fun registerChatEngineUnloadCallbackTyped(callback: ChatEngineUnloadCallback): () -> Unit {
        unloadCallbacks += callback
        return { unloadCallbacks -= callback }
    }

    fun requestChatEngineUnloadForBenchmark() {
        unloadCallbacks.forEach { runCatching { it.onUnloadRequested() } }
    }

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    private fun markChatRequested(requestId: String) {
        synchronized(ownerLock) {
            if (owner is RuntimeOwner.Benchmark) {
                throw BenchmarkRunningException()
            }
            pendingChatCount += 1
        }
    }

    private fun promoteChatOwner(requestId: String): Boolean {
        synchronized(ownerLock) {
            if (owner is RuntimeOwner.Benchmark) {
                return false
            }
            if (owner is RuntimeOwner.Chat &&
                (owner as RuntimeOwner.Chat).requestId != requestId
            ) {
                return false
            }
            owner = RuntimeOwner.Chat(requestId)
            return true
        }
    }

    private fun releaseChatOwner(requestId: String) {
        synchronized(ownerLock) {
            if (owner is RuntimeOwner.Chat &&
                (owner as RuntimeOwner.Chat).requestId == requestId
            ) {
                owner = RuntimeOwner.Idle
            }
            pendingChatCount = (pendingChatCount - 1).coerceAtLeast(0)
        }
    }

    suspend fun <T> withChatGeneration(block: suspend (String) -> T): T {
        val requestId = "chat-${UUID.randomUUID()}"
        markChatRequested(requestId)
        var acquired = false
        try {
            mutex.withLock {
                if (!promoteChatOwner(requestId)) {
                    throw BenchmarkRunningException()
                }
                acquired = true
                return block(requestId)
            }
        } finally {
            releaseChatOwner(requestId)
        }
    }

    suspend fun <T> withExclusiveBenchmark(
        onPrepareExclusiveMode: suspend () -> Unit,
        block: suspend () -> T
    ): T {
        val runId = "benchmark-${UUID.randomUUID()}"
        synchronized(ownerLock) {
            if (owner is RuntimeOwner.Chat || pendingChatCount > 0) {
                throw ChatGenerationRunningException()
            }
            if (owner is RuntimeOwner.Benchmark &&
                (owner as RuntimeOwner.Benchmark).runId != runId
            ) {
                throw BenchmarkRunningException()
            }
            owner = RuntimeOwner.Benchmark(runId)
        }
        var acquired = false
        try {
            mutex.withLock {
                synchronized(ownerLock) {
                    if (owner is RuntimeOwner.Chat) {
                        throw ChatGenerationRunningException()
                    }
                    if (owner is RuntimeOwner.Benchmark &&
                        (owner as RuntimeOwner.Benchmark).runId != runId
                    ) {
                        throw BenchmarkRunningException()
                    }
                    owner = RuntimeOwner.Benchmark(runId)
                    acquired = true
                }
                onPrepareExclusiveMode()
                return block()
            }
        } finally {
            synchronized(ownerLock) {
                if (acquired && owner is RuntimeOwner.Benchmark &&
                    (owner as RuntimeOwner.Benchmark).runId == runId
                ) {
                    owner = RuntimeOwner.Idle
                }
            }
        }
    }
}

class BenchmarkRunningException : IllegalStateException("Benchmark is running")

class ChatGenerationRunningException : IllegalStateException("Chat generation is running")
