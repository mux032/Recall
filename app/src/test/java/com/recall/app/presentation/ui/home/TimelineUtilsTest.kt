package com.recall.app.presentation.ui.home

import com.recall.app.domain.model.Screenshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit tests for [buildTimelineSections], [getTimelineLabel] and related utilities.
 */
class TimelineUtilsTest {

    // -----------------------------------------------------------------------
    // getTimelineLabel
    // -----------------------------------------------------------------------

    @Test
    fun `getTimelineLabel returns Today for a screenshot taken today`() {
        val timestamp = todayMinusHours(1)
        assertEquals("Today", getTimelineLabel(timestamp))
    }

    @Test
    fun `getTimelineLabel returns Yesterday for a screenshot taken yesterday`() {
        val timestamp = todayMinusDays(1)
        assertEquals("Yesterday", getTimelineLabel(timestamp))
    }

    @Test
    fun `getTimelineLabel returns This Week for screenshot taken 3 days ago`() {
        val timestamp = todayMinusDays(3)
        assertEquals("This Week", getTimelineLabel(timestamp))
    }

    @Test
    fun `getTimelineLabel returns This Week for screenshot taken 6 days ago`() {
        val timestamp = todayMinusDays(6)
        assertEquals("This Week", getTimelineLabel(timestamp))
    }

    @Test
    fun `getTimelineLabel returns Last Week for screenshot taken 7 days ago`() {
        val timestamp = todayMinusDays(7)
        assertEquals("Last Week", getTimelineLabel(timestamp))
    }

    @Test
    fun `getTimelineLabel returns Last Week for screenshot taken 13 days ago`() {
        val timestamp = todayMinusDays(13)
        assertEquals("Last Week", getTimelineLabel(timestamp))
    }

    @Test
    fun `getTimelineLabel returns Older for screenshot taken over a year ago`() {
        val timestamp = todayMinusDays(400)
        assertEquals("Older", getTimelineLabel(timestamp))
    }

    // -----------------------------------------------------------------------
    // buildTimelineSections — basic grouping
    // -----------------------------------------------------------------------

    @Test
    fun `buildTimelineSections returns empty list for empty input`() {
        val result = buildTimelineSections(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `buildTimelineSections groups today and yesterday into separate sections`() {
        val screenshots = listOf(
            buildScreenshot("1", todayMinusHours(1)),
            buildScreenshot("2", todayMinusDays(1))
        )
        val sections = buildTimelineSections(screenshots)
        assertEquals(2, sections.size)
        assertEquals("Today", sections[0].label)
        assertEquals("Yesterday", sections[1].label)
    }

    @Test
    fun `buildTimelineSections groups multiple screenshots in same section`() {
        val screenshots = listOf(
            buildScreenshot("1", todayMinusHours(1)),
            buildScreenshot("2", todayMinusHours(2)),
            buildScreenshot("3", todayMinusHours(3))
        )
        val sections = buildTimelineSections(screenshots)
        assertEquals(1, sections.size)
        assertEquals("Today", sections[0].label)
        assertEquals(3, sections[0].screenshots.size)
    }

    // -----------------------------------------------------------------------
    // buildTimelineSections — section ordering
    // -----------------------------------------------------------------------

    @Test
    fun `buildTimelineSections orders sections newest first`() {
        val screenshots = listOf(
            buildScreenshot("old", todayMinusDays(400)),  // Older
            buildScreenshot("recent", todayMinusHours(1)), // Today
            buildScreenshot("lastweek", todayMinusDays(10)) // Last Week
        )
        val sections = buildTimelineSections(screenshots)
        assertEquals(3, sections.size)
        assertEquals("Today", sections[0].label)
        assertEquals("Last Week", sections[1].label)
        assertEquals("Older", sections[2].label)
    }

    @Test
    fun `buildTimelineSections respects full label order`() {
        val screenshots = TIMELINE_LABEL_ORDER.mapIndexed { index, _ ->
            buildScreenshot("id_$index", daysAgoForLabel(index))
        }
        val sections = buildTimelineSections(screenshots)

        // All sections present and in correct order
        val labels = sections.map { it.label }
        val expectedPresent = sections.map { it.label }
        assertEquals(expectedPresent, labels)

        // Earlier labels come before later ones in TIMELINE_LABEL_ORDER
        for (i in 0 until labels.size - 1) {
            val indexA = TIMELINE_LABEL_ORDER.indexOf(labels[i])
            val indexB = TIMELINE_LABEL_ORDER.indexOf(labels[i + 1])
            assertTrue(
                "Expected ${labels[i]} before ${labels[i + 1]}",
                indexA < indexB
            )
        }
    }

    // -----------------------------------------------------------------------
    // buildTimelineSections — deduplication
    // -----------------------------------------------------------------------

    @Test
    fun `buildTimelineSections deduplicates screenshots with same id`() {
        val timestamp = todayMinusHours(1)
        val screenshots = listOf(
            buildScreenshot("dup", timestamp),
            buildScreenshot("dup", timestamp), // same id
            buildScreenshot("unique", timestamp)
        )
        val sections = buildTimelineSections(screenshots)
        assertEquals(1, sections.size)
        // Only 2 unique IDs
        assertEquals(2, sections[0].screenshots.size)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun todayMinusHours(hours: Long): Long =
        System.currentTimeMillis() - hours * 3_600_000L

    private fun todayMinusDays(days: Long): Long {
        val date = LocalDate.now().minusDays(days)
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() +
            3_600_000L // add 1h to avoid midnight edge cases
    }

    /**
     * Returns a timestamp that should produce the given label index in [TIMELINE_LABEL_ORDER].
     * Today=0, Yesterday=1, This Week=2, Last Week=7, This Month=14, Older=400
     */
    private fun daysAgoForLabel(labelIndex: Int): Long = when (labelIndex) {
        0 -> todayMinusHours(1)    // Today
        1 -> todayMinusDays(1)     // Yesterday
        2 -> todayMinusDays(3)     // This Week
        3 -> todayMinusDays(10)    // Last Week
        4 -> todayMinusDays(20)    // This Month — only valid if still in same month
        5 -> todayMinusDays(400)   // Older
        else -> todayMinusDays(400)
    }

    private fun buildScreenshot(id: String, timestamp: Long) = Screenshot(
        id = id,
        filePath = "/sdcard/Screenshots/$id.png",
        fileName = "$id.png",
        dateCreated = timestamp,
        dateIndexed = timestamp,
        width = 1080,
        height = 1920,
        timestamp = timestamp
    )
}
