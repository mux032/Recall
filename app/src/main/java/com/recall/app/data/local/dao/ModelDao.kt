package com.recall.app.data.local.dao

import androidx.room.*
import com.recall.app.data.local.entity.ModelEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for model tracking operations.
 */
@Dao
interface ModelDao {
    
    @Query("SELECT * FROM models WHERE modelName = :modelName")
    suspend fun getModelByName(modelName: String): ModelEntity?
    
    @Query("SELECT * FROM models WHERE downloaded = 1")
    fun getDownloadedModels(): Flow<List<ModelEntity>>
    
    @Query("SELECT * FROM models")
    fun getAllModels(): Flow<List<ModelEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: ModelEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(models: List<ModelEntity>)
    
    @Update
    suspend fun update(model: ModelEntity)
    
    @Delete
    suspend fun delete(model: ModelEntity)
    
    @Query("UPDATE models SET downloaded = :downloaded, downloadDate = :downloadDate WHERE modelName = :modelName")
    suspend fun updateDownloadStatus(modelName: String, downloaded: Boolean, downloadDate: Long? = null)
}
