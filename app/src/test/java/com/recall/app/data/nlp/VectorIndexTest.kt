package com.recall.app.data.nlp

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

class VectorIndexTest {

    private lateinit var vectorIndex: VectorIndex

    @Before
    fun setup() {
        vectorIndex = VectorIndex()
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

        val results = vectorIndex.search(floatArrayOf(1.0f, 0.0f, 0.0f), limit = 3)

        assertEquals(3, results.size)
        // A should be first (highest similarity)
        assertEquals("A", results[0].first)
        // B should be second
        assertEquals("B", results[1].first)
        // C should be last
        assertEquals("C", results[2].first)
    }
}
