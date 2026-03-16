package com.recall.app.di

import com.recall.app.data.ocr.MlKitOcrProcessor
import com.recall.app.data.repository.OcrRepositoryImpl
import com.recall.app.domain.ocr.OcrProcessor
import com.recall.app.domain.repository.OcrRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {

    @Binds
    @Singleton
    abstract fun bindOcrProcessor(
        impl: MlKitOcrProcessor
    ): OcrProcessor

    @Binds
    @Singleton
    abstract fun bindOcrRepository(
        impl: OcrRepositoryImpl
    ): OcrRepository
}
