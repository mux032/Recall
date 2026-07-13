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

@Database(
    entities = [
        ScreenshotEntity::class,
        FtsScreenshotEntity::class,
        SearchHistoryEntity::class
    ],
    version = 1,
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
