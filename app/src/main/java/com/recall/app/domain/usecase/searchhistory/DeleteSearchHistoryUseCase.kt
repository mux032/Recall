package com.recall.app.domain.usecase.searchhistory

import com.recall.app.domain.repository.SearchHistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteSearchHistoryUseCase @Inject constructor(
    private val searchHistoryRepository: SearchHistoryRepository
) {
    /**
     * Delete a single history item by ID.
     * @param id The item ID to delete
     * @return true if deleted, false if not found
     */
    suspend operator fun invoke(id: String): Boolean {
        return searchHistoryRepository.deleteHistoryItem(id)
    }
}
