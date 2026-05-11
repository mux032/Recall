package com.recall.app.domain.repository

import com.recall.app.domain.model.SearchHistoryItem
import kotlinx.coroutines.flow.Flow

interface SearchHistoryRepository {

    /**
     * Get all search history items as a Flow.
     * Emits whenever the database changes.
     */
    fun getAllHistory(): Flow<List<SearchHistoryItem>>

    /**
     * Add a new search to history.
     * Handles deduplication and LRU eviction.
     * @param query The search query to save
     * @return The saved SearchHistoryItem
     */
    suspend fun addSearch(query: String): SearchHistoryItem

    /**
     * Delete a single history item.
     * @param id The item ID to delete
     * @return true if deleted, false if not found
     */
    suspend fun deleteHistoryItem(id: String): Boolean

    /**
     * Delete a history item by query.
     * @param query The query to delete
     * @return true if deleted, false if not found
     */
    suspend fun deleteHistoryItemByQuery(query: String): Boolean

    /**
     * Clear all search history.
     * @return number of items deleted
     */
    suspend fun clearAllHistory(): Int
}
