package com.recall.app.domain.model

/**
 * Domain model representing a vector embedding.
 */
data class Embedding(
    val vector: FloatArray,
    val dimension: Int = 384,
    val modelUsed: String,
    val normalized: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Embedding
        if (dimension != other.dimension) return false
        if (modelUsed != other.modelUsed) return false
        if (!vector.contentEquals(other.vector)) return false
        return true
    }
    
    override fun hashCode(): Int {
        var result = vector.contentHashCode()
        result = 31 * result + dimension
        result = 31 * result + modelUsed.hashCode()
        return result
    }
    
    /**
     * Calculate cosine similarity with another embedding.
     */
    fun cosineSimilarity(other: Embedding): Float {
        require(this.dimension == other.dimension) {
            "Embedding dimensions must match: ${this.dimension} vs ${other.dimension}"
        }
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in vector.indices) {
            dotProduct += vector[i] * other.vector[i]
            normA += vector[i] * vector[i]
            normB += other.vector[i] * other.vector[i]
        }
        
        return if (normA == 0f || normB == 0f) 0f
        else dotProduct / (Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())).toFloat()
    }
    
    /**
     * Return a normalized copy of this embedding.
     */
    fun normalize(): Embedding {
        if (normalized) return this
        
        val magnitude = Math.sqrt(vector.sumByDouble { it.toDouble() * it.toDouble() }).toFloat()
        if (magnitude == 0f) return this
        
        val normalizedVector = vector.map { it / magnitude }.toFloatArray()
        return copy(
            vector = normalizedVector,
            normalized = true
        )
    }
}

/**
 * Search result with similarity score.
 */
data class SearchResult(
    val screenshotId: Long,
    val filePath: String,
    val summary: String?,
    val tags: String?,
    val category: String?,
    val timestamp: Long,
    val similarityScore: Float,
    val matchedTerms: List<String> = emptyList()
) {
    companion object {
        const val MIN_SIMILARITY = 0.5f
        const val HIGH_SIMILARITY = 0.8f
    }
    
    val isHighMatch: Boolean = similarityScore >= HIGH_SIMILARITY
    val isRelevant: Boolean = similarityScore >= MIN_SIMILARITY
}

/**
 * Search query with optional filters.
 */
data class SearchQuery(
    val query: String,
    val limit: Int = 20,
    val category: String? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val minSimilarity: Float = SearchResult.MIN_SIMILARITY
)
