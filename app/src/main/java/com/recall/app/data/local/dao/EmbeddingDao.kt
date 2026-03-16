package com.recall.app.data.local.dao

import androidx.room.*
import com.recall.app.data.local.entity.EmbeddingEntity

/**
 * Data Access Object for embedding operations.
 */
@Dao
interface EmbeddingDao {
    
    @Query("SELECT * FROM embeddings WHERE id = :id")
    suspend fun getEmbeddingById(id: Long): EmbeddingEntity?
    
    @Query("SELECT * FROM embeddings")
    suspend fun getAllEmbeddings(): List<EmbeddingEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: EmbeddingEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(embeddings: List<EmbeddingEntity>)
    
    @Update
    suspend fun update(embedding: EmbeddingEntity)
    
    @Delete
    suspend fun delete(embedding: EmbeddingEntity)
    
    @Query("DELETE FROM embeddings WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM embeddings")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM embeddings")
    suspend fun getEmbeddingCount(): Int
}
