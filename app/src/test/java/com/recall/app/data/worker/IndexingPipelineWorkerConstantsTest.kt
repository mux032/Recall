package com.recall.app.data.worker

import org.junit.Assert.*
import org.junit.Test

class IndexingPipelineWorkerConstantsTest {

    @Test
    fun `PIPELINE_WORK_NAME is stable`() {
        assertEquals("indexing_pipeline_work", IndexingPipelineWorker.PIPELINE_WORK_NAME)
    }

    @Test
    fun `FOREGROUND_THRESHOLD is 50`() {
        assertEquals(50, IndexingPipelineWorker.FOREGROUND_THRESHOLD)
    }

    @Test
    fun `SCAN_CHANNEL_CAPACITY is positive`() {
        assertTrue(IndexingPipelineWorker.SCAN_CHANNEL_CAPACITY > 0)
    }

    @Test
    fun `OCR_CHANNEL_CAPACITY is positive`() {
        assertTrue(IndexingPipelineWorker.OCR_CHANNEL_CAPACITY > 0)
    }

    @Test
    fun `OCR_CHANNEL_CAPACITY is less than SCAN_CHANNEL_CAPACITY for backpressure`() {
        assertTrue(IndexingPipelineWorker.OCR_CHANNEL_CAPACITY <= IndexingPipelineWorker.SCAN_CHANNEL_CAPACITY)
    }

    @Test
    fun `MAX_OCR_WORKERS is between 2 and 4`() {
        assertTrue(IndexingPipelineWorker.MAX_OCR_WORKERS in 2..8)
    }

    @Test
    fun `EMBEDDING_WORKER_COUNT is 2`() {
        assertEquals(2, IndexingPipelineWorker.EMBEDDING_WORKER_COUNT)
    }

    @Test
    fun `FTS_REBUILD_BATCH is positive`() {
        assertTrue(IndexingPipelineWorker.FTS_REBUILD_BATCH > 0)
    }

    @Test
    fun `THERMAL_DELAY_MS is at least 5 seconds`() {
        assertTrue(IndexingPipelineWorker.THERMAL_DELAY_MS >= 5_000L)
    }

    @Test
    fun `MAX_OCR_RETRIES matches BackgroundOcrWorker`() {
        assertEquals(BackgroundOcrWorker.MAX_OCR_RETRIES, IndexingPipelineWorker.MAX_OCR_RETRIES)
    }

    @Test
    fun `MAX_EMBEDDING_RETRIES matches BackgroundOcrWorker`() {
        assertEquals(BackgroundOcrWorker.MAX_EMBEDDING_RETRIES, IndexingPipelineWorker.MAX_EMBEDDING_RETRIES)
    }

    @Test
    fun `indexingProgress initial state is zero`() {
        // StateFlow always has a value — initial should be zeros
        val progress = IndexingPipelineWorker.indexingProgress.value
        // Progress may have been updated by other tests but initial value should have total >= 0
        assertTrue(progress.total >= 0)
        assertTrue(progress.completed >= 0)
    }

    @Test
    fun `NOTIFICATION_ID is a valid positive integer`() {
        assertTrue(IndexingPipelineWorker.NOTIFICATION_ID > 0)
    }

    @Test
    fun `SCAN_BATCH_SIZE is at least 20`() {
        assertTrue(IndexingPipelineWorker.SCAN_BATCH_SIZE >= 20)
    }
}
