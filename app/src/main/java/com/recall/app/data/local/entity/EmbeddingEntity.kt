package com.recall.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Database entity for storing embedding vectors.
 */
@Entity(tableName = "embeddings")
data class EmbeddingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val vector: FloatArray,
    
    val dimension: Int = 384,
    
    val modelVersion: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as EmbeddingEntity
        
        if (id != other.id) return false
        if (dimension != other.dimension) return false
        if (modelVersion != other.modelVersion) return false
        if (!vector.contentEquals(other.vector)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + dimension
        result = 31 * result + modelVersion.hashCode()
        return result
    }
}
