package com.recall.app.domain.model

data class SearchHistoryItem(
    val id: String,
    val query: String,
    val timestamp: Long,
    val iconType: HistoryIconType = HistoryIconType.DESCRIPTION,
    val timeLabel: String
)

enum class HistoryIconType {
    RECEIPT_LONG,
    FLIGHT_TAKEOFF,
    AUTO_AWESOME,
    DESCRIPTION,
    SEARCH
}
