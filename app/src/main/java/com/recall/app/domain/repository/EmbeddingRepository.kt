package com.recall.app.domain.repository

import com.recall.app.domain.model.Embedding
import com.recall.app.domain.model.SearchQuery
import com.recall.app.domain.model.SearchResult

/**
 * Repository for embedding and search operations.
 */
interface EmbeddingRepository {
    
    /**
     * Generate and store embedding for text.
     */
    suspend fun generateAndStoreEmbedding(
        id: Long,
        text: String,
        metadata: EmbeddingMetadata
    ): Embedding
    
    /**
     * Search for similar embeddings.
     */
    suspend fun search(query: SearchQuery): List<SearchResult>
    
    /**
     * Search by text query.
     */
    suspend fun searchByText(
        query: String,
        limit: Int = 20
    ): List<SearchResult>
    
    /**
     * Remove embedding.
     */
    suspend fun removeEmbedding(id: Long)
    
    /**
     * Get embedding count.
     */
    fun getEmbeddingCount(): Int
    
    /**
     * Rebuild index.
     */
    suspend fun rebuildIndex()
}
