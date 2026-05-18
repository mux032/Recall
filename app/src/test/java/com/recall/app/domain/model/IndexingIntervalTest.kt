package com.recall.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [IndexingInterval] pure logic.
 */
class IndexingIntervalTest {

    @Test
    fun `DEFAULT is EVERY_1_HOUR`() {
        assertEquals(IndexingInterval.EVERY_1_HOUR, IndexingInterval.DEFAULT)
    }

    @Test
    fun `DEFAULT is not high battery impact`() {
        // Production default must never show the battery warning out of the box
        assertFalse(IndexingInterval.DEFAULT.isHighBatteryImpact)
    }

    @Test
    fun `all intervals use TimeUnit MINUTES`() {
        IndexingInterval.entries.forEach { interval ->
            assertEquals(
                "Expected MINUTES for $interval",
                TimeUnit.MINUTES, interval.timeUnit
            )
        }
    }

    @Test
    fun `intervals below 60 minutes are flagged as high battery impact`() {
        assertTrue(IndexingInterval.EVERY_15_MIN.isHighBatteryImpact)
        assertTrue(IndexingInterval.EVERY_30_MIN.isHighBatteryImpact)
    }

    @Test
    fun `intervals at or above 60 minutes are not high battery impact`() {
        assertFalse(IndexingInterval.EVERY_1_HOUR.isHighBatteryImpact)
        assertFalse(IndexingInterval.EVERY_3_HOURS.isHighBatteryImpact)
        assertFalse(IndexingInterval.EVERY_6_HOURS.isHighBatteryImpact)
        assertFalse(IndexingInterval.EVERY_12_HOURS.isHighBatteryImpact)
    }

    @Test
    fun `fromName returns correct interval`() {
        IndexingInterval.entries.forEach { interval ->
            assertEquals(interval, IndexingInterval.fromName(interval.name))
        }
    }

    @Test
    fun `fromName returns DEFAULT for unknown name`() {
        assertEquals(IndexingInterval.DEFAULT, IndexingInterval.fromName("UNKNOWN"))
        assertEquals(IndexingInterval.DEFAULT, IndexingInterval.fromName(""))
    }

    @Test
    fun `all intervals have non-blank displayName`() {
        IndexingInterval.entries.forEach { interval ->
            assertTrue(
                "displayName must not be blank for $interval",
                interval.displayName.isNotBlank()
            )
        }
    }

    @Test
    fun `minute values are in ascending order`() {
        val minutes = IndexingInterval.entries.map { it.minutes }
        for (i in 0 until minutes.size - 1) {
            assertTrue(
                "Expected ascending minutes but ${minutes[i]} >= ${minutes[i + 1]}",
                minutes[i] < minutes[i + 1]
            )
        }
    }

    @Test
    fun `EVERY_1_HOUR has exactly 60 minutes`() {
        assertEquals(60L, IndexingInterval.EVERY_1_HOUR.minutes)
    }

    @Test
    fun `EVERY_6_HOURS has exactly 360 minutes`() {
        assertEquals(360L, IndexingInterval.EVERY_6_HOURS.minutes)
    }
}
