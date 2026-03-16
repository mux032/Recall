package com.recall.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.recall.app.data.local.converter.Converters
import com.recall.app.data.local.dao.EmbeddingDao
import com.recall.app.data.local.dao.ModelDao
import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.local.entity.EmbeddingEntity
import com.recall.app.data.local.entity.ModelEntity
import com.recall.app.data.local.entity.ScreenshotEntity

/**
 * Main Room database for Recall app.
 * Stores screenshots, embeddings, and model metadata.
 */
@Database(
    entities = [
        ScreenshotEntity::class,
        EmbeddingEntity::class,
        ModelEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RecallDatabase : RoomDatabase() {
    
    abstract fun screenshotDao(): ScreenshotDao
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun modelDao(): ModelDao
    
    companion object {
        const val DATABASE_NAME = "recall_database"
    }
}
