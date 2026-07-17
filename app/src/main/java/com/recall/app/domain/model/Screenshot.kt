package com.recall.app.domain.model

/**
 * Domain model for a screenshot.
 *
 * @property pipelineCode Terminal failure code — see [PipelineFailureCode]. 0 = no failure.
 * @property isUserEdited True if the user has manually edited the OCR text.
 * @property userEditedAt Timestamp when the user last edited the OCR text (null if never edited).
 * @property ocrRetryCount Number of times OCR processing has been retried (prevents infinite loops).
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
    val pipelineCode: Int = PipelineFailureCode.NONE
)
