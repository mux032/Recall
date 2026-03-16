package com.recall.app.di

import android.content.Context
import com.recall.app.data.repository.ScreenshotDetectionRepositoryImpl
import com.recall.app.data.service.ScreenshotContentObserver
import com.recall.app.data.service.ScreenshotDetectionService
import com.recall.app.domain.repository.ScreenshotDetectionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DetectionModule {
    
    @Binds
    @Singleton
    abstract fun bindScreenshotDetectionRepository(
        impl: ScreenshotDetectionRepositoryImpl
    ): ScreenshotDetectionRepository
    
    companion object {
        
        @Provides
        @Singleton
        fun provideScreenshotDetectionService(
            @ApplicationContext context: Context,
            detectionRepository: ScreenshotDetectionRepositoryImpl,
            contentObserver: ScreenshotContentObserver
        ): ScreenshotDetectionService {
            return ScreenshotDetectionService(context, detectionRepository, contentObserver)
        }
        
        @Provides
        @Singleton
        fun provideScreenshotContentObserver(
            @ApplicationContext context: Context,
            detectionRepository: ScreenshotDetectionRepositoryImpl
        ): ScreenshotContentObserver {
            return ScreenshotContentObserver(context, detectionRepository)
        }
    }
}
