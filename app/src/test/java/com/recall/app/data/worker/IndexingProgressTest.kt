package com.recall.app.data.worker

import org.junit.Assert.*
import org.junit.Test

class IndexingProgressTest {

    @Test
    fun `isComplete is false when total is zero`() {
        val progress = IndexingProgress(0, 0)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `isComplete is false when completed is less than total`() {
        val progress = IndexingProgress(5, 10)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `isComplete is true when completed equals total`() {
        val progress = IndexingProgress(10, 10)
        assertTrue(progress.isComplete)
    }

    @Test
    fun `isComplete is true when completed exceeds total`() {
        val progress = IndexingProgress(11, 10)
        assertTrue(progress.isComplete)
    }

    @Test
    fun `percentComplete is zero when total is zero`() {
        val progress = IndexingProgress(0, 0)
        assertEquals(0, progress.percentComplete)
    }

    @Test
    fun `percentComplete is 50 when half complete`() {
        val progress = IndexingProgress(5, 10)
        assertEquals(50, progress.percentComplete)
    }

    @Test
    fun `percentComplete is 100 when fully complete`() {
        val progress = IndexingProgress(10, 10)
        assertEquals(100, progress.percentComplete)
    }

    @Test
    fun `percentComplete is capped at 100`() {
        val progress = IndexingProgress(15, 10)
        assertEquals(100, progress.percentComplete)
    }

    @Test
    fun `default IndexingProgress has zero values`() {
        val progress = IndexingProgress(0, 0)
        assertEquals(0, progress.completed)
        assertEquals(0, progress.total)
    }
}
