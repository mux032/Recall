package com.recall.app.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.data.repository.ScreenshotRepository
import com.recall.app.domain.model.OcrResult
import com.recall.app.domain.repository.EmbeddingMetadata
import com.recall.app.domain.repository.EmbeddingRepository
import com.recall.app.domain.repository.OcrRepository
import com.recall.app.domain.repository.VisionRepository
import com.recall.app.util.NotificationUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * Worker that processes newly detected screenshots.
 * Runs OCR, vision model analysis, and generates embeddings.
 */
@HiltWorker
class ScreenshotProcessingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val screenshotRepository: ScreenshotRepository,
    private val ocrRepository: OcrRepository,
    private val visionRepository: VisionRepository,
    private val embeddingRepository: EmbeddingRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "screenshot_processing"
        const val INPUT_FILE_PATH = "file_path"
        const val INPUT_SCREENSHOT_ID = "screenshot_id"
    }

    override suspend fun doWork(): Result {
        val filePath = inputData.getString(INPUT_FILE_PATH)
        val screenshotId = inputData.getLong(INPUT_SCREENSHOT_ID, -1)

        if (filePath == null || screenshotId == -1L) {
            return Result.failure()
        }

        return try {
            android.util.Log.d("ScreenshotWorker", "Processing: $filePath")

            // Validate file exists
            val file = File(filePath)
            if (!file.exists()) {
                android.util.Log.e("ScreenshotWorker", "File does not exist: $filePath")
                return Result.failure()
            }

            // Show processing notification
            NotificationUtils.showProcessingNotification(applicationContext)

            // Step 1: Extract text using OCR
            android.util.Log.d("ScreenshotWorker", "Starting OCR for: $filePath")
            val ocrResult = try {
                val result = ocrRepository.extractText(file, enhanceImage = true)
                android.util.Log.d("ScreenshotWorker", "OCR completed: ${result.text.length} chars")
                result
            } catch (e: Exception) {
                android.util.Log.e("ScreenshotWorker", "OCR failed: ${e.message}", e)
                null
            }

            // Step 2: Analyze with Vision Model
            val visionResult = try {
                visionRepository.analyzeScreenshot(file, ocrResult?.text)
            } catch (e: Exception) {
                android.util.Log.e("ScreenshotWorker", "Vision analysis failed: ${e.message}")
                null
            }

            // Update screenshot with OCR and Vision results
            val screenshot = screenshotRepository.getScreenshotById(screenshotId)
            if (screenshot != null) {
                val updated = screenshot.copy(
                    ocrText = ocrResult?.text?.take(5000), // Limit text length
                    summary = generateSummary(visionResult, ocrResult),
                    tags = generateTags(visionResult, ocrResult),
                    category = visionResult?.contentType?.name,
                    processedAt = System.currentTimeMillis(),
                    isIndexed = ocrResult != null || visionResult != null,
                    processingStatus = if (ocrResult != null || visionResult != null) {
                        ScreenshotEntity.ProcessingStatus.COMPLETED
                    } else {
                        ScreenshotEntity.ProcessingStatus.FAILED
                    }
                )
                android.util.Log.d("ScreenshotWorker", "Updating screenshot $screenshotId with OCR text: ${updated.ocrText?.take(50)}...")
                screenshotRepository.updateScreenshot(updated)
                android.util.Log.d("ScreenshotWorker", "Screenshot $screenshotId updated successfully")
                
                // Step 3: Generate and store embedding for semantic search
                try {
                    val embeddingMetadata = EmbeddingMetadata(
                        filePath = updated.filePath,
                        summary = updated.summary,
                        tags = updated.tags,
                        category = updated.category,
                        timestamp = updated.timestamp,
                        ocrText = updated.ocrText
                    )
                    
                    embeddingRepository.generateAndStoreEmbedding(
                        id = updated.id,
                        text = updated.summary ?: "",
                        metadata = embeddingMetadata
                    )
                    
                    android.util.Log.d(
                        "ScreenshotWorker",
                        "Embedding generated for screenshot $screenshotId"
                    )
                } catch (e: Exception) {
                    android.util.Log.e(
                        "ScreenshotWorker",
                        "Embedding generation failed: ${e.message}"
                    )
                    // Don't fail the worker if embedding fails
                }
            }

            // Show completion notification
            if (ocrResult != null && ocrResult.text.isNotBlank()) {
                NotificationUtils.showProcessingCompleteNotification(
                    applicationContext,
                    count = 1
                )
            }

            android.util.Log.d(
                "ScreenshotWorker",
                "Processing completed: OCR=${ocrResult?.text?.length ?: 0} chars, " +
                "Vision=${visionResult?.contentType}, Time=${ocrResult?.processingTimeMs ?: 0}ms"
            )

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("ScreenshotWorker", "Error processing screenshot", e)
            NotificationUtils.showErrorNotification(
                applicationContext,
                "Failed to process screenshot"
            )
            Result.retry()
        }
    }

    /**
     * Generate a unified summary from vision and OCR results.
     */
    private fun generateSummary(
        visionResult: com.recall.app.domain.model.ScreenshotContext?,
        ocrResult: OcrResult?
    ): String? {
        // Prefer vision-generated summary if available
        val visionSummary = visionResult?.generateSummary()
        if (!visionSummary.isNullOrBlank()) {
            return visionSummary.take(500)
        }
        
        // Fallback to OCR summary
        return ocrResult?.text?.take(200)?.trim()
    }

    /**
     * Generate combined tags from vision and OCR results.
     */
    private fun generateTags(
        visionResult: com.recall.app.domain.model.ScreenshotContext?,
        ocrResult: OcrResult?
    ): String? {
        val allTags = mutableSetOf<String>()
        
        // Add vision tags
        visionResult?.generateTags()?.forEach { tag ->
            allTags.add(tag.lowercase().replace(" ", "_"))
        }
        
        // Add OCR-based tags
        if (ocrResult != null && ocrResult.text.isNotBlank()) {
            val text = ocrResult.text.lowercase()
            if (text.contains("http") || text.contains("www")) allTags.add("web")
            if (text.contains("@") && text.contains("com")) allTags.add("email")
            if (text.contains("₹") || text.contains("$") || text.contains("€")) allTags.add("finance")
        }
        
        return if (allTags.isNotEmpty()) allTags.joinToString(",") else null
    }
}
