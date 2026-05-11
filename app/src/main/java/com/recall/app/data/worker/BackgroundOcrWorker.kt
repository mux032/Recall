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
            val batchSize = 5 // Process 5 at a time
            val batches = allScreenshots.chunked(batchSize)

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

                    // Small delay between processing to avoid overheating
                    if (processedCount % 3 == 0) {
                        kotlinx.coroutines.delay(500)
                    }
                }

                // Longer delay between batches
                if (batchIndex < batches.size - 1) {
                    kotlinx.coroutines.delay(2000)
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
                processingState = ProcessingState.Done.value,
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
         * Prevents infinite loops on persistent OCR failures.
         */
        const val MAX_OCR_RETRIES = 3

        /**
         * Maximum number of screenshots to process in one run.
         * This prevents the worker from running too long and draining battery.
         */
        const val MAX_SCREENSHOTS_PER_RUN = 20
    }
}
