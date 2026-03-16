package com.recall.app.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recall.app.data.repository.ScreenshotDetectionRepositoryImpl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker that scans for existing screenshots on first launch.
 */
@HiltWorker
class ExistingScreenshotScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val detectionRepository: ScreenshotDetectionRepositoryImpl
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result {
        return try {
            android.util.Log.d("ScanWorker", "Scanning existing screenshots")
            detectionRepository.scanExistingScreenshots()
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("ScanWorker", "Error scanning screenshots", e)
            Result.retry()
        }
    }
}
