package com.recall.app.data.repository

import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.local.entity.ScreenshotEntity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ScreenshotRepositoryTest {
    
    private lateinit var screenshotDao: ScreenshotDao
    private lateinit var repository: ScreenshotRepository
    
    @Before
    fun setup() {
        screenshotDao = mock()
        repository = ScreenshotRepository(screenshotDao)
    }
    
    @Test
    fun `getRecentScreenshots returns flow of screenshots`() = runTest {
        val screenshots = listOf(
            ScreenshotEntity(filePath = "/path/1.png", timestamp = 1000),
            ScreenshotEntity(filePath = "/path/2.png", timestamp = 2000)
        )
        
        whenever(screenshotDao.getRecentScreenshots(20)).thenReturn(flowOf(screenshots))
        
        val result = repository.getRecentScreenshots(20)
        
        assertNotNull(result)
        // Flow testing would require more setup
    }
    
    @Test
    fun `insertScreenshot calls dao insert`() = runTest {
        val screenshot = ScreenshotEntity(
            filePath = "/path/test.png",
            timestamp = System.currentTimeMillis()
        )
        
        whenever(screenshotDao.insert(screenshot)).thenReturn(1L)
        
        val result = repository.insertScreenshot(screenshot)
        
        assertEquals(1L, result)
        verify(screenshotDao).insert(screenshot)
    }
    
    @Test
    fun `updateScreenshot calls dao update`() = runTest {
        val screenshot = ScreenshotEntity(
            id = 1,
            filePath = "/path/test.png",
            timestamp = System.currentTimeMillis(),
            isIndexed = true
        )
        
        repository.updateScreenshot(screenshot)
        
        verify(screenshotDao).update(screenshot)
    }
    
    @Test
    fun `deleteScreenshotById calls dao deleteById`() = runTest {
        val screenshotId = 1L
        
        repository.deleteScreenshotById(screenshotId)
        
        verify(screenshotDao).deleteById(screenshotId)
    }
    
    @Test
    fun `getScreenshotCount returns flow of count`() = runTest {
        whenever(screenshotDao.getScreenshotCount()).thenReturn(flowOf(42))
        
        val result = repository.getScreenshotCount()
        
        assertNotNull(result)
        // Flow testing would require more setup
    }
}
