package com.recall.app.data.service

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.recall.app.data.worker.ScreenshotProcessingWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Observes MediaStore for newly added images and enqueues [ScreenshotProcessingWorker].
 *
 * ## Debounce
 * Android fires [onChange] 3–5 times per screenshot save (temp file write, rename, metadata
 * update). Without debouncing this spawns duplicate OCR workers for every save event.
 *
 * A 1-second debounce is applied per URI: every new [onChange] call for the same URI cancels
 * the pending [Job] and restarts the delay. Only the final event after the burst triggers the
 * worker enqueue.
 *
 * ## Deduplication (second layer)
 * [WorkManager.enqueueUniqueWork] with [ExistingWorkPolicy.KEEP] ensures that even if two
 * URIs somehow resolve to the same file path, only one worker runs.
 *
 * @param context       Application context.
 * @param coroutineScope Scope used for debounce jobs. Should be cancelled when the observer
 *                       is unregistered (call [destroy]).
 * @param debounceMs    Debounce window in milliseconds. Defaults to 1 000 ms.
 * @param handler       Handler for [ContentObserver] callbacks.
 */
class ScreenshotContentObserver(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val debounceMs: Long = DEBOUNCE_MS,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    companion object {
        private const val TAG = "ScreenshotObserver"
        const val DEBOUNCE_MS = 1_000L
    }

    /** Tracks the active debounce job per URI string. */
    private val debounceJobs = mutableMapOf<String, Job>()

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        uri ?: return

        val uriKey = uri.toString()

        // Cancel any pending debounce job for this URI and restart the timer
        debounceJobs[uriKey]?.cancel()
        debounceJobs[uriKey] = coroutineScope.launch {
            delay(debounceMs)
            debounceJobs.remove(uriKey)
            handleNewMedia(uri)
        }

        Log.d(TAG, "onChange debounced for URI: $uri")
    }

    /**
     * Resolves [uri] to a file path and enqueues a [ScreenshotProcessingWorker] if it points
     * to a screenshot. Uses [ExistingWorkPolicy.KEEP] as a second dedup layer.
     */
    private fun handleNewMedia(uri: Uri) {
        try {
            val projection = arrayOf(
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH
            )

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    val relPathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

                    val path = cursor.getString(dataColumn) ?: ""
                    val relPath = cursor.getString(relPathColumn) ?: ""

                    if (relPath.contains("Screenshots", ignoreCase = true) ||
                        path.contains("screenshot", ignoreCase = true)
                    ) {
                        Log.d(TAG, "New screenshot detected: $path")

                        val workData = Data.Builder()
                            .putString("KEY_IMAGE_PATH", path)
                            .build()

                        val workRequest = OneTimeWorkRequestBuilder<ScreenshotProcessingWorker>()
                            .setInputData(workData)
                            .build()

                        // Unique work name per file path prevents duplicate workers even if
                        // debounce is bypassed (e.g. two different URIs for the same file).
                        val uniqueWorkName = "screenshot_process_${path.hashCode()}"
                        WorkManager.getInstance(context).enqueueUniqueWork(
                            uniqueWorkName,
                            ExistingWorkPolicy.KEEP,
                            workRequest
                        )

                        Log.i(TAG, "Enqueued ScreenshotProcessingWorker for: $path")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving new media", e)
        }
    }

    /**
     * Cancels all pending debounce jobs. Call this when unregistering the observer to
     * prevent coroutine leaks.
     */
    fun destroy() {
        debounceJobs.values.forEach { it.cancel() }
        debounceJobs.clear()
    }
}
