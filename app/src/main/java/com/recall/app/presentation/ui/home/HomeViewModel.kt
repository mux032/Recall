package com.recall.app.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.model.ScreenshotFilter
import com.recall.app.domain.model.SearchHistoryItem
import com.recall.app.domain.usecase.GetAllScreenshotsUseCase
import com.recall.app.domain.usecase.searchhistory.ClearSearchHistoryUseCase
import com.recall.app.domain.usecase.searchhistory.DeleteSearchHistoryUseCase
import com.recall.app.domain.usecase.searchhistory.GetSearchHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    getAllScreenshotsUseCase: GetAllScreenshotsUseCase,
    private val getSearchHistoryUseCase: GetSearchHistoryUseCase,
    private val deleteSearchHistoryUseCase: DeleteSearchHistoryUseCase,
    private val clearSearchHistoryUseCase: ClearSearchHistoryUseCase
) : ViewModel() {

    companion object {
        /** 7 days in milliseconds — the window for the RECENT filter. */
        private const val RECENT_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L
    }

    /** The currently active filter. Defaults to ALL (no filter). */
    private val _selectedFilter = MutableStateFlow(ScreenshotFilter.ALL)
    val selectedFilter: StateFlow<ScreenshotFilter> = _selectedFilter

    private val allScreenshots = getAllScreenshotsUseCase()

    /**
     * The filtered list of screenshots based on [selectedFilter].
     *
     * - [ScreenshotFilter.ALL]       — all screenshots, no filtering.
     * - [ScreenshotFilter.RECENT]    — screenshots from the last 7 days.
     * - [ScreenshotFilter.BY_APP]    — empty until Phase 8 CategoryClassifier populates appName.
     * - [ScreenshotFilter.SUMMARIZED]— screenshots with a non-blank description (AI summary).
     */
    val screenshots: StateFlow<List<Screenshot>> = combine(
        allScreenshots,
        _selectedFilter
    ) { screenshots, filter ->
        when (filter) {
            ScreenshotFilter.ALL -> screenshots
            ScreenshotFilter.RECENT -> {
                val since = System.currentTimeMillis() - RECENT_WINDOW_MS
                screenshots.filter { it.dateCreated >= since }
            }
            ScreenshotFilter.BY_APP -> {
                // Phase 8: full category classifier not yet available.
                // Show only screenshots that have a known source app for now.
                screenshots.filter { it.appName.isNotBlank() }
            }
            ScreenshotFilter.SUMMARIZED -> {
                // Phase 7: AI summary backend not yet available.
                // Show only screenshots that already have a description.
                screenshots.filter { it.description.isNotBlank() }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val searchHistory: StateFlow<List<SearchHistoryItem>> = getSearchHistoryUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Sets the active filter.
     * - Tapping [ScreenshotFilter.ALL] always resets to ALL (no toggle needed).
     * - Tapping any other filter while it is already active deselects it (returns to ALL).
     */
    fun setFilter(filter: ScreenshotFilter) {
        _selectedFilter.value = when {
            filter == ScreenshotFilter.ALL -> ScreenshotFilter.ALL
            _selectedFilter.value == filter -> ScreenshotFilter.ALL
            else -> filter
        }
    }

    fun deleteHistoryItem(id: String) {
        viewModelScope.launch {
            deleteSearchHistoryUseCase(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            clearSearchHistoryUseCase()
        }
    }
}
