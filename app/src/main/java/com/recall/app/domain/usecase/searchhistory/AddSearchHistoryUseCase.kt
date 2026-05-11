package com.recall.app.domain.usecase.searchhistory

import com.recall.app.domain.model.SearchHistoryItem
import com.recall.app.domain.repository.SearchHistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddSearchHistoryUseCase @Inject constructor(
    private val searchHistoryRepository: SearchHistoryRepository
) {
    /**
     * Save a search query to history.
     * Handles deduplication and LRU eviction automatically.
     *
     * @param query The search query to save
     * @return The saved SearchHistoryItem
     */
    suspend operator fun invoke(query: String): SearchHistoryItem {
        require(query.isNotBlank()) { "Query cannot be blank" }
        return searchHistoryRepository.addSearch(query)
    }
}
