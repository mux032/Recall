package com.recall.app.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recall.app.domain.repository.ScreenshotRepository
import com.recall.app.domain.usecase.EmbeddingGenerator
import com.recall.app.domain.usecase.OcrProcessor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class ScreenshotProcessingWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val ocrProcessor: OcrProcessor,
    private val embeddingGenerator: EmbeddingGenerator,
    private val screenshotRepository: ScreenshotRepository
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

            // Create domain model and insert to DB
            val screenshot = com.recall.app.domain.model.Screenshot(
                id = java.util.UUID.randomUUID().toString(),
                filePath = imagePath,
                fileName = java.io.File(imagePath).name,
                dateCreated = System.currentTimeMillis(),
                dateIndexed = System.currentTimeMillis(),
                width = 0, // We can decode bounds here if needed
                height = 0,
                ocrText = extractedText,
                category = "Uncategorized",
                tags = emptyList(),
                embedding = embeddingFloatArray
            )

            screenshotRepository.addScreenshot(screenshot)

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
