package com.recall.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.util.Log
import androidx.room.withTransaction
import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.data.local.entity.toDomainModel
import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.repository.ScreenshotRepository
import com.recall.app.domain.usecase.EmbeddingGenerator
import com.recall.app.domain.usecase.OcrProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenshotRepositoryImpl @Inject constructor(
    private val screenshotDao: ScreenshotDao,
    private val ocrProcessor: OcrProcessor,
    private val embeddingGenerator: EmbeddingGenerator,
    @ApplicationContext private val context: Context
) : ScreenshotRepository {

    companion object {
        private const val TAG = "ScreenshotRepository"
    }

    override fun getAllScreenshots(): Flow<List<Screenshot>> {
        return screenshotDao.getAllScreenshots().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getScreenshotById(id: String): Screenshot? {
        return screenshotDao.getScreenshotById(id)?.toDomainModel()
    }

    override suspend fun getScreenshotsByIds(ids: List<String>): List<Screenshot> {
        return screenshotDao.getScreenshotsByIds(ids).map { it.toDomainModel() }
    }

    override suspend fun searchFts(query: String): List<Screenshot> {
        // SQLite FTS matches query strings specifically, we format for prefix match
        val ftsQuery = "*$query*"
        return screenshotDao.searchFts(ftsQuery).map { it.toDomainModel() }
    }

    override suspend fun addScreenshot(screenshot: Screenshot) {
        val entity = ScreenshotEntity(
            id = screenshot.id,
            filePath = screenshot.filePath,
            fileName = screenshot.fileName,
            dateCreated = screenshot.dateCreated,
            dateIndexed = screenshot.dateIndexed,
            width = screenshot.width,
            height = screenshot.height,
            ocrText = screenshot.ocrText,
            category = screenshot.category,
            tagsJson = screenshot.tags.joinToString(","),
            processingState = "DONE",
            embeddingByteArray = floatArrayToByteArray(screenshot.embedding)
        )
        screenshotDao.insert(entity)
    }

    private fun floatArrayToByteArray(floatArray: FloatArray?): ByteArray? {
        if (floatArray == null) return null
        val buffer = java.nio.ByteBuffer.allocate(floatArray.size * 4) // 4 bytes per float
        for (f in floatArray) {
            buffer.putFloat(f)
        }
        return buffer.array()
    }

    override suspend fun updateScreenshot(screenshot: Screenshot) {
        addScreenshot(screenshot) // Insert with REPLACE acts as update
    }

    override suspend fun deleteScreenshot(id: String) {
        screenshotDao.deleteById(id)
    }

    override suspend fun processOcr(id: String): Screenshot? {
        val entity = screenshotDao.getScreenshotById(id) ?: return null
        
        // Skip if file doesn't exist
        val file = java.io.File(entity.filePath)
        if (!file.exists()) return null

        // Run OCR
        val extractedText = ocrProcessor.process(entity.filePath)
        
        // Generate embedding
        val embedding = extractedText?.let { text ->
            embeddingGenerator.generate(text)
        }

        // Update database
        val updatedEntity = entity.copy(
            ocrText = extractedText,
            embeddingByteArray = embedding?.let { floatArrayToByteArray(it) },
            processingState = "DONE",
            dateIndexed = System.currentTimeMillis()
        )

        screenshotDao.update(updatedEntity)
        return updatedEntity.toDomainModel()
    }

    suspend fun scanExistingScreenshots(): Int {
        var addedCount = 0
        var skippedCount = 0
        var errorCount = 0
        val contentResolver: ContentResolver = context.contentResolver
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.RELATIVE_PATH
        )

        // Query ALL images first to debug what's available
        Log.i(TAG, "=== Starting Screenshot Scan Debug ===")
        Log.i(TAG, "Querying MediaStore at: $uri")
        
        // First, query ALL images to see what's in MediaStore
        val allImagesCount = try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val count = cursor.count
                Log.i(TAG, "Total images in MediaStore: $count")
                
                // Log first 5 images for debugging
                if (cursor.moveToFirst()) {
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    val relPathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                    var shown = 0
                    do {
                        if (shown < 5) {
                            val path = cursor.getString(dataColumn) ?: "null"
                            val relPath = cursor.getString(relPathColumn) ?: "null"
                            Log.i(TAG, "Image $shown: PATH=$path, RELATIVE_PATH=$relPath")
                            shown++
                        }
                    } while (cursor.moveToNext())
                }
                count
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying all images", e)
            0
        }

        // Common screenshot directories across Android devices
        val screenshotPatterns = listOf(
            "Screenshots",
            "Pictures/Screenshots",
            "DCIM/Screenshots",
            "screenshot"
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            for (pattern in screenshotPatterns) {
                val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("%$pattern%")

                Log.d(TAG, "Scanning for screenshots with pattern: $selectionArgs[0]")

                contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                    val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                    val relPathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

                    val cursorCount = cursor.count
                    Log.i(TAG, "Pattern '$pattern' found $cursorCount matches in MediaStore")

                    if (cursorCount == 0) {
                        Log.w(TAG, "No matches for pattern: $pattern")
                    }

                    while (cursor.moveToNext()) {
                        try {
                            val filePath = cursor.getString(dataColumn) ?: continue
                            val fileName = cursor.getString(nameColumn) ?: "Screenshot.png"
                            val dateAddedSeconds = cursor.getLong(dateColumn)
                            var width = cursor.getInt(widthColumn)
                            var height = cursor.getInt(heightColumn)
                            val relativePath = cursor.getString(relPathColumn) ?: ""

                            Log.d(TAG, "Processing: $fileName | Path: $filePath | RelPath: $relativePath")

                            // Skip if file doesn't exist
                            val file = java.io.File(filePath)
                            if (!file.exists()) {
                                Log.w(TAG, "Skipping non-existent file: $filePath")
                                continue
                            }

                            // Check if already exists in DB
                            val existing = screenshotDao.getScreenshotByPath(filePath)
                            if (existing != null) {
                                Log.d(TAG, "Skipping duplicate: $filePath")
                                skippedCount++
                                continue
                            }

                            // Fallback: If width/height are 0 or invalid, decode image bounds
                            if (width <= 0 || height <= 0) {
                                try {
                                    val options = android.graphics.BitmapFactory.Options().apply {
                                        inJustDecodeBounds = true
                                    }
                                    android.graphics.BitmapFactory.decodeFile(filePath, options)
                                    width = options.outWidth
                                    height = options.outHeight
                                    Log.d(TAG, "Decoded image dimensions: ${width}x${height} for $fileName")
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to decode image bounds for $filePath", e)
                                    width = 1080 // Fallback default
                                    height = 1920
                                }
                            }

                            val entity = ScreenshotEntity(
                                id = UUID.randomUUID().toString(),
                                filePath = filePath,
                                fileName = fileName,
                                dateCreated = dateAddedSeconds * 1000L, // Convert to MS
                                dateIndexed = System.currentTimeMillis(),
                                width = width,
                                height = height,
                                ocrText = null,
                                category = "Uncategorized",
                                tagsJson = "",
                                processingState = "PENDING"
                            )

                            screenshotDao.insert(entity)
                            addedCount++
                            Log.i(TAG, "✓ Added screenshot: $fileName ($relativePath)")
                        } catch (e: Exception) {
                            errorCount++
                            Log.e(TAG, "Error processing cursor row", e)
                        }
                    }
                } ?: run {
                    Log.e(TAG, "Cursor is NULL for pattern: $pattern - Permission issue?")
                }
            }

            Log.i(TAG, "=== Scan Complete ===")
            Log.i(TAG, "Results: Added=$addedCount, Skipped (duplicates)=$skippedCount, Errors=$errorCount")
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error during screenshot scan", e)
        }

        return addedCount
    }
}
