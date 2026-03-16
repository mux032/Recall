package com.recall.app.domain.repository

/**
 * Metadata for embedding storage.
 */
data class EmbeddingMetadata(
    val filePath: String,
    val summary: String?,
    val tags: String?,
    val category: String?,
    val timestamp: Long,
    val ocrText: String?
)
