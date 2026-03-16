package com.recall.app.domain.repository

import com.recall.app.domain.model.ScreenshotContext
import com.recall.app.domain.model.VisionResult
import java.io.File

/**
 * Repository interface for vision model operations.
 */
interface VisionRepository {
    
    /**
     * Analyze an image and generate vision results.
     */
    suspend fun analyzeImage(imageFile: File): VisionResult
    
    /**
     * Analyze and create unified screenshot context.
     */
    suspend fun analyzeScreenshot(
        imageFile: File,
        ocrText: String?
    ): ScreenshotContext
    
    /**
     * Check if vision model is available.
     */
    fun isVisionAvailable(): Boolean
    
    /**
     * Get the current model name.
     */
    fun getModelName(): String
}
