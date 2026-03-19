package com.recall.app.domain.usecase

interface EmbeddingGenerator {
    /**
     * Converts the given string of text into a fixed-size semantic vector embedding.
     * @param text The input text to be embedded.
     * @return A FloatArray representing the vector embedding, or null if generation fails.
     */
    suspend fun generate(text: String): FloatArray?
}
