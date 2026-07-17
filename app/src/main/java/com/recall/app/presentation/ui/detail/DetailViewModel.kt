package com.recall.app.presentation.ui.detail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.recall.app.data.worker.IndexingPipelineWorker
import com.recall.app.domain.model.PipelineFailureCode
import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.repository.ScreenshotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val workManager: WorkManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "DetailViewModel"
    }

    private val screenshotId: String = checkNotNull(savedStateHandle["screenshotId"])

    private val _screenshot = MutableStateFlow<Screenshot?>(null)
    val screenshot: StateFlow<Screenshot?> = _screenshot.asStateFlow()

    /**
     * True when the screenshot has permanently failed OCR — drives the Refresh button in the UI.
     * Implemented as a [StateFlow] so collectors recompose correctly when [pipelineCode] changes
     * (e.g. after [retryOcr] reloads the row with pipelineCode = NONE).
     */
    val isOcrFailed: StateFlow<Boolean> = _screenshot
        .map { it?.pipelineCode == PipelineFailureCode.OCR_FAILED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** True while a delete operation is in progress — disables the confirm button. */
    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    /** True while an OCR retry is in progress — replaces the Refresh icon with a spinner. */
    private val _isRetrying = MutableStateFlow(false)
    val isRetrying: StateFlow<Boolean> = _isRetrying.asStateFlow()

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
     * Also resets pipelineCode so the embedding pipeline picks up the corrected text.
     */
    fun saveEditedOcrText(editedText: String) {
        viewModelScope.launch {
            val currentScreenshot = _screenshot.value ?: return@launch
            screenshotRepository.saveUserEditedOcrText(
                id = currentScreenshot.id,
                editedText = editedText
            )
            // Reload from DB so pipelineCode reset is reflected in UI state
            _screenshot.value = screenshotRepository.getScreenshotById(screenshotId)
        }
    }

    /**
     * Manually triggers OCR for the current screenshot.
     * Used when the user clicks the \"Generate\" icon in the detail screen.
     */
    fun prioritizeOcr() {
        viewModelScope.launch {
            val updatedScreenshot = screenshotRepository.processOcr(screenshotId)
            if (updatedScreenshot != null) {
                _screenshot.value = updatedScreenshot
            }
        }
    }

    /**
     * Resets a permanently-failed screenshot back to OCR-pending state and immediately
     * enqueues [IndexingPipelineWorker] so the user sees it re-indexing within seconds
     * rather than waiting for the next scheduled run.
     */
    fun retryOcr() {
        viewModelScope.launch {
            _isRetrying.value = true
            try {
                screenshotRepository.resetForOcrRetry(screenshotId)
                // Reload so UI reflects the reset state
                _screenshot.value = screenshotRepository.getScreenshotById(screenshotId)
                // Kick the pipeline immediately
                workManager.enqueueUniqueWork(
                    IndexingPipelineWorker.PIPELINE_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<IndexingPipelineWorker>()
                        .addTag(IndexingPipelineWorker.INDEXING_TAG)
                        .build()
                )
                Log.i(TAG, "OCR retry enqueued for $screenshotId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset screenshot for OCR retry", e)
            } finally {
                _isRetrying.value = false
            }
        }
    }
}
