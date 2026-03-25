package com.recall.app.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.recall.app.data.repository.ScreenshotRepositoryImpl
import com.recall.app.domain.repository.ScreenshotRepository
import com.recall.app.domain.usecase.OcrProcessor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@HiltWorker
class ScanExistingWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val screenshotRepository: ScreenshotRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting deep scan for existing screenshots")

            // 1. Scan device using MediaStore and insert pending records into Database
            // Note: In an ideal architecture, downcasting repository shouldn't happen, however we need
            // access to our specific Impl function for this phase.
            val countAdded = (screenshotRepository as? ScreenshotRepositoryImpl)?.scanExistingScreenshots() ?: 0
            Log.d(TAG, "Found $countAdded new unindexed screenshots in MediaStore")

            Log.d(TAG, "Deep scan complete. Added $countAdded screenshots to DB for background OCR processing.")

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during deep scan", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "ScanExistingWorker"
    }
}
