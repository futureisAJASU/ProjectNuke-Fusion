package com.projectnuke.fusion.ui

import com.projectnuke.fusion.chat.ComposerDraftState
import com.projectnuke.fusion.chat.PersistentComposerDraftStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CommittedDraftReconciliationDebtStoreTest {

    @Test
    fun `record and remove are bounded and keyed by conversation and token`() {
        val dir = Files.createTempDirectory("fusion-reconciliation-debt").toFile()
        val file = File(dir, "committed_draft_reconciliation_debt.json")
        val debt = CommittedDraftReconciliationDebt(1L, "token-a", "hello", setOf("/managed/a"), 0, 0L)

        repeat(70) { i ->
            CommittedDraftReconciliationDebtStore.record(file, debt.copy(draftKey = i.toLong()))
        }
        CommittedDraftReconciliationDebtStore.record(file, debt.copy(draftKey = 1L, token = "token-b"))
        CommittedDraftReconciliationDebtStore.record(file, debt)
        assertEquals(64, loadAll(file).size)

        CommittedDraftReconciliationDebtStore.remove(file, 1L, "token-a")
        assertTrue(loadAll(file).none { it.draftKey == 1L && it.token == "token-a" })
        assertTrue(loadAll(file).any { it.draftKey == 1L && it.token == "token-b" })
        dir.deleteRecursively()
    }

    @Test
    fun `malformed debt file loads as empty and allows writes`() {
        val dir = Files.createTempDirectory("fusion-reconciliation-debt").toFile()
        val file = File(dir, "committed_draft_reconciliation_debt.json")
        file.writeText("[{]broken}[")

        assertTrue(
            CommittedDraftReconciliationDebtStore.record(
                file,
                CommittedDraftReconciliationDebt(1L, "token-a", "hello", emptySet(), 0, 0L),
            )
        )
        assertTrue(loadAll(file).isNotEmpty())
        dir.deleteRecursively()
    }

    @Test
    fun `retry reconciles the exact owned draft and clears the debt`() = runTest {
        val dir = Files.createTempDirectory("fusion-reconciliation-debt").toFile()
        val debtFile = File(dir, "committed_draft_reconciliation_debt.json")
        val draftFile = File(dir, "composer_drafts.json")
        val managedA = File(dir, "a.txt").apply { writeText("a") }
        val managedB = File(dir, "b.txt").apply { writeText("b") }
        val registered = mutableListOf<String>()
        val store = PersistentComposerDraftStore(
            file = draftFile,
            resolveManagedAttachment = { path ->
                if (path == managedA.absolutePath || path == managedB.absolutePath) File(path) else null
            },
            registerPendingAttachment = { registered += it.absolutePath; it.absolutePath },
        )
        store.write(
            1L,
            mapOf(
                7L to ComposerDraftState(
                    rawInput = "hello",
                    pendingAttachments = listOf(
                        managed("a", managedA.absolutePath),
                        managed("b", managedB.absolutePath),
                    ),
                    activeSubmissionToken = "token-a",
                    version = 3L,
                ),
            ),
        )
        val unregistered = mutableListOf<String>()
        CommittedDraftReconciliationDebtStore.record(
            debtFile,
            CommittedDraftReconciliationDebt(
                draftKey = 7L,
                token = "token-a",
                capturedRawInput = "hello",
                committedPaths = setOf(managedA.absolutePath),
                attempts = 0,
                lastAttemptAt = 0L,
            ),
        )

        val reconciled = CommittedDraftReconciliationDebtStore.retry(
            owner = DraftReconciliationOwner { debt ->
                val drafts = store.load()
                val current = drafts[debt.draftKey] ?: return@DraftReconciliationOwner DraftReconciliationResult(true, true)
                if (current.activeSubmissionToken != debt.token) return@DraftReconciliationOwner DraftReconciliationResult(true, false)
                DraftReconciliationResult(store.write(drafts + (debt.draftKey to current.copy(
                    rawInput = if (current.rawInput == debt.capturedRawInput) "" else current.rawInput,
                    pendingAttachments = current.pendingAttachments.filterNot { it.localPath in debt.committedPaths },
                    activeSubmissionToken = null,
                    version = current.version + 1L,
                ))), true)
            },
            unregisterPath = { unregistered += it },
            file = debtFile,
            limit = 4,
        )

        assertEquals(1, reconciled)
        assertTrue(loadAll(debtFile).isEmpty())
        assertEquals(listOf(managedA.absolutePath), unregistered)
        val after = store.load()[7L]
        assertEquals("", after?.rawInput)
        assertNull(after?.activeSubmissionToken)
        assertEquals(listOf(managedB.absolutePath), after?.pendingAttachments?.map { it.localPath })
        assertEquals(4L, after?.version)
        dir.deleteRecursively()
    }

    @Test
    fun `retry preserves a draft owned by a newer submission and resolves the debt`() = runTest {
        val dir = Files.createTempDirectory("fusion-reconciliation-debt").toFile()
        val debtFile = File(dir, "committed_draft_reconciliation_debt.json")
        val draftFile = File(dir, "composer_drafts.json")
        val store = PersistentComposerDraftStore(
            file = draftFile,
            resolveManagedAttachment = { null },
            registerPendingAttachment = { it.path },
        )
        store.write(
            1L,
            mapOf(
                7L to ComposerDraftState(
                    rawInput = "newer edit",
                    activeSubmissionToken = "token-newer",
                    version = 5L,
                ),
            ),
        )
        val unregistered = mutableListOf<String>()
        CommittedDraftReconciliationDebtStore.record(
            debtFile,
            CommittedDraftReconciliationDebt(
                draftKey = 7L,
                token = "token-stale",
                capturedRawInput = "hello",
                committedPaths = setOf("/managed/a"),
                attempts = 0,
                lastAttemptAt = 0L,
            ),
        )

        val reconciled = CommittedDraftReconciliationDebtStore.retry(
            owner = DraftReconciliationOwner { debt ->
                val current = store.load()[debt.draftKey] ?: return@DraftReconciliationOwner DraftReconciliationResult(true, true)
                if (current.activeSubmissionToken != debt.token) return@DraftReconciliationOwner DraftReconciliationResult(true, false)
                DraftReconciliationResult(store.write(store.load()), true)
            },
            unregisterPath = { unregistered += it },
            file = debtFile,
            limit = 4,
        )

        assertEquals(1, reconciled)
        assertTrue(loadAll(debtFile).isEmpty())
        assertTrue(unregistered.isEmpty())
        val after = store.load()[7L]
        assertEquals("newer edit", after?.rawInput)
        assertEquals("token-newer", after?.activeSubmissionToken)
        dir.deleteRecursively()
    }

    @Test
    fun `retry with a missing draft releases committed registrations`() = runTest {
        val dir = Files.createTempDirectory("fusion-reconciliation-debt").toFile()
        val debtFile = File(dir, "committed_draft_reconciliation_debt.json")
        val draftFile = File(dir, "composer_drafts.json")
        val store = PersistentComposerDraftStore(
            file = draftFile,
            resolveManagedAttachment = { null },
            registerPendingAttachment = { it.path },
        )
        val unregistered = mutableListOf<String>()
        CommittedDraftReconciliationDebtStore.record(
            debtFile,
            CommittedDraftReconciliationDebt(
                draftKey = 7L,
                token = "token-a",
                capturedRawInput = "hello",
                committedPaths = setOf("/managed/a"),
                attempts = 0,
                lastAttemptAt = 0L,
            ),
        )

        val reconciled = CommittedDraftReconciliationDebtStore.retry(
            owner = DraftReconciliationOwner { DraftReconciliationResult(true, true) },
            unregisterPath = { unregistered += it },
            file = debtFile,
            limit = 4,
        )

        assertEquals(1, reconciled)
        assertTrue(loadAll(debtFile).isEmpty())
        assertEquals(listOf("/managed/a"), unregistered)
        dir.deleteRecursively()
    }

    @Test
    fun `retry keeps a debt whose write failed and increments attempts`() = runTest {
        val dir = Files.createTempDirectory("fusion-reconciliation-debt").toFile()
        val debtFile = File(dir, "committed_draft_reconciliation_debt.json")
        val store = FailingDraftStoreForRetry(dir)
        CommittedDraftReconciliationDebtStore.record(
            debtFile,
            CommittedDraftReconciliationDebt(
                draftKey = 7L,
                token = "token-a",
                capturedRawInput = "hello",
                committedPaths = setOf("/managed/a"),
                attempts = 0,
                lastAttemptAt = 0L,
            ),
        )

        val reconciled = CommittedDraftReconciliationDebtStore.retry(
            owner = DraftReconciliationOwner { DraftReconciliationResult(false, false) },
            unregisterPath = {},
            file = debtFile,
            limit = 4,
        )

        assertEquals(0, reconciled)
        val retained = loadAll(debtFile).single()
        assertEquals(7L, retained.draftKey)
        assertEquals(1, retained.attempts)
        dir.deleteRecursively()
    }

    private fun loadAll(file: File): List<CommittedDraftReconciliationDebt> {
        if (!file.isFile) return emptyList()
        val array = org.json.JSONArray(file.readText())
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            CommittedDraftReconciliationDebt(
                draftKey = item.optLong("id"),
                token = item.optString("token"),
                capturedRawInput = item.optString("rawInput"),
                committedPaths = item.optJSONArray("paths")?.let { paths ->
                    (0 until paths.length()).map { paths.optString(it) }.toSet()
                }.orEmpty(),
                attempts = item.optInt("attempts"),
                lastAttemptAt = item.optLong("lastAttemptAt"),
            )
        }
    }

    private fun managed(path: String, absolutePath: String) =
        com.projectnuke.fusion.chat.PendingAttachmentIdentity(path, "text/plain", absolutePath)

    private class FailingDraftStoreForRetry(dir: File) : PersistentComposerDraftStore(
        file = File(dir, "composer_drafts.json"),
        resolveManagedAttachment = { null },
        registerPendingAttachment = { it.path },
    ) {
        override suspend fun load(): Map<Long, ComposerDraftState> =
            mapOf(7L to ComposerDraftState(rawInput = "hello", activeSubmissionToken = "token-a", version = 3L))

        override suspend fun write(writeId: Long, drafts: Map<Long, ComposerDraftState>): Boolean = false
    }
}
