package com.recall.app.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.domain.repository.ScreenshotRepository
import com.recall.app.domain.usecase.EmbeddingGenerator
import com.recall.app.domain.usecase.OcrProcessor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@HiltWorker
class ScreenshotProcessingWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val screenshotRepository: ScreenshotRepository,
    private val ocrProcessor: OcrProcessor,
    private val embeddingGenerator: EmbeddingGenerator
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val imagePath = inputData.getString("KEY_IMAGE_PATH")

        if (imagePath == null) {
            Log.e(TAG, "No image path provided")
            return@withContext Result.failure()
        }

        try {
            Log.d(TAG, "Starting OCR processing for $imagePath")

            // Extract text using ML Kit
            val extractedText = ocrProcessor.process(imagePath)
            Log.d(TAG, "OCR Completed. Text size: ${extractedText?.length ?: 0}")

            // Generate Embedding from OCR text
            val embeddingFloatArray = extractedText?.let { text ->
                Log.d(TAG, "Generating Embedding for OCR Text...")
                embeddingGenerator.generate(text)
            }

            // Use repository to insert or update (handles race conditions)
            val screenshotId = screenshotRepository.insertOrUpdateWithOcr(
                filePath = imagePath,
                ocrText = extractedText,
                embedding = embeddingFloatArray
            )

            Log.d(TAG, "Successfully processed screenshot: $screenshotId")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing screenshot $imagePath", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "ScreenshotWorker"
        const val KEY_SCREENSHOT_ID = "KEY_SCREENSHOT_ID"
    }
}
