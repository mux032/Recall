package com.recall.app.di

import com.recall.app.data.repository.VisionRepositoryImpl
import com.recall.app.data.vision.MobileClipVisionProcessor
import com.recall.app.domain.repository.VisionRepository
import com.recall.app.domain.vision.VisionProcessor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VisionModule {
    
    @Binds
    @Singleton
    abstract fun bindVisionProcessor(
        impl: MobileClipVisionProcessor
    ): VisionProcessor
    
    @Binds
    @Singleton
    abstract fun bindVisionRepository(
        impl: VisionRepositoryImpl
    ): VisionRepository
    
    companion object {
        
        @Provides
        @Singleton
        fun provideVisionProcessor(): VisionProcessor {
            return MobileClipVisionProcessor()
        }
    }
}
