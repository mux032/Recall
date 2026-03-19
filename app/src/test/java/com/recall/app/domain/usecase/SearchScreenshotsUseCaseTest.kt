package com.recall.app.domain.usecase

import com.recall.app.data.nlp.VectorIndex
import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.repository.ScreenshotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class SearchScreenshotsUseCaseTest {

    private lateinit var embeddingGenerator: EmbeddingGenerator
    private lateinit var vectorIndex: VectorIndex
    private lateinit var screenshotRepository: ScreenshotRepository
    private lateinit var useCase: SearchScreenshotsUseCase

    @Before
    fun setup() {
        embeddingGenerator = mock()
        vectorIndex = mock()
        screenshotRepository = mock()
        useCase = SearchScreenshotsUseCase(embeddingGenerator, vectorIndex, screenshotRepository)
    }

    @Test
    fun `when vector index is ready, it searches semantic vectors and returns combined results`() = runTest {
        val query = "test query"
        val queryVector = floatArrayOf(0.5f, 0.5f)
        
        whenever(vectorIndex.isReady()).thenReturn(true)
        whenever(embeddingGenerator.generate(query)).thenReturn(queryVector)
        
        // Mock semantic matches
        whenever(vectorIndex.search(queryVector, 10)).thenReturn(
            listOf(Pair("id1", 0.9f))
        )
        
        val mockScreenshot1 = Screenshot(id = "id1", filePath = "path", fileName = "file", dateCreated = 0, dateIndexed = 0, width = 0, height = 0)
        whenever(screenshotRepository.getScreenshotsByIds(listOf("id1"))).thenReturn(listOf(mockScreenshot1))

        // Mock FTS fallback matches
        val mockScreenshot2 = Screenshot(id = "id2", filePath = "path2", fileName = "file2", dateCreated = 0, dateIndexed = 0, width = 0, height = 0)
        whenever(screenshotRepository.searchFts(query)).thenReturn(listOf(mockScreenshot2))

        val results = useCase.execute(query, 10)

        // It should combine Semantic and FTS results, preferring semantic first
        assertEquals(2, results.size)
        assertEquals("id1", results[0].id)
        assertEquals("id2", results[1].id)
        
        verify(embeddingGenerator).generate(query)
        verify(vectorIndex).search(queryVector, 10)
        verify(screenshotRepository).searchFts(query)
    }
}
