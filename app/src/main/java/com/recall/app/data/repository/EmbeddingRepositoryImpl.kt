package com.recall.app.data.repository

import com.recall.app.data.vector.EmbeddingItem
import com.recall.app.data.vector.SearchMetadata
import com.recall.app.data.vector.VectorIndex
import com.recall.app.domain.embedding.EmbeddingGenerator
import com.recall.app.domain.model.Embedding
import com.recall.app.domain.model.SearchQuery
import com.recall.app.domain.model.SearchResult
import com.recall.app.domain.repository.EmbeddingMetadata
import com.recall.app.domain.repository.EmbeddingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of embedding repository.
 */
@Singleton
class EmbeddingRepositoryImpl @Inject constructor(
    private val embeddingGenerator: EmbeddingGenerator,
    private val vectorIndex: VectorIndex
) : EmbeddingRepository {
    
    override suspend fun generateAndStoreEmbedding(
        id: Long,
        text: String,
        metadata: EmbeddingMetadata
    ): Embedding = withContext(Dispatchers.Default) {
        // Prepare text for embedding (combine all searchable fields)
        val embeddingText = prepareTextForEmbedding(metadata)
        
        // Generate embedding
        val embedding = embeddingGenerator.generateEmbedding(embeddingText)
        
        // Store in vector index
        val searchMetadata = SearchMetadata(
            filePath = metadata.filePath,
            summary = metadata.summary,
            tags = metadata.tags,
            category = metadata.category,
            timestamp = metadata.timestamp
        )
        
        vectorIndex.addEmbedding(id, embedding, searchMetadata)
        
        embedding
    }
    
    override suspend fun search(query: SearchQuery): List<SearchResult> = withContext(Dispatchers.Default) {
        // Generate embedding for query
        val queryEmbedding = embeddingGenerator.generateEmbedding(query.query)
        
        // Search vector index
        val results = vectorIndex.search(
            queryEmbedding = queryEmbedding,
            limit = query.limit,
            minSimilarity = query.minSimilarity
        )
        
        // Apply filters
        results.filter { result ->
            // Category filter
            if (query.category != null && result.category != query.category) {
                return@filter false
            }
            
            // Date range filter
            if (query.startDate != null && result.timestamp < query.startDate) {
                return@filter false
            }
            if (query.endDate != null && result.timestamp > query.endDate) {
                return@filter false
            }
            
            true
        }
    }
    
    override suspend fun searchByText(
        query: String,
        limit: Int
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        search(SearchQuery(query = query, limit = limit))
    }
    
    override suspend fun removeEmbedding(id: Long) = withContext(Dispatchers.Default) {
        vectorIndex.removeEmbedding(id)
    }
    
    override fun getEmbeddingCount(): Int {
        return vectorIndex.getSize()
    }
    
    override suspend fun rebuildIndex() = withContext(Dispatchers.Default) {
        vectorIndex.clear()
        // In production, would reload all embeddings from database
    }
    
    /**
     * Prepare text for embedding by combining all searchable fields.
     */
    private fun prepareTextForEmbedding(metadata: EmbeddingMetadata): String {
        val parts = mutableListOf<String>()
        
        // Add summary (most important)
        metadata.summary?.let { parts.add(it) }
        
        // Add tags
        metadata.tags?.let { parts.add(it) }
        
        // Add category
        metadata.category?.let { parts.add(it) }
        
        // Add OCR text (full text content)
        metadata.ocrText?.let { parts.add(it) }
        
        return parts.joinToString(" ")
    }
}
