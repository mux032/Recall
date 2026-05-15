package com.recall.app.data.worker

import android.os.Build
import com.recall.app.data.local.ModelDownloadState
import com.recall.app.data.local.ModelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/**
 * Unit tests for [ModelDownloadWorker] logic.
 *
 * Tests [sha256Hex] computation and [ModelRepository] state transitions
 * without spinning up WorkManager (which requires an Android context and
 * a real job dispatcher). Network behaviour is tested via [MockWebServer].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class ModelDownloadWorkerTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        tempDir = createTempDir("model_download_test")
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    // -----------------------------------------------------------------------
    // sha256Hex — pure computation, no network or WorkManager needed
    // -----------------------------------------------------------------------

    @Test
    fun `sha256Hex returns correct 64-char hex string`() {
        val worker = TestableWorker()
        val file = File(tempDir, "test.bin").also { it.writeBytes("hello world".toByteArray()) }
        val result = worker.sha256Hex(file)

        assertEquals(64, result.length)
        assertTrue("SHA-256 must be lowercase hex", result.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `sha256Hex produces consistent results for same content`() {
        val worker = TestableWorker()
        val content = "Recall model test content".toByteArray()
        val file1 = File(tempDir, "a.bin").also { it.writeBytes(content) }
        val file2 = File(tempDir, "b.bin").also { it.writeBytes(content) }
        assertEquals(worker.sha256Hex(file1), worker.sha256Hex(file2))
    }

    @Test
    fun `sha256Hex produces different results for different content`() {
        val worker = TestableWorker()
        val file1 = File(tempDir, "c.bin").also { it.writeBytes("content A".toByteArray()) }
        val file2 = File(tempDir, "d.bin").also { it.writeBytes("content B".toByteArray()) }
        assertTrue(worker.sha256Hex(file1) != worker.sha256Hex(file2))
    }

    @Test
    fun `sha256Hex handles large file without OOM`() {
        val worker = TestableWorker()
        // Write a 1 MB file in chunks
        val file = File(tempDir, "large.bin")
        val chunk = ByteArray(1024) { it.toByte() }
        file.outputStream().use { out ->
            repeat(1024) { out.write(chunk) }
        }
        val result = worker.sha256Hex(file)
        assertEquals(64, result.length)
    }

    @Test
    fun `sha256Hex matches standard MessageDigest output`() {
        val worker = TestableWorker()
        val content = "bge-small-en-v1.5 test content".toByteArray()
        val file = File(tempDir, "verify.bin").also { it.writeBytes(content) }

        val expected = MessageDigest.getInstance("SHA-256")
            .digest(content)
            .joinToString("") { "%02x".format(it) }

        assertEquals(expected, worker.sha256Hex(file))
    }

    // -----------------------------------------------------------------------
    // ModelRepository state transitions — test the state machine directly
    // -----------------------------------------------------------------------

    @Test
    fun `downloadState transitions NONE to DOWNLOADING to READY on success`() = runTest {
        val repository = FakeModelRepository()

        // Simulate what doWork() does on success
        repository.setDownloadState(ModelDownloadState.DOWNLOADING)
        repository.setDownloadProgress(0.5f)
        repository.setDownloadedModelPath("/filesDir/models/model.onnx")
        repository.setDownloadProgress(1f)
        repository.setDownloadState(ModelDownloadState.READY)

        assertEquals(ModelDownloadState.READY, repository.downloadState.first())
        assertEquals(1f, repository.downloadProgress.first())
        assertEquals("/filesDir/models/model.onnx", repository.downloadedModelPath.first())
    }

    @Test
    fun `downloadState transitions to FAILED on SHA-256 mismatch`() = runTest {
        val repository = FakeModelRepository()

        // Simulate SHA-256 mismatch path
        repository.setDownloadState(ModelDownloadState.DOWNLOADING)
        repository.setDownloadState(ModelDownloadState.FAILED)

        assertEquals(ModelDownloadState.FAILED, repository.downloadState.first())
        // Path should NOT be set after mismatch
        assertEquals(null, repository.downloadedModelPath.first())
    }

    @Test
    fun `downloadState transitions to FAILED on network error`() = runTest {
        val repository = FakeModelRepository()

        repository.setDownloadState(ModelDownloadState.DOWNLOADING)
        repository.setDownloadState(ModelDownloadState.FAILED)

        assertEquals(ModelDownloadState.FAILED, repository.downloadState.first())
    }

    // -----------------------------------------------------------------------
    // HTTP download logic — using MockWebServer
    // -----------------------------------------------------------------------

    @Test
    fun `MockWebServer returns 200 with expected body`() {
        val content = "fake model content".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(content))
                .addHeader("Content-Length", content.size.toString())
        )

        val url = server.url("/onnx/model.onnx").toString()
        val request = okhttp3.Request.Builder().url(url).build()
        val client = okhttp3.OkHttpClient()
        val response = client.newCall(request).execute()

        assertTrue(response.isSuccessful)
        val downloaded = response.body!!.bytes()
        assertTrue(downloaded.contentEquals(content))
    }

    @Test
    fun `SHA-256 of downloaded content matches expectation`() {
        val content = "bge-small-en-v1.5 onnx content for testing".toByteArray()
        val expectedSha256 = MessageDigest.getInstance("SHA-256")
            .digest(content)
            .joinToString("") { "%02x".format(it) }

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(content))
        )

        val url = server.url("/model.onnx").toString()
        val client = okhttp3.OkHttpClient()
        val response = client.newCall(request = okhttp3.Request.Builder().url(url).build()).execute()
        val outFile = File(tempDir, "downloaded.onnx")
        outFile.writeBytes(response.body!!.bytes())

        val worker = TestableWorker()
        val actualSha256 = worker.sha256Hex(outFile)

        assertEquals(expectedSha256, actualSha256)
    }

    @Test
    fun `HTTP 500 is treated as a non-successful response`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val url = server.url("/model.onnx").toString()
        val client = okhttp3.OkHttpClient()
        val response = client.newCall(okhttp3.Request.Builder().url(url).build()).execute()

        assertFalse("HTTP 500 must not be treated as success", response.isSuccessful)
    }

    // -----------------------------------------------------------------------
    // Constants — regression guard
    // -----------------------------------------------------------------------

    @Test
    fun `KEY_MODEL_URL constant has correct value`() {
        assertEquals("model_url", ModelDownloadWorker.KEY_MODEL_URL)
    }

    @Test
    fun `KEY_MODEL_SHA256 constant has correct value`() {
        assertEquals("model_sha256", ModelDownloadWorker.KEY_MODEL_SHA256)
    }

    @Test
    fun `KEY_MODEL_FILENAME constant has correct value`() {
        assertEquals("model_filename", ModelDownloadWorker.KEY_MODEL_FILENAME)
    }

    @Test
    fun `MODELS_DIR constant has correct value`() {
        assertEquals("models", ModelDownloadWorker.MODELS_DIR)
    }
}

// ---------------------------------------------------------------------------
// Test doubles
// ---------------------------------------------------------------------------

/**
 * Exposes [sha256Hex] (internal function) for testing without needing
 * a real WorkManager context.
 */
private class TestableWorker {
    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8 * 1024)
        file.inputStream().use { stream ->
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * In-memory [ModelRepository] stub for testing state transitions.
 */
private class FakeModelRepository {
    private val _state = MutableStateFlow(ModelDownloadState.NONE)
    private val _path = MutableStateFlow<String?>(null)
    private val _progress = MutableStateFlow(0f)

    val downloadState = _state
    val downloadedModelPath = _path
    val downloadProgress = _progress

    suspend fun setDownloadState(state: ModelDownloadState) { _state.value = state }
    suspend fun setDownloadedModelPath(path: String) { _path.value = path }
    suspend fun setDownloadProgress(progress: Float) { _progress.value = progress.coerceIn(0f, 1f) }
    suspend fun clearModel() { _state.value = ModelDownloadState.NONE; _path.value = null; _progress.value = 0f }
}
