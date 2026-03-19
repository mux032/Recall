package com.recall.app.data.di

import com.recall.app.data.ocr.MlKitOcrProcessor
import com.recall.app.domain.usecase.OcrProcessor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {

    @Binds
    abstract fun bindOcrProcessor(
        mlKitOcrProcessor: MlKitOcrProcessor
    ): OcrProcessor
}
