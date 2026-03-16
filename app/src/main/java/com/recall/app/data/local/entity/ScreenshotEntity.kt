package com.recall.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Database entity for storing screenshot metadata.
 */
@Entity(tableName = "screenshots")
data class ScreenshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val filePath: String,
    
    val timestamp: Long,
    
    val processedAt: Long? = null,
    
    val ocrText: String? = null,
    
    val summary: String? = null,
    
    val tags: String? = null, // Comma-separated tags
    
    val embeddingId: Long? = null,
    
    val category: String? = null,
    
    val isIndexed: Boolean = false,
    
    val processingStatus: ProcessingStatus = ProcessingStatus.PENDING
) {
    enum class ProcessingStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
