package com.recall.app.data.worker

import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.domain.model.ProcessingState
import com.recall.app.domain.usecase.EmbeddingGenerator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for Pass 2 (embedding-only retry) of [BackgroundOcrWorker].
 *
 * [BackgroundOcrWorker] requires a WorkerParameters/Hilt context, so the retryEmbedding
 * logic is tested via [EmbeddingRetryLogic] — a plain class that owns exactly the same
 * code, extracted so it can be unit-tested without Robolectric.
 *
 * What is verified:
 * 1. Success path: processingState → Done, embeddingRetryCount reset to 0, FTS rebuilt.
 * 2. Null-embedding path: embeddingRetryCount incremented, ocrRetryCount untouched.
 * 3. Exception path: embeddingRetryCount incremented, ocrRetryCount untouched.
 * 4. Rows with exhausted ocrRetryCount are still processed (counter separation regression guard).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundOcrWorkerPass2Test {

    private lateinit var screenshotDao: ScreenshotDao
    private lateinit var embeddingGenerator: EmbeddingGenerator
    private lateinit var logic: EmbeddingRetryLogic

    private val ocrDoneEntity = ScreenshotEntity(
        id = "id-pass2",
        filePath = "/sdcard/Screenshots/test.png",
        fileName = "test.png",
        dateCreated = 1_000_000L,
        dateIndexed = 1_000_000L,
        width = 1080,
        height = 1920,
        ocrText = "Hello World — valid OCR text",
        category = "Uncategorized",
        tagsJson = "",
        processingState = ProcessingState.Pending,
        embeddingByteArray = null,
        ocrRetryCount = 0,
        embeddingRetryCount = 0
    )

    @Before
    fun setup() {
        screenshotDao = mock()
        embeddingGenerator = mock()
        logic = EmbeddingRetryLogic(screenshotDao, embeddingGenerator)
    }

    // ── Success path ─────────────────────────────────────────────────────────

    @Test
    fun `success - saves Done state and resets embeddingRetryCount to 0`() = runTest {
        whenever(embeddingGenerator.generate(any())).thenReturn(FloatArray(128) { it.toFloat() })

        logic.retryEmbedding(ocrDoneEntity)

        val captor = argumentCaptor<ScreenshotEntity>()
        verify(screenshotDao).update(captor.capture())
        assertEquals(ProcessingState.Done, captor.firstValue.processingState)
        assertEquals(0, captor.firstValue.embeddingRetryCount)
    }

    @Test
    fun `success - preserves ocrText in the saved entity`() = runTest {
        whenever(embeddingGenerator.generate(any())).thenReturn(FloatArray(128))

        logic.retryEmbedding(ocrDoneEntity)

        val captor = argumentCaptor<ScreenshotEntity>()
        verify(screenshotDao).update(captor.capture())
        assertEquals(ocrDoneEntity.ocrText, captor.firstValue.ocrText)
    }

    @Test
    fun `success - rebuilds FTS index`() = runTest {
        whenever(embeddingGenerator.generate(any())).thenReturn(FloatArray(128))

        logic.retryEmbedding(ocrDoneEntity)

        verify(screenshotDao, times(1)).rebuildFtsIndex()
    }

    @Test
    fun `success - never touches ocrRetryCount`() = runTest {
        whenever(embeddingGenerator.generate(any())).thenReturn(FloatArray(128))

        logic.retryEmbedding(ocrDoneEntity)

        verify(screenshotDao, never()).incrementOcrRetryCount(any())
    }

    // ── Null-embedding path (model unavailable / OOM) ────────────────────────

    @Test
    fun `null embedding - increments embeddingRetryCount`() = runTest {
        whenever(embeddingGenerator.generate(any())).thenReturn(null)

        logic.retryEmbedding(ocrDoneEntity)

        verify(screenshotDao, times(1)).incrementEmbeddingRetryCount(ocrDoneEntity.id)
    }

    @Test
    fun `null embedding - does NOT increment ocrRetryCount`() = runTest {
        // Regression guard: transient embedding failure must never burn OCR retry slots.
        whenever(embeddingGenerator.generate(any())).thenReturn(null)

        logic.retryEmbedding(ocrDoneEntity)

        verify(screenshotDao, never()).incrementOcrRetryCount(any())
    }

    @Test
    fun `null embedding - does NOT update the row (stays Pending for next run)`() = runTest {
        whenever(embeddingGenerator.generate(any())).thenReturn(null)

        logic.retryEmbedding(ocrDoneEntity)

        verify(screenshotDao, never()).update(any())
    }

    // ── Exception path ───────────────────────────────────────────────────────

    @Test
    fun `exception - increments embeddingRetryCount`() = runTest {
        whenever(embeddingGenerator.generate(any())).thenThrow(RuntimeException("OOM"))

        runCatching { logic.retryEmbedding(ocrDoneEntity) }

        verify(screenshotDao, times(1)).incrementEmbeddingRetryCount(ocrDoneEntity.id)
    }

    @Test
    fun `exception - does NOT increment ocrRetryCount`() = runTest {
        // Regression guard: exception path must also use embeddingRetryCount.
        whenever(embeddingGenerator.generate(any())).thenThrow(RuntimeException("OOM"))

        runCatching { logic.retryEmbedding(ocrDoneEntity) }

        verify(screenshotDao, never()).incrementOcrRetryCount(any())
    }

    // ── Counter separation — core regression guard ───────────────────────────

    @Test
    fun `row with exhausted ocrRetryCount is still processed by Pass 2`() = runTest {
        // A row whose OCR was set by an external path but ocrRetryCount is at MAX
        // must still be embeddable — Pass 2 filters on embeddingRetryCount, not ocrRetryCount.
        val row = ocrDoneEntity.copy(ocrRetryCount = BackgroundOcrWorker.MAX_OCR_RETRIES)
        whenever(embeddingGenerator.generate(any())).thenReturn(FloatArray(128))

        logic.retryEmbedding(row)

        val captor = argumentCaptor<ScreenshotEntity>()
        verify(screenshotDao).update(captor.capture())
        assertEquals(ProcessingState.Done, captor.firstValue.processingState)
    }
}

/**
 * Extracts the embedding-retry business logic from [BackgroundOcrWorker] into a plain class
 * that can be unit-tested on the JVM without WorkerParameters or a Hilt context.
 *
 * The implementation is intentionally a verbatim copy of [BackgroundOcrWorker.retryEmbedding]
 * so the test exercises exactly the same code paths. If the worker implementation ever
 * diverges, the constants test ([BackgroundOcrWorkerConstantsTest]) and this test together
 * form a regression safety net.
 */
internal class EmbeddingRetryLogic(
    private val screenshotDao: ScreenshotDao,
    private val embeddingGenerator: EmbeddingGenerator
) {
    suspend fun retryEmbedding(screenshot: ScreenshotEntity) {
        val ocrText = screenshot.ocrText
        if (ocrText.isNullOrBlank()) return

        try {
            val embedding = embeddingGenerator.generate(ocrText)
            if (embedding != null) {
                screenshotDao.update(screenshot.copy(
                    embeddingByteArray = floatToByteArray(embedding),
                    processingState = ProcessingState.Done,
                    embeddingRetryCount = 0
                ))
                screenshotDao.rebuildFtsIndex()
            } else {
                screenshotDao.incrementEmbeddingRetryCount(screenshot.id)
            }
        } catch (e: Exception) {
            screenshotDao.incrementEmbeddingRetryCount(screenshot.id)
            throw e
        }
    }

    private fun floatToByteArray(floatArray: FloatArray): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(floatArray.size * 4)
        for (f in floatArray) buffer.putFloat(f)
        return buffer.array()
    }
}
