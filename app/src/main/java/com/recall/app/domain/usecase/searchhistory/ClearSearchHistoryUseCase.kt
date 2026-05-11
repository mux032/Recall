package com.recall.app.domain.usecase.searchhistory

import com.recall.app.domain.repository.SearchHistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClearSearchHistoryUseCase @Inject constructor(
    private val searchHistoryRepository: SearchHistoryRepository
) {
    /**
     * Clear all search history.
     * @return number of items deleted
     */
    suspend operator fun invoke(): Int {
        return searchHistoryRepository.clearAllHistory()
    }
}
