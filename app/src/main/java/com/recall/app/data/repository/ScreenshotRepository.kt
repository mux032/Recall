package com.recall.app.data.repository

import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.local.entity.ScreenshotEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for screenshot data operations.
 */
@Singleton
class ScreenshotRepository @Inject constructor(
    private val screenshotDao: ScreenshotDao
) {
    
    val allScreenshots: Flow<List<ScreenshotEntity>> = screenshotDao.getAllScreenshots()
    
    fun getRecentScreenshots(limit: Int = 20): Flow<List<ScreenshotEntity>> {
        return screenshotDao.getRecentScreenshots(limit)
    }
    
    fun getIndexedScreenshots(): Flow<List<ScreenshotEntity>> {
        return screenshotDao.getIndexedScreenshots()
    }
    
    fun getScreenshotsByCategory(category: String): Flow<List<ScreenshotEntity>> {
        return screenshotDao.getScreenshotsByCategory(category)
    }
    
    suspend fun getScreenshotById(id: Long): ScreenshotEntity? {
        return screenshotDao.getScreenshotById(id)
    }
    
    suspend fun getScreenshotByPath(filePath: String): ScreenshotEntity? {
        return screenshotDao.getScreenshotByPath(filePath)
    }
    
    suspend fun searchByText(query: String): List<ScreenshotEntity> {
        return screenshotDao.searchByText("%$query%")
    }
    
    fun getAllCategories(): Flow<List<String>> {
        return screenshotDao.getAllCategories()
    }
    
    suspend fun insertScreenshot(screenshot: ScreenshotEntity): Long {
        return screenshotDao.insert(screenshot)
    }
    
    suspend fun updateScreenshot(screenshot: ScreenshotEntity) {
        screenshotDao.update(screenshot)
    }
    
    suspend fun deleteScreenshot(screenshot: ScreenshotEntity) {
        screenshotDao.delete(screenshot)
    }
    
    suspend fun deleteScreenshotById(id: Long) {
        screenshotDao.deleteById(id)
    }
    
    suspend fun deleteAllScreenshots() {
        screenshotDao.deleteAll()
    }
    
    fun getScreenshotCount(): Flow<Int> {
        return screenshotDao.getScreenshotCount()
    }
    
    fun getIndexedCount(): Flow<Int> {
        return screenshotDao.getIndexedCount()
    }

    fun getAllScreenshotsForProcessing(): Flow<List<ScreenshotEntity>> {
        return screenshotDao.getAllScreenshots()
    }
}
