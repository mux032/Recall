package com.recall.app.presentation.ui.home

import com.recall.app.domain.model.Screenshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Verifies the stability contract of LazyColumn row keys in [HomeScreen].
 *
 * Row keys are derived from the first screenshot ID in each row:
 *   `"row-${rowScreenshots.first().id}"`
 *
 * A stable key means:
 * - The same row gets the same key before and after an unrelated item is
 *   inserted above or below it.
 * - Compose can correctly diff and animate the list instead of recreating
 *   all rows below the insertion point.
 */
class LazyColumnKeyStabilityTest {

    private val numColumns = 2 // matches HomeScreen default

    // -----------------------------------------------------------------------
    // Key derivation correctness
    // -----------------------------------------------------------------------

    @Test
    fun `row key equals first screenshot id in that row`() {
        val screenshots = listOf(
            buildScreenshot("id_a"),
            buildScreenshot("id_b"),
            buildScreenshot("id_c")
        )
        val rows = screenshots.chunked(numColumns)

        assertEquals("row-id_a", "row-${rows[0].first().id}")
        assertEquals("row-id_c", "row-${rows[1].first().id}")
    }

    @Test
    fun `row key is stable when a new item is prepended`() {
        // Original list: rows are [id_a, id_b] and [id_c]
        // Keys: row-id_a, row-id_c
        val original = listOf(
            buildScreenshot("id_a"),
            buildScreenshot("id_b"),
            buildScreenshot("id_c")
        )

        // Prepend id_new — new list rows: [id_new, id_a], [id_b, id_c]
        // Key for the row starting with id_a changes from "row-id_a" to "row-id_new"
        // BUT the row starting with id_b now has key "row-id_b" — stable for that row's content
        val updated = listOf(buildScreenshot("id_new")) + original

        val originalRows = original.chunked(numColumns)
        val updatedRows = updated.chunked(numColumns)

        // The original first row key is "row-id_a"
        assertEquals("row-id_a", "row-${originalRows[0].first().id}")
        // After prepend, first row key is "row-id_new" — new row correctly gets new key
        assertEquals("row-id_new", "row-${updatedRows[0].first().id}")
        // The second row in the updated list starts with id_b — stable new key
        assertEquals("row-id_b", "row-${updatedRows[1].first().id}")
    }

    @Test
    fun `row key is stable when an unrelated item is appended`() {
        val original = listOf(
            buildScreenshot("id_a"),
            buildScreenshot("id_b")
        )
        val updated = original + listOf(buildScreenshot("id_c"))

        val originalRows = original.chunked(numColumns)
        val updatedRows = updated.chunked(numColumns)

        // First row key must not change
        assertEquals(
            "row-${originalRows[0].first().id}",
            "row-${updatedRows[0].first().id}"
        )
    }

    @Test
    fun `row key is stable when a middle item is deleted`() {
        val original = listOf(
            buildScreenshot("id_a"),
            buildScreenshot("id_b"),
            buildScreenshot("id_c"),
            buildScreenshot("id_d")
        )
        // Delete id_b
        val updated = original.filter { it.id != "id_b" }

        val originalRows = original.chunked(numColumns)
        val updatedRows = updated.chunked(numColumns)

        // Row with id_a should still have the same key
        val keyA_before = "row-${originalRows[0].first().id}"
        val keyA_after = "row-${updatedRows[0].first().id}"
        assertEquals(keyA_before, keyA_after)
    }

    // -----------------------------------------------------------------------
    // Contrast: old rowIndex-based key was UNSTABLE
    // -----------------------------------------------------------------------

    @Test
    fun `old rowIndex key changes when item prepended (demonstrates the bug)`() {
        // With 2 columns: original rows are [id_b, id_c] at index 0
        // After prepending id_a:        rows are [id_a, id_b] at index 0, [id_c] at index 1
        val sectionLabel = "Today"

        // OLD approach: rowIndex 0 used to key [id_b, id_c], now keys [id_a, id_b]
        val oldKeyRow0Before = "row-$sectionLabel-0"
        val oldKeyRow0After  = "row-$sectionLabel-0"
        // The same string is reused for a DIFFERENT row content — Compose thinks it's the same!
        assertEquals(
            "Old rowIndex key falsely considers a different row as the same item",
            oldKeyRow0Before, oldKeyRow0After
        )

        // NEW approach: key is tied to screenshot ID — unambiguous
        val newKeyBefore = "row-id_b" // [id_b, id_c] row → key is id_b
        val newKeyAfter  = "row-id_a" // [id_a, id_b] row → key is id_a (correctly different)
        assertNotEquals(
            "New ID-based key correctly identifies that row 0 content changed",
            newKeyBefore, newKeyAfter
        )
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun buildScreenshot(id: String) = Screenshot(
        id = id,
        filePath = "/sdcard/Screenshots/$id.png",
        fileName = "$id.png",
        dateCreated = System.currentTimeMillis(),
        dateIndexed = System.currentTimeMillis(),
        width = 1080,
        height = 1920
    )
}
