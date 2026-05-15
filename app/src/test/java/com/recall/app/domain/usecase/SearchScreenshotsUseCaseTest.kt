package com.recall.app.domain.usecase

import com.recall.app.data.nlp.VectorIndexOptimized
import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.repository.ScreenshotRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SearchScreenshotsUseCaseTest {

    private lateinit var embeddingGenerator: EmbeddingGenerator
    private lateinit var vectorIndex: VectorIndexOptimized
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

        // Mock vector index is ready
        whenever(vectorIndex.isReady()).thenReturn(true)

        // Mock embedding generator - use doReturn for suspend function
        doReturn(queryVector).whenever(embeddingGenerator).generate(query)

        // Mock semantic matches - note: VectorIndexOptimized.search has threshold parameter
        whenever(vectorIndex.search(queryVector, 10, 0.3f)).thenReturn(
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
        verify(vectorIndex).search(queryVector, 10, 0.3f)
        verify(screenshotRepository).searchFts(query)
        verify(vectorIndex).isReady()
    }

    @Test
    fun `execute with blank query returns empty list without searching`() = runTest {
        val results = useCase.execute("", 10)

        assertEquals(emptyList<Screenshot>(), results)
        // Should return early without any searches
        verify(embeddingGenerator, never()).generate(any())
        verify(screenshotRepository, never()).searchFts(any())
    }

    @Test
    fun `execute with blank query containing only spaces returns empty list`() = runTest {
        val results = useCase.execute("   ", 10)

        assertEquals(emptyList<Screenshot>(), results)
        // Should return early without any searches
        verify(embeddingGenerator, never()).generate(any())
        verify(screenshotRepository, never()).searchFts(any())
    }

    @Test
    fun `execute when AI fails returns FTS results only`() = runTest {
        val query = "test query"

        whenever(vectorIndex.isReady()).thenReturn(true)
        doReturn(null).whenever(embeddingGenerator).generate(query)

        val mockScreenshot = Screenshot(
            id = "id1",
            filePath = "path",
            fileName = "file",
            dateCreated = 0,
            dateIndexed = 0,
            width = 0,
            height = 0
        )
        whenever(screenshotRepository.searchFts(query)).thenReturn(listOf(mockScreenshot))

        val results = useCase.execute(query, 10)

        assertEquals(1, results.size)
        assertEquals("id1", results[0].id)
        verify(embeddingGenerator).generate(query)
        verify(vectorIndex, never()).search(any(), any(), any())
        verify(screenshotRepository).searchFts(query)
    }

    @Test
    fun `execute when AI and FTS return same ID returns deduplicated results`() = runTest {
        val query = "test query"
        val queryVector = floatArrayOf(0.5f, 0.5f)

        whenever(vectorIndex.isReady()).thenReturn(true)
        doReturn(queryVector).whenever(embeddingGenerator).generate(query)

        // Both AI and FTS return the same ID
        whenever(vectorIndex.search(queryVector, 10, 0.3f)).thenReturn(
            listOf(Pair("id1", 0.9f))
        )

        val mockScreenshot = Screenshot(
            id = "id1",
            filePath = "path",
            fileName = "file",
            dateCreated = 0,
            dateIndexed = 0,
            width = 0,
            height = 0
        )
        whenever(screenshotRepository.getScreenshotsByIds(listOf("id1"))).thenReturn(listOf(mockScreenshot))
        whenever(screenshotRepository.searchFts(query)).thenReturn(listOf(mockScreenshot))

        val results = useCase.execute(query, 10)

        // Should be deduplicated - only 1 result, not 2
        assertEquals(1, results.size)
        assertEquals("id1", results[0].id)
        verify(vectorIndex).isReady()
    }

    @Test
    fun `execute with more results than limit returns exactly limit results`() = runTest {
        val query = "test query"
        val queryVector = floatArrayOf(0.5f, 0.5f)

        whenever(vectorIndex.isReady()).thenReturn(true)
        doReturn(queryVector).whenever(embeddingGenerator).generate(query)

        // AI returns 5 results, FTS returns 10 results, limit is 10
        val aiIds = (1..5).map { "ai_id$it" }
        whenever(vectorIndex.search(queryVector, 10, 0.3f)).thenReturn(
            aiIds.map { Pair(it, 0.9f) }
        )

        val aiScreenshots = aiIds.map { id ->
            Screenshot(id = id, filePath = "path", fileName = "file", dateCreated = 0, dateIndexed = 0, width = 0, height = 0)
        }
        whenever(screenshotRepository.getScreenshotsByIds(aiIds)).thenReturn(aiScreenshots)

        val ftsIds = (1..10).map { "fts_id$it" }
        val ftsScreenshots = ftsIds.map { id ->
            Screenshot(id = id, filePath = "path", fileName = "file", dateCreated = 0, dateIndexed = 0, width = 0, height = 0)
        }
        whenever(screenshotRepository.searchFts(query)).thenReturn(ftsScreenshots)

        val results = useCase.execute(query, 10)

        // Should return exactly 10 results (5 AI + 5 FTS to fill the limit)
        assertEquals(10, results.size)
        // AI results should come first
        assertEquals("ai_id1", results[0].id)
        assertEquals("ai_id5", results[4].id)
        // Then FTS results
        assertEquals("fts_id1", results[5].id)
        assertEquals("fts_id5", results[9].id)
    }

    @Test
    fun `execute when vector index not ready uses FTS only`() = runTest {
        val query = "test query"

        whenever(vectorIndex.isReady()).thenReturn(false)

        val mockScreenshot = Screenshot(
            id = "id1",
            filePath = "path",
            fileName = "file",
            dateCreated = 0,
            dateIndexed = 0,
            width = 0,
            height = 0
        )
        whenever(screenshotRepository.searchFts(query)).thenReturn(listOf(mockScreenshot))

        val results = useCase.execute(query, 10)

        assertEquals(1, results.size)
        assertEquals("id1", results[0].id)
        verify(embeddingGenerator, never()).generate(any())
        verify(vectorIndex, never()).search(any(), any(), any())
        verify(screenshotRepository).searchFts(query)
    }

    @Test
    fun `execute when AI returns enough results ignores FTS results`() = runTest {
        val query = "test query"
        val queryVector = floatArrayOf(0.5f, 0.5f)

        whenever(vectorIndex.isReady()).thenReturn(true)
        doReturn(queryVector).whenever(embeddingGenerator).generate(query)

        // AI returns 10 results (exactly at limit)
        val aiIds = (1..10).map { "ai_id$it" }
        whenever(vectorIndex.search(queryVector, 10, 0.3f)).thenReturn(
            aiIds.map { Pair(it, 0.9f) }
        )

        val aiScreenshots = aiIds.map { id ->
            Screenshot(id = id, filePath = "path", fileName = "file", dateCreated = 0, dateIndexed = 0, width = 0, height = 0)
        }
        whenever(screenshotRepository.getScreenshotsByIds(aiIds)).thenReturn(aiScreenshots)

        // FTS also returns results (but will be ignored due to early termination)
        val ftsScreenshots = listOf(
            Screenshot(id = "fts_id1", filePath = "path", fileName = "file", dateCreated = 0, dateIndexed = 0, width = 0, height = 0)
        )
        whenever(screenshotRepository.searchFts(query)).thenReturn(ftsScreenshots)

        val results = useCase.execute(query, 10)

        // Should return exactly 10 AI results (FTS results are ignored due to early termination)
        assertEquals(10, results.size)
        assertEquals("ai_id1", results[0].id)
        assertEquals("ai_id10", results[9].id)
        // FTS is still called (async starts eagerly) but results are ignored
        verify(screenshotRepository).searchFts(query)
    }

    // -----------------------------------------------------------------------
    // #21 — AI_SEARCH_TIMEOUT_MS fix
    // -----------------------------------------------------------------------

    @Test
    fun `AI_SEARCH_TIMEOUT_MS is 1500ms`() {
        assertEquals(
            "AI_SEARCH_TIMEOUT_MS must be 1500ms per TRD (P95 < 2000ms)",
            1500L,
            SearchScreenshotsUseCase.AI_SEARCH_TIMEOUT_MS
        )
    }

    /**
     * Verifies that a 400ms embedding delay (typical ONNX cold-start) does NOT cause
     * the AI path to time out within the 1500ms window.
     *
     * Uses a hand-rolled stub instead of Mockito because Mockito's doAnswer does not
     * support Kotlin suspend function continuations.
     */
    @Test
    fun `embedding generator with 400ms delay still returns AI results within 1500ms timeout`() = runTest {
        val query = "cold start test"
        val queryVector = floatArrayOf(1.0f, 0.0f)

        // Hand-rolled stub: delays 400ms then returns the vector
        val slowEmbeddingGenerator = object : EmbeddingGenerator {
            override suspend fun generate(text: String): FloatArray? {
                delay(400)
                return queryVector
            }
            override fun close() = Unit
        }

        val aiScreenshot = Screenshot(
            id = "ai_slow", filePath = "/sdcard/ai_slow.png", fileName = "ai_slow.png",
            dateCreated = 0L, dateIndexed = 0L, width = 1080, height = 1920
        )
        whenever(vectorIndex.isReady()).thenReturn(true)
        whenever(vectorIndex.search(queryVector, 10, 0.3f))
            .thenReturn(listOf(Pair("ai_slow", 0.85f)))
        whenever(screenshotRepository.getScreenshotsByIds(listOf("ai_slow")))
            .thenReturn(listOf(aiScreenshot))
        whenever(screenshotRepository.searchFts(query)).thenReturn(emptyList())

        // Build use case with the slow stub
        val slowUseCase = SearchScreenshotsUseCase(slowEmbeddingGenerator, vectorIndex, screenshotRepository)
        val results = slowUseCase.execute(query, 10)

        // AI result must be present — 400ms delay is within the 1500ms timeout
        assertTrue("Expected AI result but got empty — 400ms delay should not time out at 1500ms", results.isNotEmpty())
        assertEquals("ai_slow", results[0].id)
    }
}
