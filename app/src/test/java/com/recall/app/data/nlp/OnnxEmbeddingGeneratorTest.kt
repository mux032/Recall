package com.recall.app.data.nlp

import android.content.Context
import android.os.Build
import com.recall.app.data.local.ModelDownloadState
import com.recall.app.data.local.ModelRepository
import com.recall.app.util.MemoryInfoHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException

/**
 * Unit tests for [OnnxEmbeddingGenerator] model path resolution and graceful degradation.
 *
 * The ONNX inference itself is not tested here (requires a real model file + ONNX runtime).
 * These tests focus on:
 * 1. Model path resolution — filesDir priority over assets, null when neither
 * 2. Graceful degradation — generate() returns null without crashing when no model
 * 3. isModelLoaded() state — false when no model available
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class OnnxEmbeddingGeneratorTest {

    private lateinit var context: Context
    private lateinit var memoryInfoHelper: MemoryInfoHelper
    private lateinit var modelRepository: ModelRepository

    @Before
    fun setup() {
        context = mock()
        memoryInfoHelper = mock()
        modelRepository = ModelRepository(InMemoryDataStoreForOnnxTest())

        // Default: plenty of memory available
        whenever(memoryInfoHelper.getAvailableMemory())
            .thenReturn(500 * 1024 * 1024L) // 500 MB

        // Mock assets manager
        val mockAssets = mock<android.content.res.AssetManager>()
        whenever(context.assets).thenReturn(mockAssets)

        // vocab.txt must return an empty stream so WordPieceTokenizer initialises without crash
        whenever(mockAssets.open("vocab.txt"))
            .thenReturn(java.io.ByteArrayInputStream(ByteArray(0)))

        // Default: model.onnx not in assets
        whenever(mockAssets.open("model.onnx")).thenThrow(FileNotFoundException("model.onnx"))
    }

    // -----------------------------------------------------------------------
    // resolveModelPath — priority order
    // -----------------------------------------------------------------------

    @Test
    fun `resolveModelPath returns null when no model in filesDir and no assets`() = runTest {
        // No path in repository (default state), no assets file
        // modelRepository starts with null path by default

        val generator = buildGenerator()
        val path = generator.resolveModelPath()

        assertNull("Should return null when no model available", path)
    }

    @Test
    fun `resolveModelPath returns filesDir path when model file exists`() = runTest {
        val tempFile = createTempFile("model", ".onnx")
        try {
            modelRepository.setDownloadedModelPath(tempFile.absolutePath)

            val generator = buildGenerator()
            val path = generator.resolveModelPath()

            assertTrue("Should return filesDir path", path == tempFile.absolutePath)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `resolveModelPath returns null when filesDir path exists in repo but file deleted`() = runTest {
        // Path saved in repo but file no longer on disk
        modelRepository.setDownloadedModelPath("/nonexistent/path/model.onnx")

        val generator = buildGenerator()
        val path = generator.resolveModelPath()

        // File doesn't exist → should fall through to assets → assets throws → null
        assertNull("Deleted file should fall through to null", path)
    }

    @Test
    fun `resolveModelPath returns null when no filesDir model and no assets (default)`() = runTest {
        // Default setup: model.onnx not in assets, no path in repository
        // This covers the "no model available" path which is the common case
        val generator = buildGenerator()
        val path = generator.resolveModelPath()
        assertNull("Should return null — falls back to assets which throws → null", path)
    }

    @Test
    fun `resolveModelPath returns filesDir path — filesDir takes priority`() = runTest {
        val tempFile = createTempFile("model", ".onnx")
        try {
            modelRepository.setDownloadedModelPath(tempFile.absolutePath)

            val generator = buildGenerator()
            val path = generator.resolveModelPath()

            // filesDir path returned — assets not consulted when filesDir file exists
            assertTrue("filesDir path must be returned", path == tempFile.absolutePath)
        } finally {
            tempFile.delete()
        }
    }

    // -----------------------------------------------------------------------
    // generate() — graceful degradation when no model
    // -----------------------------------------------------------------------

    @Test
    fun `generate returns null when no model available without crashing`() = runTest {
        val generator = buildGenerator()
        val result = generator.generate("test query")
        assertNull("generate() must return null gracefully when no model", result)
    }

    @Test
    fun `generate returns null for blank text without crashing`() = runTest {
        val generator = buildGenerator()
        assertNull(generator.generate(""))
        assertNull(generator.generate("   "))
    }

    // -----------------------------------------------------------------------
    // isModelLoaded() — state
    // -----------------------------------------------------------------------

    @Test
    fun `isModelLoaded returns false before any generate call`() = runTest {
        val generator = buildGenerator()
        assertFalse(generator.isModelLoaded())
    }

    @Test
    fun `isModelLoaded returns false after generate fails due to missing model`() = runTest {
        val generator = buildGenerator()
        generator.generate("test")
        assertFalse("isModelLoaded must be false when no model available", generator.isModelLoaded())
    }

    // -----------------------------------------------------------------------
    // getFailureReason() — diagnostic info
    // -----------------------------------------------------------------------

    @Test
    fun `getFailureReason is null before any generate call`() = runTest {
        val generator = buildGenerator()
        assertNull(generator.getFailureReason())
    }

    @Test
    fun `getFailureReason is non-null after generate fails due to missing model`() = runTest {
        val generator = buildGenerator()
        generator.generate("test")
        assertTrue(
            "getFailureReason should be set after init failure",
            generator.getFailureReason() != null
        )
    }

    // -----------------------------------------------------------------------
    // close() — no crash
    // -----------------------------------------------------------------------

    @Test
    fun `close does not crash when called before generate`() {
        val generator = buildGenerator()
        generator.close() // should not throw
    }

    @Test
    fun `close does not crash when called multiple times`() {
        val generator = buildGenerator()
        generator.close()
        generator.close() // second close should be safe
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun buildGenerator() = OnnxEmbeddingGenerator(
        context = context,
        memoryInfoHelper = memoryInfoHelper,
        modelRepository = modelRepository
    )
}

// In-memory DataStore stub (same pattern as ModelRepositoryTest)
private class InMemoryDataStoreForOnnxTest : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
    private val flow = MutableStateFlow(androidx.datastore.preferences.core.emptyPreferences())
    override val data = flow
    override suspend fun updateData(transform: suspend (t: androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences): androidx.datastore.preferences.core.Preferences {
        val updated = transform(flow.value)
        flow.value = updated
        return updated
    }
}
