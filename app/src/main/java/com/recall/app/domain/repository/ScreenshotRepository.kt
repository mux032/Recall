package com.recall.app.domain.repository

import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.model.SearchFilter
import kotlinx.coroutines.flow.Flow

interface ScreenshotRepository {
    fun getAllScreenshots(): Flow<List<Screenshot>>
    suspend fun getScreenshotById(id: String): Screenshot?
    suspend fun getScreenshotsByIds(ids: List<String>): List<Screenshot>
    suspend fun searchFts(query: String): List<Screenshot>
    suspend fun addScreenshot(screenshot: Screenshot)
    suspend fun updateScreenshot(screenshot: Screenshot)
    suspend fun deleteScreenshot(id: String)
    suspend fun processOcr(id: String): Screenshot?

    /**
     * Insert or update a screenshot with OCR results.
     * Returns the screenshot ID.
     * Will NOT update if user has manually edited the OCR text.
     */
    suspend fun insertOrUpdateWithOcr(
        filePath: String,
        ocrText: String?,
        embedding: FloatArray?
    ): String

    /**
     * Save user-edited OCR text.
     * Sets isUserEdited flag to prevent automatic OCR from overriding.
     */
    suspend fun saveUserEditedOcrText(id: String, editedText: String)
}
