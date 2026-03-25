package com.recall.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.local.entity.FtsScreenshotEntity
import com.recall.app.data.local.entity.ScreenshotEntity

/**
 * DEVELOPMENT PHASE: Database version reset to 1 for fresh start.
 * Using fallbackToDestructiveMigration() - no manual migrations needed.
 */
@Database(
    entities = [ScreenshotEntity::class, FtsScreenshotEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RecallDatabase : RoomDatabase() {
    abstract val screenshotDao: ScreenshotDao

    companion object {
        const val DATABASE_NAME = "recall_db"
    }
}
