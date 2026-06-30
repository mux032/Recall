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
    /**
     * CRITICAL FIX: Removed "GROUP BY id" which was meaningless since id is PRIMARY KEY.
     * Every row already has a unique id, so GROUP BY did nothing.
     * The unique index on filePath in ScreenshotEntity now prevents duplicates at DB level.
     */
    @Query("SELECT * FROM screenshots ORDER BY dateCreated DESC")
    fun getAllScreenshots(): Flow<List<ScreenshotEntity>>

    /**
     * Returns screenshots created after [since] (epoch ms), ordered newest first.
     * Used to power the RECENT filter (last 7 days = System.currentTimeMillis() - 7 * 86_400_000).
     */
    @Query("SELECT * FROM screenshots WHERE dateCreated >= :since ORDER BY dateCreated DESC")
    fun getRecentScreenshots(since: Long): Flow<List<ScreenshotEntity>>

    /**
     * Pass 1 — Screenshots that still need OCR (and therefore also need an embedding).
     *
     * Returns at most [limit] rows where [ScreenshotEntity.ocrText] is NULL and the
     * retry counter has not been exhausted, ordered newest-first so the user sees
     * recent screenshots indexed first.
     *
     * Filtering is done in SQL to avoid loading the full table into memory when
     * there is a large backlog (e.g. 1 742 rows).
     */
    @Query("""
        SELECT * FROM screenshots
        WHERE ocrText IS NULL
          AND isUserEdited = 0
          AND ocrRetryCount < :maxRetries
        ORDER BY dateCreated DESC
        LIMIT :limit
    """)
    suspend fun getOcrPendingScreenshots(limit: Int, maxRetries: Int): List<ScreenshotEntity>

    /**
     * Pass 2 — Screenshots where OCR succeeded but embedding generation failed/was skipped.
     *
     * Returns at most [limit] rows where [ScreenshotEntity.ocrText] IS NOT NULL but
     * [ScreenshotEntity.embeddingByteArray] IS NULL and the retry counter is below the
     * threshold. These rows were left in a half-processed state (OCR done, no vector)
     * and must be re-attempted with the embedding generator only — no re-OCR needed.
     */
    @Query("""
        SELECT * FROM screenshots
        WHERE ocrText IS NOT NULL
          AND embeddingByteArray IS NULL
          AND embeddingRetryCount < :maxEmbeddingRetries
        ORDER BY dateCreated DESC
        LIMIT :limit
    """)
    suspend fun getEmbeddingPendingScreenshots(limit: Int, maxEmbeddingRetries: Int): List<ScreenshotEntity>

    /**
     * Returns the number of screenshots the pipeline will actually process on its next run:
     * - PENDING state (not yet attempted), OR
     * - FAILED with OCR retries remaining, OR
     * - FAILED with embedding retries remaining (has ocrText but no embedding)
     *
     * Excludes permanently exhausted failures so the self-chain loop terminates correctly.
     */
    @Query("""
        SELECT COUNT(*) FROM screenshots WHERE
        processingState = 'PENDING'
        OR (processingState = 'FAILED' AND ocrRetryCount < :maxOcrRetries)
        OR (processingState = 'FAILED' AND ocrText IS NOT NULL AND embeddingRetryCount < :maxEmbeddingRetries)
    """)
    suspend fun getPendingCount(maxOcrRetries: Int, maxEmbeddingRetries: Int): Int

    /**
     * Returns a single page of screenshots ordered newest first.
     * Used by the windowed lazy-loading flow to avoid loading the entire library into RAM.
     *
     * @param limit  Number of rows to return (page size).
     * @param offset Number of rows to skip (page index × page size).
     */
    @Query("SELECT * FROM screenshots ORDER BY dateCreated DESC LIMIT :limit OFFSET :offset")
    suspend fun getScreenshotPage(limit: Int, offset: Int): List<ScreenshotEntity>

    /**
     * Returns the total number of screenshots in the database.
     * Used to determine when all pages have been loaded.
     */
    @Query("SELECT COUNT(*) FROM screenshots")
    suspend fun getScreenshotCount(): Int

    /**
     * Reactive count — Room re-emits whenever the screenshots table changes.
     * Used by [HomeViewModel] to detect new rows inserted by [ScanExistingWorker]
     * and trigger a list refresh so screenshots appear immediately.
     */
    @Query("SELECT COUNT(*) FROM screenshots")
    fun getScreenshotCountFlow(): Flow<Int>

    @Query("SELECT * FROM screenshots WHERE id = :id")
    suspend fun getScreenshotById(id: String): ScreenshotEntity?

    @Query("SELECT * FROM screenshots WHERE filePath = :filePath LIMIT 1")
    suspend fun getScreenshotByPath(filePath: String): ScreenshotEntity?

    /**
     * CRITICAL PERFORMANCE FIX: Returns all screenshot file paths for O(1) lookup.
     * Used to prevent N+1 query problem when scanning MediaStore.
     * Instead of querying DB for each screenshot (500 screenshots = 500 queries),
     * we load all paths once and use HashSet.contains() for O(1) lookup.
     */
    @Query("SELECT filePath FROM screenshots")
    suspend fun getAllScreenshotPaths(): List<String>

    @Query("SELECT * FROM screenshots WHERE id IN (:ids)")
    suspend fun getScreenshotsByIds(ids: List<String>): List<ScreenshotEntity>

    /**
     * Atomic update: Only updates if processingState matches expected value.
     * Returns number of rows updated (0 if no match, 1 if updated).
     * This prevents TOCTOU (Time-of-Check-Time-of-Use) race conditions.
     * 
     * USER EDIT PROTECTION: Will not override OCR text if isUserEdited is true.
     */
    @Query("""
        UPDATE screenshots
        SET ocrText = :ocrText,
            embeddingByteArray = :embedding,
            processingState = 'DONE',
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
        expectedState: String = "PENDING"
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
     * Rebuild the FTS index by updating all OCR texts.
     * This is needed after destructive migrations or if FTS index gets corrupted.
     */
    @Query("""
        UPDATE screenshots
        SET ocrText = ocrText
        WHERE ocrText IS NOT NULL AND ocrText != ''
    """)
    suspend fun rebuildFtsIndex(): Int

    /**
     * Insert with IGNORE strategy to prevent crashes on duplicate filePath.
     * Returns the row ID of inserted row, or -1 if insert failed due to conflict.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(screenshot: ScreenshotEntity): Long

    /**
     * Insert with REPLACE strategy - replaces existing record on conflict.
     * Used for update operations where we want to overwrite existing data.
     * Returns the row ID of inserted/replaced row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(screenshot: ScreenshotEntity): Long

    @Update
    suspend fun update(screenshot: ScreenshotEntity)

    @Query("DELETE FROM screenshots WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Increment the OCR retry count for a screenshot.
     * Used to track failed OCR attempts and prevent infinite retry loops.
     * @return Number of rows updated (1 if successful, 0 if screenshot not found)
     */
    @Query("""
        UPDATE screenshots
        SET ocrRetryCount = ocrRetryCount + 1
        WHERE id = :id
    """)
    suspend fun incrementOcrRetryCount(id: String): Int

    /**
     * Increment the embedding retry count for a screenshot.
     * Tracked separately from [incrementOcrRetryCount] so transient embedding failures
     * (model not loaded, OOM) do not exhaust the OCR retry budget on rows that already
     * have valid OCR text.
     */
    @Query("""
        UPDATE screenshots
        SET embeddingRetryCount = embeddingRetryCount + 1
        WHERE id = :id
    """)
    suspend fun incrementEmbeddingRetryCount(id: String): Int

    /**
     * Reset the OCR retry count for a screenshot after successful processing.
     * Called when OCR succeeds to clear any previous failure count.
     */
    @Query("""
        UPDATE screenshots
        SET ocrRetryCount = 0
        WHERE id = :id
    """)
    suspend fun resetOcrRetryCount(id: String): Int

    /**
     * Upsert: Insert new screenshot or update if exists (by filePath).
     * Uses atomic UPDATE with WHERE clause to prevent TOCTOU race conditions.
     * Only updates if current processingState is PENDING and isUserEdited is false.
     *
     * USER EDIT PROTECTION: Will not override OCR text if isUserEdited is true.
     */
    @androidx.room.Transaction
    suspend fun insertOrUpdateWithOcr(
        filePath: String,
        ocrText: String?,
        embedding: ByteArray?,
        timestamp: Long = System.currentTimeMillis()
    ): String {
        // Check if exists
        val existing = getScreenshotByPath(filePath)

        return if (existing != null) {
            // Skip update if user has manually edited the text
            if (existing.isUserEdited) {
                Log.d(TAG, "Skipping OCR update - user has edited this screenshot: ${existing.id}")
                existing.id
            } else {
                // Atomic update - returns 0 if state doesn't match (race condition prevented)
                val rowsUpdated = updateIfProcessingState(
                    filePath = filePath,
                    ocrText = ocrText,
                    embedding = embedding,
                    timestamp = timestamp,
                    expectedState = "PENDING"
                )
                
                if (rowsUpdated > 0) {
                    Log.d(TAG, "OCR update succeeded: ${existing.id}")
                    // Rebuild FTS index after OCR update
                    rebuildFtsIndex()
                } else {
                    Log.d(TAG, "OCR update skipped - state mismatch: ${existing.id}")
                }
                existing.id
            }
        } else {
            // Insert new
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
                processingState = ProcessingState.Done,
                embeddingByteArray = embedding
            )
            insert(entity)
            entity.id
        }
    }

    /**
     * Save user-edited OCR text.
     * Sets isUserEdited flag to prevent automatic OCR from overriding user edits.
     * Single atomic UPDATE query - more efficient than SELECT + UPDATE.
     */
    @Query("""
        UPDATE screenshots
        SET ocrText = :editedOcrText,
            isUserEdited = 1,
            userEditedAt = :timestamp,
            processingState = 'DONE'
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
