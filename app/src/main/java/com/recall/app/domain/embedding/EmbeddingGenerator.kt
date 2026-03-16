package com.recall.app.domain.embedding

import com.recall.app.domain.model.Embedding

/**
 * Interface for generating embeddings from text.
 */
interface EmbeddingGenerator {
    
    /**
     * Generate embedding from text.
     */
    suspend fun generateEmbedding(text: String): Embedding
    
    /**
     * Generate embeddings from multiple texts (batch).
     */
    suspend fun generateEmbeddings(texts: List<String>): List<Embedding>
    
    /**
     * Get the embedding dimension.
     */
    fun getDimension(): Int
    
    /**
     * Get the model name.
     */
    fun getModelName(): String
    
    /**
     * Check if the generator is available.
     */
    fun isAvailable(): Boolean
    
    /**
     * Release resources.
     */
    fun close()
}
