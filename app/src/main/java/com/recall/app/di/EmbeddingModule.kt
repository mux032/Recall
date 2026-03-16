package com.recall.app.di

import com.recall.app.data.embedding.E5SmallEmbeddingGenerator
import com.recall.app.data.repository.EmbeddingRepositoryImpl
import com.recall.app.data.vector.VectorIndex
import com.recall.app.domain.embedding.EmbeddingGenerator
import com.recall.app.domain.repository.EmbeddingRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EmbeddingModule {
    
    @Binds
    @Singleton
    abstract fun bindEmbeddingGenerator(
        impl: E5SmallEmbeddingGenerator
    ): EmbeddingGenerator
    
    @Binds
    @Singleton
    abstract fun bindEmbeddingRepository(
        impl: EmbeddingRepositoryImpl
    ): EmbeddingRepository
    
    companion object {

        @Provides
        @Singleton
        fun provideVectorIndex(): VectorIndex {
            return VectorIndex()
        }
    }
}
