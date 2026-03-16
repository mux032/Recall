package com.recall.app.domain.repository

import com.recall.app.domain.model.OcrResult
import java.io.File

/**
 * Repository interface for OCR operations.
 */
interface OcrRepository {
    
    /**
     * Extract text from an image file.
     */
    suspend fun extractText(imageFile: File): OcrResult
    
    /**
     * Extract text with custom options.
     */
    suspend fun extractText(
        imageFile: File,
        enhanceImage: Boolean
    ): OcrResult
    
    /**
     * Check if OCR is available on this device.
     */
    fun isOcrAvailable(): Boolean
}
