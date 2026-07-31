package com.projectnuke.fusion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSearchPolicyTest {
    @Test
    fun `short queries do not search message bodies`() {
        assertNull(boundedConversationSearchQuery(" a "))
        assertNull(boundedConversationSearchQuery("   "))
    }

    @Test
    fun `queries and results are bounded`() {
        assertEquals(100, boundedConversationSearchQuery("x".repeat(500))?.length)
        assertTrue(ConversationSearchResultLimit in 1..200)
        assertTrue(ConversationSearchDebounceMs in 250L..350L)
    }
}
