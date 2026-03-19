package com.recall.app.domain.usecase

interface OcrProcessor {
    /**
     * Extracts text from an image file at the given path.
     * @param imagePath Absolute path to the source image.
     * @return Extracted plain text string, or null if nothing was found or extraction failed.
     */
    suspend fun process(imagePath: String): String?
}
