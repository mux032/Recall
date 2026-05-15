package com.recall.app.data.nlp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.recall.app.data.local.UserPreferences
import com.recall.app.util.MemoryInfoHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
class VectorIndexOptimizedTest {

    private lateinit var vectorIndex: VectorIndexOptimized
    private lateinit var memoryInfoHelper: MemoryInfoHelper
    private lateinit var userPreferences: UserPreferences

    @Before
    fun setup() {
        // Create stub implementations for testing
        val context = org.robolectric.RuntimeEnvironment.getApplication() as Context
        memoryInfoHelper = MemoryInfoHelper(context)
        
        // Create a simple in-memory DataStore mock for testing
        val testDataStore = object : DataStore<Preferences> {
            private val preferencesFlow = MutableStateFlow(emptyPreferences())
            
            override val data = preferencesFlow
            
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                val newPrefs = transform(preferencesFlow.value)
                preferencesFlow.value = newPrefs
                return newPrefs
            }
        }
        userPreferences = UserPreferences(testDataStore)
        
        vectorIndex = VectorIndexOptimized(memoryInfoHelper, userPreferences)
        // Initialize cache for tests
        vectorIndex.initializeCache()
    }

    private fun emptyPreferences(): Preferences {
        return androidx.datastore.preferences.core.emptyPreferences()
    }

    private fun floatArrayToByteArray(array: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(array.size * 4)
        for (f in array) {
            buffer.putFloat(f)
        }
        return buffer.array()
    }

    @Test
    fun `test cosine similarity math and sorting`() {
        // Create 3 vectors
        // A is very similar to Query
        val query = floatArrayToByteArray(floatArrayOf(1.0f, 0.0f, 0.0f))
        val vecA = floatArrayToByteArray(floatArrayOf(0.9f, 0.1f, 0.0f))

        // B is orthogonal (dissimilar)
        val vecB = floatArrayToByteArray(floatArrayOf(0.0f, 1.0f, 0.0f))

        // C is opposite
        val vecC = floatArrayToByteArray(floatArrayOf(-1.0f, 0.0f, 0.0f))

        val map = mapOf(
            "A" to vecA,
            "B" to vecB,
            "C" to vecC
        )
        vectorIndex.loadAll(map)

        // Use threshold 0.0f to get all results (including negative similarities)
        val results = vectorIndex.search(floatArrayOf(1.0f, 0.0f, 0.0f), limit = 3, threshold = 0.0f)

        // Note: C has -1.0 similarity which is < 0.0 threshold, so only A and B are returned
        assertEquals(2, results.size)
        // A should be first (highest similarity)
        assertEquals("A", results[0].first)
        // B should be second
        assertEquals("B", results[1].first)
    }

    @Test
    fun `test threshold filtering`() {
        // Create vectors with different similarities
        val query = floatArrayToByteArray(floatArrayOf(1.0f, 0.0f, 0.0f))
        val vecHigh = floatArrayToByteArray(floatArrayOf(0.95f, 0.1f, 0.0f)) // High similarity
        val vecLow = floatArrayToByteArray(floatArrayOf(0.1f, 0.95f, 0.0f))  // Low similarity

        val map = mapOf(
            "HIGH" to vecHigh,
            "LOW" to vecLow
        )
        vectorIndex.loadAll(map)

        // With threshold 0.5, only HIGH should be returned
        val results = vectorIndex.search(floatArrayOf(1.0f, 0.0f, 0.0f), limit = 10, threshold = 0.5f)

        assertEquals(1, results.size)
        assertEquals("HIGH", results[0].first)
        assertTrue(results[0].second > 0.5f)
    }

    @Test
    fun `test LRU cache`() {
        val query = floatArrayOf(1.0f, 0.0f, 0.0f)
        val vecA = floatArrayToByteArray(floatArrayOf(0.9f, 0.1f, 0.0f))

        vectorIndex.loadAll(mapOf("A" to vecA))

        // First search (cache miss)
        val results1 = vectorIndex.search(query, limit = 10, threshold = 0.0f)
        assertEquals(0.0f, vectorIndex.getCacheHitRate(), 0.01f)

        // Second search with same query (cache hit)
        val results2 = vectorIndex.search(query, limit = 10, threshold = 0.0f)
        assertTrue(vectorIndex.getCacheHitRate() > 0.0f)

        // Results should be identical
        assertEquals(results1, results2)
    }

    @Test
    fun `test isReady returns false when empty`() {
        assertTrue(!vectorIndex.isReady())
    }

    @Test
    fun `test isReady returns true when loaded`() {
        val vecA = floatArrayToByteArray(floatArrayOf(0.9f, 0.1f, 0.0f))
        vectorIndex.loadAll(mapOf("A" to vecA))
        assertTrue(vectorIndex.isReady())
    }

    @Test
    fun `test search returns empty when index is empty`() {
        val results = vectorIndex.search(floatArrayOf(1.0f, 0.0f, 0.0f), limit = 10)
        assertEquals(0, results.size)
    }

    @Test
    fun `test metrics tracking`() {
        val vecA = floatArrayToByteArray(floatArrayOf(0.9f, 0.1f, 0.0f))
        vectorIndex.loadAll(mapOf("A" to vecA))

        val metrics = vectorIndex.getMetrics()
        assertEquals(1, metrics["index_size"])
        assertEquals(0, metrics["cache_size"])
        assertEquals(0L, metrics["search_count"])
        assertEquals(0L, metrics["cache_hits"])
    }

    // -----------------------------------------------------------------------
    // LRU vector cache cap tests (#15)
    // -----------------------------------------------------------------------

    @Test
    fun `vector cache does not exceed cap when loadVector called beyond limit`() {
        // Set a small cap directly via reflection for testing
        val capField = VectorIndexOptimized::class.java.getDeclaredField("vectorCacheLimit")
        capField.isAccessible = true
        capField.setInt(vectorIndex, 3)

        // Load 5 vectors — only 3 should be retained
        for (i in 1..5) {
            val blob = floatArrayToByteArray(floatArrayOf(i.toFloat(), 0.0f, 0.0f))
            vectorIndex.loadVector("id_$i", blob)
        }

        assertTrue(
            "Vector cache size ${vectorIndex.size()} exceeds cap of 3",
            vectorIndex.size() <= 3
        )
    }

    @Test
    fun `loadAll respects vector cache cap`() {
        val capField = VectorIndexOptimized::class.java.getDeclaredField("vectorCacheLimit")
        capField.isAccessible = true
        capField.setInt(vectorIndex, 2)

        val data = (1..5).associate { "id_$it" to floatArrayToByteArray(floatArrayOf(it.toFloat(), 0f, 0f)) }
        vectorIndex.loadAll(data)

        assertTrue(
            "loadAll should cap vector cache at 2, got ${vectorIndex.size()}",
            vectorIndex.size() <= 2
        )
    }

    @Test
    fun `vector cache cap is set from calculateOptimalCacheLimit on initializeCache`() {
        // After initializeCache(), vectorCacheLimit should be >= DEFAULT_VECTOR_CACHE_SIZE (50_000)
        assertTrue(
            "vectorCacheLimit should be >= 50_000 after initializeCache, got ${vectorIndex.getVectorCacheLimit()}",
            vectorIndex.getVectorCacheLimit() >= 50_000
        )
    }

    @Test
    fun `search still works correctly after LRU eviction`() {
        val capField = VectorIndexOptimized::class.java.getDeclaredField("vectorCacheLimit")
        capField.isAccessible = true
        capField.setInt(vectorIndex, 2)

        // Load 3 vectors into a cap-2 cache — first one gets evicted
        val vec1 = floatArrayToByteArray(floatArrayOf(1.0f, 0.0f, 0.0f))
        val vec2 = floatArrayToByteArray(floatArrayOf(0.0f, 1.0f, 0.0f))
        val vec3 = floatArrayToByteArray(floatArrayOf(0.0f, 0.0f, 1.0f))
        vectorIndex.loadVector("v1", vec1)
        vectorIndex.loadVector("v2", vec2)
        vectorIndex.loadVector("v3", vec3) // v1 should be evicted

        assertEquals(2, vectorIndex.size())
        assertTrue(vectorIndex.isReady())

        // Search should work on the 2 remaining vectors
        val results = vectorIndex.search(floatArrayOf(0.0f, 1.0f, 0.0f), limit = 5, threshold = 0.0f)
        assertTrue(results.isNotEmpty())
    }
}
