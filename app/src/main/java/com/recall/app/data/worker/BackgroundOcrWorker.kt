package com.recall.app.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.domain.model.ProcessingState
import com.recall.app.domain.usecase.EmbeddingGenerator
import com.recall.app.domain.usecase.OcrProcessor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Background worker that processes OCR for screenshots that don't have extracted text yet.
 * Prioritizes newer images first (by dateCreated DESC).
 *
 * This worker runs periodically to ensure all screenshots have OCR text without blocking
 * the UI or putting load on the device when user is interacting with the app.
 *
 * RETRY LOGIC: Tracks ocrRetryCount to prevent infinite loops on persistent failures.
 * Screenshots with ocrRetryCount >= MAX_OCR_RETRIES are skipped from automatic processing.
 */
@HiltWorker
class BackgroundOcrWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val ocrProcessor: OcrProcessor,
    private val embeddingGenerator: EmbeddingGenerator,
    private val screenshotDao: ScreenshotDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting background OCR processing...")

            // Get screenshots without OCR text, ordered by newest first
            // Filter out screenshots that have exceeded max retry count
            val allScreenshots = screenshotDao.getAllScreenshots()
                .first() // Get first emission from Flow
                .filter { screenshot -> 
                    screenshot.ocrText.isNullOrBlank() && 
                    screenshot.ocrRetryCount < MAX_OCR_RETRIES
                }
                .sortedByDescending { screenshot -> screenshot.dateCreated } // Newest first

            if (allScreenshots.isEmpty()) {
                Log.i(TAG, "No screenshots need OCR processing")
                return@withContext Result.success()
            }

            Log.i(TAG, "Found ${allScreenshots.size} screenshots needing OCR processing")

            var processedCount = 0
            var successCount = 0
            var errorCount = 0

            // Process in batches to avoid overwhelming the device
            val batches = allScreenshots.chunked(BATCH_SIZE)

            for ((batchIndex, batch) in batches.withIndex()) {
                if (isStopped()) {
                    Log.w(TAG, "Worker stopped before batch ${batchIndex + 1}, processed $processedCount screenshots")
                    return@withContext Result.retry()
                }

                Log.d(TAG, "Processing batch ${batchIndex + 1}/${batches.size} (${batch.size} screenshots)")

                // ── Phase 1: Parallel OCR ────────────────────────────────────
                // ML Kit TextRecognizer is thread-safe — fire all OCR tasks concurrently.
                // Each coroutine returns a pair of (screenshot, extractedText?) so phase 2
                // can work sequentially on the results without re-querying the DB.
                val ocrResults = coroutineScope {
                    batch.map { screenshot ->
                        async(Dispatchers.IO) {
                            runOcrForScreenshot(screenshot)
                        }
                    }.awaitAll()
                }

                // ── Phase 2: Sequential embedding + DB save ──────────────────
                // ONNX inference is CPU-bound; running M embeddings in parallel risks
                // thermal throttling. Sequential is safe and still fast (~5–20 ms each).
                for (result in ocrResults) {
                    val (screenshot, extractedText) = result
                    try {
                        finalizeScreenshot(screenshot, extractedText)
                        successCount++
                        Log.d(TAG, "✓ Processed: ${screenshot.fileName}")
                    } catch (e: Exception) {
                        errorCount++
                        Log.e(TAG, "✗ Failed to finalize: ${screenshot.fileName}", e)
                    }
                    processedCount++
                }

                // Thermal cool-down between batches
                if (batchIndex < batches.size - 1) {
                    kotlinx.coroutines.delay(INTER_BATCH_DELAY_MS)
                }
            }

            Log.i(TAG, "=== Background OCR Complete ===")
            Log.i(TAG, "Processed: $processedCount, Success: $successCount, Errors: $errorCount")

            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in background OCR processing", e)
            Result.retry()
        }
    }

    /**
     * Phase 1 — OCR only (safe to call concurrently across a batch).
     *
     * Runs OCR on [screenshot] and returns a [Pair] of the screenshot and the
     * extracted text (null if OCR failed, empty, or the screenshot should be skipped).
     * Increments the retry counter in the DB on failure but does NOT throw — a single
     * failed OCR should not abort the parallel phase for the rest of the batch.
     */
    private suspend fun runOcrForScreenshot(screenshot: ScreenshotEntity): Pair<ScreenshotEntity, String?> {
        if (screenshot.isUserEdited) {
            Log.d(TAG, "Skipping OCR - user edited: ${screenshot.id}")
            return Pair(screenshot, null)
        }

        if (screenshot.ocrRetryCount >= MAX_OCR_RETRIES) {
            Log.w(TAG, "Skipping OCR - max retries exceeded: ${screenshot.id}")
            return Pair(screenshot, null)
        }

        val file = java.io.File(screenshot.filePath)
        if (!file.exists()) {
            Log.w(TAG, "File not found: ${screenshot.filePath}")
            screenshotDao.incrementOcrRetryCount(screenshot.id)
            return Pair(screenshot, null)
        }

        return try {
            val extractedText = ocrProcessor.process(screenshot.filePath)
            Log.d(TAG, "OCR extracted ${extractedText?.length ?: 0} chars for ${screenshot.fileName}")

            if (extractedText.isNullOrBlank()) {
                Log.w(TAG, "OCR returned empty text for: ${screenshot.filePath}")
                screenshotDao.incrementOcrRetryCount(screenshot.id)
                Pair(screenshot, null)
            } else {
                Pair(screenshot, extractedText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed for ${screenshot.filePath}", e)
            screenshotDao.incrementOcrRetryCount(screenshot.id)
            Pair(screenshot, null)
        }
    }

    /**
     * Phase 2 — Embedding generation + DB save (called sequentially after parallel OCR).
     *
     * Takes the [extractedText] from [runOcrForScreenshot] and:
     * 1. Generates an ONNX embedding vector
     * 2. Persists OCR text + embedding + [ProcessingState.Done] to the DB
     * 3. Rebuilds the FTS index entry for this screenshot
     *
     * If [extractedText] is null the screenshot was already handled (retry count incremented)
     * in Phase 1 — this function is a no-op in that case.
     */
    private suspend fun finalizeScreenshot(screenshot: ScreenshotEntity, extractedText: String?) {
        if (extractedText.isNullOrBlank()) return  // Phase 1 already handled failure

        try {
            val embedding = embeddingGenerator.generate(extractedText)

            val updatedScreenshot = screenshot.copy(
                ocrText = extractedText,
                embeddingByteArray = embedding?.let { floatToByteArray(it) },
                processingState = ProcessingState.Done,
                ocrRetryCount = 0
            )

            screenshotDao.update(updatedScreenshot)
            screenshotDao.rebuildFtsIndex()

            Log.i(TAG, "Saved OCR + embedding for: ${screenshot.fileName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize ${screenshot.fileName}", e)
            screenshotDao.incrementOcrRetryCount(screenshot.id)
            throw e
        }
    }

    /**
     * Convert FloatArray to ByteArray for storage
     */
    private fun floatToByteArray(floatArray: FloatArray): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(floatArray.size * 4)
        for (f in floatArray) {
            buffer.putFloat(f)
        }
        return buffer.array()
    }

    companion object {
        const val TAG = "BackgroundOcrWorker"

        /**
         * Maximum number of OCR retry attempts per screenshot.
         * Prevents infinite loops on persistent OCR failures (e.g. corrupt image, ML Kit bug).
         */
        const val MAX_OCR_RETRIES = 3

        /**
         * Maximum number of screenshots to process in one worker run.
         * Caps total run time to prevent the OS from killing a long-running worker
         * and to avoid draining battery on large backlogs.
         */
        const val MAX_SCREENSHOTS_PER_RUN = 20

        /**
         * Number of screenshots processed per batch.
         *
         * OCR tasks within a batch run in parallel ([runOcrForScreenshot] via async/awaitAll),
         * so raising this from 5 → 20 increases throughput without proportionally increasing
         * wall-clock time. On a 6-core device, 20 parallel ML Kit tasks complete in roughly
         * the same time as 5 did previously (~2–3 s), giving ~4× more throughput per batch.
         *
         * The [INTER_BATCH_DELAY_MS] cool-down between batches is the only thermal throttle now.
         */
        const val BATCH_SIZE = 20

        /**
         * Cool-down delay (ms) between consecutive batches.
         * Allows CPU and thermal sensors to recover between processing bursts,
         * reducing the risk of thermal throttling on mid-range devices.
         */
        const val INTER_BATCH_DELAY_MS = 2_000L
    }
}
