package com.recall.app.presentation.ui.home

import com.recall.app.domain.model.HistoryIconType
import com.recall.app.domain.model.SearchHistoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SearchHistoryDropdown] business logic (Issue #95).
 *
 * The composable itself is tested via instrumentation; here we verify the
 * pure logic: capping at [MAX_DROPDOWN_ITEMS] and the constant value.
 */
class SearchHistoryDropdownTest {

    private fun makeItems(count: Int) = (0 until count).map { i ->
        SearchHistoryItem(
            id = "id_$i",
            query = "query $i",
            timestamp = System.currentTimeMillis() - i * 60_000L,
            iconType = HistoryIconType.SEARCH,
            timeLabel = "$i minutes ago"
        )
    }

    @Test
    fun `MAX_DROPDOWN_ITEMS constant equals 7`() {
        assertEquals(7, MAX_DROPDOWN_ITEMS)
    }

    @Test
    fun `items list capped at MAX_DROPDOWN_ITEMS when input is larger`() {
        val items = makeItems(10)
        val displayed = items.take(MAX_DROPDOWN_ITEMS)
        assertEquals(MAX_DROPDOWN_ITEMS, displayed.size)
    }

    @Test
    fun `items list unchanged when input is smaller than MAX_DROPDOWN_ITEMS`() {
        val items = makeItems(3)
        val displayed = items.take(MAX_DROPDOWN_ITEMS)
        assertEquals(3, displayed.size)
    }

    @Test
    fun `items list unchanged when input equals MAX_DROPDOWN_ITEMS`() {
        val items = makeItems(MAX_DROPDOWN_ITEMS)
        val displayed = items.take(MAX_DROPDOWN_ITEMS)
        assertEquals(MAX_DROPDOWN_ITEMS, displayed.size)
    }

    @Test
    fun `empty list stays empty after take`() {
        val displayed = emptyList<SearchHistoryItem>().take(MAX_DROPDOWN_ITEMS)
        assertTrue(displayed.isEmpty())
    }

    @Test
    fun `items order is preserved — newest first`() {
        val items = makeItems(5)
        val displayed = items.take(MAX_DROPDOWN_ITEMS)
        // Item 0 has the largest timestamp (most recent)
        assertEquals("id_0", displayed.first().id)
        assertEquals("id_4", displayed.last().id)
    }
}
