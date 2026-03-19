package com.recall.app.data.di

import com.recall.app.data.repository.ScreenshotRepositoryImpl
import com.recall.app.domain.repository.ScreenshotRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import com.recall.app.data.nlp.OnnxEmbeddingGenerator
import com.recall.app.domain.usecase.EmbeddingGenerator

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindScreenshotRepository(
        screenshotRepositoryImpl: ScreenshotRepositoryImpl
    ): ScreenshotRepository
    
    @Binds
    @Singleton
    abstract fun bindEmbeddingGenerator(
        onnxEmbeddingGenerator: OnnxEmbeddingGenerator
    ): EmbeddingGenerator
}
