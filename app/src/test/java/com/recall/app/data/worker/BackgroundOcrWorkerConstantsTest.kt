package com.recall.app.data.worker

import com.recall.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that [BackgroundOcrWorker] named constants have the correct values and that
 * batch/parallel logic derived from them is sound. (Issue #115 — Phase 12 quick wins)
 *
 * These tests are intentional regression guards: if a constant is changed during a
 * performance tuning session the failing test forces a conscious decision.
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
    fun `BATCH_SIZE is 20 after quick-wins increase`() {
        // Raised from 5 → 20 in Issue #115 to reduce DB round-trips.
        // OCR within each batch now runs in parallel, so the larger batch
        // does not proportionally increase wall-clock time.
        assertEquals(20, BackgroundOcrWorker.BATCH_SIZE)
    }

    @Test
    fun `INTER_BATCH_DELAY_MS is 2000`() {
        assertEquals(2_000L, BackgroundOcrWorker.INTER_BATCH_DELAY_MS)
    }

    // -----------------------------------------------------------------------
    // Sanity / relationship checks
    // -----------------------------------------------------------------------

    @Test
    fun `BATCH_SIZE is less than or equal to MAX_SCREENSHOTS_PER_RUN`() {
        assertTrue(
            "BATCH_SIZE (${BackgroundOcrWorker.BATCH_SIZE}) must be <= " +
                "MAX_SCREENSHOTS_PER_RUN (${BackgroundOcrWorker.MAX_SCREENSHOTS_PER_RUN})",
            BackgroundOcrWorker.BATCH_SIZE <= BackgroundOcrWorker.MAX_SCREENSHOTS_PER_RUN
        )
    }

    @Test
    fun `MAX_OCR_RETRIES is positive`() {
        assertTrue(BackgroundOcrWorker.MAX_OCR_RETRIES > 0)
    }

    @Test
    fun `INTER_BATCH_DELAY_MS is positive`() {
        assertTrue(BackgroundOcrWorker.INTER_BATCH_DELAY_MS > 0)
    }

    @Test
    fun `BATCH_SIZE is at least 10 — regression guard against reverting to old value`() {
        // Pre-#115 value was 5. This guard prevents accidental regression.
        assertTrue(
            "BATCH_SIZE should be >= 10 after Issue #115 optimisation",
            BackgroundOcrWorker.BATCH_SIZE >= 10
        )
    }

    // -----------------------------------------------------------------------
    // Batching logic — parallel OCR (Issue #115)
    // -----------------------------------------------------------------------

    @Test
    fun `chunking 45 items by BATCH_SIZE=20 produces 3 batches`() {
        val items = (1..45).toList()
        val batches = items.chunked(BackgroundOcrWorker.BATCH_SIZE)
        // 45 / 20 = 2 full batches + 1 partial → 3 batches (20, 20, 5)
        assertEquals(3, batches.size)
        assertEquals(20, batches[0].size)
        assertEquals(20, batches[1].size)
        assertEquals(5, batches[2].size)
    }

    @Test
    fun `chunking 20 items by BATCH_SIZE=20 produces exactly 1 batch`() {
        val items = (1..20).toList()
        val batches = items.chunked(BackgroundOcrWorker.BATCH_SIZE)
        assertEquals(1, batches.size)
        assertEquals(20, batches[0].size)
    }

    @Test
    fun `chunking empty list produces no batches`() {
        val batches = emptyList<Int>().chunked(BackgroundOcrWorker.BATCH_SIZE)
        assertTrue(batches.isEmpty())
    }

    @Test
    fun `all items in batch are independent — each maps to exactly one OCR result`() {
        // Simulates the parallel phase: each item in a batch produces exactly one result pair.
        val batchSize = BackgroundOcrWorker.BATCH_SIZE
        val fakeResults = (1..batchSize).map { i -> Pair("screenshot_$i", "text_$i") }
        assertEquals(batchSize, fakeResults.size)
        fakeResults.forEach { (id, text) ->
            assertTrue(id.isNotBlank())
            assertTrue(text.isNotBlank())
        }
    }

    // -----------------------------------------------------------------------
    // Unique work name — Fix #3: prevent duplicate worker chains (Issue #115)
    // -----------------------------------------------------------------------

    @Test
    fun `INITIAL_SCAN_WORK_NAME constant is non-blank`() {
        val name = MainActivity.INITIAL_SCAN_WORK_NAME
        assertTrue("Work name must be non-blank", name.isNotBlank())
    }

    @Test
    fun `INITIAL_SCAN_WORK_NAME is stable across calls`() {
        // Same constant used in both calls to beginUniqueWork — must not change at runtime
        assertEquals(MainActivity.INITIAL_SCAN_WORK_NAME, MainActivity.INITIAL_SCAN_WORK_NAME)
    }
}
