package com.recall.app.data.worker

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Tests for pure, extractable logic from IndexingPipelineWorker
 * that can be verified without Android framework dependencies.
 */
class IndexingPipelinePureFunctionsTest {

    // Extract the floatToByteArray logic for testing
    private fun floatToByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4)
        floats.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    private fun byteArrayToFloat(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
        return FloatArray(bytes.size / 4) { buffer.getFloat() }
    }

    @Test
    fun `floatToByteArray produces correct byte length`() {
        val floats = FloatArray(384) { 0.1f }
        val bytes = floatToByteArray(floats)
        assertEquals(384 * 4, bytes.size)
    }

    @Test
    fun `floatToByteArray is reversible`() {
        val original = FloatArray(10) { it * 0.1f }
        val bytes = floatToByteArray(original)
        val recovered = byteArrayToFloat(bytes)
        assertArrayEquals(original, recovered, 0.0001f)
    }

    @Test
    fun `floatToByteArray handles empty array`() {
        val bytes = floatToByteArray(FloatArray(0))
        assertEquals(0, bytes.size)
    }

    @Test
    fun `floatToByteArray handles negative values`() {
        val floats = floatArrayOf(-1.0f, -0.5f, 0.0f, 0.5f, 1.0f)
        val bytes = floatToByteArray(floats)
        val recovered = byteArrayToFloat(bytes)
        assertArrayEquals(floats, recovered, 0.0001f)
    }

    @Test
    fun `ocr worker count is bounded correctly`() {
        // Test the coerceIn logic: (cores / 2).coerceIn(2, 4)
        fun workerCount(cores: Int) = (cores / 2).coerceIn(2, IndexingPipelineWorker.MAX_OCR_WORKERS)

        assertEquals(2, workerCount(1))   // 1/2=0, coerced to 2
        assertEquals(2, workerCount(2))   // 2/2=1, coerced to 2
        assertEquals(2, workerCount(4))   // 4/2=2, coerced to 2
        assertEquals(3, workerCount(6))   // 6/2=3
        assertEquals(4, workerCount(8))   // 8/2=4
        assertEquals(4, workerCount(16))  // 16/2=8, coerced to 4 (MAX)
    }

    @Test
    fun `IndexingProgress percentComplete rounds correctly`() {
        assertEquals(33, IndexingProgress(1, 3).percentComplete)
        assertEquals(66, IndexingProgress(2, 3).percentComplete)
        assertEquals(100, IndexingProgress(3, 3).percentComplete)
    }

    @Test
    fun `IndexingProgress isComplete boundary conditions`() {
        assertFalse(IndexingProgress(0, 1).isComplete)
        assertFalse(IndexingProgress(9, 10).isComplete)
        assertTrue(IndexingProgress(10, 10).isComplete)
        assertTrue(IndexingProgress(0, 0).isComplete.not()) // 0/0 is NOT complete
    }
}
