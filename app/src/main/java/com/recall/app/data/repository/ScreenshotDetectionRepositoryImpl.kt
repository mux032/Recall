package com.recall.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.domain.repository.ScreenshotDetectionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of screenshot detection repository.
 * Monitors system screenshot folders for new files.
 */
@Singleton
class ScreenshotDetectionRepositoryImpl @Inject constructor(
    private val screenshotDao: ScreenshotDao,
    @ApplicationContext private val context: Context
) : ScreenshotDetectionRepository {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val _newScreenshots = Channel<ScreenshotEntity>(Channel.BUFFERED)
    override val newScreenshots: Flow<ScreenshotEntity> = _newScreenshots.receiveAsFlow()

    private var isMonitoring = false

    override fun getScreenshotFolders(): List<String> {
        return listOf(
            "/storage/emulated/0/Pictures/Screenshots",
            "/storage/emulated/0/DCIM/Screenshots",
            "/storage/emulated/0/Pictures",
            "/storage/emulated/0/DCIM"
        )
    }

    override fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        android.util.Log.d("ScreenshotDetection", "Started monitoring")
    }

    override fun stopMonitoring() {
        isMonitoring = false
        android.util.Log.d("ScreenshotDetection", "Stopped monitoring")
    }

    override fun isMonitoring(): Boolean = isMonitoring

    /**
     * Scan existing screenshots from MediaStore and file system.
     */
    suspend fun scanExistingScreenshots(): List<ScreenshotEntity> = withContext(Dispatchers.IO) {
        android.util.Log.d("ScreenshotDetection", "Scanning for existing screenshots...")
        
        // Try 1: MediaStore
        val mediaStoreScreenshots = getExistingScreenshotsFromMediaStore()
        android.util.Log.d("ScreenshotDetection", "Found ${mediaStoreScreenshots.size} screenshots from MediaStore")
        
        // Try 2: Direct file system scan (fallback)
        val fileSystemScreenshots = if (mediaStoreScreenshots.isEmpty()) {
            scanFileSystem()
        } else {
            emptyList()
        }
        android.util.Log.d("ScreenshotDetection", "Found ${fileSystemScreenshots.size} screenshots from file system")
        
        val allScreenshots = mediaStoreScreenshots + fileSystemScreenshots
        android.util.Log.d("ScreenshotDetection", "Total found: ${allScreenshots.size} screenshots")
        
        val insertedList = mutableListOf<ScreenshotEntity>()

        allScreenshots.forEach { screenshot ->
            val existing = screenshotDao.getScreenshotByPath(screenshot.filePath)
            if (existing == null) {
                val id = screenshotDao.insert(screenshot)
                insertedList.add(screenshot.copy(id = id))
                android.util.Log.d("ScreenshotDetection", "Inserted: ${screenshot.filePath}")
            } else {
                android.util.Log.d("ScreenshotDetection", "Already exists: ${screenshot.filePath}")
            }
        }

        android.util.Log.d("ScreenshotDetection", "Inserted ${insertedList.size} new screenshots")
        insertedList
    }

    /**
     * Get screenshots from MediaStore.
     */
    private fun getExistingScreenshotsFromMediaStore(): List<ScreenshotEntity> {
        val screenshots = mutableListOf<ScreenshotEntity>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )

        try {
            // Query ALL images
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
                null
            )?.use { cursor ->
                android.util.Log.d("ScreenshotDetection", "MediaStore query returned ${cursor.count} results")
                
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                var count = 0
                val maxCount = 200
                
                while (cursor.moveToNext() && count < maxCount) {
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(nameColumn)
                    val timestamp = cursor.getLong(dateColumn) * 1000

                    // Filter by name
                    if (displayName.contains("Screenshot", ignoreCase = true) ||
                        displayName.contains("screenshot", ignoreCase = true)) {
                        
                        val contentUri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )

                        screenshots.add(
                            ScreenshotEntity(
                                id = id,
                                filePath = contentUri.toString(),
                                timestamp = timestamp,
                                ocrText = null,
                                summary = "Screenshot: $displayName",
                                tags = null,
                                category = null,
                                isIndexed = false,
                                processingStatus = ScreenshotEntity.ProcessingStatus.PENDING
                            )
                        )
                    }
                    count++
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ScreenshotDetection", "Error querying MediaStore: ${e.message}", e)
        }

        return screenshots
    }

    /**
     * Scan file system directly for screenshots.
     */
    private fun scanFileSystem(): List<ScreenshotEntity> {
        val screenshots = mutableListOf<ScreenshotEntity>()
        val folders = getScreenshotFolders()

        folders.forEach { folderPath ->
            val folder = File(folderPath)
            android.util.Log.d("ScreenshotDetection", "Scanning folder: $folderPath, exists: ${folder.exists()}")
            
            if (folder.exists() && folder.isDirectory) {
                folder.listFiles { file ->
                    file.isFile && (
                        file.name.endsWith(".png", ignoreCase = true) ||
                        file.name.endsWith(".jpg", ignoreCase = true) ||
                        file.name.endsWith(".jpeg", ignoreCase = true)
                    ) && file.name.contains("screenshot", ignoreCase = true)
                }?.forEach { file ->
                    screenshots.add(
                        ScreenshotEntity(
                            filePath = file.absolutePath,
                            timestamp = file.lastModified(),
                            ocrText = null,
                            summary = "Screenshot: ${file.name}",
                            tags = null,
                            category = null,
                            isIndexed = false,
                            processingStatus = ScreenshotEntity.ProcessingStatus.PENDING
                        )
                    )
                }
            }
        }

        return screenshots
    }

    /**
     * Process a newly detected screenshot.
     */
    suspend fun onNewScreenshotDetected(filePath: String) {
        android.util.Log.d("ScreenshotDetection", "New screenshot detected: $filePath")
        
        val screenshot = ScreenshotEntity(
            filePath = filePath,
            timestamp = System.currentTimeMillis(),
            processingStatus = ScreenshotEntity.ProcessingStatus.PENDING
        )

        val existing = screenshotDao.getScreenshotByPath(filePath)
        if (existing == null) {
            screenshotDao.insert(screenshot)
            _newScreenshots.send(screenshot)
            android.util.Log.d("ScreenshotDetection", "Saved new screenshot to database")
        } else {
            android.util.Log.d("ScreenshotDetection", "Screenshot already in database")
        }
    }
}
