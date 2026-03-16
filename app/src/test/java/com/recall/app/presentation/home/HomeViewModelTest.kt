package com.recall.app.presentation.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.recall.app.data.repository.ScreenshotRepository
import com.recall.app.data.repository.ScreenshotDetectionRepositoryImpl
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class HomeViewModelTest {
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var viewModel: HomeViewModel
    private lateinit var screenshotRepository: ScreenshotRepository
    
    @Before
    fun setup() {
        screenshotRepository = mock()
        val detectionRepository = mock<ScreenshotDetectionRepositoryImpl>()
        viewModel = HomeViewModel(screenshotRepository, detectionRepository)
    }
    
    @Test
    fun `viewModel initialization successful`() {
        // Basic test to ensure ViewModel initializes without errors
        assertNotNull(viewModel)
        assertNotNull(viewModel.uiState)
    }
    
    @Test
    fun `uiState initial value has correct defaults`() {
        val state = viewModel.uiState.value
        
        assertNotNull(state)
        assertTrue(state?.isLoading == true)
        assertTrue(state?.isEmpty == false)
        assertNull(state?.error)
        assertTrue(state?.recentScreenshots?.isEmpty() == true)
    }
    
    @Test
    fun `refresh sets isLoading to true`() {
        viewModel.refresh()
        
        val state = viewModel.uiState.value
        assertNotNull(state)
        assertTrue(state?.isLoading == true)
    }
}
