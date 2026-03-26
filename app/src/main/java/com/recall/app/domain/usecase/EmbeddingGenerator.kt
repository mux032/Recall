package com.recall.app.domain.usecase

/**
 * Interface for generating text embeddings.
 * Implementations should be @Singleton to avoid recreating heavy ML models.
 */
interface EmbeddingGenerator {
    /**
     * Converts the given string of text into a fixed-size semantic vector embedding.
     * @param text The input text to be embedded.
     * @return A FloatArray representing the vector embedding, or null if generation fails.
     */
    suspend fun generate(text: String): FloatArray?
    
    /**
     * Release resources when the generator is no longer needed.
     * Call this in Application.onTerminate() or during cleanup.
     */
    fun close()
}
