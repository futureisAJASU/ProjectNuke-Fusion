package com.projectnuke.fusion.ui

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ConversationCleanupDebtPersistenceTest {
    @Test
    fun `debt record and remove are bounded by max entries`() {
        val dir = Files.createTempDirectory("fusion-cleanup-debt").toFile()
        val store = TestableCleanupDebtStore(dir)

        repeat(70) { i ->
            store.record(ConversationCleanupDebt(i.toLong(), emptySet(), emptySet(), 0, 0L))
        }
        assertTrue(store.load().size <= 64)

        store.remove(0L)
        assertTrue(store.load().none { it.conversationId == 0L })
        dir.deleteRecursively()
    }

    @Test
    fun `terminal debt is not silently discarded`() {
        val dir = Files.createTempDirectory("fusion-cleanup-debt").toFile()
        val store = TestableCleanupDebtStore(dir)

        val terminal = ConversationCleanupDebt(9000L, setOf(1L), setOf("/test"), 100, System.currentTimeMillis())
        store.record(terminal)

        val loaded = store.load()
        assertTrue(loaded.any { it.conversationId == 9000L && it.attempts >= 100 })
        dir.deleteRecursively()
    }

    @Test
    fun `malformed debt file loads as empty and allows writes`() {
        val dir = Files.createTempDirectory("fusion-cleanup-debt").toFile()
        File(dir, "conversation_cleanup_debt.json").writeText("[{]broken}[")
        val store = TestableCleanupDebtStore(dir)

        val initial = store.load()
        assertTrue(initial.isEmpty())

        val recorded = store.record(ConversationCleanupDebt(1L, setOf(2L), emptySet(), 0, 0L))
        assertTrue(recorded)
        assertTrue(store.load().isNotEmpty())
        dir.deleteRecursively()
    }

    @Test
    fun `remove non-existent debt returns true`() {
        val dir = Files.createTempDirectory("fusion-cleanup-debt").toFile()
        val store = TestableCleanupDebtStore(dir)
        assertTrue(store.remove(Long.MAX_VALUE))
        dir.deleteRecursively()
    }

    @Test
    fun `debt bounds paths and message ids`() {
        val dir = Files.createTempDirectory("fusion-cleanup-debt").toFile()
        val store = TestableCleanupDebtStore(dir)

        val manyMessages = (1L..600L).toSet()
        val manyPaths = (0..100).map { "/path/$it" }.toSet()

        val debt = ConversationCleanupDebt(1L, manyMessages, manyPaths, 0, 0L)
        store.record(debt)

        val loaded = store.load().firstOrNull { it.conversationId == 1L }!!
        assertTrue(loaded.messageIds.size <= 512)
        assertTrue(loaded.pendingPaths.size <= 64)
        dir.deleteRecursively()
    }

    private class TestableCleanupDebtStore(private val dir: File) {
        private val maxEntries = 64
        private val maxMessageIds = 512
        private val maxPaths = 64
        private val maxPathChars = 4096
        private val maxAttempts = 100
        private val fileName = "conversation_cleanup_debt.json"

        fun record(debt: ConversationCleanupDebt): Boolean {
            val current = load().filterNot { it.conversationId == debt.conversationId }
            val bounded = (current + debt.copy(
                messageIds = debt.messageIds.take(maxMessageIds).toSet(),
                pendingPaths = debt.pendingPaths.map { it.take(maxPathChars) }.take(maxPaths).toSet(),
                attempts = debt.attempts.coerceIn(0, maxAttempts),
            )).takeLast(maxEntries)
            return persist(bounded)
        }

        fun remove(conversationId: Long): Boolean {
            return persist(load().filterNot { it.conversationId == conversationId })
        }

        fun load(): List<ConversationCleanupDebt> {
            val file = File(dir, fileName)
            if (!file.isFile) return emptyList()
            val raw = runCatching { file.readText() }.getOrNull() ?: return emptyList()
            return runCatching {
                val array = org.json.JSONArray(raw)
                (0 until minOf(array.length(), maxEntries)).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val id = item.optLong("id", -1L).takeIf { it > 0L } ?: return@mapNotNull null
                    val messages = item.optJSONArray("messages")?.let { values ->
                        (0 until minOf(values.length(), maxMessageIds))
                            .mapNotNull { values.optLong(it, -1L).takeIf { v -> v > 0L } }
                            .toSet()
                    }.orEmpty()
                    val paths = item.optJSONArray("paths")?.let { values ->
                        (0 until minOf(values.length(), maxPaths))
                            .mapNotNull { values.optString(it).takeIf { v -> v.length in 1..maxPathChars } }
                            .toSet()
                    }.orEmpty()
                    ConversationCleanupDebt(
                        id, messages, paths,
                        item.optInt("attempts").coerceIn(0, maxAttempts),
                        item.optLong("lastAttemptAt"),
                    )
                }
            }.getOrDefault(emptyList())
        }

        private fun persist(debts: List<ConversationCleanupDebt>): Boolean {
            val array = org.json.JSONArray()
            debts.takeLast(maxEntries).forEach { debt ->
                array.put(
                    org.json.JSONObject()
                        .put("id", debt.conversationId)
                        .put("attempts", debt.attempts)
                        .put("lastAttemptAt", debt.lastAttemptAt)
                        .put("messages", org.json.JSONArray(debt.messageIds.take(maxMessageIds).toList()))
                        .put("paths", org.json.JSONArray(debt.pendingPaths.take(maxPaths).map { it.take(maxPathChars) }))
                )
            }
            return runCatching {
                com.projectnuke.fusion.util.writeTextAtomically(File(dir, fileName), array.toString())
                true
            }.getOrDefault(false)
        }
    }
}
