package com.recall.app.data.service

import android.content.Context
import androidx.work.*
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.data.repository.ScreenshotDetectionRepositoryImpl
import com.recall.app.data.worker.ExistingScreenshotScanWorker
import com.recall.app.data.worker.ScreenshotMonitoringWorker
import com.recall.app.data.worker.ScreenshotProcessingWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages screenshot detection and processing workflow.
 */
@Singleton
class ScreenshotDetectionService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val detectionRepository: ScreenshotDetectionRepositoryImpl,
    private val contentObserver: ScreenshotContentObserver
) {
    
    private val workManager = WorkManager.getInstance(context)
    
    companion object {
        private const val TAG = "ScreenshotDetectionService"
        private const val MONITORING_INTERVAL_MINUTES = 15L
    }
    
    /**
     * Start screenshot monitoring service.
     */
    fun startMonitoring() {
        android.util.Log.d(TAG, "Starting screenshot monitoring")
        
        // Start content observer
        contentObserver.startObserving()
        
        // Schedule periodic monitoring
        schedulePeriodicMonitoring()
        
        // Scan for existing screenshots
        scanExistingScreenshots()
        
        detectionRepository.startMonitoring()
    }
    
    /**
     * Stop screenshot monitoring service.
     */
    fun stopMonitoring() {
        android.util.Log.d(TAG, "Stopping screenshot monitoring")
        
        contentObserver.stopObserving()
        workManager.cancelAllWorkByTag(ScreenshotProcessingWorker.WORK_NAME)
        detectionRepository.stopMonitoring()
    }
    
    /**
     * Check if monitoring is active.
     */
    fun isMonitoring(): Boolean = detectionRepository.isMonitoring()
    
    /**
     * Schedule periodic background monitoring.
     */
    private fun schedulePeriodicMonitoring() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .setRequiresCharging(false)
            .build()
        
        val monitoringRequest = PeriodicWorkRequestBuilder<ScreenshotMonitoringWorker>(
            MONITORING_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(ScreenshotProcessingWorker.WORK_NAME)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            ScreenshotProcessingWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            monitoringRequest
        )
    }
    
    /**
     * Scan device for existing screenshots.
     */
    private fun scanExistingScreenshots() {
        android.util.Log.d(TAG, "Scanning for existing screenshots")
        
        // Launch in background
        workManager.enqueue(
            OneTimeWorkRequestBuilder<ExistingScreenshotScanWorker>()
                .build()
        )
    }
    
    /**
     * Queue a screenshot for processing.
     */
    fun queueScreenshotForProcessing(filePath: String, screenshotId: Long) {
        val inputData = workDataOf(
            ScreenshotProcessingWorker.INPUT_FILE_PATH to filePath,
            ScreenshotProcessingWorker.INPUT_SCREENSHOT_ID to screenshotId
        )
        
        val processingRequest = OneTimeWorkRequestBuilder<ScreenshotProcessingWorker>()
            .setInputData(inputData)
            .addTag("screenshot_$screenshotId")
            .build()
        
        workManager.enqueue(processingRequest)
    }
}
