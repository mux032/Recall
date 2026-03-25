package com.recall.app.data.di

import android.content.Context
import androidx.room.Room
import com.recall.app.data.local.RecallDatabase
import com.recall.app.data.local.dao.ScreenshotDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Database module with destructive migration fallback for development.
 * Since the app is still in DEVELOPMENT ONLY with no production users,
 * we use .fallbackToDestructiveMigration() instead of complex manual migrations.
 * This will recreate the database schema if migration fails, simplifying development.
 */
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
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideScreenshotDao(database: RecallDatabase): ScreenshotDao {
        return database.screenshotDao
    }
}
