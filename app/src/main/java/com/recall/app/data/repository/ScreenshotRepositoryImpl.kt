package com.recall.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.room.withTransaction
import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.data.local.entity.toDomainModel
import com.recall.app.domain.model.ProcessingState
import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.repository.ScreenshotRepository
import com.recall.app.domain.usecase.EmbeddingGenerator
import com.recall.app.domain.usecase.OcrProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
        Log.d("ScreenshotRepository", "searchFts called with query: '$query'")
        // Pass query as-is to DAO - DAO handles wildcard matching
        val result = screenshotDao.searchFts(query).map { it.toDomainModel() }
        Log.d("ScreenshotRepository", "searchFts returned ${result.size} results")
        return result
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
            processingState = ProcessingState.Done.value,
            embeddingByteArray = floatArrayToByteArray(screenshot.embedding),
            isUserEdited = screenshot.isUserEdited,
            userEditedAt = screenshot.userEditedAt
        )
        screenshotDao.insert(entity) // Return value ignored - caller doesn't need rowId
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
        val existingEntity = screenshotDao.getScreenshotById(screenshot.id)

        // Preserve isUserEdited flag if caller isn't explicitly updating it
        // This prevents overwriting user edits when the domain model has default isUserEdited = false
        val shouldPreserveUserEditedFlag = existingEntity?.isUserEdited == true &&
                                            !screenshot.isUserEdited

        // CRITICAL FIX: Preserve user's edited OCR text to prevent data loss
        // If isUserEdited flag should be preserved, also preserve the ocrText
        val finalOcrText = if (shouldPreserveUserEditedFlag && existingEntity != null) {
            existingEntity.ocrText  // Keep user's edited text
        } else {
            screenshot.ocrText
        }

        val entity = ScreenshotEntity(
            id = screenshot.id,
            filePath = screenshot.filePath,
            fileName = screenshot.fileName,
            dateCreated = screenshot.dateCreated,
            dateIndexed = screenshot.dateIndexed,
            width = screenshot.width,
            height = screenshot.height,
            ocrText = finalOcrText,  // Use preserved text
            category = screenshot.category,
            tagsJson = screenshot.tags.joinToString(","),
            processingState = ProcessingState.Done.value,
            embeddingByteArray = floatArrayToByteArray(screenshot.embedding),
            isUserEdited = if (shouldPreserveUserEditedFlag) true else screenshot.isUserEdited,
            userEditedAt = if (shouldPreserveUserEditedFlag) existingEntity?.userEditedAt else screenshot.userEditedAt,
            ocrRetryCount = screenshot.ocrRetryCount
        )
        // Use REPLACE strategy to properly update existing records
        screenshotDao.insertOrReplace(entity)
    }

    override suspend fun saveUserEditedOcrText(id: String, editedText: String) {
        screenshotDao.saveUserEditedOcrText(
            id = id,
            editedOcrText = editedText,
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun deleteScreenshot(id: String) {
        screenshotDao.deleteById(id)
    }

    override suspend fun processOcr(id: String): Screenshot? {
        val entity = screenshotDao.getScreenshotById(id) ?: return null

        // Skip if user has manually edited the text
        if (entity.isUserEdited) {
            Log.d(TAG, "Skipping manual OCR - user has edited screenshot: $id")
            return entity.toDomainModel()
        }

        // Check if file exists
        val file = java.io.File(entity.filePath)
        if (!file.exists()) {
            Log.w(TAG, "File not found: ${entity.filePath}")
            // Increment retry count for missing file and return updated entity
            val newRetryCount = entity.ocrRetryCount + 1
            screenshotDao.incrementOcrRetryCount(id)
            return entity.copy(ocrRetryCount = newRetryCount).toDomainModel()
        }

        try {
            // Run OCR
            val extractedText = ocrProcessor.process(entity.filePath)

            // Consolidated null/blank check - return updated entity with incremented retry count
            if (extractedText.isNullOrBlank()) {
                Log.w(TAG, "OCR failed or returned empty text for ${entity.filePath}")
                val newRetryCount = entity.ocrRetryCount + 1
                screenshotDao.incrementOcrRetryCount(id)
                // Return updated entity instead of old one
                return entity.copy(ocrRetryCount = newRetryCount).toDomainModel()
            }

            // Generate embedding
            val embedding = extractedText.let { text ->
                embeddingGenerator.generate(text)
            }

            // Update database with successful OCR result
            val updatedEntity = entity.copy(
                ocrText = extractedText,
                embeddingByteArray = embedding?.let { floatArrayToByteArray(it) },
                processingState = ProcessingState.Done.value,
                dateIndexed = System.currentTimeMillis(),
                ocrRetryCount = 0  // Reset retry count on success
            )

            screenshotDao.update(updatedEntity)
            return updatedEntity.toDomainModel()

        } catch (e: Exception) {
            Log.e(TAG, "OCR processing failed for ${entity.filePath}", e)
            // Increment retry count on exception and return updated entity
            val newRetryCount = entity.ocrRetryCount + 1
            screenshotDao.incrementOcrRetryCount(id)
            // Return updated entity instead of throwing exception
            return entity.copy(ocrRetryCount = newRetryCount).toDomainModel()
        }
    }

    override suspend fun insertOrUpdateWithOcr(
        filePath: String,
        ocrText: String?,
        embedding: FloatArray?
    ): String = withContext(Dispatchers.IO) {
        val embeddingBytes = embedding?.let { floatArrayToByteArray(it) }
        screenshotDao.insertOrUpdateWithOcr(
            filePath = filePath,
            ocrText = ocrText,
            embedding = embeddingBytes
        )
    }

    suspend fun scanExistingScreenshots(): Int {
        var addedCount = 0
        var skippedCount = 0
        var errorCount = 0

        // Check permissions first
        val hasPermission = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10-12: No runtime permission needed for MediaStore
                true
            }
            else -> {
                context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        }

        if (!hasPermission) {
            Log.e(TAG, "Missing required storage permission")
            return 0
        }

        // CRITICAL PERFORMANCE FIX: Load all existing paths ONCE instead of N+1 queries
        // Before: 500 screenshots = 500 DB queries (one per screenshot)
        // After: 1 DB query + 500 HashSet lookups (O(1) each)
        val existingPaths = screenshotDao.getAllScreenshotPaths().toSet()
        Log.d(TAG, "Loaded ${existingPaths.size} existing screenshot paths for comparison")

        val contentResolver: ContentResolver = context.contentResolver
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        // OWNER_PACKAGE_NAME is only available on API 29 (Android 10) and above.
        // On older devices we omit it from the projection and fall back to an empty string.
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.OWNER_PACKAGE_NAME
            )
        } else {
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.RELATIVE_PATH
            )
        }

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
                    // OWNER_PACKAGE_NAME is guaranteed in the projection on API 29+; absent on older
                    val ownerPackageColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.OWNER_PACKAGE_NAME)
                    } else {
                        -1
                    }

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
                            // Read source app package name on API 29+; empty string on older devices
                            val appName = if (ownerPackageColumn >= 0) {
                                cursor.getString(ownerPackageColumn) ?: ""
                            } else {
                                ""
                            }

                            Log.d(TAG, "Processing: $fileName | Path: $filePath | RelPath: $relativePath")

                            // Skip if file doesn't exist
                            val file = java.io.File(filePath)
                            if (!file.exists()) {
                                Log.w(TAG, "Skipping non-existent file: $filePath")
                                continue
                            }

                            // CRITICAL FIX: O(1) HashSet lookup instead of DB query
                            // Before: val existing = screenshotDao.getScreenshotByPath(filePath) // DB query
                            // After: existingPaths.contains(filePath) // O(1) HashSet lookup
                            if (existingPaths.contains(filePath)) {
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
                                processingState = ProcessingState.Pending.value,
                                appName = appName
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
