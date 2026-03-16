package com.recall.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Database entity for tracking downloaded AI models.
 */
@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey
    val modelName: String,
    
    val version: String,
    
    val size: Long,
    
    val downloaded: Boolean = false,
    
    val downloadDate: Long? = null,
    
    val modelTier: ModelTier = ModelTier.STANDARD
) {
    enum class ModelTier {
        LIGHTWEIGHT,  // For 4GB RAM devices
        STANDARD,     // For 6GB RAM devices
        PREMIUM       // For 8GB+ RAM devices
    }
}
