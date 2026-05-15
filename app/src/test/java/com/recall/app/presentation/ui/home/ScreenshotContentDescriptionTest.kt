package com.recall.app.presentation.ui.home

import com.recall.app.domain.model.Screenshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [screenshotContentDescription].
 *
 * Verifies the TalkBack content description logic:
 * - OCR text snippet (first 100 chars) when available
 * - Time-based fallback when OCR text is absent or blank
 * - Truncation at exactly [CONTENT_DESCRIPTION_MAX_CHARS] characters
 */
class ScreenshotContentDescriptionTest {

    // -----------------------------------------------------------------------
    // OCR text available
    // -----------------------------------------------------------------------

    @Test
    fun `returns OCR text snippet when ocrText is available`() {
        val screenshot = buildScreenshot(ocrText = "Invoice #1234 Total due: \$99.99")
        val description = screenshotContentDescription(screenshot)
        assertEquals("Screenshot: Invoice #1234 Total due: \$99.99", description)
    }

    @Test
    fun `truncates OCR text to CONTENT_DESCRIPTION_MAX_CHARS characters`() {
        val longText = "A".repeat(200)
        val screenshot = buildScreenshot(ocrText = longText)
        val description = screenshotContentDescription(screenshot)
        val expected = "Screenshot: ${"A".repeat(CONTENT_DESCRIPTION_MAX_CHARS)}"
        assertEquals(expected, description)
    }

    @Test
    fun `OCR text exactly at limit is not truncated`() {
        val exactText = "B".repeat(CONTENT_DESCRIPTION_MAX_CHARS)
        val screenshot = buildScreenshot(ocrText = exactText)
        val description = screenshotContentDescription(screenshot)
        assertEquals("Screenshot: $exactText", description)
    }

    @Test
    fun `OCR text shorter than limit is used in full`() {
        val shortText = "Hello world"
        val screenshot = buildScreenshot(ocrText = shortText)
        val description = screenshotContentDescription(screenshot)
        assertEquals("Screenshot: Hello world", description)
    }

    @Test
    fun `leading and trailing whitespace in OCR text is trimmed`() {
        val screenshot = buildScreenshot(ocrText = "  Hello world  ")
        val description = screenshotContentDescription(screenshot)
        assertEquals("Screenshot: Hello world", description)
    }

    // -----------------------------------------------------------------------
    // OCR text absent or blank → time-based fallback
    // -----------------------------------------------------------------------

    @Test
    fun `falls back to time description when ocrText is null`() {
        val screenshot = buildScreenshot(ocrText = null)
        val description = screenshotContentDescription(screenshot)
        assertTrue(
            "Expected fallback description, got: $description",
            description.startsWith("Screenshot from ")
        )
    }

    @Test
    fun `falls back to time description when ocrText is empty string`() {
        val screenshot = buildScreenshot(ocrText = "")
        val description = screenshotContentDescription(screenshot)
        assertTrue(
            "Expected fallback description, got: $description",
            description.startsWith("Screenshot from ")
        )
    }

    @Test
    fun `falls back to time description when ocrText is only whitespace`() {
        val screenshot = buildScreenshot(ocrText = "   ")
        val description = screenshotContentDescription(screenshot)
        assertTrue(
            "Expected fallback description, got: $description",
            description.startsWith("Screenshot from ")
        )
    }

    // -----------------------------------------------------------------------
    // Fallback format
    // -----------------------------------------------------------------------

    @Test
    fun `fallback description contains just now for very recent screenshot`() {
        val screenshot = buildScreenshot(
            ocrText = null,
            timestamp = System.currentTimeMillis() - 5_000 // 5 seconds ago
        )
        val description = screenshotContentDescription(screenshot)
        assertEquals("Screenshot from Just now", description)
    }

    @Test
    fun `fallback description contains minutes for screenshot taken minutes ago`() {
        val screenshot = buildScreenshot(
            ocrText = null,
            timestamp = System.currentTimeMillis() - 10 * 60_000 // 10 minutes ago
        )
        val description = screenshotContentDescription(screenshot)
        assertEquals("Screenshot from 10m ago", description)
    }

    @Test
    fun `fallback description contains hours for screenshot taken hours ago`() {
        val screenshot = buildScreenshot(
            ocrText = null,
            timestamp = System.currentTimeMillis() - 3 * 3600_000 // 3 hours ago
        )
        val description = screenshotContentDescription(screenshot)
        assertEquals("Screenshot from 3h ago", description)
    }

    @Test
    fun `CONTENT_DESCRIPTION_MAX_CHARS is 100`() {
        assertEquals(100, CONTENT_DESCRIPTION_MAX_CHARS)
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private fun buildScreenshot(
        ocrText: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ) = Screenshot(
        id = "test_id",
        filePath = "/sdcard/Screenshots/test.png",
        fileName = "test.png",
        dateCreated = timestamp,
        dateIndexed = timestamp,
        width = 1080,
        height = 1920,
        ocrText = ocrText,
        timestamp = timestamp
    )
}
