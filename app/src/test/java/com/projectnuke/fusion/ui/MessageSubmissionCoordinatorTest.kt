package com.projectnuke.fusion.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MessageSubmissionCoordinatorTest {

    @Test
    fun `new conversation commit preserves exact production order`() = runBlocking {
        val events = mutableListOf<String>()
        val state = SubmissionCommitState(conversationId = 0L)

        val result = commitAndSettleUserSubmission(
            state = state,
            parentJob = Job(),
            runInTransaction = { block ->
                events += "transaction-begin"
                block()
                events += "transaction-commit"
            },
            createConversation = {
                events += "conversation-insert"
                42L
            },
            insertUserMessage = { id -> events += "message-insert:$id" },
            publishConversation = { id -> events += "publish:$id" },
            reconcileCommittedDraft = { events += "reconcile" },
            updateConversationTimestamp = { id -> events += "timestamp:$id" },
        )

        assertEquals(
            listOf(
                "transaction-begin",
                "conversation-insert",
                "message-insert:42",
                "transaction-commit",
                "publish:42",
                "reconcile",
                "timestamp:42",
            ),
            events,
        )
        assertEquals(42L, result.conversationId)
        assertTrue(state.conversationWasCreated)
        assertTrue(state.messageInserted)
        assertNull(result.publicationFailure)
    }

    @Test
    fun `failed atomic transaction restores uncommitted state and never settles composer`() = runBlocking {
        val events = mutableListOf<String>()
        val state = SubmissionCommitState(conversationId = 0L)
        val expected = IllegalStateException("insert failed")

        try {
            commitAndSettleUserSubmission(
                state = state,
                parentJob = Job(),
                runInTransaction = { block ->
                    events += "transaction-begin"
                    block()
                    error("transaction must not commit")
                },
                createConversation = {
                    events += "conversation-insert"
                    7L
                },
                insertUserMessage = {
                    events += "message-insert"
                    throw expected
                },
                publishConversation = { events += "publish" },
                reconcileCommittedDraft = { events += "reconcile" },
                updateConversationTimestamp = { events += "timestamp" },
            )
            fail("Expected insert failure")
        } catch (actual: IllegalStateException) {
            assertEquals(expected.message, actual.message)
        }

        assertEquals(
            listOf("transaction-begin", "conversation-insert", "message-insert"),
            events,
        )
        assertEquals(0L, state.conversationId)
        assertFalse(state.conversationWasCreated)
        assertFalse(state.messageInserted)
    }

    @Test
    fun `existing conversation failure preserves its identity and never settles composer`() = runBlocking {
        val state = SubmissionCommitState(conversationId = 9L)
        var settled = false

        try {
            commitAndSettleUserSubmission(
                state = state,
                parentJob = Job(),
                runInTransaction = { block -> block() },
                createConversation = { error("must not create") },
                insertUserMessage = { throw IllegalArgumentException("failed") },
                publishConversation = { error("must not publish") },
                reconcileCommittedDraft = { settled = true },
                updateConversationTimestamp = { error("must not update") },
            )
            fail("Expected insert failure")
        } catch (_: IllegalArgumentException) {
        }

        assertEquals(9L, state.conversationId)
        assertFalse(state.conversationWasCreated)
        assertFalse(state.messageInserted)
        assertFalse(settled)
    }

    @Test
    fun `cancellation before commit performs zero side effects`() = runBlocking {
        val parentJob = Job().apply { cancel() }
        var sideEffects = 0

        try {
            commitAndSettleUserSubmission(
                state = SubmissionCommitState(0L),
                parentJob = parentJob,
                runInTransaction = { sideEffects++; it() },
                createConversation = { sideEffects++; 1L },
                insertUserMessage = { sideEffects++ },
                publishConversation = { sideEffects++ },
                reconcileCommittedDraft = { sideEffects++ },
                updateConversationTimestamp = { sideEffects++ },
            )
            fail("Expected cancellation")
        } catch (_: CancellationException) {
        }

        assertEquals(0, sideEffects)
    }

    @Test
    fun `cancellation after transaction starts still commits and settles exactly once`() = runBlocking {
        val parentJob = Job()
        val events = mutableListOf<String>()
        val state = SubmissionCommitState(0L)

        commitAndSettleUserSubmission(
            state = state,
            parentJob = parentJob,
            runInTransaction = { block -> block() },
            createConversation = {
                events += "conversation-insert"
                parentJob.cancel()
                11L
            },
            insertUserMessage = { events += "message-insert" },
            publishConversation = { events += "publish" },
            reconcileCommittedDraft = { events += "reconcile" },
            updateConversationTimestamp = { events += "timestamp" },
        )

        assertEquals(
            listOf("conversation-insert", "message-insert", "publish", "reconcile", "timestamp"),
            events,
        )
        assertTrue(state.messageInserted)
    }

    @Test
    fun `transaction commit failure never reports a message or conversation as committed`() = runBlocking {
        val events = mutableListOf<String>()
        val state = SubmissionCommitState(0L)
        val expected = IllegalStateException("transaction commit failed")

        try {
            commitAndSettleUserSubmission(
                state = state,
                parentJob = Job(),
                runInTransaction = { block ->
                    block()
                    throw expected
                },
                createConversation = { 33L },
                insertUserMessage = { events += "message-insert" },
                publishConversation = { events += "publish" },
                reconcileCommittedDraft = { events += "reconcile" },
                updateConversationTimestamp = { events += "timestamp" },
            )
            fail("Expected transaction failure")
        } catch (actual: IllegalStateException) {
            assertEquals(expected.message, actual.message)
        }

        assertFalse(state.messageInserted)
        assertFalse(state.conversationWasCreated)
        assertEquals(0L, state.conversationId)
        assertEquals(listOf("message-insert"), events)
    }

    @Test
    fun `publication failure is reported but committed draft still reconciles`() = runBlocking {
        val publicationFailure = IllegalStateException("navigation failed")
        var reconciled = false
        var reported: Throwable? = null

        val result = commitAndSettleUserSubmission(
            state = SubmissionCommitState(0L),
            parentJob = Job(),
            runInTransaction = { block -> block() },
            createConversation = { 12L },
            insertUserMessage = {},
            publishConversation = { throw publicationFailure },
            reconcileCommittedDraft = { reconciled = true },
            updateConversationTimestamp = {},
            onPublicationFailure = { reported = it },
        )

        assertTrue(reconciled)
        assertSame(publicationFailure, result.publicationFailure)
        assertSame(publicationFailure, reported)
    }

    @Test
    fun `timestamp failure is best effort`() = runBlocking {
        val timestampFailure = IllegalStateException("timestamp failed")
        var reported: Throwable? = null

        val result = commitAndSettleUserSubmission(
            state = SubmissionCommitState(3L),
            parentJob = Job(),
            runInTransaction = { block -> block() },
            createConversation = { error("must not create") },
            insertUserMessage = {},
            publishConversation = {},
            reconcileCommittedDraft = {},
            updateConversationTimestamp = { throw timestampFailure },
            onTimestampFailure = { reported = it },
        )

        assertEquals(3L, result.conversationId)
        assertSame(timestampFailure, reported)
    }

    @Test
    fun `registry owner remains active during install and clears after success or failure`() = runBlocking {
        val owner = MessageSubmissionOwner("owner", 1L)
        var activeOwner: MessageSubmissionOwner? = owner

        val result = installGenerationRequestAndSettleOwner(
            owner = owner,
            getActiveOwner = { activeOwner },
            setActiveOwner = { activeOwner = it },
        ) {
            assertEquals(owner, activeOwner)
            "installed"
        }

        assertEquals("installed", result)
        assertNull(activeOwner)

        activeOwner = owner
        try {
            installGenerationRequestAndSettleOwner<Unit>(
                owner = owner,
                getActiveOwner = { activeOwner },
                setActiveOwner = { activeOwner = it },
            ) {
                throw IllegalStateException("install failed")
            }
            fail("Expected install failure")
        } catch (_: IllegalStateException) {
        }
        assertNull(activeOwner)
    }

    @Test
    fun `registry installation stays cancellable and stale completion preserves newer owner`() = runBlocking {
        val oldOwner = MessageSubmissionOwner("old", 1L)
        val newerOwner = MessageSubmissionOwner("new", 2L)
        var activeOwner: MessageSubmissionOwner? = oldOwner
        var installStarted = false
        var installCompleted = false

        val job = launch {
            installGenerationRequestAndSettleOwner(
                owner = oldOwner,
                getActiveOwner = { activeOwner },
                setActiveOwner = { activeOwner = it },
            ) {
                installStarted = true
                delay(Long.MAX_VALUE)
                installCompleted = true
            }
        }
        while (!installStarted) yield()
        assertEquals(oldOwner, activeOwner)
        job.cancelAndJoin()
        assertFalse(installCompleted)
        assertNull(activeOwner)

        activeOwner = newerOwner
        installGenerationRequestAndSettleOwner(
            owner = oldOwner,
            getActiveOwner = { activeOwner },
            setActiveOwner = { activeOwner = it },
        ) {
            Unit
        }
        assertEquals(newerOwner, activeOwner)
    }
}
