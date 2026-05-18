package com.recall.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.recall.app.data.local.converter.ProcessingStateConverter
import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.local.dao.SearchHistoryDao
import com.recall.app.data.local.entity.FtsScreenshotEntity
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.data.local.entity.SearchHistoryEntity

/**
 * Recall Database
 *
 * ⚠️ DEVELOPMENT WARNING: This database uses fallbackToDestructiveMigration()
 * which WILL DELETE ALL DATA when upgrading between versions.
 *
 * Current version: 5 (added embeddingRetryCount column to ScreenshotEntity)
 * Previous version: 4 (ProcessingState TypeConverter — processingState column type unchanged)
 *
 * Before releasing to production:
 * - Create proper Room migrations for all entities
 * - Replace fallbackToDestructiveMigration() with explicit migrations
 * - Test migration path from version 1 to current version
 */
@Database(
    entities = [
        ScreenshotEntity::class,
        FtsScreenshotEntity::class,
        SearchHistoryEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(ProcessingStateConverter::class)
abstract class RecallDatabase : RoomDatabase() {
    abstract val screenshotDao: ScreenshotDao
    abstract val searchHistoryDao: SearchHistoryDao

    companion object {
        const val DATABASE_NAME = "recall_db"
    }
}
