package com.recall.app.data.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.recall.app.data.local.MIGRATION_1_2
import com.recall.app.data.local.RecallDatabase
import com.recall.app.data.local.dao.ScreenshotDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideRecallDatabase(@ApplicationContext context: Context): RecallDatabase {
        return Room.databaseBuilder(
            context,
            RecallDatabase::class.java,
            RecallDatabase.DATABASE_NAME
        )
        .addMigrations(MIGRATION_1_2)
        .build()
    }

    @Provides
    @Singleton
    fun provideScreenshotDao(database: RecallDatabase): ScreenshotDao {
        return database.screenshotDao
    }
}
