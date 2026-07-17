package com.recall.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.recall.app.domain.model.PipelineFailureCode
import com.recall.app.domain.model.Screenshot

/**
 * Room entity for a screenshot in the indexing pipeline.
 *
 * Pipeline state is derived from the actual data columns rather than a separate flag:
 *   - ocrText IS NULL  AND pipelineCode = 0  → OCR pending
 *   - ocrText NOT NULL AND embeddingByteArray IS NULL AND pipelineCode = 0 → embedding pending
 *   - ocrText NOT NULL AND embeddingByteArray NOT NULL → fully indexed
 *   - pipelineCode != 0 → permanently failed (see [PipelineFailureCode])
 *
 * USER EDIT TRACKING: isUserEdited prevents the pipeline from overwriting user-edited OCR text.
 * Any user edit must be non-blank; the edit screen enforces this to prevent ocrText being set
 * to null (which would make the row look OCR-pending again).
 *
 * RETRY TRACKING: ocrRetryCount and embeddingRetryCount are tracked separately so transient
 * embedding failures do not burn through the OCR retry budget on rows that already have valid
 * OCR text.
 */
@Entity(
    tableName = "screenshots",
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["ocrText", "embeddingByteArray", "pipelineCode", "isUserEdited"])
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
    /**
     * Terminal failure code — see [PipelineFailureCode].
     * 0 = no failure; pipeline state is derived from [ocrText] and [embeddingByteArray].
     */
    val pipelineCode: Int = PipelineFailureCode.NONE,
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
    /** Package name of the app that created this screenshot (e.g. "com.whatsapp"). Populated from
     *  MediaStore.Images.Media.OWNER_PACKAGE_NAME on API 29+; empty string on older devices. */
    val appName: String = ""
)

@Entity(tableName = "screenshots_fts")
@Fts4(contentEntity = ScreenshotEntity::class)
data class FtsScreenshotEntity(
    val ocrText: String?
)

fun ScreenshotEntity.toDomainModel(): Screenshot {
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
        pipelineCode = pipelineCode,
        appName = appName
    )
}
