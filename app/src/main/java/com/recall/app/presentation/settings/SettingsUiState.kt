package com.recall.app.presentation.settings

/**
 * UI state for Settings screen.
 */
data class SettingsUiState(
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
