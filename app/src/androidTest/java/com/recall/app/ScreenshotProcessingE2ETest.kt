package com.recall.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.recall.app.data.local.database.RecallDatabase
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.data.repository.ScreenshotRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end test for screenshot processing flow.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class ScreenshotProcessingE2ETest {
    
    private lateinit var context: Context
    private lateinit var database: RecallDatabase
    private lateinit var repository: ScreenshotRepository
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            RecallDatabase::class.java
        ).allowMainThreadQueries().build()
        
        repository = ScreenshotRepository(database.screenshotDao())
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    @Test
    fun `complete screenshot processing flow`() = runBlocking {
        // Step 1: Insert screenshot (simulating detection)
        val screenshot = ScreenshotEntity(
            filePath = "/test/screenshot.png",
            timestamp = System.currentTimeMillis(),
            processingStatus = ScreenshotEntity.ProcessingStatus.PENDING
        )
        
        val id = repository.insertScreenshot(screenshot)
        assertTrue(id > 0)
        
        // Step 2: Verify it appears in recent screenshots
        val recent = repository.getRecentScreenshots(20).first()
        assertEquals(1, recent.size)
        
        // Step 3: Update with OCR results (simulating OCR processing)
        val updated = screenshot.copy(
            id = id,
            ocrText = "Test OCR text from screenshot",
            summary = "Test summary",
            tags = "test,example",
            category = "TEST",
            processedAt = System.currentTimeMillis(),
            isIndexed = true,
            processingStatus = ScreenshotEntity.ProcessingStatus.COMPLETED
        )
        
        repository.updateScreenshot(updated)
        
        // Step 4: Verify update
        val retrieved = repository.getScreenshotById(id)
        assertNotNull(retrieved)
        assertEquals("Test OCR text from screenshot", retrieved?.ocrText)
        assertEquals(ScreenshotEntity.ProcessingStatus.COMPLETED, retrieved?.processingStatus)
        
        // Step 5: Verify it appears in indexed screenshots
        val indexed = repository.getIndexedScreenshots().first()
        assertEquals(1, indexed.size)
        
        // Step 6: Search by text
        val searchResults = repository.searchByText("OCR")
        assertEquals(1, searchResults.size)
        
        // Step 7: Get by category
        val categoryResults = repository.getScreenshotsByCategory("TEST").first()
        assertEquals(1, categoryResults.size)
        
        // Step 8: Get count
        val count = repository.getScreenshotCount().first()
        assertEquals(1, count)
        
        // Step 9: Delete
        repository.deleteScreenshotById(id)
        
        // Step 10: Verify deletion
        val deleted = repository.getScreenshotById(id)
        assertNull(deleted)
    }
    
    @Test
    fun `multiple screenshots processing flow`() = runBlocking {
        // Insert multiple screenshots
        repeat(10) { i ->
            repository.insertScreenshot(
                ScreenshotEntity(
                    filePath = "/test/screenshot$i.png",
                    timestamp = System.currentTimeMillis() - (i * 1000),
                    ocrText = "Screenshot $i content",
                    category = if (i % 2 == 0) "CATEGORY_A" else "CATEGORY_B",
                    isIndexed = true,
                    processingStatus = ScreenshotEntity.ProcessingStatus.COMPLETED
                )
            )
        }
        
        // Verify count
        val count = repository.getScreenshotCount().first()
        assertEquals(10, count)
        
        // Verify recent returns limited results
        val recent = repository.getRecentScreenshots(5).first()
        assertEquals(5, recent.size)
        
        // Verify category filtering
        val categoryA = repository.getScreenshotsByCategory("CATEGORY_A").first()
        assertEquals(5, categoryA.size)
        
        val categoryB = repository.getScreenshotsByCategory("CATEGORY_B").first()
        assertEquals(5, categoryB.size)
        
        // Verify search
        val searchResults = repository.searchByText("Screenshot 5")
        assertEquals(1, searchResults.size)
    }
}
