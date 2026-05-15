package com.recall.app.data.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that [BackgroundOcrWorker] named constants have correct values and that
 * the throttle/batch logic derived from them is sound.
 *
 * These tests serve as a regression guard — if a constant is accidentally changed
 * during a performance tuning session the test will fail and prompt a conscious decision.
 */
class BackgroundOcrWorkerConstantsTest {

    // -----------------------------------------------------------------------
    // Constant value assertions
    // -----------------------------------------------------------------------

    @Test
    fun `MAX_OCR_RETRIES is 3`() {
        assertEquals(3, BackgroundOcrWorker.MAX_OCR_RETRIES)
    }

    @Test
    fun `MAX_SCREENSHOTS_PER_RUN is 20`() {
        assertEquals(20, BackgroundOcrWorker.MAX_SCREENSHOTS_PER_RUN)
    }

    @Test
    fun `BATCH_SIZE is 5`() {
        assertEquals(5, BackgroundOcrWorker.BATCH_SIZE)
    }

    @Test
    fun `INTER_BATCH_DELAY_MS is 2000`() {
        assertEquals(2_000L, BackgroundOcrWorker.INTER_BATCH_DELAY_MS)
    }

    @Test
    fun `INTER_ITEM_DELAY_MS is 500`() {
        assertEquals(500L, BackgroundOcrWorker.INTER_ITEM_DELAY_MS)
    }

    @Test
    fun `THROTTLE_EVERY_N_ITEMS is 3`() {
        assertEquals(3, BackgroundOcrWorker.THROTTLE_EVERY_N_ITEMS)
    }

    // -----------------------------------------------------------------------
    // Sanity / relationship checks
    // -----------------------------------------------------------------------

    @Test
    fun `BATCH_SIZE is less than or equal to MAX_SCREENSHOTS_PER_RUN`() {
        // A single batch must never exceed the total run cap
        assertTrue(
            "BATCH_SIZE (${BackgroundOcrWorker.BATCH_SIZE}) must be <= " +
                "MAX_SCREENSHOTS_PER_RUN (${BackgroundOcrWorker.MAX_SCREENSHOTS_PER_RUN})",
            BackgroundOcrWorker.BATCH_SIZE <= BackgroundOcrWorker.MAX_SCREENSHOTS_PER_RUN
        )
    }

    @Test
    fun `THROTTLE_EVERY_N_ITEMS is less than or equal to BATCH_SIZE`() {
        // Throttling must fire at least once within each batch
        assertTrue(
            "THROTTLE_EVERY_N_ITEMS (${BackgroundOcrWorker.THROTTLE_EVERY_N_ITEMS}) must be <= " +
                "BATCH_SIZE (${BackgroundOcrWorker.BATCH_SIZE})",
            BackgroundOcrWorker.THROTTLE_EVERY_N_ITEMS <= BackgroundOcrWorker.BATCH_SIZE
        )
    }

    @Test
    fun `INTER_ITEM_DELAY_MS is less than INTER_BATCH_DELAY_MS`() {
        // Item-level delay should always be shorter than the batch cool-down
        assertTrue(
            "INTER_ITEM_DELAY_MS (${BackgroundOcrWorker.INTER_ITEM_DELAY_MS}) must be < " +
                "INTER_BATCH_DELAY_MS (${BackgroundOcrWorker.INTER_BATCH_DELAY_MS})",
            BackgroundOcrWorker.INTER_ITEM_DELAY_MS < BackgroundOcrWorker.INTER_BATCH_DELAY_MS
        )
    }

    @Test
    fun `MAX_OCR_RETRIES is positive`() {
        assertTrue(BackgroundOcrWorker.MAX_OCR_RETRIES > 0)
    }

    @Test
    fun `delays are positive`() {
        assertTrue(BackgroundOcrWorker.INTER_ITEM_DELAY_MS > 0)
        assertTrue(BackgroundOcrWorker.INTER_BATCH_DELAY_MS > 0)
    }

    // -----------------------------------------------------------------------
    // Batching logic
    // -----------------------------------------------------------------------

    @Test
    fun `chunking list by BATCH_SIZE produces correct number of batches`() {
        val items = (1..13).toList()
        val batches = items.chunked(BackgroundOcrWorker.BATCH_SIZE)
        // 13 items / 5 per batch = 3 batches (5, 5, 3)
        assertEquals(3, batches.size)
        assertEquals(5, batches[0].size)
        assertEquals(5, batches[1].size)
        assertEquals(3, batches[2].size)
    }

    @Test
    fun `throttle fires at correct intervals`() {
        val throttlePoints = mutableListOf<Int>()
        val total = BackgroundOcrWorker.BATCH_SIZE * 2

        for (i in 1..total) {
            if (i % BackgroundOcrWorker.THROTTLE_EVERY_N_ITEMS == 0) {
                throttlePoints.add(i)
            }
        }

        // With THROTTLE_EVERY_N_ITEMS=3 and total=10: fires at 3, 6, 9
        assertEquals(listOf(3, 6, 9), throttlePoints)
    }
}
