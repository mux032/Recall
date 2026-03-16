package com.recall.app.domain.ocr

import com.recall.app.domain.model.OcrResult
import java.io.File

/**
 * Interface for OCR processing implementations.
 */
interface OcrProcessor {
    
    /**
     * Process an image file and extract text.
     */
    suspend fun processImage(file: File): OcrResult
    
    /**
     * Process an image file with options.
     */
    suspend fun processImage(
        file: File,
        options: OcrOptions
    ): OcrResult
    
    /**
     * Check if the processor is available.
     */
    fun isAvailable(): Boolean
    
    /**
     * Release resources.
     */
    fun close()
}

/**
 * OCR processing options.
 */
data class OcrOptions(
    val language: String = "en",
    val maxImageWidth: Int = 1024,
    val enhanceContrast: Boolean = true,
    val deskew: Boolean = false
)
