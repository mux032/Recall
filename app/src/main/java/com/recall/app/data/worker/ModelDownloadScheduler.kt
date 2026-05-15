package com.recall.app.data.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.recall.app.data.nlp.ModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules and manages [ModelDownloadWorker] via WorkManager with appropriate constraints.
 *
 * ## Constraints
 * The ONNX model file is 32–127 MB. To avoid unwanted data charges and battery drain:
 * - **UNMETERED network** — Wi-Fi only; never downloads over mobile data
 * - **Charging** — device must be plugged in to prevent battery drain on large downloads
 *
 * When constraints are not met WorkManager holds the request in a ENQUEUED state and
 * starts the download automatically once both conditions are satisfied.
 *
 * ## Deduplication
 * [ExistingWorkPolicy.KEEP] ensures only one download runs at a time — calling
 * [scheduleDownload] while a download is already enqueued or running is a no-op.
 *
 * @param context Application context for WorkManager access.
 */
@Singleton
class ModelDownloadScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "ModelDownloadScheduler"

        /** Unique WorkManager work name — ensures only one download runs at a time. */
        const val WORK_NAME = "model_download"

        /**
         * WorkManager constraints for model download:
         * - UNMETERED: Wi-Fi or ethernet only — never on mobile data (non-negotiable)
         * - requiresBatteryNotLow: skip download when battery is critically low (<~15%)
         *
         * Note: [Constraints.Builder.setRequiresCharging] is intentionally NOT set.
         * Requiring charging is too strict — a user at 80% battery on Wi-Fi should not
         * have to plug in just to download a 32–127 MB file. Major apps (Spotify, Netflix,
         * Google Maps) download large files on battery without issue.
         * [Constraints.Builder.setRequiresBatteryNotLow] acts as a safety net to prevent
         * downloads when the device is genuinely low on power.
         */
        val DOWNLOAD_CONSTRAINTS: Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()
    }

    private val workManager = WorkManager.getInstance(context)

    /**
     * Schedules a [ModelDownloadWorker] for the given [config].
     *
     * The download will start automatically once:
     * 1. The device is connected to an unmetered network (Wi-Fi / ethernet)
     * 2. The device is charging
     *
     * If a download for [WORK_NAME] is already enqueued or running, this call is a no-op
     * ([ExistingWorkPolicy.KEEP] — the existing job is preserved).
     *
     * @param config The [ModelConfig] describing what to download and verify.
     */
    fun scheduleDownload(config: ModelConfig) {
        val inputData = workDataOf(
            ModelDownloadWorker.KEY_MODEL_URL to config.url,
            ModelDownloadWorker.KEY_MODEL_SHA256 to config.sha256,
            ModelDownloadWorker.KEY_MODEL_FILENAME to config.fileName
        )

        val workRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(DOWNLOAD_CONSTRAINTS)
            .setInputData(inputData)
            .build()

        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            workRequest
        )

        Log.i(TAG, "Download scheduled for ${config.fileName} (${config.sizeBytes / 1_000_000} MB) — waiting for Wi-Fi + battery not low")
    }

    /**
     * Cancels any pending or running download for [WORK_NAME].
     * The partial file cleanup is handled by [ModelDownloadWorker] itself via [isStopped].
     */
    fun cancelDownload() {
        workManager.cancelUniqueWork(WORK_NAME)
        Log.i(TAG, "Download cancelled")
    }

    /**
     * Returns the current [WorkInfo.State] of the download job, or `null` if no job exists.
     * Useful for SettingsViewModel to show constraint status (BLOCKED, ENQUEUED, RUNNING, etc.).
     */
    fun getDownloadWorkInfo(): Flow<WorkInfo?> {
        return workManager
            .getWorkInfosForUniqueWorkFlow(WORK_NAME)
            .map { it.firstOrNull() }
    }

    /**
     * Returns true if there is currently an active download job
     * (state is ENQUEUED, RUNNING, or BLOCKED waiting for constraints).
     */
    fun isDownloadPending(): Flow<Boolean> {
        return getDownloadWorkInfo().map { info ->
            info?.state in listOf(
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.RUNNING,
                WorkInfo.State.BLOCKED
            )
        }
    }
}
