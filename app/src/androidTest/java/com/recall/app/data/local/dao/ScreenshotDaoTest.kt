package com.recall.app.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.recall.app.data.local.database.RecallDatabase
import com.recall.app.data.local.entity.ScreenshotEntity
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
            processingState = "DONE"
        )
        screenshotDao.insert(screenshot)

        val retrieved = screenshotDao.getById("test-uuid-1")
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
            processingState = "PENDING"
        )
        screenshotDao.insert(screenshot)
        screenshotDao.deleteById("test-uuid-2")
        
        val retrieved = screenshotDao.getById("test-uuid-2")
        assertNull(retrieved)
    }
}
