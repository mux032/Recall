package com.recall.app.domain.model

/**
 * Domain model representing OCR extraction result.
 */
data class OcrResult(
    val text: String,
    val confidence: Float,
    val textBlocks: List<TextBlock>,
    val language: String? = null,
    val processingTimeMs: Long
) {
    val isEmpty: Boolean = text.isBlank()
    val wordCount: Int = text.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
}

/**
 * Represents a detected text block.
 */
data class TextBlock(
    val text: String,
    val confidence: Float,
    val boundingBox: BoundingBox?,
    val lines: List<TextLine>
)

/**
 * Represents a line of text.
 */
data class TextLine(
    val text: String,
    val confidence: Float,
    val elements: List<TextElement>
)

/**
 * Represents a text element (word).
 */
data class TextElement(
    val text: String,
    val confidence: Float,
    val boundingBox: BoundingBox?
)

/**
 * Bounding box coordinates.
 */
data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int = right - left
    val height: Int = bottom - top
}
