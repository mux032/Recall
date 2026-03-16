package com.recall.app.data.repository

import com.recall.app.data.ocr.OcrException
import com.recall.app.domain.model.OcrResult
import com.recall.app.domain.ocr.OcrOptions
import com.recall.app.domain.ocr.OcrProcessor
import com.recall.app.domain.repository.OcrRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of OCR repository.
 */
@Singleton
class OcrRepositoryImpl @Inject constructor(
    private val ocrProcessor: OcrProcessor
) : OcrRepository {
    
    override suspend fun extractText(imageFile: File): OcrResult {
        return extractText(imageFile, enhanceImage = true)
    }
    
    override suspend fun extractText(
        imageFile: File,
        enhanceImage: Boolean
    ): OcrResult = withContext(Dispatchers.IO) {
        // Validate file
        if (!imageFile.exists()) {
            throw OcrException("Image file does not exist: ${imageFile.absolutePath}")
        }
        
        if (!imageFile.isFile) {
            throw OcrException("Path is not a file: ${imageFile.absolutePath}")
        }
        
        // Check file size (limit to 10MB for performance)
        val maxSize = 10 * 1024 * 1024 // 10MB
        if (imageFile.length() > maxSize) {
            throw OcrException("Image file too large: ${imageFile.length()} bytes")
        }
        
        // Check if file is an image
        val extension = imageFile.extension.lowercase()
        val validExtensions = listOf("jpg", "jpeg", "png", "webp", "bmp")
        if (extension !in validExtensions) {
            throw OcrException("Invalid image format: $extension")
        }
        
        // Process with OCR
        val options = OcrOptions(
            language = "en",
            maxImageWidth = 1024,
            enhanceContrast = enhanceImage,
            deskew = false
        )
        
        try {
            ocrProcessor.processImage(imageFile, options)
        } catch (e: Exception) {
            throw OcrException("OCR processing failed", e)
        }
    }
    
    override fun isOcrAvailable(): Boolean {
        return ocrProcessor.isAvailable()
    }
}
