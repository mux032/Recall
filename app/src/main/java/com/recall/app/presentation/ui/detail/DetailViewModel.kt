package com.recall.app.presentation.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.repository.ScreenshotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val screenshotRepository: ScreenshotRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val screenshotId: String = checkNotNull(savedStateHandle["screenshotId"])

    private val _screenshot = MutableStateFlow<Screenshot?>(null)
    val screenshot: StateFlow<Screenshot?> = _screenshot.asStateFlow()

    init {
        loadScreenshot()
    }

    private fun loadScreenshot() {
        viewModelScope.launch {
            _screenshot.value = screenshotRepository.getScreenshotById(screenshotId)
        }
    }

    /**
     * Saves the edited OCR text for the current screenshot.
     * This updates the database so changes persist across app sessions.
     */
    fun saveEditedOcrText(editedText: String) {
        viewModelScope.launch {
            val currentScreenshot = _screenshot.value ?: return@launch
            val updatedScreenshot = currentScreenshot.copy(ocrText = editedText)
            screenshotRepository.updateScreenshot(updatedScreenshot)
            _screenshot.value = updatedScreenshot
        }
    }

    /**
     * Manually triggers OCR for the current screenshot.
     * Used when the user clicks the "Generate" icon in the detail screen.
     */
    fun prioritizeOcr() {
        viewModelScope.launch {
            val updatedScreenshot = screenshotRepository.processOcr(screenshotId)
            if (updatedScreenshot != null) {
                _screenshot.value = updatedScreenshot
            }
        }
    }
}
