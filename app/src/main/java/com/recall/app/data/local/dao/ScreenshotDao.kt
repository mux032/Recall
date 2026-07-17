package com.recall.app.data.local.dao

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.domain.model.PipelineFailureCode
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
     * Pass 1 — Screenshots that still need OCR.
     *
     * State is derived from data: ocrText IS NULL means OCR has not been completed.
     * Excludes rows that have permanently failed ([pipelineCode] != 0) and user-edited rows.
     *
     * Returns at most [limit] rows ordered newest-first so the user sees recent screenshots
     * indexed first. Filtering is done in SQL to avoid loading the full table into memory.
     */
    @Query("""
        SELECT * FROM screenshots
        WHERE ocrText IS NULL
          AND pipelineCode = ${PipelineFailureCode.NONE}
          AND isUserEdited = 0
          AND ocrRetryCount < :maxRetries
        ORDER BY dateCreated DESC
        LIMIT :limit
    """)
    suspend fun getOcrPendingScreenshots(limit: Int, maxRetries: Int): List<ScreenshotEntity>

    /**
     * Pass 2 — Screenshots where OCR text is present but embedding has not yet been generated.
     *
     * Intentionally does NOT filter by isUserEdited — when a user edits OCR text,
     * [saveUserEditedOcrText] clears [embeddingByteArray] so the embedding is regenerated
     * from the corrected text. Excluding user-edited rows here would leave a stale embedding
     * (derived from the old, wrong OCR text) in the database permanently.
     *
     * OCR is blocked on user-edited rows ([getOcrPendingScreenshots] keeps isUserEdited = 0);
     * embedding regeneration is always safe and always needed when [embeddingByteArray] is null.
     */
    @Query("""
        SELECT * FROM screenshots
        WHERE ocrText IS NOT NULL
          AND embeddingByteArray IS NULL
          AND pipelineCode = ${PipelineFailureCode.NONE}
          AND embeddingRetryCount < :maxEmbeddingRetries
        ORDER BY dateCreated DESC
        LIMIT :limit
    """)
    suspend fun getEmbeddingPendingScreenshots(limit: Int, maxEmbeddingRetries: Int): List<ScreenshotEntity>

    /**
     * Returns the total number of screenshots the pipeline will process on its next run.
     *
     * Counts both OCR-pending (ocrText IS NULL) and embedding-pending
     * (ocrText IS NOT NULL AND embeddingByteArray IS NULL) rows, excluding permanently
     * failed rows. User-edited rows are excluded from OCR-pending but included in
     * embedding-pending — their corrected text still needs an up-to-date embedding.
     */
    @Query("""
        SELECT COUNT(*) FROM screenshots
        WHERE pipelineCode = ${PipelineFailureCode.NONE}
          AND (
            (ocrText IS NULL AND isUserEdited = 0 AND ocrRetryCount < :maxOcrRetries)
            OR (ocrText IS NOT NULL AND embeddingByteArray IS NULL AND embeddingRetryCount < :maxEmbeddingRetries)
          )
    """)
    suspend fun getPendingCount(maxOcrRetries: Int, maxEmbeddingRetries: Int): Int

    /**
     * Returns a single page of screenshots ordered newest first.
     * Used by the windowed lazy-loading flow to avoid loading the entire library into RAM.
     */
    @Query("SELECT * FROM screenshots ORDER BY dateCreated DESC LIMIT :limit OFFSET :offset")
    suspend fun getScreenshotPage(limit: Int, offset: Int): List<ScreenshotEntity>

    /** Returns the total number of screenshots in the database. */
    @Query("SELECT COUNT(*) FROM screenshots")
    suspend fun getScreenshotCount(): Int

    /**
     * Reactive count — Room re-emits whenever the screenshots table changes.
     * Used by HomeViewModel to detect new rows and trigger a list refresh.
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
     * Atomic conditional update: only updates a row if it is still OCR-pending
     * (ocrText IS NULL) and not user-edited, preventing TOCTOU race conditions.
     * Returns the number of rows updated (0 = skipped, 1 = updated).
     */
    @Query("""
        UPDATE screenshots
        SET ocrText = :ocrText,
            embeddingByteArray = :embedding,
            dateIndexed = :timestamp
        WHERE filePath = :filePath
          AND ocrText IS NULL
          AND (isUserEdited = 0 OR isUserEdited IS NULL)
    """)
    suspend fun updateIfOcrPending(
        filePath: String,
        ocrText: String?,
        embedding: ByteArray?,
        timestamp: Long
    ): Int

    /**
     * Full-text search using FTS4 with wildcard matching.
     * Automatically appends wildcard (*) to enable prefix matching.
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
     * Saves the generated embedding for a screenshot — but only if the user has NOT manually
     * edited the OCR text ([isUserEdited] = 0).
     *
     * If the user edited the text while the embedding was being generated, this write is skipped
     * (returns 0 rows updated). [saveUserEditedOcrText] already cleared [embeddingByteArray]
     * so the pipeline will regenerate the embedding from the correct text on the next run.
     *
     * Does NOT overwrite [ocrText] or [isUserEdited] — those columns are owned by the OCR
     * stage and the user respectively.
     *
     * @return number of rows updated (0 = skipped because user edited; 1 = success)
     */
    @Query("""
        UPDATE screenshots
        SET embeddingByteArray = :embedding,
            ocrRetryCount = 0,
            embeddingRetryCount = 0
        WHERE id = :id
          AND isUserEdited = 0
    """)
    suspend fun saveEmbeddingIfNotUserEdited(id: String, embedding: ByteArray): Int

    /**
     * Marks a row as permanently failed with the given [failureCode] (see [PipelineFailureCode]).
     * Write-once: once set, the pipeline will not retry the row.
     */
    @Query("""
        UPDATE screenshots
        SET pipelineCode = :failureCode
        WHERE id = :id
          AND pipelineCode = 0
    """)
    suspend fun markFailed(id: String, failureCode: Int): Int

    /**
     * Upsert: insert a new screenshot or update an existing one (matched by filePath).
     * Only updates if the existing row is still OCR-pending (ocrText IS NULL) and not
     * user-edited, preventing TOCTOU races.
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
                val rowsUpdated = updateIfOcrPending(
                    filePath = filePath,
                    ocrText = ocrText,
                    embedding = embedding,
                    timestamp = timestamp
                )
                if (rowsUpdated > 0) {
                    Log.d(TAG, "OCR update succeeded: ${existing.id}")
                    rebuildFtsIndex()
                } else {
                    Log.d(TAG, "OCR update skipped - row already processed: ${existing.id}")
                }
                existing.id
            }
        } else {
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
                embeddingByteArray = embedding
                // pipelineCode defaults to NONE (0)
            )
            insert(entity)
            entity.id
        }
    }

    /**
     * Save user-edited OCR text.
     *
     * Sets isUserEdited = 1 to prevent automatic OCR from overriding the user's text.
     * Clears embeddingByteArray so the pipeline regenerates the embedding from the
     * corrected text — without this, semantic search would match the screenshot using
     * a vector derived from the old, potentially wrong OCR text.
     *
     * [editedOcrText] must not be null or blank — enforced at the UI layer.
     */
    @Query("""
        UPDATE screenshots
        SET ocrText = :editedOcrText,
            isUserEdited = 1,
            userEditedAt = :timestamp,
            embeddingByteArray = NULL,
            pipelineCode = ${PipelineFailureCode.NONE}
        WHERE id = :id
    """)
    suspend fun saveUserEditedOcrText(
        id: String,
        editedOcrText: String,
        timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Resets a permanently-failed screenshot back to OCR-pending state for a fresh retry.
     *
     * Clears [ocrText], [embeddingByteArray], [pipelineCode], and [ocrRetryCount] so the
     * row re-enters the pipeline from scratch on the next worker run.
     * Also clears [isUserEdited] — a refresh is a fresh machine OCR attempt, not a user edit.
     *
     * Only meaningful when [pipelineCode] = [PipelineFailureCode.OCR_FAILED]; calling it on
     * a healthy row is a no-op in practice but harmless.
     */
    @Query("""
        UPDATE screenshots
        SET ocrText = NULL,
            embeddingByteArray = NULL,
            pipelineCode = ${PipelineFailureCode.NONE},
            ocrRetryCount = 0,
            isUserEdited = 0
        WHERE id = :id
    """)
    suspend fun resetForOcrRetry(id: String)

    companion object {
        private const val TAG = "ScreenshotDao"
    }
}
