package com.recall.app.presentation.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.recall.app.RecallApplication
import com.recall.app.data.di.DeviceProfile
import com.recall.app.data.di.DeviceProfiler
import com.recall.app.data.local.ModelDownloadState
import com.recall.app.data.local.ModelRepository
import com.recall.app.data.nlp.ModelConfig
import com.recall.app.data.nlp.ModelSelector
import com.recall.app.data.local.UserPreferences
import com.recall.app.data.nlp.VectorIndexOptimized
import com.recall.app.data.worker.ModelDownloadScheduler
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.recall.app.data.worker.BackgroundOcrWorker
import com.recall.app.domain.model.CacheLimitOption
import com.recall.app.domain.model.IndexingInterval
import com.recall.app.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for [SettingsScreen].
 *
 * Exposes real device hardware info, the recommended ONNX model, and live download
 * state sourced from Phase 7 components:
 *
 * ```
 * DeviceProfiler ──► deviceProfile
 * ModelSelector  ──► recommendedModel
 * ModelRepository ──► downloadState, downloadProgress
 * ModelDownloadScheduler ──► startModelDownload(), cancelModelDownload()
 * ModelRepository.clearModel() ──► deleteModel()
 * ```
 *
 * All StateFlows use [SharingStarted.WhileSubscribed] so upstream collection stops
 * 5 seconds after the last subscriber disappears (screen navigated away).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceProfiler: DeviceProfiler,
    private val modelSelector: ModelSelector,
    private val modelRepository: ModelRepository,
    private val modelDownloadScheduler: ModelDownloadScheduler,
    private val userPreferences: UserPreferences,
    private val vectorIndex: VectorIndexOptimized
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    // -----------------------------------------------------------------------
    // Device profile — RAM, cores, ABI (criteria: deviceProfile reflects actual hardware)
    // -----------------------------------------------------------------------

    /**
     * Snapshot of the device's hardware capabilities.
     * Sourced from [DeviceProfiler.getProfile] — cached after first call, cheap to access.
     * Wrapped in a StateFlow so the UI can reactively observe it via `collectAsState()`.
     */
    val deviceProfile: StateFlow<DeviceProfile> = kotlinx.coroutines.flow.flow {
        emit(deviceProfiler.getProfile())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = deviceProfiler.getProfile()
    )

    // -----------------------------------------------------------------------
    // Recommended model — selected by ModelSelector based on RAM class
    // (criteria: recommendedModel uses ModelSelector.selectModel())
    // -----------------------------------------------------------------------

    /**
     * The ONNX model recommended for this device based on its RAM class.
     * LOW / MEDIUM → quantized INT8 (34 MB); HIGH / VERY_HIGH → full FP32 (133 MB).
     */
    val recommendedModel: StateFlow<ModelConfig> = kotlinx.coroutines.flow.flow {
        emit(modelSelector.selectModel())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = modelSelector.selectModel()
    )

    // -----------------------------------------------------------------------
    // Download state & progress — streamed from ModelRepository
    // (criteria: downloadState and downloadProgress stream from ModelRepository)
    // -----------------------------------------------------------------------

    /**
     * Current lifecycle state of the model download.
     * Transitions: NONE → DOWNLOADING → READY (or FAILED).
     * Written by [ModelDownloadWorker]; read here for the UI.
     */
    val downloadState: StateFlow<ModelDownloadState> = modelRepository.downloadState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ModelDownloadState.NONE
        )

    /**
     * Download progress in the range [0.0, 1.0].
     * Updated by [ModelDownloadWorker] during streaming download.
     * Emits 0.0 when idle.
     */
    val downloadProgress: StateFlow<Float> = modelRepository.downloadProgress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0f
        )

    /**
     * WorkManager state for the download job (ENQUEUED, RUNNING, BLOCKED, etc.).
     * Useful for showing constraint status in the UI (e.g. "Waiting for Wi-Fi").
     */
    val downloadWorkState: StateFlow<WorkInfo.State?> =
        modelDownloadScheduler.getDownloadWorkInfo()
            .map { it?.state }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null
            )

    // -----------------------------------------------------------------------
    // Cache limit — sourced from UserPreferences DataStore
    // -----------------------------------------------------------------------

    /** Current vector cache limit option, persisted in DataStore. */
    val cacheLimitOption: StateFlow<CacheLimitOption> = userPreferences.vectorCacheLimitFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CacheLimitOption.AUTO
        )

    /** Persists the user's chosen cache limit option to DataStore. */
    fun setCacheLimitOption(option: CacheLimitOption) {
        viewModelScope.launch { userPreferences.setVectorCacheLimit(option) }
    }

    // -----------------------------------------------------------------------
    // Theme mode — sourced from UserPreferences DataStore
    // -----------------------------------------------------------------------

    /** Current theme mode (SYSTEM / LIGHT / DARK), persisted in DataStore. */
    val themeMode: StateFlow<ThemeMode> = userPreferences.themeModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM
        )

    /** Persists the user's chosen [ThemeMode] to DataStore. */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { userPreferences.setThemeMode(mode) }
    }

    // -----------------------------------------------------------------------
    // Indexing interval — sourced from UserPreferences DataStore
    // -----------------------------------------------------------------------

    /** Current background indexing interval, persisted in DataStore. */
    val indexingInterval: StateFlow<IndexingInterval> = userPreferences.indexingIntervalFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = IndexingInterval.DEFAULT
        )

    /**
     * Persists the user's chosen [IndexingInterval] and re-schedules
     * the WorkManager periodic worker so the new interval takes effect
     * on the next period (WorkManager applies it via UPDATE policy).
     */
    fun setIndexingInterval(interval: IndexingInterval) {
        viewModelScope.launch {
            userPreferences.setIndexingInterval(interval)
            // Re-schedule immediately so the new interval takes effect without app restart
            runCatching {
                val request = PeriodicWorkRequestBuilder<BackgroundOcrWorker>(
                    repeatInterval = interval.minutes,
                    repeatIntervalTimeUnit = interval.timeUnit
                )
                    .addTag("background_ocr")
                    .addTag(RecallApplication.INDEXING_TAG)
                    .build()
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(
                        "background_ocr_work",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request
                    )
            }
        }
    }

    // -----------------------------------------------------------------------
    // Actions
    // -----------------------------------------------------------------------

    /**
     * Enqueues [ModelDownloadWorker] via [ModelDownloadScheduler] with the model config
     * selected for this device. Download starts automatically once Wi-Fi is available
     * and battery is not low.
     *
     * No-op if a download is already enqueued or running
     * ([ExistingWorkPolicy.KEEP] is used by the scheduler).
     *
     * (criteria: startModelDownload() enqueues ModelDownloadWorker with correct constraints)
     */
    fun startModelDownload() {
        val config = recommendedModel.value
        Log.i(TAG, "Scheduling download for ${config.fileName} (${config.sizeBytes / 1_000_000} MB)")
        modelDownloadScheduler.scheduleDownload(config)
    }

    /**
     * Cancels any pending or running download job.
     * The [ModelDownloadWorker] handles partial file cleanup on cancellation.
     */
    fun cancelModelDownload() {
        Log.i(TAG, "Cancelling model download")
        modelDownloadScheduler.cancelDownload()
        viewModelScope.launch {
            // Reset state so UI returns to the NONE / idle state
            modelRepository.setDownloadState(ModelDownloadState.NONE)
            modelRepository.setDownloadProgress(0f)
        }
    }

    /**
     * Deletes the downloaded model file from disk and clears all model state in DataStore.
     * After this call [downloadState] emits [ModelDownloadState.NONE] and the VectorIndex
     * will stop being bootstrapped until the model is re-downloaded.
     */
    fun deleteModel() {
        viewModelScope.launch {
            // 1. Cancel WorkManager job — clears SUCCEEDED/FAILED entry so
            //    the next scheduleDownload() isn't blocked by ExistingWorkPolicy.KEEP
            modelDownloadScheduler.cancelDownload()

            // 2. Delete the model file from disk (one-shot read, no infinite collect)
            val modelPath = modelRepository.downloadedModelPath
                .firstOrNull()
            if (modelPath != null) {
                val file = File(modelPath)
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.i(TAG, "Model file deleted=$deleted at $modelPath")
                } else {
                    Log.w(TAG, "Model file not found at $modelPath — already deleted?")
                }
            }

            // 3. Clear DataStore state — downloadState → NONE, path → null
            modelRepository.clearModel()
            Log.i(TAG, "Model state cleared from DataStore")

            // 4. Clear in-memory VectorIndex so the AI search banner reappears
            //    immediately without requiring an app restart
            vectorIndex.clear()
            Log.i(TAG, "VectorIndex cleared — AI search banner will reappear")
        }
    }
}
