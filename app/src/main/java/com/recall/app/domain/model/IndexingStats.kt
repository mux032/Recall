package com.recall.app.domain.model

/**
 * Snapshot of background indexing progress for the [ProcessingStatusBanner] (Issue #106).
 *
 * @param total            Total number of screenshots known to the app.
 * @param ocrDoneCount     Screenshots that have extracted OCR text (ocrText != null).
 * @param embeddingDoneCount Screenshots that have a generated embedding vector (embeddingByteArray != null).
 */
data class IndexingStats(
    val total: Int,
    val ocrDoneCount: Int,
    val embeddingDoneCount: Int
) {
    /** Fraction of screenshots with OCR text. 0.0–1.0. Returns 1.0 when total is 0. */
    val ocrProgress: Float
        get() = if (total == 0) 1f else ocrDoneCount.toFloat() / total

    /** Fraction of screenshots with AI embedding. 0.0–1.0. Returns 1.0 when total is 0. */
    val embeddingProgress: Float
        get() = if (total == 0) 1f else embeddingDoneCount.toFloat() / total

    /** True when all screenshots have OCR text. */
    val isOcrComplete: Boolean
        get() = total == 0 || ocrDoneCount >= total

    /** True when all screenshots have AI embeddings. */
    val isEmbeddingComplete: Boolean
        get() = total == 0 || embeddingDoneCount >= total

    /** True when both OCR and embedding are fully done — banner should not show. */
    val isFullyIndexed: Boolean
        get() = isOcrComplete && isEmbeddingComplete

    /** Number of screenshots still needing OCR. */
    val ocrPendingCount: Int
        get() = (total - ocrDoneCount).coerceAtLeast(0)

    /** Number of screenshots still needing embedding. */
    val embeddingPendingCount: Int
        get() = (total - embeddingDoneCount).coerceAtLeast(0)

    companion object {
        /** Sentinel used as initial value before DB emits. Treated as fully indexed — no banner shown. */
        val IDLE = IndexingStats(total = 0, ocrDoneCount = 0, embeddingDoneCount = 0)
    }
}
