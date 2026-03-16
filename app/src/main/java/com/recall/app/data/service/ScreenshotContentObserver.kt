package com.recall.app.data.service

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.recall.app.data.repository.ScreenshotDetectionRepositoryImpl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Observes the MediaStore for changes in screenshot folders.
 */
class ScreenshotContentObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val detectionRepository: ScreenshotDetectionRepositoryImpl
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private var isRegistered = false
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "ScreenshotObserver"
    }

    /**
     * Start observing screenshot folders.
     */
    fun startObserving() {
        if (isRegistered) return

        val contentResolver = context.contentResolver
        val externalUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        // Register observer for external storage - this watches ALL image changes
        contentResolver.registerContentObserver(
            externalUri,
            true, // Notify for descendants
            this
        )

        isRegistered = true
        android.util.Log.d(TAG, "Started observing MediaStore")
    }

    /**
     * Stop observing.
     */
    fun stopObserving() {
        if (!isRegistered) return

        context.contentResolver.unregisterContentObserver(this)
        isRegistered = false
        android.util.Log.d(TAG, "Stopped observing MediaStore")
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        
        // Debounce - ignore rapid successive calls
        if (uri == null) return

        android.util.Log.d(TAG, "MediaStore changed: $uri")

        // Check if the changed URI is a screenshot
        if (isScreenshotUri(uri)) {
            onScreenshotChanged(uri)
        }
    }

    /**
     * Check if the URI points to a screenshot folder.
     */
    private fun isScreenshotUri(uri: Uri): Boolean {
        val path = uri.path ?: return false
        val isScreenshot = path.contains("Screenshots", ignoreCase = true) ||
               path.contains("screenshot", ignoreCase = true)
        
        android.util.Log.d(TAG, "URI path: $path, isScreenshot: $isScreenshot")
        return isScreenshot
    }

    /**
     * Handle screenshot change event.
     */
    private fun onScreenshotChanged(uri: Uri) {
        android.util.Log.d(TAG, "Screenshot changed: $uri")
        
        // Launch coroutine to process the screenshot
        scope.launch {
            val filePath = getFilePathFromUri(uri)
            filePath?.let {
                android.util.Log.d(TAG, "Processing screenshot: $it")
                detectionRepository.onNewScreenshotDetected(it)
            }
        }
    }

    /**
     * Get file path from content URI.
     */
    private fun getFilePathFromUri(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)

        return try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    cursor.getString(columnIndex)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting file path from URI: ${e.message}")
            null
        }
    }
}
