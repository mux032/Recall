package com.recall.app.data.local.dao

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.domain.model.ProcessingState
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenshotDao {

    @Query("SELECT * FROM screenshots ORDER BY dateCreated DESC")
    fun getAllScreenshots(): Flow<List<ScreenshotEntity>>

    /**
     * Returns screenshots created after [since] (epoch ms), ordered newest first.
     * Used to power the RECENT filter (last 7 days = System.currentTimeMillis() - 7 * 86_400_000).
     */
    @Query("SELECT * FROM screenshots WHERE dateCreated >= :since ORDER BY dateCreated DESC")
    fun getRecentScreenshots(since: Long): Flow<List<ScreenshotEntity>>

    /**
     * Pass 1 — Screenshots that still need OCR (state = 0 / OcrPending).
     *
     * Returns at most [limit] rows ordered newest-first so the user sees
     * recent screenshots indexed first.
     *
     * Filtering is done in SQL to avoid loading the full table into memory when
     * there is a large backlog.
     */
    @Query("""
        SELECT * FROM screenshots
        WHERE processingState = ${ProcessingState.VAL_OCR_PENDING}
          AND isUserEdited = 0
          AND ocrRetryCount < :maxRetries
        ORDER BY dateCreated DESC
        LIMIT :limit
    """)
    suspend fun getOcrPendingScreenshots(limit: Int, maxRetries: Int): List<ScreenshotEntity>

    /**
     * Pass 2 — Screenshots where OCR succeeded but embedding generation failed/was skipped
     * (state = 1 / OcrCompleted).
     *
     * Returns at most [limit] rows that must be re-attempted with the embedding
     * generator only — no re-OCR needed.
     * Excludes user-edited rows for safety (their text is preserved, but embedding
     * retries are still safe; kept consistent with Pass 1 for simplicity).
     */
    @Query("""
        SELECT * FROM screenshots
        WHERE processingState = ${ProcessingState.VAL_OCR_COMPLETED}
          AND isUserEdited = 0
          AND embeddingRetryCount < :maxEmbeddingRetries
        ORDER BY dateCreated DESC
        LIMIT :limit
    """)
    suspend fun getEmbeddingPendingScreenshots(limit: Int, maxEmbeddingRetries: Int): List<ScreenshotEntity>

    /**
     * Returns the number of screenshots the pipeline will process on its next run.
     *
     * Counts ONLY OcrPending rows (state = 0) — OcrCompleted means "waiting for
     * embedding model" and must NOT be counted as pending work for the self-chain loop.
     * Including OcrCompleted rows here would cause the pipeline to self-chain indefinitely
     * when the ONNX model is absent.
     *
     * Excludes permanently exhausted failures and user-edited rows.
     */
    @Query("""
        SELECT COUNT(*) FROM screenshots
        WHERE processingState = ${ProcessingState.VAL_OCR_PENDING}
          AND isUserEdited = 0
          AND ocrRetryCount < :maxOcrRetries
    """)
    suspend fun getPendingCount(maxOcrRetries: Int): Int

    /**
     * Returns count of screenshots with OCR done but no embedding — used to trigger
     * the pipeline when the model becomes available.
     */
    @Query("SELECT COUNT(*) FROM screenshots WHERE processingState = ${ProcessingState.VAL_OCR_COMPLETED}")
    suspend fun getOcrCompletedCount(): Int

    /**
     * Returns a single page of screenshots ordered newest first.
     * Used by the windowed lazy-loading flow to avoid loading the entire library into RAM.
     *
     * @param limit  Number of rows to return (page size).
     * @param offset Number of rows to skip (page index × page size).
     */
    @Query("SELECT * FROM screenshots ORDER BY dateCreated DESC LIMIT :limit OFFSET :offset")
    suspend fun getScreenshotPage(limit: Int, offset: Int): List<ScreenshotEntity>

    /** Returns the total number of screenshots in the database. */
    @Query("SELECT COUNT(*) FROM screenshots")
    suspend fun getScreenshotCount(): Int

    /**
     * Reactive count — Room re-emits whenever the screenshots table changes.
     * Used by [HomeViewModel] to detect new rows and trigger a list refresh.
     */
    @Query("SELECT COUNT(*) FROM screenshots")
    fun getScreenshotCountFlow(): Flow<Int>

    @Query("SELECT * FROM screenshots WHERE id = :id")
    suspend fun getScreenshotById(id: String): ScreenshotEntity?

    @Query("SELECT * FROM screenshots WHERE filePath = :filePath LIMIT 1")
    suspend fun getScreenshotByPath(filePath: String): ScreenshotEntity?

    /**
     * Returns all screenshot file paths for O(1) lookup during MediaStore scan.
     * Avoids the N+1 query problem: load all paths once, use HashSet.contains() per file.
     */
    @Query("SELECT filePath FROM screenshots")
    suspend fun getAllScreenshotPaths(): List<String>

    @Query("SELECT * FROM screenshots WHERE id IN (:ids)")
    suspend fun getScreenshotsByIds(ids: List<String>): List<ScreenshotEntity>

    /**
     * Atomic update: Only updates if processingState matches [expectedState].
     * Returns number of rows updated (0 if no match, 1 if updated).
     * Prevents TOCTOU race conditions.
     *
     * USER EDIT PROTECTION: Will not override OCR text if isUserEdited is true.
     */
    @Query("""
        UPDATE screenshots
        SET ocrText = :ocrText,
            embeddingByteArray = :embedding,
            processingState = ${ProcessingState.VAL_OCR_EMB_COMPLETED},
            dateIndexed = :timestamp
        WHERE filePath = :filePath
          AND processingState = :expectedState
          AND (isUserEdited = 0 OR isUserEdited IS NULL)
    """)
    suspend fun updateIfProcessingState(
        filePath: String,
        ocrText: String?,
        embedding: ByteArray?,
        timestamp: Long,
        expectedState: Int = ProcessingState.VAL_OCR_PENDING
    ): Int

    /**
     * Full-text search using FTS4 with wildcard matching.
     * Automatically appends wildcard (*) to enable prefix matching.
     * Example: "insta" matches "instagram", "installation", etc.
     */
    @Query("""
        SELECT screenshots.*
        FROM screenshots
        JOIN screenshots_fts ON screenshots.rowid = screenshots_fts.docid
        WHERE screenshots_fts MATCH :query || '*'
    """)
    suspend fun searchFts(query: String): List<ScreenshotEntity>

    /**
     * Rebuild the FTS index — touch all rows with OCR text so the FTS virtual table
     * re-indexes them. Needed after batch inserts or if the FTS index gets out of sync.
     */
    @Query("""
        UPDATE screenshots
        SET ocrText = ocrText
        WHERE ocrText IS NOT NULL AND ocrText != ''
    """)
    suspend fun rebuildFtsIndex(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(screenshot: ScreenshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(screenshot: ScreenshotEntity): Long

    @Update
    suspend fun update(screenshot: ScreenshotEntity)

    @Query("DELETE FROM screenshots WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("""
        UPDATE screenshots
        SET ocrRetryCount = ocrRetryCount + 1
        WHERE id = :id
    """)
    suspend fun incrementOcrRetryCount(id: String): Int

    /**
     * Increment the embedding retry count separately from [incrementOcrRetryCount] so
     * transient embedding failures (model not loaded, OOM) do not exhaust the OCR retry
     * budget on rows that already have valid OCR text.
     */
    @Query("""
        UPDATE screenshots
        SET embeddingRetryCount = embeddingRetryCount + 1
        WHERE id = :id
    """)
    suspend fun incrementEmbeddingRetryCount(id: String): Int

    @Query("""
        UPDATE screenshots
        SET ocrRetryCount = 0
        WHERE id = :id
    """)
    suspend fun resetOcrRetryCount(id: String): Int

    /**
     * Upsert: Insert new screenshot or update if exists (by filePath).
     * Uses an atomic UPDATE with a state guard to prevent TOCTOU race conditions.
     * Only updates if current processingState is OcrPending (0) and isUserEdited is false.
     */
    @Transaction
    suspend fun insertOrUpdateWithOcr(
        filePath: String,
        ocrText: String?,
        embedding: ByteArray?,
        timestamp: Long = System.currentTimeMillis()
    ): String {
        val existing = getScreenshotByPath(filePath)

        return if (existing != null) {
            if (existing.isUserEdited) {
                Log.d(TAG, "Skipping OCR update - user has edited this screenshot: ${existing.id}")
                existing.id
            } else {
                val rowsUpdated = updateIfProcessingState(
                    filePath = filePath,
                    ocrText = ocrText,
                    embedding = embedding,
                    timestamp = timestamp,
                    expectedState = ProcessingState.VAL_OCR_PENDING
                )

                if (rowsUpdated > 0) {
                    Log.d(TAG, "OCR update succeeded: ${existing.id}")
                    rebuildFtsIndex()
                } else {
                    Log.d(TAG, "OCR update skipped - state mismatch: ${existing.id}")
                }
                existing.id
            }
        } else {
            val initialState = when {
                ocrText != null && embedding != null -> ProcessingState.OcrEmbCompleted
                ocrText != null                      -> ProcessingState.OcrCompleted
                else                                 -> ProcessingState.OcrPending
            }
            val entity = ScreenshotEntity(
                id = java.util.UUID.randomUUID().toString(),
                filePath = filePath,
                fileName = java.io.File(filePath).name,
                dateCreated = timestamp,
                dateIndexed = timestamp,
                width = 0,
                height = 0,
                ocrText = ocrText,
                category = "Uncategorized",
                tagsJson = "",
                processingState = initialState,
                embeddingByteArray = embedding
            )
            insert(entity)
            entity.id
        }
    }

    /**
     * Save user-edited OCR text.
     * Sets isUserEdited flag to prevent automatic OCR from overriding user edits.
     * Marks the row as fully indexed (OcrEmbCompleted = 2) so it appears in search results.
     */
    @Query("""
        UPDATE screenshots
        SET ocrText = :editedOcrText,
            isUserEdited = 1,
            userEditedAt = :timestamp,
            processingState = ${ProcessingState.VAL_OCR_EMB_COMPLETED}
        WHERE id = :id
    """)
    suspend fun saveUserEditedOcrText(
        id: String,
        editedOcrText: String,
        timestamp: Long = System.currentTimeMillis()
    )

    companion object {
        private const val TAG = "ScreenshotDao"
    }
}
