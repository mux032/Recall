package com.recall.app.data.worker

data class IndexingProgress(
    val completed: Int,
    val total: Int
) {
    val isComplete: Boolean get() = total > 0 && completed >= total
    val percentComplete: Int get() = if (total == 0) 0 else (completed * 100 / total).coerceIn(0, 100)
}
