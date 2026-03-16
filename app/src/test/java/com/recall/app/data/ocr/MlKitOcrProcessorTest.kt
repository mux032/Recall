package com.recall.app.data.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.recall.app.domain.model.OcrResult
import com.recall.app.domain.ocr.OcrOptions
import com.recall.app.domain.ocr.OcrProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream

/**
 * Unit tests for ML Kit OCR processor.
 */
@RunWith(RobolectricTestRunner::class)
class MlKitOcrProcessorTest {

    private lateinit var processor: MlKitOcrProcessor
    private lateinit var testImageFile: File

    @Before
    fun setup() {
        processor = MlKitOcrProcessor()
        testImageFile = createTestImage()
    }

    @Test
    fun `processor is available`() {
        assertTrue(processor.isAvailable())
    }

    @Test
    fun `processImage extracts text from test image`() {
        runBlocking {
            val result = processor.processImage(testImageFile)

            assertNotNull(result)
            assertTrue(result.confidence in 0f..1f)
            assertTrue(result.processingTimeMs > 0)
        }
    }

    @Test
    fun `processImage with options respects max width`() {
        runBlocking {
            val options = OcrOptions(maxImageWidth = 512)
            val result = processor.processImage(testImageFile, options)

            assertNotNull(result)
        }
    }

    @Test(expected = OcrException::class)
    fun `processImage throws exception for non-existent file`() {
        runBlocking {
            val nonExistentFile = File("/path/that/does/not/exist.png")
            processor.processImage(nonExistentFile)
        }
    }

    @Test
    fun `close releases resources`() {
        processor.close()
        // No exception means success
    }

    // Helper to create test image with text
    private fun createTestImage(): File {
        val file = File.createTempFile("test_ocr", ".png")
        // Create a simple bitmap with text
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 40f
        }
        canvas.drawText("Hello World", 20f, 60f, paint)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }
}
