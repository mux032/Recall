package com.recall.app

import android.app.Application
import android.provider.MediaStore
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.recall.app.data.nlp.VectorIndexBootstrapper
import com.recall.app.data.service.ScreenshotContentObserver
import com.recall.app.data.worker.BackgroundOcrWorker
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

        // Schedule background OCR processing
        scheduleBackgroundOcrProcessing()
        
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
            repeatInterval = 6,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiresBatteryNotLow(true) // Don't run if battery is low
                    .setRequiresCharging(false) // Can run on battery
                    .build()
            )
            .addTag("background_ocr")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "background_ocr_work",
            ExistingPeriodicWorkPolicy.KEEP,
            ocrWorkRequest
        )

        // Also enqueue an initial one-time work to process any pending screenshots
        val initialWorkRequest = androidx.work.OneTimeWorkRequestBuilder<BackgroundOcrWorker>()
            .addTag("background_ocr_initial")
            .build()

        workManager.enqueueUniqueWork(
            "background_ocr_initial_work",
            androidx.work.ExistingWorkPolicy.KEEP,
            initialWorkRequest
        )
    }
}
