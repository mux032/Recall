package com.recall.app.domain.usecase.searchhistory

import com.recall.app.domain.model.SearchHistoryItem
import com.recall.app.domain.repository.SearchHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetSearchHistoryUseCase @Inject constructor(
    private val searchHistoryRepository: SearchHistoryRepository
) {
    /**
     * Get all search history items as a Flow.
     * Emits whenever the database changes.
     */
    operator fun invoke(): Flow<List<SearchHistoryItem>> {
        return searchHistoryRepository.getAllHistory()
    }
}
