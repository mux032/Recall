package com.recall.app.di

import android.content.Context
import androidx.room.Room
import com.recall.app.data.local.database.RecallDatabase
import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.local.dao.EmbeddingDao
import com.recall.app.data.local.dao.ModelDao
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
    fun provideDatabase(
        @ApplicationContext context: Context
    ): RecallDatabase {
        return Room.databaseBuilder(
            context,
            RecallDatabase::class.java,
            "recall_database"
        )
            .fallbackToDestructiveMigration() // For MVP, will add proper migrations later
            .build()
    }
    
    @Provides
    @Singleton
    fun provideScreenshotDao(database: RecallDatabase): ScreenshotDao {
        return database.screenshotDao()
    }
    
    @Provides
    @Singleton
    fun provideEmbeddingDao(database: RecallDatabase): EmbeddingDao {
        return database.embeddingDao()
    }
    
    @Provides
    @Singleton
    fun provideModelDao(database: RecallDatabase): ModelDao {
        return database.modelDao()
    }
}
