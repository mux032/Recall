package com.recall.app.domain.usecase

import android.util.Log
import com.recall.app.data.nlp.VectorIndexOptimized
import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.repository.ScreenshotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchScreenshotsUseCase @Inject constructor(
    private val embeddingGenerator: EmbeddingGenerator,
    private val vectorIndex: VectorIndexOptimized,
    private val screenshotRepository: ScreenshotRepository
) {
    companion object {
        private const val TAG = "SearchScreenshotsUseCase"

        /**
         * Minimum cosine similarity score (0.0-1.0) to consider a vector match relevant.
         * Values below 0.3 typically indicate unrelated semantic content.
         */
        private const val SIMILARITY_THRESHOLD = 0.3f

        /**
         * Default maximum number of results to return.
         */
        private const val DEFAULT_LIMIT = 10

        /**
         * Timeout in milliseconds for AI embedding generation.
         * Prevents hanging searches if the embedding model is slow or unresponsive.
         */
        private const val AI_SEARCH_TIMEOUT_MS = 100L

        /**
         * Maximum number of cached query results to store in LRU cache.
         */
        private const val MAX_CACHE_SIZE = 100
    }

    // LRU cache for query results — instance-level so each @Singleton instance owns its own
    // cache. Keeps tests isolated and avoids stale results leaking across test instances.
    private val queryCache = object : LinkedHashMap<String, List<Screenshot>>(10, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Screenshot>>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    /**
     * Executes a hybrid search over screenshots using parallel AI and FTS searches.
     * It generates an embedding for the [query] and queries the in-memory [VectorIndexOptimized]
     * with HNSW concurrently with a Room FTS4 search. Results are merged and deduplicated.
     *
     * Features:
     * - Parallel execution: AI (~50ms) and FTS (~10ms) run concurrently
     * - O(log n) search time with HNSW instead of O(n²) brute-force
     * - LRU cache for repeated queries (instant responses)
     * - Similarity threshold filtering to exclude irrelevant results
     * - Graceful degradation: If AI embedding fails, FTS results still returned
     * - AI timeout guard: prevents a slow ONNX model from blocking FTS indefinitely
     */
    suspend fun execute(query: String, limit: Int = DEFAULT_LIMIT): List<Screenshot> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext emptyList()
        }

        // Check cache first (use query+limit as key)
        val cacheKey = "$query:$limit"
        synchronized(queryCache) {
            queryCache[cacheKey]?.let {
                Log.d(TAG, "Cache hit for query: '$query'")
                return@withContext it
            }
        }

        // If vector index not ready, use FTS only (no parallelism needed)
        if (!vectorIndex.isReady()) {
            return@withContext try {
                screenshotRepository.searchFts(query).also { result ->
                    synchronized(queryCache) {
                        queryCache[cacheKey] = result
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "FTS search failed: ${e.message}", e)
                emptyList()
            }
        }

        Log.d(TAG, "Starting parallel search for: '$query'")

        // Run AI and FTS searches in parallel for optimal latency
        val aiSearch = async {
            try {
                val queryEmbedding = withTimeoutOrNull(AI_SEARCH_TIMEOUT_MS) {
                    embeddingGenerator.generate(query)
                }

                if (queryEmbedding != null) {
                    val vectorMatches = vectorIndex.search(queryEmbedding, limit, threshold = SIMILARITY_THRESHOLD)

                    if (vectorMatches.isNotEmpty()) {
                        val screenshots = screenshotRepository.getScreenshotsByIds(vectorMatches.map { it.first })
                        val screenshotMap = screenshots.associateBy { it.id }

                        val missingIds = vectorMatches.map { it.first }.toSet() - screenshotMap.keys
                        if (missingIds.isNotEmpty()) {
                            Log.w(TAG, "Vector search returned ${missingIds.size} IDs not found in database")
                        }

                        vectorMatches.mapNotNull { (id, _) -> screenshotMap[id] }
                    } else {
                        emptyList()
                    }
                } else {
                    Log.w(TAG, "AI embedding generation failed or timed out after ${AI_SEARCH_TIMEOUT_MS}ms")
                    emptyList()
                }
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OutOfMemoryError during AI search", e)
                emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "AI search error: ${e.message}", e)
                emptyList()
            }
        }

        // FTS always runs — captures exact keyword matches that semantic search may miss
        val ftsSearch = async {
            try {
                screenshotRepository.searchFts(query)
            } catch (e: Exception) {
                Log.e(TAG, "Fallback FTS search failed: ${e.message}", e)
                emptyList()
            }
        }

        // Wait for AI results first (higher quality — add these first)
        val aiResults = aiSearch.await()

        // Collect FTS results (always await — never cancel, as per hybrid search design)
        val ftsResults = ftsSearch.await()

        // Merge: AI first (semantic quality), then FTS (exact keyword matches), deduplicated
        val results = ArrayList<Screenshot>(limit)
        val seenIds = HashSet<String>(limit)

        var aiCount = 0
        for (screenshot in aiResults) {
            if (results.size >= limit) break
            if (seenIds.add(screenshot.id)) {
                results.add(screenshot)
                aiCount++
            }
        }
        Log.d(TAG, "Added $aiCount screenshots from AI search")

        var ftsCount = 0
        for (screenshot in ftsResults) {
            if (results.size >= limit) break
            if (seenIds.add(screenshot.id)) {
                results.add(screenshot)
                ftsCount++
            }
        }
        Log.d(TAG, "Added $ftsCount new screenshots from FTS search")

        Log.d(TAG, "Search completed: returning ${results.size} total results")

        // Cache the result
        synchronized(queryCache) {
            queryCache[cacheKey] = results
        }

        return@withContext results
    }
}
