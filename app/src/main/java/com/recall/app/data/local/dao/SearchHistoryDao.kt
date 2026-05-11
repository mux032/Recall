package com.recall.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recall.app.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    /**
     * Get all search history items ordered by timestamp (newest first).
     * Returns Flow for reactive UI updates.
     */
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<SearchHistoryEntity>>

    /**
     * Get limited search history (for initial load).
     * @param limit Maximum number of items to return (default: 100)
     */
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int = 100): List<SearchHistoryEntity>

    /**
     * Insert new search history item.
     * Uses ABORT conflict strategy - caller handles deduplication.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(history: SearchHistoryEntity): Long

    /**
     * Insert or update search history item.
     * If query already exists, updates timestamp and iconType.
     * Uses REPLACE conflict strategy.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(history: SearchHistoryEntity): Long

    /**
     * Check if a query already exists in history.
     * @return true if query exists, false otherwise
     */
    @Query("SELECT EXISTS(SELECT 1 FROM search_history WHERE query = :query)")
    suspend fun queryExists(query: String): Boolean

    /**
     * Get existing history item by query.
     * Used to retrieve ID for updates.
     */
    @Query("SELECT * FROM search_history WHERE query = :query LIMIT 1")
    suspend fun getByQuery(query: String): SearchHistoryEntity?

    /**
     * Delete a single history item by ID.
     * @return number of rows deleted (0 or 1)
     */
    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /**
     * Delete a single history item by query.
     * @return number of rows deleted (0 or 1)
     */
    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteByQuery(query: String): Int

    /**
     * Delete all search history items.
     * @return number of rows deleted
     */
    @Query("DELETE FROM search_history")
    suspend fun deleteAll(): Int

    /**
     * Get total count of search history items.
     * Used for LRU eviction logic.
     */
    @Query("SELECT COUNT(*) FROM search_history")
    suspend fun getCount(): Int

    /**
     * Get oldest history item (for LRU eviction).
     */
    @Query("SELECT * FROM search_history ORDER BY timestamp ASC LIMIT 1")
    suspend fun getOldest(): SearchHistoryEntity?

    /**
     * Delete oldest items to maintain LRU limit.
     * @param keepCount Number of most recent items to keep
     * @return number of rows deleted
     */
    @Query("""
        DELETE FROM search_history
        WHERE id IN (
            SELECT id FROM search_history
            ORDER BY timestamp DESC
            LIMIT -1 OFFSET :keepCount
        )
    """)
    suspend fun deleteOldest(keepCount: Int): Int
}
