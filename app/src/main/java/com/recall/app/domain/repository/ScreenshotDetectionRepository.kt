package com.recall.app.domain.repository

import com.recall.app.data.local.entity.ScreenshotEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for screenshot detection operations.
 */
interface ScreenshotDetectionRepository {
    
    /**
     * Flow of newly detected screenshots.
     */
    val newScreenshots: Flow<ScreenshotEntity>
    
    /**
     * Start monitoring screenshot folders.
     */
    fun startMonitoring()
    
    /**
     * Stop monitoring screenshot folders.
     */
    fun stopMonitoring()
    
    /**
     * Check if monitoring is active.
     */
    fun isMonitoring(): Boolean
    
    /**
     * Get list of screenshot folders to monitor.
     */
    fun getScreenshotFolders(): List<String>
}
