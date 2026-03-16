package com.recall.app.data.repository

import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.local.entity.ScreenshotEntity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class ScreenshotDetectionRepositoryImplTest {
    
    private lateinit var screenshotDao: ScreenshotDao
    private lateinit var repository: ScreenshotDetectionRepositoryImpl
    
    @Before
    fun setup() {
        screenshotDao = mock()
        // Note: Full testing requires Android context, using simplified tests
        repository = ScreenshotDetectionRepositoryImpl(screenshotDao, mock())
    }
    
    @Test
    fun `startMonitoring sets isMonitoring to true`() {
        assertFalse(repository.isMonitoring())
        
        repository.startMonitoring()
        
        assertTrue(repository.isMonitoring())
    }
    
    @Test
    fun `stopMonitoring sets isMonitoring to false`() {
        repository.startMonitoring()
        assertTrue(repository.isMonitoring())
        
        repository.stopMonitoring()
        
        assertFalse(repository.isMonitoring())
    }
    
    @Test
    fun `getScreenshotFolders returns expected paths`() {
        val folders = repository.getScreenshotFolders()
        
        assertTrue(folders.contains("/Pictures/Screenshots"))
        assertTrue(folders.contains("/DCIM/Screenshots"))
        assertTrue(folders.contains("/Pictures"))
        assertTrue(folders.contains("/DCIM"))
        assertEquals(4, folders.size)
    }
    
    @Test
    fun `repository is created successfully`() {
        assertNotNull(repository)
    }
    
    @Test
    fun `isMonitoring returns correct initial state`() {
        assertFalse(repository.isMonitoring())
    }
}
