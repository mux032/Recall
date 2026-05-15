package com.recall.app.data.local

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents the lifecycle state of the ONNX model download.
 *
 * State transitions:
 * ```
 * NONE ──► DOWNLOADING ──► READY
 *              │
 *              └──────────► FAILED
 * READY ──► NONE  (via clearModel())
 * FAILED ──► DOWNLOADING  (retry)
 * ```
 */
enum class ModelDownloadState {
    /** No model has been downloaded or a download has not been started. */
    NONE,

    /** A download is currently in progress. */
    DOWNLOADING,

    /** Model file has been downloaded and SHA-256 verified. Ready for use. */
    READY,

    /** The last download attempt failed (network error, SHA-256 mismatch, etc.). */
    FAILED
}

/**
 * Persists ONNX model download state across app restarts using DataStore.
 *
 * This is the **central state store** for the Phase 7 model pipeline:
 * - [ModelDownloadWorker] writes progress and state during download
 * - [OnnxEmbeddingGenerator] reads [downloadedModelPath] to locate the model file
 * - [SettingsViewModel] observes all flows to update the UI
 *
 * All state is stored in the shared `recall_prefs` DataStore so it survives
 * process death and app restarts.
 *
 * @param dataStore Shared app DataStore (Hilt-injected via [DataStoreModule]).
 */
@Singleton
class ModelRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        private const val TAG = "ModelRepository"

        // DataStore preference keys
        internal val KEY_DOWNLOAD_STATE = stringPreferencesKey("model_download_state")
        internal val KEY_MODEL_PATH = stringPreferencesKey("model_downloaded_path")
        internal val KEY_DOWNLOAD_PROGRESS = floatPreferencesKey("model_download_progress")

        /** Default state when no model has been downloaded. */
        private val DEFAULT_STATE = ModelDownloadState.NONE
    }

    // -----------------------------------------------------------------------
    // Observable flows
    // -----------------------------------------------------------------------

    /**
     * Current download state of the ONNX model.
     * Emits [ModelDownloadState.NONE] if no state has been persisted yet.
     * Never errors — falls back to [ModelDownloadState.NONE] on DataStore failure.
     */
    val downloadState: Flow<ModelDownloadState> = dataStore.data
        .catch { e ->
            Log.e(TAG, "Error reading downloadState from DataStore", e)
            emit(androidx.datastore.preferences.core.emptyPreferences())
        }
        .map { prefs ->
            val raw = prefs[KEY_DOWNLOAD_STATE] ?: DEFAULT_STATE.name
            runCatching { ModelDownloadState.valueOf(raw) }.getOrDefault(DEFAULT_STATE)
        }

    /**
     * Absolute path to the downloaded model file in `filesDir/models/`.
     * Emits `null` when no model has been downloaded yet.
     * Never errors — falls back to `null` on DataStore failure.
     */
    val downloadedModelPath: Flow<String?> = dataStore.data
        .catch { e ->
            Log.e(TAG, "Error reading downloadedModelPath from DataStore", e)
            emit(androidx.datastore.preferences.core.emptyPreferences())
        }
        .map { prefs -> prefs[KEY_MODEL_PATH] }

    /**
     * Download progress as a value between 0.0 (not started) and 1.0 (complete).
     * Updated by [ModelDownloadWorker] via [setDownloadProgress].
     * Emits 0.0 when no download is in progress.
     */
    val downloadProgress: Flow<Float> = dataStore.data
        .catch { e ->
            Log.e(TAG, "Error reading downloadProgress from DataStore", e)
            emit(androidx.datastore.preferences.core.emptyPreferences())
        }
        .map { prefs -> prefs[KEY_DOWNLOAD_PROGRESS] ?: 0f }

    // -----------------------------------------------------------------------
    // Write operations
    // -----------------------------------------------------------------------

    /**
     * Updates the download state.
     * Called by [ModelDownloadWorker] as the download lifecycle progresses:
     *   NONE → DOWNLOADING → READY (or FAILED)
     */
    suspend fun setDownloadState(state: ModelDownloadState) {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_DOWNLOAD_STATE] = state.name
            }
            Log.d(TAG, "Download state updated: $state")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting downloadState", e)
        }
    }

    /**
     * Persists the absolute path to the successfully downloaded model file.
     * Should only be called after SHA-256 verification passes.
     *
     * @param path Absolute path in `context.filesDir/models/<fileName>`
     */
    suspend fun setDownloadedModelPath(path: String) {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_MODEL_PATH] = path
            }
            Log.d(TAG, "Model path saved: $path")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting downloadedModelPath", e)
        }
    }

    /**
     * Updates the download progress indicator.
     * Called periodically by [ModelDownloadWorker] during streaming download.
     *
     * @param progress Value between 0.0 (start) and 1.0 (complete).
     */
    suspend fun setDownloadProgress(progress: Float) {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_DOWNLOAD_PROGRESS] = progress.coerceIn(0f, 1f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting downloadProgress", e)
        }
    }

    /**
     * Resets all model-related state in DataStore.
     *
     * Called when:
     * - User taps "Delete model" in SettingsScreen
     * - A download fails and cleanup is needed
     * - App detects that the model file has been deleted externally
     *
     * After calling this, [downloadState] emits [ModelDownloadState.NONE],
     * [downloadedModelPath] emits `null`, and [downloadProgress] emits `0f`.
     */
    suspend fun clearModel() {
        try {
            dataStore.edit { prefs ->
                prefs.remove(KEY_DOWNLOAD_STATE)
                prefs.remove(KEY_MODEL_PATH)
                prefs.remove(KEY_DOWNLOAD_PROGRESS)
            }
            Log.i(TAG, "Model state cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing model state", e)
        }
    }
}
