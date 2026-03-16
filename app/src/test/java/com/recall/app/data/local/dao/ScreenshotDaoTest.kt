package com.recall.app.data.local.dao

import com.recall.app.data.local.entity.ScreenshotEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ScreenshotDao.
 * Note: Full DAO testing requires instrumented tests with Room's in-memory database.
 * These tests verify the entity and basic logic.
 */
class ScreenshotDaoTest {
    
    @Test
    fun `ScreenshotEntity creation works correctly`() {
        val screenshot = ScreenshotEntity(
            filePath = "/Pictures/Screenshots/test.png",
            timestamp = System.currentTimeMillis(),
            ocrText = "Test OCR text",
            summary = "Test summary",
            tags = "test,example",
            category = "Test",
            isIndexed = false,
            processingStatus = ScreenshotEntity.ProcessingStatus.PENDING
        )
        
        assertEquals("/Pictures/Screenshots/test.png", screenshot.filePath)
        assertEquals("Test OCR text", screenshot.ocrText)
        assertEquals("Test summary", screenshot.summary)
        assertFalse(screenshot.isIndexed)
        assertEquals(ScreenshotEntity.ProcessingStatus.PENDING, screenshot.processingStatus)
    }
    
    @Test
    fun `ScreenshotEntity default values work correctly`() {
        val screenshot = ScreenshotEntity(
            filePath = "/test.png",
            timestamp = 1000L
        )
        
        assertEquals(0, screenshot.id)
        assertNull(screenshot.ocrText)
        assertNull(screenshot.summary)
        assertNull(screenshot.tags)
        assertNull(screenshot.embeddingId)
        assertNull(screenshot.category)
        assertFalse(screenshot.isIndexed)
        assertEquals(ScreenshotEntity.ProcessingStatus.PENDING, screenshot.processingStatus)
    }
    
    @Test
    fun `ProcessingStatus enum values are correct`() {
        assertEquals(ScreenshotEntity.ProcessingStatus.PENDING, ScreenshotEntity.ProcessingStatus.valueOf("PENDING"))
        assertEquals(ScreenshotEntity.ProcessingStatus.PROCESSING, ScreenshotEntity.ProcessingStatus.valueOf("PROCESSING"))
        assertEquals(ScreenshotEntity.ProcessingStatus.COMPLETED, ScreenshotEntity.ProcessingStatus.valueOf("COMPLETED"))
        assertEquals(ScreenshotEntity.ProcessingStatus.FAILED, ScreenshotEntity.ProcessingStatus.valueOf("FAILED"))
    }
}
