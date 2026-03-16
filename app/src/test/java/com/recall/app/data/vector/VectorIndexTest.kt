package com.recall.app.data.vector

import com.recall.app.domain.model.Embedding
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VectorIndexTest {
    
    private lateinit var vectorIndex: VectorIndex
    
    @Before
    fun setup() {
        vectorIndex = VectorIndex()
    }
    
    @Test
    fun `addEmbedding increases index size`() {
        assertEquals(0, vectorIndex.getSize())
        
        val embedding = createTestEmbedding()
        val metadata = createTestMetadata()
        vectorIndex.addEmbedding(1L, embedding, metadata)
        
        assertEquals(1, vectorIndex.getSize())
    }
    
    @Test
    fun `addEmbeddings adds multiple items`() {
        val items = listOf(
            EmbeddingItem(1L, createTestEmbedding(), createTestMetadata()),
            EmbeddingItem(2L, createTestEmbedding(), createTestMetadata()),
            EmbeddingItem(3L, createTestEmbedding(), createTestMetadata())
        )
        
        vectorIndex.addEmbeddings(items)
        
        assertEquals(3, vectorIndex.getSize())
    }
    
    @Test
    fun `search returns results sorted by similarity`() {
        // Add embeddings with known similarities
        val embedding1 = createTestEmbedding(value = 0.9f)
        val embedding2 = createTestEmbedding(value = 0.5f)
        val embedding3 = createTestEmbedding(value = 0.7f)
        
        vectorIndex.addEmbedding(1L, embedding1, createTestMetadata("First"))
        vectorIndex.addEmbedding(2L, embedding2, createTestMetadata("Second"))
        vectorIndex.addEmbedding(3L, embedding3, createTestMetadata("Third"))
        
        val queryEmbedding = createTestEmbedding(value = 0.85f)
        val results = vectorIndex.search(queryEmbedding, limit = 10, minSimilarity = 0.3f)
        
        // Results should be sorted by similarity (descending)
        assertTrue(results.size >= 1)
        for (i in 0 until results.size - 1) {
            assertTrue(
                "Results should be sorted by similarity",
                results[i].similarityScore >= results[i + 1].similarityScore
            )
        }
    }
    
    @Test
    fun `search respects limit parameter`() {
        repeat(10) { i ->
            vectorIndex.addEmbedding(
                i.toLong(),
                createTestEmbedding(),
                createTestMetadata("Item $i")
            )
        }
        
        val queryEmbedding = createTestEmbedding()
        val results = vectorIndex.search(queryEmbedding, limit = 5)
        
        assertTrue(results.size <= 5)
    }
    
    @Test
    fun `search respects minSimilarity parameter`() {
        vectorIndex.addEmbedding(1L, createTestEmbedding(value = 0.9f), createTestMetadata())
        vectorIndex.addEmbedding(2L, createTestEmbedding(value = 0.3f), createTestMetadata())
        
        val queryEmbedding = createTestEmbedding()
        val results = vectorIndex.search(queryEmbedding, minSimilarity = 0.5f)
        
        // Should only return high similarity results
        results.forEach { result ->
            assertTrue("Similarity should be >= 0.5", result.similarityScore >= 0.5f)
        }
    }
    
    @Test
    fun `removeEmbedding decreases index size`() {
        vectorIndex.addEmbedding(1L, createTestEmbedding(), createTestMetadata())
        assertEquals(1, vectorIndex.getSize())
        
        vectorIndex.removeEmbedding(1L)
        assertEquals(0, vectorIndex.getSize())
    }
    
    @Test
    fun `clear removes all embeddings`() {
        vectorIndex.addEmbedding(1L, createTestEmbedding(), createTestMetadata())
        vectorIndex.addEmbedding(2L, createTestEmbedding(), createTestMetadata())
        assertEquals(2, vectorIndex.getSize())
        
        vectorIndex.clear()
        assertEquals(0, vectorIndex.getSize())
    }
    
    @Test
    fun `contains returns true for existing embedding`() {
        vectorIndex.addEmbedding(1L, createTestEmbedding(), createTestMetadata())
        
        assertTrue(vectorIndex.contains(1L))
        assertFalse(vectorIndex.contains(2L))
    }
    
    @Test
    fun `getEmbedding returns correct embedding`() {
        val embedding = createTestEmbedding()
        vectorIndex.addEmbedding(1L, embedding, createTestMetadata())
        
        val retrieved = vectorIndex.getEmbedding(1L)
        assertNotNull(retrieved)
        assertArrayEquals(embedding.vector, retrieved?.vector, 0.0001f)
    }
    
    @Test
    fun `cosineSimilarity calculates correctly for identical vectors`() {
        val embedding1 = Embedding(
            vector = floatArrayOf(0.5f, 0.5f, 0.5f),
            dimension = 3,
            modelUsed = "test",
            normalized = false
        )
        val embedding2 = Embedding(
            vector = floatArrayOf(0.5f, 0.5f, 0.5f),
            dimension = 3,
            modelUsed = "test",
            normalized = false
        )
        
        val similarity = embedding1.cosineSimilarity(embedding2)
        assertEquals(1.0f, similarity, 0.0001f)
    }
    
    @Test
    fun `cosineSimilarity calculates correctly for orthogonal vectors`() {
        val embedding1 = Embedding(
            vector = floatArrayOf(1f, 0f, 0f),
            dimension = 3,
            modelUsed = "test",
            normalized = true
        )
        val embedding2 = Embedding(
            vector = floatArrayOf(0f, 1f, 0f),
            dimension = 3,
            modelUsed = "test",
            normalized = true
        )
        
        val similarity = embedding1.cosineSimilarity(embedding2)
        assertEquals(0.0f, similarity, 0.0001f)
    }
    
    private fun createTestEmbedding(value: Float = 0.5f): Embedding {
        return Embedding(
            vector = FloatArray(384) { value },
            dimension = 384,
            modelUsed = "test",
            normalized = true
        )
    }
    
    private fun createTestMetadata(summary: String = "Test"): SearchMetadata {
        return SearchMetadata(
            filePath = "/test.png",
            summary = summary,
            tags = "test",
            category = "TEST",
            timestamp = System.currentTimeMillis()
        )
    }
}
