package com.recall.app

import android.app.Application
import android.provider.MediaStore
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.recall.app.data.service.ScreenshotContentObserver
import com.recall.app.data.worker.BackgroundOcrWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import com.recall.app.data.nlp.VectorIndexBootstrapper
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class RecallApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var vectorIndexBootstrapper: VectorIndexBootstrapper

    private val applicationScope = MainScope()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private lateinit var contentObserver: ScreenshotContentObserver

    override fun onCreate() {
        super.onCreate()

        // Initialize the in-memory semantic Vector Index
        vectorIndexBootstrapper.initialize(applicationScope)

        // Register content observer for new screenshots
        contentObserver = ScreenshotContentObserver(this)
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )

        // Schedule background OCR processing
        scheduleBackgroundOcrProcessing()
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
