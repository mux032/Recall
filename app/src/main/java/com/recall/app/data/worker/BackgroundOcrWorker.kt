package com.recall.app.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.local.entity.ScreenshotEntity
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
            val allScreenshots = screenshotDao.getAllScreenshots()
                .first() // Get first emission from Flow
                .filter { screenshot -> screenshot.ocrText.isNullOrBlank() }
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
        // Skip if file doesn't exist
        val file = java.io.File(screenshot.filePath)
        if (!file.exists()) {
            Log.w(TAG, "File not found: ${screenshot.filePath}")
            return
        }

        // Run OCR
        val extractedText = ocrProcessor.process(screenshot.filePath)
        Log.d(TAG, "OCR extracted ${extractedText?.length ?: 0} characters")

        // Generate embedding if OCR succeeded
        val embedding = extractedText?.let { text ->
            embeddingGenerator.generate(text)
        }

        // Update database
        val updatedScreenshot = screenshot.copy(
            ocrText = extractedText,
            embeddingByteArray = embedding?.let { floatToByteArray(it) },
            processingState = "DONE"
        )

        screenshotDao.update(updatedScreenshot)
        Log.i(TAG, "Updated screenshot: ${screenshot.fileName}")
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
         * Maximum number of screenshots to process in one run.
         * This prevents the worker from running too long and draining battery.
         */
        const val MAX_SCREENSHOTS_PER_RUN = 20
    }
}
