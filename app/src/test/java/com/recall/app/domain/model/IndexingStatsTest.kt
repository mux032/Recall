package com.recall.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [IndexingStats] computed properties (Issue #106).
 *
 * These are pure logic tests — no Android dependencies required.
 */
class IndexingStatsTest {

    // -----------------------------------------------------------------------
    // ocrProgress
    // -----------------------------------------------------------------------

    @Test
    fun `ocrProgress is 0 when no screenshots have OCR`() {
        val stats = IndexingStats(total = 10, ocrDoneCount = 0, embeddingDoneCount = 0)
        assertEquals(0f, stats.ocrProgress, 0.001f)
    }

    @Test
    fun `ocrProgress is 1 when all screenshots have OCR`() {
        val stats = IndexingStats(total = 10, ocrDoneCount = 10, embeddingDoneCount = 0)
        assertEquals(1f, stats.ocrProgress, 0.001f)
    }

    @Test
    fun `ocrProgress is 0_5 when half screenshots have OCR`() {
        val stats = IndexingStats(total = 10, ocrDoneCount = 5, embeddingDoneCount = 0)
        assertEquals(0.5f, stats.ocrProgress, 0.001f)
    }

    @Test
    fun `ocrProgress is 1 when total is 0`() {
        val stats = IndexingStats(total = 0, ocrDoneCount = 0, embeddingDoneCount = 0)
        assertEquals(1f, stats.ocrProgress, 0.001f)
    }

    // -----------------------------------------------------------------------
    // embeddingProgress
    // -----------------------------------------------------------------------

    @Test
    fun `embeddingProgress is 0 when no embeddings generated`() {
        val stats = IndexingStats(total = 8, ocrDoneCount = 8, embeddingDoneCount = 0)
        assertEquals(0f, stats.embeddingProgress, 0.001f)
    }

    @Test
    fun `embeddingProgress is 1 when all embeddings generated`() {
        val stats = IndexingStats(total = 8, ocrDoneCount = 8, embeddingDoneCount = 8)
        assertEquals(1f, stats.embeddingProgress, 0.001f)
    }

    @Test
    fun `embeddingProgress is 0_25 when quarter embeddings done`() {
        val stats = IndexingStats(total = 8, ocrDoneCount = 8, embeddingDoneCount = 2)
        assertEquals(0.25f, stats.embeddingProgress, 0.001f)
    }

    // -----------------------------------------------------------------------
    // isOcrComplete / isEmbeddingComplete / isFullyIndexed
    // -----------------------------------------------------------------------

    @Test
    fun `isOcrComplete is true when ocrDoneCount equals total`() {
        val stats = IndexingStats(total = 5, ocrDoneCount = 5, embeddingDoneCount = 0)
        assertTrue(stats.isOcrComplete)
    }

    @Test
    fun `isOcrComplete is false when some screenshots have no OCR`() {
        val stats = IndexingStats(total = 5, ocrDoneCount = 4, embeddingDoneCount = 0)
        assertFalse(stats.isOcrComplete)
    }

    @Test
    fun `isEmbeddingComplete is true when embeddingDoneCount equals total`() {
        val stats = IndexingStats(total = 5, ocrDoneCount = 5, embeddingDoneCount = 5)
        assertTrue(stats.isEmbeddingComplete)
    }

    @Test
    fun `isFullyIndexed is true when both OCR and embedding are complete`() {
        val stats = IndexingStats(total = 3, ocrDoneCount = 3, embeddingDoneCount = 3)
        assertTrue(stats.isFullyIndexed)
    }

    @Test
    fun `isFullyIndexed is false when OCR is complete but embedding is not`() {
        val stats = IndexingStats(total = 3, ocrDoneCount = 3, embeddingDoneCount = 1)
        assertFalse(stats.isFullyIndexed)
    }

    @Test
    fun `isFullyIndexed is true when total is 0 — IDLE case`() {
        assertTrue(IndexingStats.IDLE.isFullyIndexed)
    }

    // -----------------------------------------------------------------------
    // Pending counts
    // -----------------------------------------------------------------------

    @Test
    fun `ocrPendingCount is correct`() {
        val stats = IndexingStats(total = 10, ocrDoneCount = 3, embeddingDoneCount = 0)
        assertEquals(7, stats.ocrPendingCount)
    }

    @Test
    fun `embeddingPendingCount is correct`() {
        val stats = IndexingStats(total = 10, ocrDoneCount = 10, embeddingDoneCount = 6)
        assertEquals(4, stats.embeddingPendingCount)
    }

    @Test
    fun `pending counts are never negative`() {
        // Defensive: ocrDoneCount could theoretically exceed total due to race conditions
        val stats = IndexingStats(total = 5, ocrDoneCount = 6, embeddingDoneCount = 7)
        assertEquals(0, stats.ocrPendingCount)
        assertEquals(0, stats.embeddingPendingCount)
    }

    // -----------------------------------------------------------------------
    // IDLE sentinel
    // -----------------------------------------------------------------------

    @Test
    fun `IDLE sentinel is fully indexed — banner should not show`() {
        assertTrue(IndexingStats.IDLE.isFullyIndexed)
        assertEquals(0, IndexingStats.IDLE.total)
    }
}
