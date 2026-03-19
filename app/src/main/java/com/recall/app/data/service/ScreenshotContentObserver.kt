package com.recall.app.data.service

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.recall.app.data.worker.ScreenshotProcessingWorker
import java.util.UUID

class ScreenshotContentObserver(
    private val context: Context,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        uri?.let { handleNewMedia(it) }
    }

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

                    // Check if newly added image is actually a screenshot
                    if (relPath.contains("Screenshots", ignoreCase = true) || 
                        path.contains("screenshot", ignoreCase = true)) {
                        
                        Log.d("ScreenshotObserver", "New screenshot detected: $path")
                        
                        // Enqueue WorkManager job
                        val workData = Data.Builder()
                            .putString("KEY_IMAGE_PATH", path)
                            .build()
                        
                        val workRequest = OneTimeWorkRequestBuilder<ScreenshotProcessingWorker>()
                            .setInputData(workData)
                            .build()
                            
                        WorkManager.getInstance(context).enqueue(workRequest)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ScreenshotObserver", "Error resolving new media", e)
        }
    }
}
