package com.recall.app.data.embedding

import com.recall.app.domain.model.Embedding
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class E5SmallEmbeddingGeneratorTest {
    
    private lateinit var generator: E5SmallEmbeddingGenerator
    
    @Before
    fun setup() {
        generator = E5SmallEmbeddingGenerator()
    }
    
    @Test
    fun `generator returns correct dimension`() {
        assertEquals(384, generator.getDimension())
    }
    
    @Test
    fun `generator returns model name`() {
        val modelName = generator.getModelName()
        assertTrue(modelName.contains("E5"))
    }
    
    @Test
    fun `generateEmbedding returns embedding with correct dimension`() = runTest {
        val text = "This is a test sentence for embedding generation"
        val embedding = generator.generateEmbedding(text)
        
        assertNotNull(embedding)
        assertEquals(384, embedding.dimension)
        assertEquals(384, embedding.vector.size)
        assertTrue(embedding.modelUsed.contains("E5"))
    }
    
    @Test
    fun `generateEmbedding returns normalized vector`() = runTest {
        val text = "Test text"
        val embedding = generator.generateEmbedding(text)
        
        // Check if vector is normalized (magnitude should be ~1.0)
        val magnitude = Math.sqrt(embedding.vector.sumByDouble { it.toDouble() * it.toDouble() }).toFloat()
        assertTrue("Magnitude should be close to 1.0", Math.abs(magnitude - 1.0f) < 0.01f)
    }
    
    @Test
    fun `generateEmbedding is deterministic for same input`() = runTest {
        val text = "Deterministic test"
        
        val embedding1 = generator.generateEmbedding(text)
        val embedding2 = generator.generateEmbedding(text)
        
        // Simulated embeddings should be deterministic
        assertArrayEquals(embedding1.vector, embedding2.vector, 0.0001f)
    }
    
    @Test
    fun `generateEmbedding produces different vectors for different texts`() = runTest {
        val text1 = "First test sentence"
        val text2 = "Completely different sentence"
        
        val embedding1 = generator.generateEmbedding(text1)
        val embedding2 = generator.generateEmbedding(text2)
        
        // Vectors should be different
        val similarity = embedding1.cosineSimilarity(embedding2)
        assertTrue("Similarity should be less than 1.0", similarity < 1.0f)
    }
    
    @Test
    fun `generateEmbeddings processes batch of texts`() = runTest {
        val texts = listOf("Text 1", "Text 2", "Text 3")
        
        val embeddings = generator.generateEmbeddings(texts)
        
        assertEquals(3, embeddings.size)
        embeddings.forEach { embedding ->
            assertEquals(384, embedding.dimension)
        }
    }
    
    @Test
    fun `embedding cosineSimilarity works correctly`() = runTest {
        val text = "Test text"
        val embedding1 = generator.generateEmbedding(text)
        val embedding2 = generator.generateEmbedding(text)
        
        // Same vector should have similarity of 1.0
        val similarity = embedding1.cosineSimilarity(embedding2)
        assertEquals(1.0f, similarity, 0.0001f)
    }
    
    @Test
    fun `embedding normalize returns same vector if already normalized`() = runTest {
        val text = "Test"
        val embedding = generator.generateEmbedding(text)
        
        // Generator already returns normalized vectors
        val normalized = embedding.normalize()
        assertArrayEquals(embedding.vector, normalized.vector, 0.0001f)
        assertTrue(normalized.normalized)
    }
}
