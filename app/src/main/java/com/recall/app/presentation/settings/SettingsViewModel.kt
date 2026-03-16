package com.recall.app.presentation.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recall.app.data.repository.ScreenshotDetectionRepositoryImpl
import com.recall.app.data.repository.ScreenshotRepository
import com.recall.app.domain.repository.OcrRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Settings screen.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val screenshotRepository: ScreenshotRepository,
    private val detectionRepository: ScreenshotDetectionRepositoryImpl,
    private val ocrRepository: OcrRepository
) : ViewModel() {

    private val _uiState = MutableLiveData(SettingsUiState())
    val uiState: LiveData<SettingsUiState> = _uiState

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            // Load model status
            _uiState.value = _uiState.value?.copy(
                modelStatus = "E5-small: Ready",
                modelSize = "Size: ~150 MB (simulated)"
            )

            // Load screenshot count - this is the total count from database
            screenshotRepository.getScreenshotCount()
                .collect { count ->
                    val indexSizeMB = calculateIndexSize(count)
                    _uiState.value = _uiState.value?.copy(
                        screenshotCount = count,
                        totalCount = count,
                        processedCount = count, // All screenshots in DB are "processed" (indexed)
                        indexSize = indexSizeMB
                    )
                }
        }

        // Load version
        _uiState.value = _uiState.value?.copy(versionName = "1.0.0")
    }

    private fun calculateIndexSize(screenshotCount: Int): String {
        // Estimate ~0.5 MB per screenshot for embeddings + metadata
        val sizeMB = screenshotCount * 0.5
        return if (sizeMB < 1) {
            "<1 MB"
        } else {
            "${sizeMB.toInt()} MB"
        }
    }

    fun onPermissionsResult(allGranted: Boolean) {
        if (allGranted) {
            onPermissionsGranted()
        }
    }

    fun onPermissionsGranted() {
        reindexScreenshots()
    }

    fun reindexScreenshots() {
        viewModelScope.launch {
            _uiState.value = _uiState.value?.copy(isProcessing = true)

            try {
                // Get all screenshots as a one-time list (not a Flow that keeps emitting)
                val allScreenshots = screenshotRepository.getAllScreenshotsForProcessing().first()
                val totalCount = allScreenshots.size
                var processedCount = 0
                
                android.util.Log.d("SettingsViewModel", "Starting OCR processing for $totalCount screenshots")
                
                for (screenshot in allScreenshots) {
                    try {
                        // Process OCR directly
                        val ocrResult = ocrRepository.extractText(
                            java.io.File(screenshot.filePath),
                            enhanceImage = true
                        )
                        
                        // Update screenshot with OCR result
                        val updated = screenshot.copy(
                            ocrText = ocrResult.text.take(5000),
                            summary = screenshot.summary ?: ocrResult.text.take(200),
                            processedAt = System.currentTimeMillis(),
                            isIndexed = true,
                            processingStatus = com.recall.app.data.local.entity.ScreenshotEntity.ProcessingStatus.COMPLETED
                        )
                        
                        screenshotRepository.updateScreenshot(updated)
                        processedCount++
                        
                        android.util.Log.d("SettingsViewModel", "Processed OCR for screenshot ${screenshot.id}: ${ocrResult.text.length} chars")
                    } catch (e: Exception) {
                        android.util.Log.e("SettingsViewModel", "OCR failed for screenshot ${screenshot.id}: ${e.message}", e)
                    }
                }
                
                android.util.Log.d("SettingsViewModel", "OCR processing complete: $processedCount/$totalCount screenshots processed")
                
                _uiState.value = _uiState.value?.copy(
                    isProcessing = false,
                    screenshotCount = totalCount,
                    totalCount = totalCount,
                    processedCount = processedCount,
                    indexSize = calculateIndexSize(totalCount)
                )
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Reindex failed: ${e.message}", e)
                _uiState.value = _uiState.value?.copy(
                    isProcessing = false,
                    error = e.message
                )
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            try {
                screenshotRepository.deleteAllScreenshots()
                _uiState.value = _uiState.value?.copy(
                    screenshotCount = 0,
                    processedCount = 0,
                    totalCount = 0,
                    indexSize = "0 MB"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value?.copy(error = e.message)
            }
        }
    }

    fun setIndexingEnabled(enabled: Boolean) {
        if (enabled) {
            detectionRepository.startMonitoring()
        } else {
            detectionRepository.stopMonitoring()
        }
        _uiState.value = _uiState.value?.copy(isIndexingEnabled = enabled)
    }
}

data class UiState(
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val modelStatus: String = "E5-small: Ready",
    val modelSize: String = "Size: ~150 MB",
    val indexSize: String = "0 MB",
    val screenshotCount: Int = 0,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val isIndexingEnabled: Boolean = true,
    val versionName: String = "1.0.0"
)
