package com.recall.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.recall.app.data.local.entity.ScreenshotEntity
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

    @Query("SELECT * FROM screenshots WHERE id = :id")
    suspend fun getScreenshotById(id: String): ScreenshotEntity?

    @Query("SELECT * FROM screenshots WHERE filePath = :filePath LIMIT 1")
    suspend fun getScreenshotByPath(filePath: String): ScreenshotEntity?

    @Query("SELECT * FROM screenshots WHERE id IN (:ids)")
    suspend fun getScreenshotsByIds(ids: List<String>): List<ScreenshotEntity>

    // FTS4 query matching against the ocrText 
    @Query("""
        SELECT screenshots.* 
        FROM screenshots 
        JOIN screenshots_fts ON screenshots.id = screenshots_fts.docid 
        WHERE screenshots_fts MATCH :query
    """)
    suspend fun searchFts(query: String): List<ScreenshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(screenshot: ScreenshotEntity)

    @Update
    suspend fun update(screenshot: ScreenshotEntity)

    @Query("DELETE FROM screenshots WHERE id = :id")
    suspend fun deleteById(id: String)
}
