package com.recall.app.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.recall.app.data.local.RecallDatabase
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.domain.model.PipelineFailureCode
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
            embeddingByteArray = ByteArray(512) // non-null = fully indexed
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
            tagsJson = ""
            // ocrText = null → OCR pending; pipelineCode defaults to NONE
        )
        screenshotDao.insert(screenshot)
        screenshotDao.deleteById("test-uuid-2")

        val retrieved = screenshotDao.getScreenshotById("test-uuid-2")
        assertNull(retrieved)
    }

    /**
     * Verifies the FTS JOIN uses screenshots.rowid (INTEGER) not screenshots.id (TEXT UUID).
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
            tagsJson = ""
        )
        screenshotDao.insert(screenshot)

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
            tagsJson = ""
        )
        screenshotDao.insert(screenshot)

        val results = screenshotDao.searchFts("instagram")
        assertEquals("FTS search should return empty for non-matching query", 0, results.size)
    }

    @Test
    fun getOcrPendingScreenshots_excludes_failed_rows() = runBlocking {
        // Insert one OCR-pending row and one permanently-failed row
        screenshotDao.insert(ScreenshotEntity(
            id = "pending", filePath = "/a.png", fileName = "a.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = null, category = "", tagsJson = "",
            pipelineCode = PipelineFailureCode.NONE
        ))
        screenshotDao.insert(ScreenshotEntity(
            id = "failed", filePath = "/b.png", fileName = "b.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = null, category = "", tagsJson = "",
            pipelineCode = PipelineFailureCode.OCR_FAILED
        ))

        val results = screenshotDao.getOcrPendingScreenshots(limit = 10, maxRetries = 3)
        assertEquals(1, results.size)
        assertEquals("pending", results[0].id)
    }

    @Test
    fun getEmbeddingPendingScreenshots_returns_only_ocr_done_without_embedding() = runBlocking {
        // OCR done, no embedding → should appear
        screenshotDao.insert(ScreenshotEntity(
            id = "emb-pending", filePath = "/c.png", fileName = "c.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = "some text", category = "", tagsJson = "",
            embeddingByteArray = null
        ))
        // Fully indexed → should NOT appear
        screenshotDao.insert(ScreenshotEntity(
            id = "fully-done", filePath = "/d.png", fileName = "d.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = "some text", category = "", tagsJson = "",
            embeddingByteArray = ByteArray(512)
        ))

        val results = screenshotDao.getEmbeddingPendingScreenshots(limit = 10, maxEmbeddingRetries = 3)
        assertEquals(1, results.size)
        assertEquals("emb-pending", results[0].id)
    }

    @Test
    fun getPendingCount_counts_both_ocr_pending_and_embedding_pending() = runBlocking {
        // OCR pending
        screenshotDao.insert(ScreenshotEntity(
            id = "ocr-p", filePath = "/e.png", fileName = "e.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = null, category = "", tagsJson = ""
        ))
        // Embedding pending (OCR done, no embedding)
        screenshotDao.insert(ScreenshotEntity(
            id = "emb-p", filePath = "/f.png", fileName = "f.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = "text", category = "", tagsJson = "",
            embeddingByteArray = null
        ))
        // Fully indexed — should NOT be counted
        screenshotDao.insert(ScreenshotEntity(
            id = "done", filePath = "/g.png", fileName = "g.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = "text", category = "", tagsJson = "",
            embeddingByteArray = ByteArray(512)
        ))

        val count = screenshotDao.getPendingCount(maxOcrRetries = 3, maxEmbeddingRetries = 3)
        assertEquals(2, count)
    }

    @Test
    fun getPendingCount_excludes_permanently_failed_rows() = runBlocking {
        // OCR-failed row — must NOT be counted
        screenshotDao.insert(ScreenshotEntity(
            id = "ocr-fail", filePath = "/h.png", fileName = "h.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = null, category = "", tagsJson = "",
            pipelineCode = PipelineFailureCode.OCR_FAILED
        ))
        // Embedding-failed row — must NOT be counted
        screenshotDao.insert(ScreenshotEntity(
            id = "emb-fail", filePath = "/i.png", fileName = "i.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = "text", category = "", tagsJson = "",
            embeddingByteArray = null,
            pipelineCode = PipelineFailureCode.EMBEDDING_FAILED
        ))

        val count = screenshotDao.getPendingCount(maxOcrRetries = 3, maxEmbeddingRetries = 3)
        assertEquals(0, count)
    }

    @Test
    fun markFailed_prevents_row_from_reappearing_in_pending_queries() = runBlocking {
        // Insert a clean OCR-pending row
        screenshotDao.insert(ScreenshotEntity(
            id = "to-fail", filePath = "/j.png", fileName = "j.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = null, category = "", tagsJson = ""
        ))

        // Confirm it appears as pending before marking failed
        assertEquals(1, screenshotDao.getPendingCount(maxOcrRetries = 3, maxEmbeddingRetries = 3))
        assertEquals(1, screenshotDao.getOcrPendingScreenshots(limit = 10, maxRetries = 3).size)

        // Mark it permanently failed
        screenshotDao.markFailed("to-fail", PipelineFailureCode.OCR_FAILED)

        // Must no longer appear in any pending query
        assertEquals(0, screenshotDao.getPendingCount(maxOcrRetries = 3, maxEmbeddingRetries = 3))
        assertEquals(0, screenshotDao.getOcrPendingScreenshots(limit = 10, maxRetries = 3).size)
    }

    @Test
    fun markFailed_is_write_once_cannot_overwrite_existing_code() = runBlocking {
        screenshotDao.insert(ScreenshotEntity(
            id = "write-once", filePath = "/k.png", fileName = "k.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = null, category = "", tagsJson = "",
            pipelineCode = PipelineFailureCode.OCR_FAILED
        ))

        // Attempt to overwrite with EMBEDDING_FAILED — should be a no-op
        screenshotDao.markFailed("write-once", PipelineFailureCode.EMBEDDING_FAILED)

        val entity = screenshotDao.getScreenshotById("write-once")
        assertEquals(PipelineFailureCode.OCR_FAILED, entity?.pipelineCode)
    }

    @Test
    fun saveEmbeddingIfNotUserEdited_writes_when_not_user_edited() = runBlocking {
        screenshotDao.insert(ScreenshotEntity(
            id = "emb-write", filePath = "/s.png", fileName = "s.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = "machine ocr text", category = "", tagsJson = "",
            embeddingByteArray = null, isUserEdited = false
        ))

        val rows = screenshotDao.saveEmbeddingIfNotUserEdited("emb-write", ByteArray(512))

        assertEquals(1, rows)
        assertNotNull(screenshotDao.getScreenshotById("emb-write")?.embeddingByteArray)
    }

    @Test
    fun saveEmbeddingIfNotUserEdited_skips_when_user_edited() = runBlocking {
        // Simulate user editing OCR text while embedding was being generated
        screenshotDao.insert(ScreenshotEntity(
            id = "emb-skip", filePath = "/t.png", fileName = "t.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = "user corrected text", category = "", tagsJson = "",
            embeddingByteArray = null, isUserEdited = true
        ))

        val rows = screenshotDao.saveEmbeddingIfNotUserEdited("emb-skip", ByteArray(512))

        // Must be a no-op — user's corrected text must not lose its embedding slot
        assertEquals(0, rows)
        assertNull(screenshotDao.getScreenshotById("emb-skip")?.embeddingByteArray)
    }

    @Test
    fun getOcrPendingScreenshots_excludes_user_edited_rows() = runBlocking {
        // Normal OCR-pending row → should appear
        screenshotDao.insert(ScreenshotEntity(
            id = "normal", filePath = "/l.png", fileName = "l.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = null, category = "", tagsJson = ""
        ))
        // User-edited row with null ocrText → must NOT appear (user cleared text intentionally)
        screenshotDao.insert(ScreenshotEntity(
            id = "user-edited", filePath = "/m.png", fileName = "m.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = null, category = "", tagsJson = "",
            isUserEdited = true
        ))

        val results = screenshotDao.getOcrPendingScreenshots(limit = 10, maxRetries = 3)
        assertEquals(1, results.size)
        assertEquals("normal", results[0].id)
    }

    @Test
    fun getEmbeddingPendingScreenshots_includes_user_edited_rows() = runBlocking {
        // User-edited row with ocrText but no embedding → MUST appear so the
        // corrected text gets a fresh embedding for semantic search.
        screenshotDao.insert(ScreenshotEntity(
            id = "user-edited-emb", filePath = "/n.png", fileName = "n.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = "corrected text by user",
            category = "", tagsJson = "",
            embeddingByteArray = null,
            isUserEdited = true
        ))

        val results = screenshotDao.getEmbeddingPendingScreenshots(limit = 10, maxEmbeddingRetries = 3)
        assertEquals(1, results.size)
        assertEquals("user-edited-emb", results[0].id)
    }

    @Test
    fun saveUserEditedOcrText_clears_embedding_and_resets_pipelineCode() = runBlocking {
        // Insert a fully indexed row
        screenshotDao.insert(ScreenshotEntity(
            id = "full", filePath = "/o.png", fileName = "o.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = "original ocr text",
            category = "", tagsJson = "",
            embeddingByteArray = ByteArray(512)
        ))

        // User edits the OCR text
        screenshotDao.saveUserEditedOcrText("full", "corrected text")

        val entity = screenshotDao.getScreenshotById("full")
        assertEquals("corrected text", entity?.ocrText)
        assertEquals(true, entity?.isUserEdited)
        // Embedding must be cleared so the pipeline regenerates it from the corrected text
        assertNull(entity?.embeddingByteArray)
        // pipelineCode must be reset so the embedding pipeline picks up the row
        assertEquals(PipelineFailureCode.NONE, entity?.pipelineCode)
    }

    @Test
    fun saveUserEditedOcrText_on_failed_row_resets_pipelineCode_enabling_embedding() = runBlocking {
        // Insert a permanently-failed row — user is manually providing the OCR text
        screenshotDao.insert(ScreenshotEntity(
            id = "failed-fix", filePath = "/p.png", fileName = "p.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = null, category = "", tagsJson = "",
            pipelineCode = PipelineFailureCode.OCR_FAILED,
            ocrRetryCount = 3
        ))

        // User manually types the OCR text
        screenshotDao.saveUserEditedOcrText("failed-fix", "manually typed text")

        val entity = screenshotDao.getScreenshotById("failed-fix")
        assertEquals("manually typed text", entity?.ocrText)
        assertEquals(true, entity?.isUserEdited)
        assertNull(entity?.embeddingByteArray)
        // pipelineCode must be cleared so the embedding stage picks up the corrected text
        assertEquals(PipelineFailureCode.NONE, entity?.pipelineCode)
        // ocrRetryCount intentionally NOT reset — isUserEdited=1 already blocks OCR from re-running
        assertEquals(3, entity?.ocrRetryCount)
    }

    @Test
    fun resetForOcrRetry_clears_all_five_fields() = runBlocking {
        // Insert a permanently-failed, user-edited, fully-retried row
        screenshotDao.insert(ScreenshotEntity(
            id = "reset-me", filePath = "/q.png", fileName = "q.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = "user typed this",
            category = "", tagsJson = "",
            embeddingByteArray = ByteArray(512),
            pipelineCode = PipelineFailureCode.OCR_FAILED,
            ocrRetryCount = 3,
            isUserEdited = true
        ))

        screenshotDao.resetForOcrRetry("reset-me")

        val entity = screenshotDao.getScreenshotById("reset-me")
        // All five pipeline fields must be reset to initial state
        assertNull(entity?.ocrText)
        assertNull(entity?.embeddingByteArray)
        assertEquals(PipelineFailureCode.NONE, entity?.pipelineCode)
        assertEquals(0, entity?.ocrRetryCount)
        assertEquals(false, entity?.isUserEdited)
    }

    @Test
    fun resetForOcrRetry_row_reappears_in_ocr_pending_queue() = runBlocking {
        // Insert a permanently-failed row
        screenshotDao.insert(ScreenshotEntity(
            id = "requeue", filePath = "/r.png", fileName = "r.png",
            dateCreated = 1L, dateIndexed = 1L, width = 0, height = 0,
            ocrText = null, category = "", tagsJson = "",
            pipelineCode = PipelineFailureCode.OCR_FAILED,
            ocrRetryCount = 3
        ))

        // Confirm it's not in the pending queue before reset
        assertEquals(0, screenshotDao.getOcrPendingScreenshots(limit = 10, maxRetries = 3).size)

        screenshotDao.resetForOcrRetry("requeue")

        // After reset it must appear as OCR-pending
        val results = screenshotDao.getOcrPendingScreenshots(limit = 10, maxRetries = 3)
        assertEquals(1, results.size)
        assertEquals("requeue", results[0].id)
    }
}
