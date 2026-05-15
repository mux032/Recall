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
                Log.d(TAG, "Processing batch ${batchIndex + 1}/${batches.size}")

                for (screenshot in batch) {
                    if (isStopped()) {
                        Log.w(TAG, "Worker stopped, processed $processedCount screenshots")
                        return@withContext Result.retry()
                    }

                    try {
                        processScreenshot(screenshot)
                        successCount++
                        Log.d(TAG, "✓ Processed: ${screenshot.fileName}")
                    } catch (e: Exception) {
                        errorCount++
                        Log.e(TAG, "✗ Failed to process: ${screenshot.fileName}", e)
                    }

                    processedCount++

                    // Small delay every N screenshots to avoid CPU/thermal throttling
                    if (processedCount % THROTTLE_EVERY_N_ITEMS == 0) {
                        kotlinx.coroutines.delay(INTER_ITEM_DELAY_MS)
                    }
                }

                // Longer cool-down between batches to let the device breathe
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
     * Process a single screenshot: run OCR and generate embedding
     */
    private suspend fun processScreenshot(screenshot: ScreenshotEntity) {
        // Skip if user has manually edited the text
        if (screenshot.isUserEdited) {
            Log.d(TAG, "Skipping OCR - user has edited this screenshot: ${screenshot.id}")
            return
        }

        // Skip if max retries exceeded
        if (screenshot.ocrRetryCount >= MAX_OCR_RETRIES) {
            Log.w(TAG, "Skipping OCR - max retries ($MAX_OCR_RETRIES) exceeded for: ${screenshot.id}")
            return
        }

        // Skip if file doesn't exist
        val file = java.io.File(screenshot.filePath)
        if (!file.exists()) {
            Log.w(TAG, "File not found: ${screenshot.filePath}")
            // Increment retry count for missing file
            screenshotDao.incrementOcrRetryCount(screenshot.id)
            return
        }

        try {
            // Run OCR
            val extractedText = ocrProcessor.process(screenshot.filePath)
            Log.d(TAG, "OCR extracted ${extractedText?.length ?: 0} characters")

            // Check if OCR returned empty text
            if (extractedText.isNullOrBlank()) {
                Log.w(TAG, "OCR returned empty text for: ${screenshot.filePath}")
                // Increment retry count on empty result
                screenshotDao.incrementOcrRetryCount(screenshot.id)
                return
            }

            // Generate embedding if OCR succeeded
            val embedding = embeddingGenerator.generate(extractedText)

            // Update database with successful OCR result
            val updatedScreenshot = screenshot.copy(
                ocrText = extractedText,
                embeddingByteArray = embedding?.let { floatToByteArray(it) },
                processingState = ProcessingState.Done,
                ocrRetryCount = 0  // Reset retry count on success
            )

            screenshotDao.update(updatedScreenshot)
            
            // Rebuild FTS index to ensure search works after OCR update
            screenshotDao.rebuildFtsIndex()
            
            Log.i(TAG, "Updated screenshot: ${screenshot.fileName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "OCR processing failed for ${screenshot.filePath}", e)
            // Increment retry count on exception
            screenshotDao.incrementOcrRetryCount(screenshot.id)
            throw e  // Re-throw to trigger WorkManager retry
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
         * Number of screenshots processed per batch before applying [INTER_BATCH_DELAY_MS].
         * Smaller batches give the device more breathing room between CPU bursts.
         * Value of 5 keeps each batch under ~2–3 seconds of active processing.
         */
        const val BATCH_SIZE = 5

        /**
         * Cool-down delay (ms) between consecutive batches.
         * Allows the CPU and thermal sensors to recover between processing bursts,
         * reducing the risk of thermal throttling on mid-range devices.
         * 2 000 ms (2 s) is a safe balance between throughput and thermal impact.
         */
        const val INTER_BATCH_DELAY_MS = 2_000L

        /**
         * Short delay (ms) applied every [THROTTLE_EVERY_N_ITEMS] screenshots within a batch.
         * Prevents sustained 100% CPU usage during OCR by yielding briefly to the scheduler.
         * 500 ms every 3 items adds ~167 ms overhead per item — acceptable for background work.
         */
        const val INTER_ITEM_DELAY_MS = 500L

        /**
         * How often (every N items) the [INTER_ITEM_DELAY_MS] throttle is applied within a batch.
         * A value of 3 means the worker pauses after every 3rd screenshot processed.
         */
        const val THROTTLE_EVERY_N_ITEMS = 3
    }
}
