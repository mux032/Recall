package com.recall.app.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recall.app.data.repository.ScreenshotDetectionRepositoryImpl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker that periodically monitors screenshot folders.
 */
@HiltWorker
class ScreenshotMonitoringWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val detectionRepository: ScreenshotDetectionRepositoryImpl
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result {
        return try {
            // Periodic monitoring is handled by ContentObserver
            // This worker ensures the observer is still active
            android.util.Log.d("MonitoringWorker", "Checking monitoring status")
            
            if (!detectionRepository.isMonitoring()) {
                detectionRepository.startMonitoring()
            }
            
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("MonitoringWorker", "Error in monitoring", e)
            Result.retry()
        }
    }
}
