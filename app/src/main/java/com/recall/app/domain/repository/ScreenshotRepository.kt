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
}
