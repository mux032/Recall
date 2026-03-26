package com.recall.app.domain.model

/**
 * Domain model for Screenshot with user edit tracking.
 *
 * @property isUserEdited True if the user has manually edited the OCR text
 * @property userEditedAt Timestamp when the user last edited the OCR text (null if never edited)
 * @property ocrRetryCount Number of times OCR processing has been retried for this screenshot (prevents infinite loops)
 * @property processingState The current processing state of the screenshot in the OCR pipeline
 */
data class Screenshot(
    val id: String,
    val filePath: String,
    val fileName: String,
    val dateCreated: Long,
    val dateIndexed: Long,
    val width: Int,
    val height: Int,
    val ocrText: String? = null,
    val category: String = "Uncategorized",
    val tags: List<String> = emptyList(),
    val embedding: FloatArray? = null,
    val appName: String = "",
    val description: String = "",
    val timestamp: Long = dateCreated,
    val isUserEdited: Boolean = false,
    val userEditedAt: Long? = null,
    val ocrRetryCount: Int = 0,
    val processingState: String = "PENDING"
) {
    // Helper property to convert between String and ProcessingState
    val processingStateEnum: ProcessingState
        get() = ProcessingState.fromValue(processingState)
}
