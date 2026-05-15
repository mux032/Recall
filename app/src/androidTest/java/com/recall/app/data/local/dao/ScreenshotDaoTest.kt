package com.recall.app.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.recall.app.data.local.RecallDatabase
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.domain.model.ProcessingState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ScreenshotDaoTest {

    private lateinit var screenshotDao: ScreenshotDao
    private lateinit var db: RecallDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, RecallDatabase::class.java
        ).allowMainThreadQueries().build()
        screenshotDao = db.screenshotDao
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertAndGetScreenshot() = runBlocking {
        val screenshot = ScreenshotEntity(
            id = "test-uuid-1",
            filePath = "/storage/emulated/0/DCIM/Screenshots/Screenshot_1.png",
            fileName = "Screenshot_1.png",
            dateCreated = 1710780000000L,
            dateIndexed = System.currentTimeMillis(),
            width = 1080,
            height = 2400,
            ocrText = "Sample Extracted Text",
            category = "Testing",
            tagsJson = "tag1,tag2",
            processingState = ProcessingState.Done
        )
        screenshotDao.insert(screenshot)

        val retrieved = screenshotDao.getScreenshotById("test-uuid-1")
        assertEquals(screenshot.fileName, retrieved?.fileName)

        // Test Flow retrieval
        val allScreenshots = screenshotDao.getAllScreenshots().first()
        assertEquals(1, allScreenshots.size)
        assertEquals("test-uuid-1", allScreenshots[0].id)
    }

    @Test
    @Throws(Exception::class)
    fun deleteScreenshot() = runBlocking {
        val screenshot = ScreenshotEntity(
            id = "test-uuid-2",
            filePath = "/path/test.png",
            fileName = "test.png",
            dateCreated = 0L,
            dateIndexed = 0L,
            width = 0,
            height = 0,
            ocrText = null,
            category = "Other",
            tagsJson = "",
            processingState = ProcessingState.Pending
        )
        screenshotDao.insert(screenshot)
        screenshotDao.deleteById("test-uuid-2")

        val retrieved = screenshotDao.getScreenshotById("test-uuid-2")
        assertNull(retrieved)
    }

    /**
     * Verifies the FTS JOIN uses screenshots.rowid (INTEGER) not screenshots.id (TEXT UUID).
     * The previous bug had screenshots.id = screenshots_fts.docid which never matched,
     * causing searchFts to always return 0 results even when ocrText was populated.
     */
    @Test
    @Throws(Exception::class)
    fun searchFts_returnsResultsWhenOcrTextMatches() = runBlocking {
        val screenshot = ScreenshotEntity(
            id = "test-uuid-fts",
            filePath = "/storage/emulated/0/Screenshots/instagram.png",
            fileName = "instagram.png",
            dateCreated = System.currentTimeMillis(),
            dateIndexed = System.currentTimeMillis(),
            width = 1080,
            height = 1920,
            ocrText = "Instagram post from January 2025 showing travel photos",
            category = "Social",
            tagsJson = "",
            processingState = ProcessingState.Done
        )
        screenshotDao.insert(screenshot)

        // Search for a word that exists in the OCR text
        val results = screenshotDao.searchFts("instagram")
        assertEquals("FTS search should find the screenshot by OCR text", 1, results.size)
        assertEquals("test-uuid-fts", results[0].id)
    }

    @Test
    @Throws(Exception::class)
    fun searchFts_returnsEmptyWhenNoMatch() = runBlocking {
        val screenshot = ScreenshotEntity(
            id = "test-uuid-fts-2",
            filePath = "/storage/emulated/0/Screenshots/receipt.png",
            fileName = "receipt.png",
            dateCreated = System.currentTimeMillis(),
            dateIndexed = System.currentTimeMillis(),
            width = 1080,
            height = 1920,
            ocrText = "Total amount due: $42.99",
            category = "Finance",
            tagsJson = "",
            processingState = ProcessingState.Done
        )
        screenshotDao.insert(screenshot)

        val results = screenshotDao.searchFts("instagram")
        assertEquals("FTS search should return empty for non-matching query", 0, results.size)
    }
}
