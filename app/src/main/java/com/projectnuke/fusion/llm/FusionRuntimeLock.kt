package com.projectnuke.fusion.llm

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object FusionRuntimeLock {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T {
        return mutex.withLock { block() }
    }
}
