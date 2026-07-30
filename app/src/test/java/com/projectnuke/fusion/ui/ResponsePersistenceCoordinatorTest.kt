package com.projectnuke.fusion.ui

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ResponsePersistenceCoordinatorTest {

    @Test
    fun `timestamp failure keeps a successfully inserted answer`() = runBlocking {
        var deleted = false
        var reported: Throwable? = null
        val expected = IllegalStateException("timestamp")

        val id = persistAssistantMessage(
            insertMessage = { 41L },
            updateConversationTimestamp = { throw expected },
            onTimestampFailure = { reported = it },
        )

        assertEquals(41L, id)
        assertFalse(deleted)
        assertSame(expected, reported)
    }

    @Test
    fun `version save failure removes the new answer`() = runBlocking {
        val events = mutableListOf<String>()
        val expected = IllegalStateException("version save")

        try {
            persistAssistantVersion(
                loadPreviousState = { "old" },
                insertMessage = { events += "insert"; 9L },
                buildUpdatedState = { _, _ -> "new" },
                saveState = { events += "save"; throw expected },
                restoreState = { events += "restore" },
                deleteMessage = { events += "delete:$it" },
                updateConversationTimestamp = { events += "timestamp" },
            )
            fail("Expected version save failure")
        } catch (actual: IllegalStateException) {
            assertEquals(expected.message, actual.message)
        }

        assertEquals(listOf("insert", "save", "delete:9", "restore"), events)
    }

    @Test
    fun `timestamp failure keeps answer and version state`() = runBlocking {
        val events = mutableListOf<String>()
        var reported: Throwable? = null
        val expected = IllegalStateException("timestamp")

        val persisted = persistAssistantVersion(
            loadPreviousState = { "old" },
            insertMessage = { events += "insert"; 12L },
            buildUpdatedState = { previous, id -> "$previous+$id" },
            saveState = { events += "save:$it" },
            restoreState = { events += "restore:$it" },
            deleteMessage = { events += "delete:$it" },
            updateConversationTimestamp = { events += "timestamp"; throw expected },
            onTimestampFailure = { reported = it },
        )

        assertEquals(12L, persisted.messageId)
        assertEquals("old+12", persisted.state)
        assertEquals(listOf("insert", "save:old+12", "timestamp"), events)
        assertSame(expected, reported)
        assertTrue(events.none { it.startsWith("delete") || it.startsWith("restore") })
    }
}
