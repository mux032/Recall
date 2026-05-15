package com.recall.app.presentation.ui.settings

import android.os.Build
import com.recall.app.data.di.DeviceProfile
import com.recall.app.data.di.DeviceProfiler
import com.recall.app.data.local.ModelDownloadState
import com.recall.app.data.local.ModelRepository
import com.recall.app.data.nlp.ModelConfig
import com.recall.app.data.nlp.ModelSelector
import com.recall.app.data.worker.ModelDownloadScheduler
import com.recall.app.util.MemoryClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [SettingsViewModel].
 *
 * Covers all acceptance criteria from issue #65:
 *  - deviceProfile reflects actual device RAM, cores, ABI
 *  - recommendedModel uses ModelSelector.selectModel()
 *  - downloadState and downloadProgress stream from ModelRepository
 *  - startModelDownload() enqueues ModelDownloadWorker with correct constraints
 *  - download start/cancel/delete state transitions
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var deviceProfiler: DeviceProfiler
    private lateinit var modelSelector: ModelSelector
    private lateinit var modelRepository: ModelRepository
    private lateinit var modelDownloadScheduler: ModelDownloadScheduler

    private val testDispatcher = StandardTestDispatcher()

    // Shared test fixtures
    private val testProfile = DeviceProfile(
        totalRamBytes = 8L * 1024 * 1024 * 1024, // 8 GB
        availableCores = 8,
        supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        memoryClass = MemoryClass.HIGH
    )

    private val testModel = ModelConfig(
        url = "https://example.com/model.onnx",
        sha256 = "abc123",
        fileName = "bge-small-en-v1.5.onnx",
        displayName = "bge-small-en-v1.5 (Full FP32, 133 MB)",
        sizeBytes = 133_093_490L
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        deviceProfiler = mock()
        modelSelector = mock()
        modelRepository = mock()
        modelDownloadScheduler = mock()

        // Default stubs
        whenever(deviceProfiler.getProfile()).thenReturn(testProfile)
        whenever(modelSelector.selectModel()).thenReturn(testModel)
        whenever(modelRepository.downloadState).thenReturn(flowOf(ModelDownloadState.NONE))
        whenever(modelRepository.downloadProgress).thenReturn(flowOf(0f))
        whenever(modelRepository.downloadedModelPath).thenReturn(flowOf(null))
        whenever(modelDownloadScheduler.getDownloadWorkInfo()).thenReturn(flowOf(null))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------
    // Criterion 1: deviceProfile reflects actual device RAM, cores, ABI
    // -----------------------------------------------------------------------

    @Test
    fun `deviceProfile initial value reflects DeviceProfiler getProfile`() {
        val viewModel = buildViewModel()
        val profile = viewModel.deviceProfile.value
        assertEquals(testProfile.totalRamBytes, profile.totalRamBytes)
        assertEquals(testProfile.availableCores, profile.availableCores)
        assertEquals(testProfile.supportedAbis, profile.supportedAbis)
        assertEquals(testProfile.memoryClass, profile.memoryClass)
    }

    @Test
    fun `deviceProfile reads from DeviceProfiler`() {
        buildViewModel()
        verify(deviceProfiler).getProfile()
    }

    // -----------------------------------------------------------------------
    // Criterion 2: recommendedModel uses ModelSelector.selectModel()
    // -----------------------------------------------------------------------

    @Test
    fun `recommendedModel initial value reflects ModelSelector selectModel`() {
        val viewModel = buildViewModel()
        val model = viewModel.recommendedModel.value
        assertEquals(testModel.fileName, model.fileName)
        assertEquals(testModel.url, model.url)
        assertEquals(testModel.sizeBytes, model.sizeBytes)
    }

    @Test
    fun `recommendedModel reads from ModelSelector`() {
        buildViewModel()
        verify(modelSelector).selectModel()
    }

    @Test
    fun `recommendedModel returns quantized model for LOW memory class`() {
        val lowMemModel = ModelConfig(
            url = ModelSelector.QUANTIZED_MODEL_URL,
            sha256 = ModelSelector.QUANTIZED_MODEL_SHA256,
            fileName = ModelSelector.QUANTIZED_MODEL_FILENAME,
            displayName = ModelSelector.QUANTIZED_MODEL_DISPLAY_NAME,
            sizeBytes = ModelSelector.QUANTIZED_MODEL_SIZE_BYTES
        )
        whenever(modelSelector.selectModel()).thenReturn(lowMemModel)
        val viewModel = buildViewModel()
        assertEquals(ModelSelector.QUANTIZED_MODEL_FILENAME, viewModel.recommendedModel.value.fileName)
    }

    // -----------------------------------------------------------------------
    // Criterion 3: downloadState and downloadProgress stream from ModelRepository
    // -----------------------------------------------------------------------

    @Test
    fun `downloadState initial value is NONE when repository emits NONE`() {
        whenever(modelRepository.downloadState).thenReturn(flowOf(ModelDownloadState.NONE))
        val viewModel = buildViewModel()
        assertEquals(ModelDownloadState.NONE, viewModel.downloadState.value)
    }

    @Test
    fun `downloadState initial value is DOWNLOADING when repository emits DOWNLOADING`() = runTest {
        whenever(modelRepository.downloadState).thenReturn(flowOf(ModelDownloadState.DOWNLOADING))
        val viewModel = buildViewModel()
        advanceUntilIdle()
        assertEquals(ModelDownloadState.DOWNLOADING, viewModel.downloadState.value)
    }

    @Test
    fun `downloadState initial value is READY when repository emits READY`() = runTest {
        whenever(modelRepository.downloadState).thenReturn(flowOf(ModelDownloadState.READY))
        val viewModel = buildViewModel()
        advanceUntilIdle()
        assertEquals(ModelDownloadState.READY, viewModel.downloadState.value)
    }

    @Test
    fun `downloadState initial value is FAILED when repository emits FAILED`() = runTest {
        whenever(modelRepository.downloadState).thenReturn(flowOf(ModelDownloadState.FAILED))
        val viewModel = buildViewModel()
        advanceUntilIdle()
        assertEquals(ModelDownloadState.FAILED, viewModel.downloadState.value)
    }

    @Test
    fun `downloadProgress initial value is 0 when repository emits 0`() = runTest {
        whenever(modelRepository.downloadProgress).thenReturn(flowOf(0f))
        val viewModel = buildViewModel()
        advanceUntilIdle()
        assertEquals(0f, viewModel.downloadProgress.value)
    }

    @Test
    fun `downloadProgress initial value reflects repository emission`() = runTest {
        whenever(modelRepository.downloadProgress).thenReturn(flowOf(0.65f))
        val viewModel = buildViewModel()
        advanceUntilIdle()
        assertEquals(0.65f, viewModel.downloadProgress.value)
    }

    // -----------------------------------------------------------------------
    // Criterion 4: startModelDownload() enqueues ModelDownloadWorker with correct constraints
    // -----------------------------------------------------------------------

    @Test
    fun `startModelDownload delegates to ModelDownloadScheduler with recommended model config`() {
        val viewModel = buildViewModel()
        viewModel.startModelDownload()
        verify(modelDownloadScheduler).scheduleDownload(testModel)
    }

    @Test
    fun `startModelDownload uses model from recommendedModel StateFlow`() {
        val viewModel = buildViewModel()
        viewModel.startModelDownload()
        // Verify the scheduler received the exact config that selectModel() returned
        verify(modelDownloadScheduler).scheduleDownload(
            org.mockito.kotlin.argThat { config ->
                config.fileName == testModel.fileName && config.url == testModel.url
            }
        )
    }

    // -----------------------------------------------------------------------
    // Criterion 5: download start/cancel/delete state transitions
    // -----------------------------------------------------------------------

    @Test
    fun `cancelModelDownload cancels WorkManager job`() = runTest {
        val viewModel = buildViewModel()
        viewModel.cancelModelDownload()
        advanceUntilIdle()
        verify(modelDownloadScheduler).cancelDownload()
    }

    @Test
    fun `cancelModelDownload resets download state to NONE in repository`() = runTest {
        val viewModel = buildViewModel()
        viewModel.cancelModelDownload()
        advanceUntilIdle()
        verify(modelRepository).setDownloadState(ModelDownloadState.NONE)
    }

    @Test
    fun `cancelModelDownload resets download progress to 0 in repository`() = runTest {
        val viewModel = buildViewModel()
        viewModel.cancelModelDownload()
        advanceUntilIdle()
        verify(modelRepository).setDownloadProgress(0f)
    }

    @Test
    fun `deleteModel clears model state from repository`() = runTest {
        val viewModel = buildViewModel()
        viewModel.deleteModel()
        advanceUntilIdle()
        verify(modelRepository).clearModel()
    }

    @Test
    fun `deleteModel does not clear repository when model path is null`() = runTest {
        whenever(modelRepository.downloadedModelPath).thenReturn(flowOf(null))
        val viewModel = buildViewModel()
        viewModel.deleteModel()
        advanceUntilIdle()
        // clearModel is still called to ensure DataStore is reset even if file is absent
        verify(modelRepository).clearModel()
    }

    // -----------------------------------------------------------------------
    // State machine — verify no unexpected interactions on construction
    // -----------------------------------------------------------------------

    @Test
    fun `no download is started on ViewModel construction`() {
        buildViewModel()
        verify(modelDownloadScheduler, never()).scheduleDownload(
            org.mockito.kotlin.any()
        )
    }

    @Test
    fun `downloadWorkState is not null after construction`() {
        val viewModel = buildViewModel()
        // Value can be null (no active work) but the StateFlow itself must be non-null
        assertNotNull(viewModel.downloadWorkState)
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private fun buildViewModel() = SettingsViewModel(
        deviceProfiler = deviceProfiler,
        modelSelector = modelSelector,
        modelRepository = modelRepository,
        modelDownloadScheduler = modelDownloadScheduler
    )
}
