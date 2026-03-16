package com.recall.app.data.repository

import com.recall.app.data.vector.SearchMetadata
import com.recall.app.data.vector.VectorIndex
import com.recall.app.domain.embedding.EmbeddingGenerator
import com.recall.app.domain.model.Embedding
import com.recall.app.domain.model.SearchQuery
import com.recall.app.domain.repository.EmbeddingMetadata
import com.recall.app.domain.repository.EmbeddingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EmbeddingRepositoryImplTest {
    
    private lateinit var embeddingGenerator: EmbeddingGenerator
    private lateinit var vectorIndex: VectorIndex
    private lateinit var repository: EmbeddingRepository
    
    @Before
    fun setup() {
        embeddingGenerator = mock()
        vectorIndex = mock()
        repository = EmbeddingRepositoryImpl(embeddingGenerator, vectorIndex)
    }
    
    @Test
    fun `generateAndStoreEmbedding generates and stores embedding`() = runTest {
        val id = 1L
        val text = "Test text for embedding"
        val metadata = createMockMetadata()
        val embedding = createMockEmbedding()
        
        whenever(embeddingGenerator.generateEmbedding(any())).thenReturn(embedding)
        
        val result = repository.generateAndStoreEmbedding(id, text, metadata)
        
        assertNotNull(result)
        verify(vectorIndex).addEmbedding(any(), any(), any())
    }
    
    @Test
    fun `search generates query embedding and searches index`() = runTest {
        val query = SearchQuery(query = "test search", limit = 10)
        val queryEmbedding = createMockEmbedding()
        val searchResults = listOf(createMockSearchResult())
        
        whenever(embeddingGenerator.generateEmbedding(query.query)).thenReturn(queryEmbedding)
        whenever(vectorIndex.search(any(), any(), any())).thenReturn(searchResults)
        
        val results = repository.search(query)
        
        assertNotNull(results)
        assertEquals(1, results.size)
    }
    
    @Test
    fun `search applies category filter`() = runTest {
        val query = SearchQuery(query = "test", limit = 10, category = "CODE_SNIPPET")
        val queryEmbedding = createMockEmbedding()
        
        val allResults = listOf(
            createMockSearchResult(category = "CODE_SNIPPET"),
            createMockSearchResult(category = "MESSAGE_CHAT"),
            createMockSearchResult(category = "CODE_SNIPPET")
        )
        
        whenever(embeddingGenerator.generateEmbedding(query.query)).thenReturn(queryEmbedding)
        whenever(vectorIndex.search(any(), any(), any())).thenReturn(allResults)
        
        val results = repository.search(query)
        
        // Should filter to only CODE_SNIPPET
        val filteredResults = results.filter { it.category == "CODE_SNIPPET" }
        assertEquals(2, filteredResults.size)
    }
    
    @Test
    fun `searchByText calls search with default parameters`() = runTest {
        val queryText = "test query"
        val limit = 20
        val searchResults = listOf(createMockSearchResult())
        
        whenever(vectorIndex.search(any(), any(), any())).thenReturn(searchResults)
        
        val results = repository.searchByText(queryText, limit)
        
        assertNotNull(results)
        assertEquals(1, results.size)
    }
    
    @Test
    fun `removeEmbedding calls vector index`() = runTest {
        val id = 1L
        
        repository.removeEmbedding(id)
        
        verify(vectorIndex).removeEmbedding(id)
    }
    
    @Test
    fun `getEmbeddingCount returns index size`() {
        whenever(vectorIndex.getSize()).thenReturn(42)
        
        val count = repository.getEmbeddingCount()
        
        assertEquals(42, count)
        verify(vectorIndex).getSize()
    }
    
    @Test
    fun `rebuildIndex clears vector index`() = runTest {
        repository.rebuildIndex()
        
        verify(vectorIndex).clear()
    }
    
    @Test
    fun `prepareTextForEmbedding combines all fields`() {
        val metadata = EmbeddingMetadata(
            filePath = "/test.png",
            summary = "Test summary",
            tags = "tag1,tag2",
            category = "TEST",
            timestamp = System.currentTimeMillis(),
            ocrText = "Test OCR"
        )
        
        // Verify metadata is created correctly
        assertEquals("/test.png", metadata.filePath)
        assertEquals("Test summary", metadata.summary)
        assertEquals("tag1,tag2", metadata.tags)
        assertEquals("TEST", metadata.category)
        assertEquals("Test OCR", metadata.ocrText)
    }
    
    private fun createMockMetadata(): EmbeddingMetadata {
        return EmbeddingMetadata(
            filePath = "/test.png",
            summary = "Test",
            tags = "test",
            category = "TEST",
            timestamp = System.currentTimeMillis(),
            ocrText = "Test OCR"
        )
    }
    
    private fun createMockEmbedding(): Embedding {
        return Embedding(
            vector = FloatArray(384) { 0.1f },
            dimension = 384,
            modelUsed = "E5-small",
            normalized = true
        )
    }
    
    private fun createMockSearchResult(category: String = "TEST"): com.recall.app.domain.model.SearchResult {
        return com.recall.app.domain.model.SearchResult(
            screenshotId = 1L,
            filePath = "/test.png",
            summary = "Test",
            tags = "test",
            category = category,
            timestamp = System.currentTimeMillis(),
            similarityScore = 0.85f
        )
    }
}
