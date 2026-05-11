package com.recall.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing search history items.
 *
 * Features:
 * - Unique ID for each search
 * - Query text with full-text search capability
 * - Timestamp for chronological ordering
 * - Icon type for visual categorization
 * - Index on timestamp for efficient ORDER BY queries
 * - Index on query for deduplication lookups
 */
@Entity(
    tableName = "search_history",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["query"], unique = false)
    ]
)
data class SearchHistoryEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(typeAffinity = ColumnInfo.TEXT)
    val query: String,

    @ColumnInfo(typeAffinity = ColumnInfo.INTEGER)
    val timestamp: Long,

    @ColumnInfo(typeAffinity = ColumnInfo.TEXT)
    val iconType: String, // Store as string enum (RECEIPT_LONG, FLIGHT_TAKEOFF, etc.)

    @ColumnInfo(typeAffinity = ColumnInfo.INTEGER)
    val isPinned: Boolean = false
)
