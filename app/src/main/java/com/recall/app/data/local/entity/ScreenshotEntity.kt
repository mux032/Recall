package com.recall.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey
import com.recall.app.domain.model.Screenshot

@Entity(tableName = "screenshots")
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
