package com.recall.app.presentation.ui.home

import com.recall.app.domain.model.Screenshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Unit tests for timeline grouping logic in HomeScreen.
 *
 * Note: These tests verify the grouping algorithm logic.
 * The actual getTimelineLabel function is private in HomeScreen.kt,
 * so we test the logic by recreating it here.
 */
class TimelineGroupingTest {

    private val now = LocalDate.now()
    private val todayMillis = now.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * Recreates the getTimelineLabel logic for testing purposes.
     * Uses proper timezone conversion: Instant -> ZoneId -> LocalDate
     */
    private fun getTimelineLabel(timestamp: Long): String {
        val screenshotDate = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val daysDifference = ChronoUnit.DAYS.between(screenshotDate, now)

        return when {
            daysDifference == 0L -> "Today"
            daysDifference == 1L -> "Yesterday"
            daysDifference in 2..6 -> "This Week"
            daysDifference in 7..13 -> "Last Week"
            screenshotDate.month == now.month && screenshotDate.year == now.year -> "This Month"
            else -> "Older"
        }
    }

    private fun createScreenshot(timestamp: Long): Screenshot {
        return Screenshot(
            id = "test-${timestamp}",
            filePath = "/test/path.jpg",
            fileName = "test.jpg",
            dateCreated = timestamp,
            dateIndexed = timestamp,
            width = 1080,
            height = 1920,
            timestamp = timestamp
        )
    }

    @Test
    fun `today's screenshots are grouped as Today`() {
        val timestamp = todayMillis
        val label = getTimelineLabel(timestamp)
        assertEquals("Today", label)
    }

    @Test
    fun `yesterday's screenshots are grouped as Yesterday`() {
        val yesterday = now.minusDays(1)
        val timestamp = yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val label = getTimelineLabel(timestamp)
        assertEquals("Yesterday", label)
    }

    @Test
    fun `screenshots from 2-6 days ago are grouped as This Week`() {
        for (days in 2..6) {
            val date = now.minusDays(days.toLong())
            val timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val label = getTimelineLabel(timestamp)
            assertEquals("This Week", label)
        }
    }

    @Test
    fun `screenshots from 7-13 days ago are grouped as Last Week`() {
        for (days in 7..13) {
            val date = now.minusDays(days.toLong())
            val timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val label = getTimelineLabel(timestamp)
            assertEquals("Last Week", label)
        }
    }

    @Test
    fun `screenshots from earlier this month are grouped as This Month`() {
        // Skip if we're in the first half of the month (not enough data for "This Month" vs "Last Week")
        if (now.dayOfMonth > 14) {
            val daysAgo = 15
            val date = now.minusDays(daysAgo.toLong())
            // Only test if still in same month
            if (date.month == now.month) {
                val timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val label = getTimelineLabel(timestamp)
                assertEquals("This Month", label)
            }
        }
    }

    @Test
    fun `screenshots from previous months are grouped as Older`() {
        val lastMonth = now.minusMonths(1)
        val timestamp = lastMonth.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val label = getTimelineLabel(timestamp)
        assertEquals("Older", label)
    }

    @Test
    fun `screenshots from 30 days ago are grouped as Older`() {
        val date = now.minusDays(30)
        val timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val label = getTimelineLabel(timestamp)
        assertEquals("Older", label)
    }

    @Test
    fun `grouping screenshots by timeline produces correct groups`() {
        // Create screenshots from different time periods
        val screenshots = listOf(
            createScreenshot(todayMillis), // Today
            createScreenshot(now.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()), // Yesterday
            createScreenshot(now.minusDays(3).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()), // This Week
            createScreenshot(now.minusDays(10).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()), // Last Week
            createScreenshot(now.minusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) // Older
        )

        // Group by timeline
        val grouped = screenshots.groupBy { getTimelineLabel(it.timestamp) }

        // Verify all expected groups are present
        assertEquals(5, grouped.size)
        assertEquals(1, grouped["Today"]?.size)
        assertEquals(1, grouped["Yesterday"]?.size)
        assertEquals(1, grouped["This Week"]?.size)
        assertEquals(1, grouped["Last Week"]?.size)
        assertEquals(1, grouped["Older"]?.size)
    }

    @Test
    fun `multiple screenshots from same period are grouped together`() {
        val screenshots = listOf(
            createScreenshot(todayMillis),
            createScreenshot(todayMillis + 3600_000), // 1 hour later
            createScreenshot(todayMillis + 7200_000)  // 2 hours later
        )

        val grouped = screenshots.groupBy { getTimelineLabel(it.timestamp) }

        assertEquals(1, grouped.size)
        assertEquals(3, grouped["Today"]?.size)
    }

    @Test
    fun `sorted groups maintain chronological order (newest first)`() {
        val groupedScreenshots = mapOf(
            "Older" to listOf(createScreenshot(now.minusMonths(2).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())),
            "Today" to listOf(createScreenshot(todayMillis)),
            "Last Week" to listOf(createScreenshot(now.minusDays(10).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())),
            "Yesterday" to listOf(createScreenshot(now.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()))
        )

        val order = listOf("Today", "Yesterday", "This Week", "Last Week", "This Month", "Older")
        val sortedGroups = groupedScreenshots.toSortedMap { a, b ->
            val indexA = order.indexOf(a).takeIf { it >= 0 } ?: order.size
            val indexB = order.indexOf(b).takeIf { it >= 0 } ?: order.size
            indexA.compareTo(indexB)
        }

        val expectedOrder = listOf("Today", "Yesterday", "Last Week", "Older")
        assertEquals(expectedOrder, sortedGroups.keys.toList())
    }

    @Test
    fun `duplicate screenshots with same ID are removed by distinctBy`() {
        // Create screenshots where the same ID appears multiple times (simulating DB duplicates)
        val duplicateId = "duplicate-id-123"
        val screenshots = listOf(
            Screenshot(
                id = duplicateId,
                filePath = "/test/path.jpg",
                fileName = "test.jpg",
                dateCreated = todayMillis,
                dateIndexed = todayMillis,
                width = 1080,
                height = 1920,
                timestamp = todayMillis
            ),
            Screenshot(
                id = duplicateId, // Same ID as above - should be removed by distinctBy
                filePath = "/test/path.jpg",
                fileName = "test.jpg",
                dateCreated = todayMillis,
                dateIndexed = todayMillis,
                width = 1080,
                height = 1920,
                timestamp = todayMillis
            ),
            createScreenshot(todayMillis + 1000) // Different ID
        )

        // Apply distinctBy like in HomeScreen
        val uniqueScreenshots = screenshots.distinctBy { it.id }

        // Should have only 2 unique screenshots, not 3
        assertEquals(2, uniqueScreenshots.size)
        assertEquals(1, uniqueScreenshots.count { it.id == duplicateId })
    }

    @Test
    fun `grouping after distinctBy ensures each image appears only once`() {
        // Create scenario where duplicates could appear in multiple groups
        val duplicateId = "dup-id"
        val screenshots = listOf(
            // Same ID appearing twice with same timestamp (should be deduplicated)
            Screenshot(id = duplicateId, filePath = "/path.jpg", fileName = "path.jpg",
                dateCreated = todayMillis, dateIndexed = todayMillis,
                width = 1080, height = 1920, timestamp = todayMillis),
            Screenshot(id = duplicateId, filePath = "/path.jpg", fileName = "path.jpg",
                dateCreated = todayMillis, dateIndexed = todayMillis,
                width = 1080, height = 1920, timestamp = todayMillis),
            // Different screenshot from Today
            createScreenshot(todayMillis + 5000),
            // Screenshot from Yesterday
            createScreenshot(now.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        )

        // Apply deduplication and grouping (same as HomeScreen logic)
        val uniqueScreenshots = screenshots.distinctBy { it.id }
        val grouped = uniqueScreenshots.groupBy { getTimelineLabel(it.timestamp) }

        // Verify the duplicate ID appears only once in the entire grouped result
        val totalScreenshots = grouped.values.sumOf { it.size }
        assertEquals(3, totalScreenshots) // Should be 3, not 4

        // Verify the duplicate ID is only in "Today" group
        val todayGroup = grouped["Today"]
        assertNotNull(todayGroup)
        assertEquals(2, todayGroup!!.size) // Should have 2 unique screenshots from today
        assertEquals(1, todayGroup.count { it.id == duplicateId })
    }
}
