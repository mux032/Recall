package com.recall.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
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
 * Current version: 3 (added appName column to ScreenshotEntity)
 * Previous version: 2 (added SearchHistoryEntity)
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
    version = 3,
    exportSchema = false
)
abstract class RecallDatabase : RoomDatabase() {
    abstract val screenshotDao: ScreenshotDao
    abstract val searchHistoryDao: SearchHistoryDao

    companion object {
        const val DATABASE_NAME = "recall_db"
    }
}
