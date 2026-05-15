package com.recall.app.data.local

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ModelRepository].
 *
 * Uses an in-memory [DataStore] stub so tests run without file I/O or Android context.
 * Robolectric is used solely to provide [android.util.Log] — no real Android APIs needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class ModelRepositoryTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: ModelRepository

    @Before
    fun setup() {
        dataStore = InMemoryDataStore()
        repository = ModelRepository(dataStore)
    }

    // -----------------------------------------------------------------------
    // Initial / default state
    // -----------------------------------------------------------------------

    @Test
    fun `downloadState emits NONE by default`() = runTest {
        assertEquals(ModelDownloadState.NONE, repository.downloadState.first())
    }

    @Test
    fun `downloadedModelPath emits null by default`() = runTest {
        assertNull(repository.downloadedModelPath.first())
    }

    @Test
    fun `downloadProgress emits 0f by default`() = runTest {
        assertEquals(0f, repository.downloadProgress.first())
    }

    // -----------------------------------------------------------------------
    // setDownloadState
    // -----------------------------------------------------------------------

    @Test
    fun `setDownloadState DOWNLOADING persists and emits DOWNLOADING`() = runTest {
        repository.setDownloadState(ModelDownloadState.DOWNLOADING)
        assertEquals(ModelDownloadState.DOWNLOADING, repository.downloadState.first())
    }

    @Test
    fun `setDownloadState READY persists and emits READY`() = runTest {
        repository.setDownloadState(ModelDownloadState.READY)
        assertEquals(ModelDownloadState.READY, repository.downloadState.first())
    }

    @Test
    fun `setDownloadState FAILED persists and emits FAILED`() = runTest {
        repository.setDownloadState(ModelDownloadState.FAILED)
        assertEquals(ModelDownloadState.FAILED, repository.downloadState.first())
    }

    @Test
    fun `setDownloadState can transition through full lifecycle`() = runTest {
        repository.setDownloadState(ModelDownloadState.DOWNLOADING)
        assertEquals(ModelDownloadState.DOWNLOADING, repository.downloadState.first())

        repository.setDownloadState(ModelDownloadState.READY)
        assertEquals(ModelDownloadState.READY, repository.downloadState.first())
    }

    @Test
    fun `setDownloadState FAILED then DOWNLOADING allows retry`() = runTest {
        repository.setDownloadState(ModelDownloadState.FAILED)
        repository.setDownloadState(ModelDownloadState.DOWNLOADING)
        assertEquals(ModelDownloadState.DOWNLOADING, repository.downloadState.first())
    }

    // -----------------------------------------------------------------------
    // setDownloadedModelPath
    // -----------------------------------------------------------------------

    @Test
    fun `setDownloadedModelPath persists and emits the path`() = runTest {
        val path = "/data/user/0/com.recall.app/files/models/bge-small-en-v1.5.onnx"
        repository.setDownloadedModelPath(path)
        assertEquals(path, repository.downloadedModelPath.first())
    }

    @Test
    fun `setDownloadedModelPath can be overwritten with a new path`() = runTest {
        repository.setDownloadedModelPath("/old/path/model.onnx")
        repository.setDownloadedModelPath("/new/path/model_v2.onnx")
        assertEquals("/new/path/model_v2.onnx", repository.downloadedModelPath.first())
    }

    // -----------------------------------------------------------------------
    // setDownloadProgress
    // -----------------------------------------------------------------------

    @Test
    fun `setDownloadProgress persists and emits the value`() = runTest {
        repository.setDownloadProgress(0.5f)
        assertEquals(0.5f, repository.downloadProgress.first())
    }

    @Test
    fun `setDownloadProgress clamps values below 0 to 0f`() = runTest {
        repository.setDownloadProgress(-0.5f)
        assertEquals(0f, repository.downloadProgress.first())
    }

    @Test
    fun `setDownloadProgress clamps values above 1 to 1f`() = runTest {
        repository.setDownloadProgress(1.5f)
        assertEquals(1f, repository.downloadProgress.first())
    }

    @Test
    fun `setDownloadProgress 1f represents completion`() = runTest {
        repository.setDownloadProgress(1f)
        assertEquals(1f, repository.downloadProgress.first())
    }

    @Test
    fun `setDownloadProgress updates incrementally`() = runTest {
        repository.setDownloadProgress(0.25f)
        assertEquals(0.25f, repository.downloadProgress.first())

        repository.setDownloadProgress(0.75f)
        assertEquals(0.75f, repository.downloadProgress.first())
    }

    // -----------------------------------------------------------------------
    // clearModel
    // -----------------------------------------------------------------------

    @Test
    fun `clearModel resets downloadState to NONE`() = runTest {
        repository.setDownloadState(ModelDownloadState.READY)
        repository.clearModel()
        assertEquals(ModelDownloadState.NONE, repository.downloadState.first())
    }

    @Test
    fun `clearModel resets downloadedModelPath to null`() = runTest {
        repository.setDownloadedModelPath("/data/models/model.onnx")
        repository.clearModel()
        assertNull(repository.downloadedModelPath.first())
    }

    @Test
    fun `clearModel resets downloadProgress to 0f`() = runTest {
        repository.setDownloadProgress(0.8f)
        repository.clearModel()
        assertEquals(0f, repository.downloadProgress.first())
    }

    @Test
    fun `clearModel resets all three fields atomically`() = runTest {
        repository.setDownloadState(ModelDownloadState.READY)
        repository.setDownloadedModelPath("/data/models/model.onnx")
        repository.setDownloadProgress(1f)

        repository.clearModel()

        assertEquals(ModelDownloadState.NONE, repository.downloadState.first())
        assertNull(repository.downloadedModelPath.first())
        assertEquals(0f, repository.downloadProgress.first())
    }

    @Test
    fun `clearModel on already empty state is a no-op`() = runTest {
        // Should not throw
        repository.clearModel()
        assertEquals(ModelDownloadState.NONE, repository.downloadState.first())
        assertNull(repository.downloadedModelPath.first())
        assertEquals(0f, repository.downloadProgress.first())
    }

    // -----------------------------------------------------------------------
    // ModelDownloadState enum
    // -----------------------------------------------------------------------

    @Test
    fun `ModelDownloadState has exactly 4 values`() {
        assertEquals(4, ModelDownloadState.entries.size)
    }

    @Test
    fun `ModelDownloadState valueOf round-trips for all states`() {
        ModelDownloadState.entries.forEach { state ->
            assertEquals(state, ModelDownloadState.valueOf(state.name))
        }
    }
}

// ---------------------------------------------------------------------------
// In-memory DataStore implementation for testing
// ---------------------------------------------------------------------------

/**
 * Simple in-memory DataStore backed by a [MutableStateFlow].
 * No file I/O, no Android context — pure JVM.
 */
private class InMemoryDataStore : DataStore<Preferences> {

    private val flow = MutableStateFlow(emptyPreferences())

    override val data = flow

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences {
        val updated = transform(flow.value)
        flow.value = updated
        return updated
    }
}
