package com.recall.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.recall.app.domain.model.Screenshot

/**
 * CRITICAL FIX: Added unique index on filePath to prevent duplicate entries for the same physical file.
 * This ensures that even if multiple workers try to insert the same file simultaneously,
 * Room will enforce uniqueness at the database level, preventing the duplicate images issue.
 */
@Entity(
    tableName = "screenshots",
    indices = [Index(value = ["filePath"], unique = true)]
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
    val processingState: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val embeddingByteArray: ByteArray? = null
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
        tags = tagsList
    )
}
