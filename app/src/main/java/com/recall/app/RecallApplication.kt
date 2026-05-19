package com.recall.app

import android.app.Application
import android.provider.MediaStore
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.recall.app.data.nlp.VectorIndexBootstrapper
import com.recall.app.data.service.ScreenshotContentObserver
import com.recall.app.data.worker.BackgroundOcrWorker
import com.recall.app.data.worker.ScanExistingWorker
import com.recall.app.domain.usecase.EmbeddingGenerator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class RecallApplication : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "RecallApplication"

        /** Shared WorkManager tag applied to every indexing worker.
         *  Used by [HomeViewModel] to observe and cancel all active indexing in one call. */
        const val INDEXING_TAG = "recall_indexing"
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var vectorIndexBootstrapper: VectorIndexBootstrapper

    @Inject
    lateinit var embeddingGenerator: EmbeddingGenerator

    private val applicationScope = MainScope()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private lateinit var contentObserver: ScreenshotContentObserver

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "RecallApplication starting...")

        // Initialize the in-memory semantic Vector Index with HNSW
        vectorIndexBootstrapper.initialize(applicationScope)

        // Register content observer for new screenshots
        contentObserver = ScreenshotContentObserver(this, applicationScope)
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )

        // Schedule periodic background OCR processing
        scheduleBackgroundOcrProcessing()

        // On every cold launch, scan for screenshots taken while the app was dead,
        // then run OCR on anything newly discovered or still pending.
        scheduleLaunchTimeScan()

        Log.i(TAG, "RecallApplication initialized successfully")
    }

    /**
     * Clean up resources when the application terminates.
     * This properly closes the ONNX session to prevent memory leaks.
     */
    override fun onTerminate() {
        super.onTerminate()
        
        Log.i(TAG, "RecallApplication terminating, cleaning up resources...")
        
        try {
            // Close ONNX session to release native resources
            embeddingGenerator.close()
            Log.i(TAG, "ONNX embedding generator closed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing embedding generator", e)
        }
        
        // Unregister content observer and cancel pending debounce jobs
        try {
            contentObserver.destroy()
            contentResolver.unregisterContentObserver(contentObserver)
            Log.i(TAG, "Content observer unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering content observer", e)
        }
        
        // Cancel the application scope to clean up coroutines
        applicationScope.cancel()
        Log.i(TAG, "Resource cleanup completed")
    }

    /**
     * Schedules periodic background OCR processing for screenshots without extracted text.
     * Runs every 6 hours, processing newest images first.
     */
    private fun scheduleBackgroundOcrProcessing() {
        val workManager = WorkManager.getInstance(this)

        val ocrWorkRequest = PeriodicWorkRequestBuilder<BackgroundOcrWorker>(
            repeatInterval = 6L,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiresBatteryNotLow(!BuildConfig.DEBUG) // Skip in debug
                    .setRequiresCharging(false)
                    .build()
            )
            .addTag("background_ocr")
            .addTag(INDEXING_TAG)
            .build()

        // KEEP — preserves the existing schedule so cold launches don't reset the timer.
        workManager.enqueueUniquePeriodicWork(
            "background_ocr_work",
            ExistingPeriodicWorkPolicy.KEEP,
            ocrWorkRequest
        )
    }

    /**
     * Enqueues a [ScanExistingWorker] → [BackgroundOcrWorker] chain on every cold launch.
     *
     * This catches screenshots taken while the app process was dead — events that
     * [ScreenshotContentObserver] cannot observe. The scan is idempotent: if no new files
     * exist it exits immediately.
     *
     * [ExistingWorkPolicy.KEEP] ensures that if a launch-time chain is already running
     * (e.g. user rapidly restarts the app) it is left undisturbed.
     */
    private fun scheduleLaunchTimeScan() {
        val workManager = WorkManager.getInstance(this)

        val scanRequest = OneTimeWorkRequestBuilder<ScanExistingWorker>()
            .addTag("launch_scan")
            .addTag(INDEXING_TAG)
            .build()
        val ocrRequest = OneTimeWorkRequestBuilder<BackgroundOcrWorker>()
            .addTag("background_ocr_initial")
            .addTag(INDEXING_TAG)
            .build()

        workManager
            .beginUniqueWork("launch_scan_chain", ExistingWorkPolicy.KEEP, scanRequest)
            .then(ocrRequest)
            .enqueue()

        Log.i(TAG, "Enqueued launch-time scan chain")
    }
}
