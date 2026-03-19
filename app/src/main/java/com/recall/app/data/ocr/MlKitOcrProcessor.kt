package com.recall.app.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.recall.app.domain.usecase.OcrProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject

class MlKitOcrProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) : OcrProcessor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun process(imagePath: String): String? {
        return try {
            val bitmap = getOptimizedBitmap(imagePath) ?: return null
            val image = InputImage.fromBitmap(bitmap, 0)
            
            val result = recognizer.process(image).await()
            val text = result.text.trim()
            
            bitmap.recycle() // Free memory aggressively
            
            if (text.isEmpty()) null else text
        } catch (e: Exception) {
            Log.e("MlKitOcrProcessor", "OCR Failed for path: $imagePath", e)
            null
        }
    }

    /**
     * Decodes a large screenshot bitmap into a smaller, memory-safe version
     * optimized for ML Kit OCR (max ~1024px side).
     */
    private fun getOptimizedBitmap(imagePath: String): Bitmap? {
        val file = File(imagePath)
        if (!file.exists()) return null

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imagePath, options)

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, 1024, 1024)
        options.inJustDecodeBounds = false
        // Preferred fallback config for OCR if ARGB_8888 is too heavy
        options.inPreferredConfig = Bitmap.Config.ARGB_8888

        return BitmapFactory.decodeFile(imagePath, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
