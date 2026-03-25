package com.recall.app.presentation.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.repository.ScreenshotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Unit tests for DetailViewModel.
 * Tests the ViewModel logic for loading and displaying screenshot details.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    private lateinit var screenshotRepository: ScreenshotRepository
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setup() {
        screenshotRepository = mock()
        savedStateHandle = SavedStateHandle(mapOf("screenshotId" to "test_screenshot_123"))
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `viewModel initializes with correct screenshotId from SavedStateHandle`() {
        // Given
        val expectedId = "test_screenshot_123"

        // When
        viewModel = DetailViewModel(screenshotRepository, savedStateHandle)

        // Then
        assertEquals(expectedId, savedStateHandle.get<String>("screenshotId"))
    }

    @Test
    fun `loadScreenshot fetches screenshot from repository`() = runTest {
        // Given
        val testScreenshotId = "test_screenshot_123"
        val testScreenshot = createTestScreenshot(testScreenshotId)

        whenever(screenshotRepository.getScreenshotById(testScreenshotId))
            .thenReturn(testScreenshot)

        // When
        viewModel = DetailViewModel(screenshotRepository, savedStateHandle)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(screenshotRepository).getScreenshotById(testScreenshotId)
    }

    @Test
    fun `screenshot state updates when data is loaded`() = runTest {
        // Given
        val testScreenshotId = "test_screenshot_456"
        val testScreenshot = createTestScreenshot(testScreenshotId)
        savedStateHandle = SavedStateHandle(mapOf("screenshotId" to testScreenshotId))

        whenever(screenshotRepository.getScreenshotById(testScreenshotId))
            .thenReturn(testScreenshot)

        // When
        viewModel = DetailViewModel(screenshotRepository, savedStateHandle)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(screenshotRepository, times(1)).getScreenshotById(testScreenshotId)
    }

    @Test
    fun `screenshot is null when repository returns null`() = runTest {
        // Given
        val testScreenshotId = "non_existent_id"
        savedStateHandle = SavedStateHandle(mapOf("screenshotId" to testScreenshotId))

        whenever(screenshotRepository.getScreenshotById(testScreenshotId))
            .thenReturn(null)

        // When
        viewModel = DetailViewModel(screenshotRepository, savedStateHandle)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(screenshotRepository).getScreenshotById(testScreenshotId)
    }

    @Test
    fun `screenshot with empty OCR text is handled correctly`() = runTest {
        // Given
        val testScreenshotId = "test_no_ocr"
        val testScreenshot = createTestScreenshot(testScreenshotId, ocrText = null)
        savedStateHandle = SavedStateHandle(mapOf("screenshotId" to testScreenshotId))

        whenever(screenshotRepository.getScreenshotById(testScreenshotId))
            .thenReturn(testScreenshot)

        // When
        viewModel = DetailViewModel(screenshotRepository, savedStateHandle)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(screenshotRepository).getScreenshotById(testScreenshotId)
        assertNull(testScreenshot.ocrText)
    }

    @Test
    fun `screenshot with full OCR text is handled correctly`() = runTest {
        // Given
        val testScreenshotId = "test_full_ocr"
        val longOcrText = """
            This is a multi-line OCR text
            Line 2 of the extracted content
            Line 3 with more details
            Final line of text
        """.trimIndent()

        val testScreenshot = createTestScreenshot(testScreenshotId, ocrText = longOcrText)
        savedStateHandle = SavedStateHandle(mapOf("screenshotId" to testScreenshotId))

        whenever(screenshotRepository.getScreenshotById(testScreenshotId))
            .thenReturn(testScreenshot)

        // When
        viewModel = DetailViewModel(screenshotRepository, savedStateHandle)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(screenshotRepository).getScreenshotById(testScreenshotId)
        assertEquals(longOcrText, testScreenshot.ocrText)
    }

    @Test
    fun `screenshot with app name is handled correctly`() = runTest {
        // Given
        val testScreenshotId = "test_with_app"
        val testScreenshot = createTestScreenshot(testScreenshotId, appName = "WhatsApp")
        savedStateHandle = SavedStateHandle(mapOf("screenshotId" to testScreenshotId))

        whenever(screenshotRepository.getScreenshotById(testScreenshotId))
            .thenReturn(testScreenshot)

        // When
        viewModel = DetailViewModel(screenshotRepository, savedStateHandle)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertEquals("WhatsApp", testScreenshot.appName)
    }

    @Test
    fun `repository is called only once during initialization`() = runTest {
        // Given
        val testScreenshotId = "test_screenshot_123" // Use same ID as setup
        val testScreenshot = createTestScreenshot(testScreenshotId)

        whenever(screenshotRepository.getScreenshotById(testScreenshotId))
            .thenReturn(testScreenshot)

        // When
        viewModel = DetailViewModel(screenshotRepository, savedStateHandle)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(screenshotRepository, times(1)).getScreenshotById(testScreenshotId)
    }

    @Test
    fun `screenshot with default values is handled correctly`() = runTest {
        // Given
        val testScreenshotId = "test_defaults"
        val testScreenshot = Screenshot(
            id = testScreenshotId,
            filePath = "",
            fileName = "unknown.png",
            dateCreated = 0,
            dateIndexed = 0,
            width = 0,
            height = 0,
            ocrText = "",
            appName = ""
        )
        savedStateHandle = SavedStateHandle(mapOf("screenshotId" to testScreenshotId))

        whenever(screenshotRepository.getScreenshotById(testScreenshotId))
            .thenReturn(testScreenshot)

        // When
        viewModel = DetailViewModel(screenshotRepository, savedStateHandle)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(screenshotRepository).getScreenshotById(testScreenshotId)
        assertEquals("", testScreenshot.appName)
        assertEquals("", testScreenshot.ocrText)
    }

    // Helper function to create test screenshots
    private fun createTestScreenshot(
        id: String,
        ocrText: String? = "Sample OCR text",
        appName: String = "TestApp"
    ): Screenshot {
        return Screenshot(
            id = id,
            filePath = "/storage/screenshots/$id.png",
            fileName = "$id.png",
            dateCreated = System.currentTimeMillis(),
            dateIndexed = System.currentTimeMillis(),
            width = 1080,
            height = 2340,
            ocrText = ocrText,
            appName = appName
        )
    }

    // Reference to ViewModel - created in each test
    private lateinit var viewModel: DetailViewModel
}
