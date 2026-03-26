package com.recall.app.data.nlp

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

    @Before
    fun setup() {
        vectorIndex = VectorIndexOptimized()
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
}
