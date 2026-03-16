package com.recall.app.data.local.converter

import androidx.room.TypeConverter
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.data.local.entity.EmbeddingEntity
import com.recall.app.data.local.entity.ModelEntity

/**
 * Type converters for Room database.
 */
class Converters {
    
    @TypeConverter
    fun fromProcessingStatus(status: ScreenshotEntity.ProcessingStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toProcessingStatus(status: String): ScreenshotEntity.ProcessingStatus {
        return ScreenshotEntity.ProcessingStatus.valueOf(status)
    }
    
    @TypeConverter
    fun fromFloatArray(array: FloatArray): String {
        return array.joinToString(",") { it.toString() }
    }
    
    @TypeConverter
    fun toFloatArray(data: String): FloatArray {
        if (data.isEmpty()) return floatArrayOf()
        return data.split(",").map { it.toFloat() }.toFloatArray()
    }
    
    @TypeConverter
    fun fromModelTier(tier: ModelEntity.ModelTier): String {
        return tier.name
    }
    
    @TypeConverter
    fun toModelTier(tier: String): ModelEntity.ModelTier {
        return ModelEntity.ModelTier.valueOf(tier)
    }
}
