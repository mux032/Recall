package com.recall.app.data.vector

import com.recall.app.domain.model.Embedding
import com.recall.app.domain.model.SearchResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FAISS-based vector index for similarity search.
 * Supports adding embeddings and searching for similar vectors.
 */
@Singleton
class VectorIndex @Inject constructor() {
    
    // In-memory index for MVP (FAISS would be integrated in production)
    private val embeddings = mutableMapOf<Long, Embedding>()
    private val metadata = mutableMapOf<Long, SearchMetadata>()
    
    private var indexSize = 0
    
    /**
     * Add embedding to index.
     */
    fun addEmbedding(id: Long, embedding: Embedding, metadata: SearchMetadata) {
        embeddings[id] = embedding.normalize()
        this.metadata[id] = metadata
        indexSize++
    }
    
    /**
     * Add multiple embeddings to index.
     */
    fun addEmbeddings(items: List<EmbeddingItem>) {
        items.forEach { item ->
            addEmbedding(item.id, item.embedding, item.metadata)
        }
    }
    
    /**
     * Search for similar embeddings.
     */
    fun search(queryEmbedding: Embedding, limit: Int = 20, minSimilarity: Float = 0.5f): List<SearchResult> {
        val normalizedQuery = queryEmbedding.normalize()
        
        val results = embeddings.mapNotNull { (id, embedding) ->
            val similarity = normalizedQuery.cosineSimilarity(embedding)
            
            if (similarity >= minSimilarity) {
                val meta = metadata[id] ?: return@mapNotNull null
                SearchResult(
                    screenshotId = id,
                    filePath = meta.filePath,
                    summary = meta.summary,
                    tags = meta.tags,
                    category = meta.category,
                    timestamp = meta.timestamp,
                    similarityScore = similarity,
                    matchedTerms = emptyList()
                )
            } else {
                null
            }
        }
        
        // Sort by similarity (descending) and limit results
        return results
            .sortedByDescending { it.similarityScore }
            .take(limit)
    }
    
    /**
     * Remove embedding from index.
     */
    fun removeEmbedding(id: Long) {
        embeddings.remove(id)
        metadata.remove(id)
        indexSize--
    }
    
    /**
     * Clear all embeddings from index.
     */
    fun clear() {
        embeddings.clear()
        metadata.clear()
        indexSize = 0
    }
    
    /**
     * Get index size.
     */
    fun getSize(): Int = indexSize
    
    /**
     * Check if index contains embedding.
     */
    fun contains(id: Long): Boolean = embeddings.containsKey(id)
    
    /**
     * Get embedding by ID.
     */
    fun getEmbedding(id: Long): Embedding? = embeddings[id]
}

/**
 * Metadata associated with an embedding.
 */
data class SearchMetadata(
    val filePath: String,
    val summary: String?,
    val tags: String?,
    val category: String?,
    val timestamp: Long
)

/**
 * Complete embedding item with metadata.
 */
data class EmbeddingItem(
    val id: Long,
    val embedding: Embedding,
    val metadata: SearchMetadata
)
