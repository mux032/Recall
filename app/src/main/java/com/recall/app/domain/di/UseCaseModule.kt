package com.recall.app.domain.di

import com.recall.app.domain.repository.SearchHistoryRepository
import com.recall.app.domain.usecase.searchhistory.AddSearchHistoryUseCase
import com.recall.app.domain.usecase.searchhistory.ClearSearchHistoryUseCase
import com.recall.app.domain.usecase.searchhistory.DeleteSearchHistoryUseCase
import com.recall.app.domain.usecase.searchhistory.GetSearchHistoryUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideAddSearchHistoryUseCase(repository: SearchHistoryRepository): AddSearchHistoryUseCase {
        return AddSearchHistoryUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideGetSearchHistoryUseCase(repository: SearchHistoryRepository): GetSearchHistoryUseCase {
        return GetSearchHistoryUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideDeleteSearchHistoryUseCase(repository: SearchHistoryRepository): DeleteSearchHistoryUseCase {
        return DeleteSearchHistoryUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideClearSearchHistoryUseCase(repository: SearchHistoryRepository): ClearSearchHistoryUseCase {
        return ClearSearchHistoryUseCase(repository)
    }
}
