package com.recall.app.data.repository

import com.recall.app.data.ocr.OcrException
import com.recall.app.domain.ocr.OcrProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.io.File

/**
 * Unit tests for OCR repository implementation.
 */
class OcrRepositoryImplTest {

    private lateinit var ocrProcessor: OcrProcessor
    private lateinit var repository: OcrRepositoryImpl

    @Before
    fun setup() {
        ocrProcessor = mock()
        repository = OcrRepositoryImpl(ocrProcessor)
    }

    @Test
    fun `extractText calls processor with correct parameters`() = runBlocking {
        val testFile = File("/test/image.png")
        whenever(ocrProcessor.processImage(any(), any())).thenReturn(
            com.recall.app.domain.model.OcrResult(
                text = "Test text",
                confidence = 0.9f,
                textBlocks = emptyList(),
                processingTimeMs = 100
            )
        )

        val result = repository.extractText(testFile)

        assertNotNull(result)
        assertEquals("Test text", result.text)
        verify(ocrProcessor).processImage(eq(testFile), any())
    }

    @Test
    fun `extractText with enhanceImage parameter`() = runBlocking {
        val testFile = File("/test/image.png")
        whenever(ocrProcessor.processImage(any(), any())).thenReturn(
            com.recall.app.domain.model.OcrResult(
                text = "Enhanced text",
                confidence = 0.95f,
                textBlocks = emptyList(),
                processingTimeMs = 150
            )
        )

        val result = repository.extractText(testFile, enhanceImage = true)

        assertNotNull(result)
        verify(ocrProcessor).processImage(eq(testFile), argThat { enhanceContrast })
    }

    @Test(expected = OcrException::class)
    fun `extractText throws for non-existent file`() = runBlocking {
        val nonExistentFile = File("/path/does/not/exist.png")
        repository.extractText(nonExistentFile)
    }

    @Test(expected = OcrException::class)
    fun `extractText throws for directory instead of file`() = runBlocking {
        val directory = File("/tmp")
        repository.extractText(directory)
    }

    @Test(expected = OcrException::class)
    fun `extractText throws for invalid file format`() = runBlocking {
        val invalidFile = File.createTempFile("test", ".txt")
        repository.extractText(invalidFile)
    }

    @Test
    fun `isOcrAvailable returns processor availability`() {
        whenever(ocrProcessor.isAvailable()).thenReturn(true)
        assertTrue(repository.isOcrAvailable())

        whenever(ocrProcessor.isAvailable()).thenReturn(false)
        assertFalse(repository.isOcrAvailable())
    }
}
