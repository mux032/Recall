package com.recall.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.local.entity.FtsScreenshotEntity
import com.recall.app.data.local.entity.ScreenshotEntity

@Database(
    entities = [ScreenshotEntity::class, FtsScreenshotEntity::class],
    version = 2,
    exportSchema = false
)
abstract class RecallDatabase : RoomDatabase() {
    abstract val screenshotDao: ScreenshotDao

    companion object {
        const val DATABASE_NAME = "recall_db"
    }
}
