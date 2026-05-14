package com.recall.app.presentation.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.repository.ScreenshotRepository
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-shot navigation events emitted by [DetailViewModel]. */
sealed class DetailNavigationEvent {
    /** Navigate back to the previous screen (e.g. after deletion). */
    object NavigateBack : DetailNavigationEvent()
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val screenshotRepository: ScreenshotRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "DetailViewModel"
    }

    private val screenshotId: String = checkNotNull(savedStateHandle["screenshotId"])

    private val _screenshot = MutableStateFlow<Screenshot?>(null)
    val screenshot: StateFlow<Screenshot?> = _screenshot.asStateFlow()

    /** True while a delete operation is in progress — disables the confirm button. */
    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    /**
     * One-shot navigation events. Collected by [DetailScreen] to trigger back navigation
     * after deletion without the ViewModel holding a reference to the NavController.
     */
    private val _navigationEvent = MutableSharedFlow<DetailNavigationEvent>()
    val navigationEvent: SharedFlow<DetailNavigationEvent> = _navigationEvent.asSharedFlow()

    init {
        loadScreenshot()
    }

    private fun loadScreenshot() {
        viewModelScope.launch {
            _screenshot.value = screenshotRepository.getScreenshotById(screenshotId)
        }
    }

    /**
     * Deletes the current screenshot from the database and emits [DetailNavigationEvent.NavigateBack].
     * Guards against concurrent calls via [_isDeleting].
     */
    fun deleteScreenshot() {
        if (_isDeleting.value) return
        // Set synchronously before launching so rapid consecutive calls are blocked
        _isDeleting.value = true
        viewModelScope.launch {
            try {
                screenshotRepository.deleteScreenshot(screenshotId)
                _navigationEvent.emit(DetailNavigationEvent.NavigateBack)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete screenshot", e)
            } finally {
                _isDeleting.value = false
            }
        }
    }

    /**
     * Saves the edited OCR text for the current screenshot.
     * Sets isUserEdited flag to prevent automatic OCR from overriding user edits.
     */
    fun saveEditedOcrText(editedText: String) {
        viewModelScope.launch {
            val currentScreenshot = _screenshot.value ?: return@launch
            val updatedScreenshot = currentScreenshot.copy(
                ocrText = editedText,
                isUserEdited = true,
                userEditedAt = System.currentTimeMillis()
            )
            screenshotRepository.saveUserEditedOcrText(
                id = currentScreenshot.id,
                editedText = editedText
            )
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
