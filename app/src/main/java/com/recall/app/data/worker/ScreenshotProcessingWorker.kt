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
    private val ocrProcessor: OcrProcessor,
    private val embeddingGenerator: EmbeddingGenerator,
    private val screenshotRepository: ScreenshotRepository,
    private val screenshotDao: ScreenshotDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val imagePath = inputData.getString("KEY_IMAGE_PATH")

        if (imagePath == null) {
            Log.e(TAG, "No image path provided")
            return@withContext Result.failure()
        }

        try {
            Log.d(TAG, "Starting OCR processing for $imagePath")

            // CRITICAL FIX: Check if screenshot already exists by filePath BEFORE processing
            // This prevents duplicate entries when the same file is processed multiple times
            val existing = screenshotDao.getScreenshotByPath(imagePath)
            if (existing != null) {
                Log.d(TAG, "Screenshot already exists for $imagePath (id: ${existing.id}), skipping duplicate processing")
                // If it exists but doesn't have OCR text, process and update
                if (existing.ocrText.isNullOrBlank()) {
                    Log.d(TAG, "Existing screenshot has no OCR text, processing...")
                    val extractedText = ocrProcessor.process(imagePath)
                    val embedding = extractedText?.let { embeddingGenerator.generate(it) }
                    
                    val updatedEntity = existing.copy(
                        ocrText = extractedText,
                        embeddingByteArray = embedding?.let { floatArrayToByteArray(it) },
                        processingState = "DONE",
                        dateIndexed = System.currentTimeMillis()
                    )
                    screenshotDao.update(updatedEntity)
                    Log.d(TAG, "OCR processing complete for existing screenshot")
                }
                return@withContext Result.success()
            }

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
                id = UUID.randomUUID().toString(),
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

            Log.d(TAG, "Successfully added new screenshot: $imagePath")
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

    private fun floatArrayToByteArray(floatArray: FloatArray?): ByteArray? {
        if (floatArray == null) return null
        val buffer = java.nio.ByteBuffer.allocate(floatArray.size * 4)
        for (f in floatArray) {
            buffer.putFloat(f)
        }
        return buffer.array()
    }
}
