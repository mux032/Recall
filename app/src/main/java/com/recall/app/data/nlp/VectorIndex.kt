package com.recall.app.data.nlp

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class VectorIndex @Inject constructor() {
    
    // In-memory cache: ID -> FloatArray Embedding
    private val index = ConcurrentHashMap<String, FloatArray>()
    
    /**
     * Loads a raw byte array blob into the index memory.
     */
    fun loadVector(id: String, blob: ByteArray?) {
        if (blob == null) return
        val floatArray = byteArrayToFloatArray(blob)
        index[id] = floatArray
    }
    
    /**
     * Clears and reloads multiple vectors.
     */
    fun loadAll(data: Map<String, ByteArray?>) {
        index.clear()
        data.forEach { (id, blob) ->
            if (blob != null) {
                index[id] = byteArrayToFloatArray(blob)
            }
        }
    }
    
    /**
     * Performs a brute-force Cosine Similarity search over all vectors in memory.
     * @param queryVector The encoded FloatArray of the search query.
     * @param limit The maximum number of results to return.
     * @return List of Pairs containing (ScreenshotID, CosineSimilarityScore), sorted descending.
     */
    fun search(queryVector: FloatArray, limit: Int = 10): List<Pair<String, Float>> {
        if (index.isEmpty()) return emptyList()
        
        val scores = mutableListOf<Pair<String, Float>>()
        
        for ((id, vector) in index) {
            val score = cosineSimilarity(queryVector, vector)
            scores.add(Pair(id, score))
        }
        
        // Sort descending by score
        scores.sortByDescending { it.second }
        
        return scores.take(limit)
    }
    
    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size) return 0f
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        return if (normA == 0f || normB == 0f) 0f else (dotProduct / (sqrt(normA) * sqrt(normB)))
    }
    
    private fun byteArrayToFloatArray(byteArray: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(byteArray)
        val floatArray = FloatArray(byteArray.size / 4)
        for (i in floatArray.indices) {
            floatArray[i] = buffer.getFloat()
        }
        return floatArray
    }
    
    /**
     * Check if index holds any data to prevent premature searching.
     */
    fun isReady(): Boolean = index.isNotEmpty()
}
