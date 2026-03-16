package com.recall.app.domain.model

/**
 * Domain model representing a screenshot with its metadata.
 */
data class Screenshot(
    val id: Long,
    val filePath: String,
    val timestamp: Long,
    val ocrText: String?,
    val summary: String?,
    val tags: List<String>?,
    val category: String?,
    val isIndexed: Boolean,
    val thumbnailPath: String? = null
) {
    val displayDate: String
        get() {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            return when {
                diff < ONE_DAY_MS -> "Today"
                diff < 2 * ONE_DAY_MS -> "Yesterday"
                diff < 7 * ONE_DAY_MS -> "Last Week"
                diff < 30 * ONE_DAY_MS -> "Last Month"
                else -> "Older"
            }
        }
    
    companion object {
        private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
    }
}
