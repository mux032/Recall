package com.recall.app.data.repository

import com.recall.app.data.local.dao.SearchHistoryDao
import com.recall.app.data.local.entity.SearchHistoryEntity
import com.recall.app.domain.model.HistoryIconType
import com.recall.app.domain.model.SearchHistoryItem
import com.recall.app.domain.repository.SearchHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryRepositoryImpl @Inject constructor(
    private val searchHistoryDao: SearchHistoryDao
) : SearchHistoryRepository {

    companion object {
        private const val MAX_HISTORY_ITEMS = 100
    }

    override fun getAllHistory(): Flow<List<SearchHistoryItem>> {
        return searchHistoryDao.getAllHistory().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun addSearch(query: String): SearchHistoryItem {
        val trimmedQuery = query.trim()

        // Check if query already exists
        val existingItem = searchHistoryDao.getByQuery(trimmedQuery)

        return if (existingItem != null) {
            // Update existing item with new timestamp
            val updatedEntity = existingItem.copy(
                timestamp = System.currentTimeMillis()
            )
            searchHistoryDao.insertOrUpdate(updatedEntity)
            updatedEntity.toDomainModel()
        } else {
            // Create new history item
            val iconType = assignIconType(trimmedQuery)
            val newEntity = SearchHistoryEntity(
                id = UUID.randomUUID().toString(),
                query = trimmedQuery,
                timestamp = System.currentTimeMillis(),
                iconType = iconType.name
            )
            searchHistoryDao.insert(newEntity)

            // Enforce LRU limit
            enforceLruLimit()

            newEntity.toDomainModel()
        }
    }

    override suspend fun deleteHistoryItem(id: String): Boolean {
        return searchHistoryDao.deleteById(id) > 0
    }

    override suspend fun deleteHistoryItemByQuery(query: String): Boolean {
        return searchHistoryDao.deleteByQuery(query.trim()) > 0
    }

    override suspend fun clearAllHistory(): Int {
        return searchHistoryDao.deleteAll()
    }

    /**
     * Smart icon assignment based on query keywords.
     * Analyzes query content to determine appropriate icon.
     */
    private fun assignIconType(query: String): HistoryIconType {
        val lowerQuery = query.lowercase()

        // Receipt/Transaction keywords
        val receiptKeywords = listOf(
            "receipt", "receipts", "invoice", "bill", "payment", "purchase",
            "transaction", "order", "refund", "total", "amount", "paid",
            "coffee", "restaurant", "food", "grocery", "shopping", "uber", "lyft"
        )

        // Travel/Flight keywords
        val travelKeywords = listOf(
            "flight", "flights", "travel", "trip", "vacation", "hotel",
            "booking", "reservation", "airport", "airline", "ticket",
            "tokyo", "paris", "london", "new york", "destination"
        )

        // AI/Summary keywords
        val aiKeywords = listOf(
            "summarize", "summary", "ai", "generate", "create", "make",
            "news", "article", "report", "analyze", "analysis"
        )

        // Document keywords
        val documentKeywords = listOf(
            "document", "documents", "doc", "pdf", "file", "files",
            "tax", "form", "forms", "contract", "agreement", "legal",
            "2023", "2024", "2025", "year", "annual"
        )

        // Check for keyword matches
        return when {
            receiptKeywords.any { it in lowerQuery } -> HistoryIconType.RECEIPT_LONG
            travelKeywords.any { it in lowerQuery } -> HistoryIconType.FLIGHT_TAKEOFF
            aiKeywords.any { it in lowerQuery } -> HistoryIconType.AUTO_AWESOME
            documentKeywords.any { it in lowerQuery } -> HistoryIconType.DESCRIPTION
            else -> HistoryIconType.SEARCH
        }
    }

    /**
     * Enforce LRU (Least Recently Used) limit.
     * Deletes oldest items if count exceeds MAX_HISTORY_ITEMS.
     */
    private suspend fun enforceLruLimit() {
        val count = searchHistoryDao.getCount()
        if (count > MAX_HISTORY_ITEMS) {
            searchHistoryDao.deleteOldest(MAX_HISTORY_ITEMS)
        }
    }
}

/**
 * Extension function to convert entity to domain model.
 * Includes time label formatting.
 */
private fun SearchHistoryEntity.toDomainModel(): SearchHistoryItem {
    return SearchHistoryItem(
        id = id,
        query = query,
        timestamp = timestamp,
        iconType = HistoryIconType.valueOf(iconType),
        timeLabel = formatTimeLabel(timestamp)
    )
}

/**
 * Format timestamp to human-readable time label.
 * Examples: "2 minutes ago", "Yesterday, 4:12 PM", "Mar 15"
 */
private fun formatTimeLabel(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMillis = now - timestamp
    val diffMinutes = diffMillis / (60 * 1000)
    val diffHours = diffMillis / (60 * 60 * 1000)
    val diffDays = diffMillis / (24 * 60 * 60 * 1000)

    return when {
        // Less than 1 minute
        diffMinutes < 1 -> "Just now"

        // Today (within 24 hours)
        diffDays == 0L -> {
            when {
                diffMinutes < 60 -> "$diffMinutes minutes ago"
                diffHours == 1L -> "1 hour ago"
                else -> "$diffHours hours ago"
            }
        }

        // Yesterday
        diffDays == 1L -> {
            val hour = SimpleDateFormat("h:mm a", Locale.getDefault())
                .format(Date(timestamp))
            "Yesterday, $hour"
        }

        // This week (2-6 days)
        diffDays < 7L -> {
            val dayName = SimpleDateFormat("EEEE", Locale.getDefault())
                .format(Date(timestamp))
            "$dayName, ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))}"
        }

        // Older - show date
        else -> {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                .format(Date(timestamp))
        }
    }
}
