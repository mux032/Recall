package com.recall.app.domain.usecase

import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.repository.ScreenshotRepository
import com.recall.app.data.nlp.VectorIndexOptimized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchScreenshotsUseCase @Inject constructor(
    private val embeddingGenerator: EmbeddingGenerator,
    private val vectorIndex: VectorIndexOptimized,
    private val screenshotRepository: ScreenshotRepository
) {
    /**
     * Executes a hybrid search over screenshots. It first generates an embedding for
     * the [query] and queries the in-memory [VectorIndexOptimized] with HNSW.
     * Then it falls back to a Room FTS4 search. It combines and returns the best results.
     * 
     * Features:
     * - O(log n) search time with HNSW instead of O(n²) brute-force
     * - LRU cache for repeated queries (instant responses)
     * - Similarity threshold filtering to exclude irrelevant results
     */
    suspend fun execute(query: String, limit: Int = 10): List<Screenshot> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val results = mutableListOf<Screenshot>()
        val seenIds = mutableSetOf<String>()

        // 1. Semantic Vector Search (HNSW-optimized)
        if (vectorIndex.isReady()) {
            val queryEmbedding = embeddingGenerator.generate(query)
            if (queryEmbedding != null) {
                // Get top K cosine similarities with threshold filtering
                // Threshold of 0.3 filters out irrelevant matches
                val vectorMatches = vectorIndex.search(queryEmbedding, limit, threshold = 0.3f)
                val idsToFetch = vectorMatches.map { it.first }

                // Fetch from DB
                val screenshots = screenshotRepository.getScreenshotsByIds(idsToFetch)

                // Sort the fetched screenshots according to score order
                val sortedScreenshots = vectorMatches.mapNotNull { match ->
                    screenshots.find { it.id == match.first }
                }

                results.addAll(sortedScreenshots)
                seenIds.addAll(idsToFetch)
            }
        }

        // 2. Full-Text Fallback Search (Lexical)
        // In case the vector search misses exact keyword matches, we fetch FTS results.
        if (results.size < limit) {
            val ftsResults = screenshotRepository.searchFts(query)
            for (ftsItem in ftsResults) {
                if (results.size >= limit) break
                if (!seenIds.contains(ftsItem.id)) {
                    results.add(ftsItem)
                    seenIds.add(ftsItem.id)
                }
            }
        }

        return@withContext results
    }
}
