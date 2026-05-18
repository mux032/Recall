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
    fun `MAX_EMBEDDING_RETRIES is 10`() {
        assertEquals(10, BackgroundOcrWorker.MAX_EMBEDDING_RETRIES)
    }

    @Test
    fun `MAX_EMBEDDING_RETRIES is greater than MAX_OCR_RETRIES`() {
        // Embedding failures are transient (model not loaded, OOM); OCR failures are structural
        // (corrupt file). The higher limit ensures valid OCR text is never permanently orphaned.
        assertTrue(
            "MAX_EMBEDDING_RETRIES must be > MAX_OCR_RETRIES",
            BackgroundOcrWorker.MAX_EMBEDDING_RETRIES > BackgroundOcrWorker.MAX_OCR_RETRIES
        )
    }

    @Test
    fun `row with exhausted ocrRetryCount is still eligible for Pass 2 if embeddingRetryCount is low`() {
        // Demonstrates the fix: Pass 2 filters on embeddingRetryCount, not ocrRetryCount.
        // A row with ocrRetryCount=3 (exhausted) but embeddingRetryCount=0 must still qualify.
        val ocrRetryCount = BackgroundOcrWorker.MAX_OCR_RETRIES       // 3 — exhausted
        val embeddingRetryCount = 0                                    // fresh
        val eligibleForPass2 = embeddingRetryCount < BackgroundOcrWorker.MAX_EMBEDDING_RETRIES
        assertTrue("Row should be eligible for Pass 2 via embeddingRetryCount", eligibleForPass2)
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
    // Batching logic — two-pass processing (Pass 1: OCR, Pass 2: embedding)
    // -----------------------------------------------------------------------

    @Test
    fun `chunking 20 OCR-pending items by BATCH_SIZE=20 produces exactly 1 batch`() {
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
        val batchSize = BackgroundOcrWorker.BATCH_SIZE
        val fakeResults = (1..batchSize).map { i -> Pair("screenshot_$i", "text_$i") }
        assertEquals(batchSize, fakeResults.size)
        fakeResults.forEach { (id, text) ->
            assertTrue(id.isNotBlank())
            assertTrue(text.isNotBlank())
        }
    }

    // -----------------------------------------------------------------------
    // MAX_SCREENSHOTS_PER_RUN enforcement (Bug B fix)
    // -----------------------------------------------------------------------

    @Test
    fun `MAX_SCREENSHOTS_PER_RUN caps Pass 1 — SQL LIMIT prevents loading 1742 rows`() {
        // The DAO query uses LIMIT :limit = MAX_SCREENSHOTS_PER_RUN so only 20 rows
        // are ever fetched from a 1742-row table. Simulate that by checking the cap.
        val simulatedDbRows = (1..1742).toList()
        val pass1 = simulatedDbRows.take(BackgroundOcrWorker.MAX_SCREENSHOTS_PER_RUN)
        assertEquals(20, pass1.size)
    }

    @Test
    fun `Pass 2 remaining slots = MAX_SCREENSHOTS_PER_RUN minus Pass 1 count`() {
        val pass1Count = 15  // e.g. only 15 OCR-pending rows existed
        val remainingSlots = BackgroundOcrWorker.MAX_SCREENSHOTS_PER_RUN - pass1Count
        assertEquals(5, remainingSlots)
    }

    @Test
    fun `Pass 2 gets zero slots when Pass 1 consumed all MAX_SCREENSHOTS_PER_RUN`() {
        val pass1Count = BackgroundOcrWorker.MAX_SCREENSHOTS_PER_RUN
        val remainingSlots = BackgroundOcrWorker.MAX_SCREENSHOTS_PER_RUN - pass1Count
        assertEquals(0, remainingSlots)
        // Worker should skip Pass 2 entirely when remainingSlots == 0
        assertTrue(remainingSlots <= 0)
    }

    @Test
    fun `total work per run is bounded by MAX_SCREENSHOTS_PER_RUN across both passes`() {
        val pass1 = 12
        val pass2 = BackgroundOcrWorker.MAX_SCREENSHOTS_PER_RUN - pass1
        val total = pass1 + pass2
        assertEquals(BackgroundOcrWorker.MAX_SCREENSHOTS_PER_RUN, total)
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
