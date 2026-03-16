package com.recall.app.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.recall.app.data.local.database.RecallDatabase
import com.recall.app.data.local.entity.ScreenshotEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for ScreenshotDao with in-memory database.
 */
class ScreenshotDaoIntegrationTest {
    
    private lateinit var database: RecallDatabase
    private lateinit var screenshotDao: ScreenshotDao
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RecallDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        screenshotDao = database.screenshotDao()
    }
    
    @After
    fun teardown() = runBlocking {
        database.close()
    }
    
    @Test
    fun `insert and retrieve screenshot`() = runBlocking {
        val screenshot = ScreenshotEntity(
            filePath = "/test/path.png",
            timestamp = System.currentTimeMillis(),
            ocrText = "Test OCR",
            summary = "Test summary",
            isIndexed = true
        )
        
        val id = screenshotDao.insert(screenshot)
        assertTrue(id > 0)
        
        val retrieved = screenshotDao.getScreenshotById(id)
        assertNotNull(retrieved)
        assertEquals("/test/path.png", retrieved?.filePath)
        assertEquals("Test OCR", retrieved?.ocrText)
    }
    
    @Test
    fun `get recent screenshots returns limited results`() = runBlocking {
        // Insert 25 screenshots
        repeat(25) { i ->
            screenshotDao.insert(
                ScreenshotEntity(
                    filePath = "/test/path$i.png",
                    timestamp = System.currentTimeMillis() - (i * 1000),
                    isIndexed = true
                )
            )
        }
        
        val recent = screenshotDao.getRecentScreenshots(20).first()
        assertEquals(20, recent.size)
    }
    
    @Test
    fun `search by text returns matching screenshots`() = runBlocking {
        screenshotDao.insert(
            ScreenshotEntity(
                filePath = "/test/flight.png",
                timestamp = System.currentTimeMillis(),
                ocrText = "Flight booking Bangalore to Delhi",
                summary = "Flight confirmation",
                isIndexed = true
            )
        )
        
        screenshotDao.insert(
            ScreenshotEntity(
                filePath = "/test/recipe.png",
                timestamp = System.currentTimeMillis(),
                ocrText = "Chocolate cake recipe",
                summary = "Recipe",
                isIndexed = true
            )
        )
        
        val results = screenshotDao.searchByText("%flight%")
        assertEquals(1, results.size)
        assertEquals("/test/flight.png", results[0].filePath)
    }
    
    @Test
    fun `delete screenshot removes from database`() = runBlocking {
        val screenshot = ScreenshotEntity(
            filePath = "/test/delete.png",
            timestamp = System.currentTimeMillis()
        )
        
        val id = screenshotDao.insert(screenshot)
        val retrieved = screenshotDao.getScreenshotById(id)
        assertNotNull(retrieved)
        
        screenshotDao.delete(screenshot)
        
        val deleted = screenshotDao.getScreenshotById(id)
        assertNull(deleted)
    }
    
    @Test
    fun `get screenshots by category returns filtered results`() = runBlocking {
        screenshotDao.insert(
            ScreenshotEntity(
                filePath = "/test/code1.png",
                timestamp = System.currentTimeMillis(),
                category = "CODE_SNIPPET",
                isIndexed = true
            )
        )
        
        screenshotDao.insert(
            ScreenshotEntity(
                filePath = "/test/code2.png",
                timestamp = System.currentTimeMillis(),
                category = "CODE_SNIPPET",
                isIndexed = true
            )
        )
        
        screenshotDao.insert(
            ScreenshotEntity(
                filePath = "/test/travel.png",
                timestamp = System.currentTimeMillis(),
                category = "BOOKING_CONFIRMATION",
                isIndexed = true
            )
        )
        
        val codeScreenshots = screenshotDao.getScreenshotsByCategory("CODE_SNIPPET").first()
        assertEquals(2, codeScreenshots.size)
    }
    
    @Test
    fun `get all categories returns unique categories`() = runBlocking {
        screenshotDao.insert(
            ScreenshotEntity(
                filePath = "/test/1.png",
                timestamp = System.currentTimeMillis(),
                category = "CODE_SNIPPET",
                isIndexed = true
            )
        )
        
        screenshotDao.insert(
            ScreenshotEntity(
                filePath = "/test/2.png",
                timestamp = System.currentTimeMillis(),
                category = "CODE_SNIPPET", // Duplicate category
                isIndexed = true
            )
        )
        
        screenshotDao.insert(
            ScreenshotEntity(
                filePath = "/test/3.png",
                timestamp = System.currentTimeMillis(),
                category = "BOOKING_CONFIRMATION",
                isIndexed = true
            )
        )
        
        val categories = screenshotDao.getAllCategories().first()
        assertEquals(2, categories.size)
        assertTrue(categories.contains("CODE_SNIPPET"))
        assertTrue(categories.contains("BOOKING_CONFIRMATION"))
    }
}
