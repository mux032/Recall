package com.recall.app.data.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.recall.app.domain.model.*
import com.recall.app.domain.ocr.OcrOptions
import com.recall.app.domain.ocr.OcrProcessor
import com.recall.app.util.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit implementation of OCR processor.
 * Uses the Latin script recognizer for text extraction.
 */
@Singleton
class MlKitOcrProcessor @Inject constructor() : OcrProcessor {
    
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    override suspend fun processImage(file: File): OcrResult {
        return processImage(file, OcrOptions())
    }
    
    override suspend fun processImage(
        file: File,
        options: OcrOptions
    ): OcrResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        
        try {
            // Load and preprocess image
            val bitmap = loadAndPreprocessImage(file, options)
            
            // Create InputImage for ML Kit
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            // Process with ML Kit
            val visionText = recognizer.process(inputImage).await()
            
            // Convert ML Kit result to domain model
            val ocrResult = convertToOcrResult(visionText, startTime)
            
            bitmap.recycle()
            
            ocrResult
        } catch (e: Exception) {
            throw OcrException("Failed to process image: ${file.path}", e)
        }
    }
    
    /**
     * Load and preprocess image for OCR.
     */
    private fun loadAndPreprocessImage(file: File, options: OcrOptions): Bitmap {
        // Load bitmap
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IllegalArgumentException("Failed to decode bitmap: ${file.path}")
        
        // Correct orientation using EXIF
        val orientedBitmap = correctOrientation(bitmap, file)
        
        // Resize if needed
        val resizedBitmap = if (options.maxImageWidth > 0 && 
            orientedBitmap.width > options.maxImageWidth) {
            resizeBitmap(orientedBitmap, options.maxImageWidth)
        } else {
            orientedBitmap
        }
        
        // Enhance contrast if requested
        return if (options.enhanceContrast) {
            enhanceContrast(resizedBitmap)
        } else {
            resizedBitmap
        }
    }
    
    /**
     * Correct image orientation using EXIF data.
     */
    private fun correctOrientation(bitmap: Bitmap, file: File): Bitmap {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            }
            
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap // Return original if EXIF reading fails
        }
    }
    
    /**
     * Resize bitmap to max width while maintaining aspect ratio.
     */
    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
        val scale = maxWidth.toFloat() / bitmap.width
        val height = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, maxWidth, height, true)
    }
    
    /**
     * Enhance image contrast for better OCR accuracy.
     */
    private fun enhanceContrast(bitmap: Bitmap): Bitmap {
        // Simple contrast enhancement
        // In production, consider using RenderScript or GPU acceleration
        val enhanced = bitmap.copy(bitmap.config, true)
        
        val pixels = IntArray(enhanced.width * enhanced.height)
        enhanced.getPixels(pixels, 0, enhanced.width, 0, 0, enhanced.width, enhanced.height)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF)
            val g = ((pixel shr 8) and 0xFF)
            val b = (pixel and 0xFF)
            
            // Simple contrast enhancement
            val factor = 1.2f
            val newR = ((r - 128) * factor + 128).coerceIn(0f, 255f).toInt()
            val newG = ((g - 128) * factor + 128).coerceIn(0f, 255f).toInt()
            val newB = ((b - 128) * factor + 128).coerceIn(0f, 255f).toInt()
            
            pixels[i] = (pixel and -0x1000000) or (newR shl 16) or (newG shl 8) or newB
        }
        
        enhanced.setPixels(pixels, 0, enhanced.width, 0, 0, enhanced.width, enhanced.height)
        return enhanced
    }
    
    /**
     * Convert ML Kit VisionText to domain OcrResult.
     */
    private fun convertToOcrResult(visionText: com.google.mlkit.vision.text.Text, startTime: Long): OcrResult {
        val textBlocks = visionText.textBlocks.map { block ->
            TextBlock(
                text = block.text,
                confidence = 0.9f, // ML Kit doesn't expose confidence in this API
                boundingBox = block.boundingBox?.let { bbox ->
                    BoundingBox(
                        left = bbox.left,
                        top = bbox.top,
                        right = bbox.right,
                        bottom = bbox.bottom
                    )
                },
                lines = block.lines.map { line ->
                    TextLine(
                        text = line.text,
                        confidence = 0.9f,
                        elements = line.elements.map { element ->
                            TextElement(
                                text = element.text,
                                confidence = 0.9f,
                                boundingBox = element.boundingBox?.let { bbox ->
                                    BoundingBox(
                                        left = bbox.left,
                                        top = bbox.top,
                                        right = bbox.right,
                                        bottom = bbox.bottom
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }
        
        val fullText = visionText.text
        val averageConfidence = 0.9f // Simplified for MVP
        
        return OcrResult(
            text = fullText,
            confidence = averageConfidence,
            textBlocks = textBlocks,
            language = "en", // ML Kit Latin recognizer
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    }
    
    override fun isAvailable(): Boolean {
        // ML Kit Latin recognizer is always available
        return true
    }
    
    override fun close() {
        recognizer.close()
    }
}

/**
 * Exception thrown when OCR processing fails.
 */
class OcrException(message: String, cause: Throwable? = null) : Exception(message, cause)
