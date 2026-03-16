package com.recall.app.domain.vision

import com.recall.app.domain.model.VisionResult
import java.io.File

/**
 * Interface for vision model processing.
 */
interface VisionProcessor {
    
    /**
     * Analyze an image and generate caption, tags, and object detection.
     */
    suspend fun analyzeImage(file: File): VisionResult
    
    /**
     * Analyze with custom options.
     */
    suspend fun analyzeImage(
        file: File,
        options: VisionOptions
    ): VisionResult
    
    /**
     * Check if the vision model is available.
     */
    fun isAvailable(): Boolean
    
    /**
     * Get the model name.
     */
    fun getModelName(): String
    
    /**
     * Release resources.
     */
    fun close()
}

/**
 * Vision processing options.
 */
data class VisionOptions(
    val maxImageSize: Int = 384,
    val detectObjects: Boolean = true,
    val detectScene: Boolean = true,
    val detectColors: Boolean = false,
    val topKTags: Int = 10
)
