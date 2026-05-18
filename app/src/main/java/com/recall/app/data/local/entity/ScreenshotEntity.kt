package com.recall.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.recall.app.domain.model.ProcessingState
import com.recall.app.domain.model.Screenshot

/**
 * CRITICAL FIX: Added unique index on filePath to prevent duplicate entries for the same physical file.
 * This ensures that even if multiple workers try to insert the same file simultaneously,
 * Room will enforce uniqueness at the database level, preventing the duplicate images issue.
 *
 * USER EDIT TRACKING: Added isUserEdited and userEditedAt fields to track manual OCR text edits.
 * When isUserEdited is true, OCR processing will not override the user's edited text.
 *
 * RETRY TRACKING: Added ocrRetryCount field to prevent infinite loops on persistent OCR failures.
 * When ocrRetryCount reaches MAX_RETRIES, the screenshot will no longer be processed automatically.
 */
@Entity(
    tableName = "screenshots",
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["processingState", "isUserEdited"])
    ]
)
data class ScreenshotEntity(
    @PrimaryKey val id: String,
    val filePath: String,
    val fileName: String,
    val dateCreated: Long,
    val dateIndexed: Long,
    val width: Int,
    val height: Int,
    val ocrText: String?,
    val category: String,
    val tagsJson: String,
    /** Stored as a String in the DB via [com.recall.app.data.local.converter.ProcessingStateConverter]. */
    val processingState: ProcessingState = ProcessingState.Pending,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val embeddingByteArray: ByteArray? = null,
    val isUserEdited: Boolean = false,
    val userEditedAt: Long? = null,
    val ocrRetryCount: Int = 0,
    /**
     * Number of times embedding generation has been retried for this screenshot.
     * Tracked separately from [ocrRetryCount] so that transient embedding failures
     * (model not yet loaded, OOM, etc.) do not burn through the OCR retry budget
     * and permanently orphan rows that already have valid OCR text.
     */
    val embeddingRetryCount: Int = 0,
    /** Package name of the app that created this screenshot (e.g. \"com.whatsapp\"). Populated from
     *  MediaStore.Images.Media.OWNER_PACKAGE_NAME on API 29+; empty string on older devices. */
    val appName: String = ""
)

@Entity(tableName = "screenshots_fts")
@Fts4(contentEntity = ScreenshotEntity::class)
data class FtsScreenshotEntity(
    val ocrText: String?
)

fun ScreenshotEntity.toDomainModel(): Screenshot {
    // Basic parser for Phase 1
    val tagsList = if (tagsJson.isBlank()) emptyList() else tagsJson.split(",")
    return Screenshot(
        id = id,
        filePath = filePath,
        fileName = fileName,
        dateCreated = dateCreated,
        dateIndexed = dateIndexed,
        width = width,
        height = height,
        ocrText = ocrText,
        category = category,
        tags = tagsList,
        isUserEdited = isUserEdited,
        userEditedAt = userEditedAt,
        ocrRetryCount = ocrRetryCount,
        processingState = processingState.value,
        appName = appName
    )
}
