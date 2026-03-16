package com.recall.app.data.local.dao

import androidx.room.*
import com.recall.app.data.local.entity.ScreenshotEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for screenshot operations.
 */
@Dao
interface ScreenshotDao {
    
    @Query("SELECT * FROM screenshots ORDER BY timestamp DESC")
    fun getAllScreenshots(): Flow<List<ScreenshotEntity>>
    
    @Query("SELECT * FROM screenshots ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentScreenshots(limit: Int = 20): Flow<List<ScreenshotEntity>>
    
    @Query("SELECT * FROM screenshots WHERE id = :id")
    suspend fun getScreenshotById(id: Long): ScreenshotEntity?
    
    @Query("SELECT * FROM screenshots WHERE filePath = :filePath")
    suspend fun getScreenshotByPath(filePath: String): ScreenshotEntity?
    
    @Query("SELECT * FROM screenshots WHERE isIndexed = 1 ORDER BY timestamp DESC")
    fun getIndexedScreenshots(): Flow<List<ScreenshotEntity>>
    
    @Query("SELECT * FROM screenshots WHERE processingStatus = :status")
    fun getScreenshotsByStatus(status: ScreenshotEntity.ProcessingStatus): Flow<List<ScreenshotEntity>>
    
    @Query("SELECT * FROM screenshots WHERE category = :category ORDER BY timestamp DESC")
    fun getScreenshotsByCategory(category: String): Flow<List<ScreenshotEntity>>
    
    @Query("SELECT * FROM screenshots WHERE ocrText LIKE :query OR summary LIKE :query ORDER BY timestamp DESC")
    suspend fun searchByText(query: String): List<ScreenshotEntity>
    
    @Query("SELECT DISTINCT category FROM screenshots WHERE category IS NOT NULL")
    fun getAllCategories(): Flow<List<String>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(screenshot: ScreenshotEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(screenshots: List<ScreenshotEntity>)
    
    @Update
    suspend fun update(screenshot: ScreenshotEntity)
    
    @Delete
    suspend fun delete(screenshot: ScreenshotEntity)
    
    @Query("DELETE FROM screenshots WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM screenshots")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM screenshots")
    fun getScreenshotCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM screenshots WHERE isIndexed = 1")
    fun getIndexedCount(): Flow<Int>
}
