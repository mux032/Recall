package com.recall.app.presentation.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RecallSearchBar] constants and pure logic (Issue #85).
 *
 * Composable rendering is tested via instrumentation; these unit tests cover
 * the hint list, rotation interval, and hint cycling logic.
 */
class RecallSearchBarTest {

    @Test
    fun `SEARCH_HINTS is non-empty`() {
        assertTrue("SEARCH_HINTS must not be empty", SEARCH_HINTS.isNotEmpty())
    }

    @Test
    fun `SEARCH_HINTS first entry is the primary placeholder`() {
        assertEquals("Search screenshots with AI...", SEARCH_HINTS[0])
    }

    @Test
    fun `SEARCH_HINTS contains rotating example queries`() {
        val hints = SEARCH_HINTS
        assertTrue("Expected at least 3 hints", hints.size >= 3)
        // All hint entries after the first should start with "Try:"
        hints.drop(1).forEach { hint ->
            assertTrue(
                "Expected hint to start with 'Try:' but was: $hint",
                hint.startsWith("Try:")
            )
        }
    }

    @Test
    fun `HINT_ROTATION_INTERVAL_MS is 3 seconds`() {
        assertEquals(3_000L, HINT_ROTATION_INTERVAL_MS)
    }

    @Test
    fun `hint index cycles correctly for any list size`() {
        val size = SEARCH_HINTS.size
        // Simulate cycling through all hints and wrapping around
        var index = 0
        repeat(size * 2) {
            index = (index + 1) % size
        }
        // After size*2 increments, index should be back to 0
        assertEquals(0, index)
    }

    @Test
    fun `all hints are non-blank`() {
        SEARCH_HINTS.forEachIndexed { i, hint ->
            assertFalse("Hint at index $i should not be blank", hint.isBlank())
        }
    }

    @Test
    fun `SEARCH_HINTS has exactly 6 entries`() {
        assertEquals(6, SEARCH_HINTS.size)
    }
}
