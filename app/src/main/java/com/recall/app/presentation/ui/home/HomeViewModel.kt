package com.recall.app.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.model.ScreenshotFilter
import com.recall.app.domain.model.SearchHistoryItem
import com.recall.app.domain.repository.ScreenshotRepository
import com.recall.app.domain.usecase.searchhistory.ClearSearchHistoryUseCase
import com.recall.app.domain.usecase.searchhistory.DeleteSearchHistoryUseCase
import com.recall.app.domain.usecase.searchhistory.GetSearchHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val screenshotRepository: ScreenshotRepository,
    private val getSearchHistoryUseCase: GetSearchHistoryUseCase,
    private val deleteSearchHistoryUseCase: DeleteSearchHistoryUseCase,
    private val clearSearchHistoryUseCase: ClearSearchHistoryUseCase
) : ViewModel() {

    companion object {
        /** 7 days in milliseconds — the window for the RECENT filter. */
        private const val RECENT_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L

        /**
         * Number of screenshots loaded per page.
         * 50 items × ~2KB each = ~100KB per page — well within memory budget.
         * Keeps initial load fast while supporting large libraries.
         */
        const val PAGE_SIZE = 50
    }

    // -----------------------------------------------------------------------
    // Windowed lazy loading state
    // -----------------------------------------------------------------------

    /** Internal accumulator of all screenshots loaded so far across all pages. */
    private val _loadedScreenshots = MutableStateFlow<List<Screenshot>>(emptyList())

    /** True when a page load is in progress — prevents concurrent fetches. */
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    /** True when all pages have been loaded from the database. */
    private val _allPagesLoaded = MutableStateFlow(false)
    val allPagesLoaded: StateFlow<Boolean> = _allPagesLoaded

    // -----------------------------------------------------------------------
    // Filter state
    // -----------------------------------------------------------------------

    /** The currently active filter. Defaults to ALL (no filter). */
    private val _selectedFilter = MutableStateFlow(ScreenshotFilter.ALL)
    val selectedFilter: StateFlow<ScreenshotFilter> = _selectedFilter

    /**
     * The filtered list of screenshots based on [selectedFilter].
     * Derived from [_loadedScreenshots] combined with [_selectedFilter].
     *
     * - [ScreenshotFilter.ALL]        — all loaded screenshots, no filtering.
     * - [ScreenshotFilter.RECENT]     — screenshots from the last 7 days.
     * - [ScreenshotFilter.BY_APP]     — non-blank appName (Phase 8 full support pending).
     * - [ScreenshotFilter.SUMMARIZED] — non-blank description (Phase 7 full support pending).
     */
    val screenshots: StateFlow<List<Screenshot>> = combine(
        _loadedScreenshots,
        _selectedFilter
    ) { screenshots, filter ->
        when (filter) {
            ScreenshotFilter.ALL -> screenshots
            ScreenshotFilter.RECENT -> {
                val since = System.currentTimeMillis() - RECENT_WINDOW_MS
                screenshots.filter { it.dateCreated >= since }
            }
            ScreenshotFilter.BY_APP -> screenshots.filter { it.appName.isNotBlank() }
            ScreenshotFilter.SUMMARIZED -> screenshots.filter { it.description.isNotBlank() }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /**
     * Screenshots grouped into timeline sections (Today, Yesterday, This Week, etc.),
     * ready for direct consumption by [HomeScreen].
     *
     * Derived from [screenshots] via [buildTimelineSections] — the grouping, deduplication,
     * sorting, and sub-label computation all happen here in the ViewModel rather than
     * inside a `remember {}` block in the Composable, improving testability and reducing
     * unnecessary recompositions.
     */
    val timelineSections: StateFlow<List<TimelineSection>> = screenshots
        .map { buildTimelineSections(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // -----------------------------------------------------------------------
    // Search history
    // -----------------------------------------------------------------------

    val searchHistory: StateFlow<List<SearchHistoryItem>> = getSearchHistoryUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // -----------------------------------------------------------------------
    // Initialisation — load first page eagerly
    // -----------------------------------------------------------------------

    init {
        loadNextPage()
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Loads the next page of screenshots into [_loadedScreenshots].
     * Safe to call multiple times — guards against concurrent loads and
     * no-ops when all pages are already loaded.
     */
    fun loadNextPage() {
        if (_isLoadingMore.value || _allPagesLoaded.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val offset = _loadedScreenshots.value.size
                val page = screenshotRepository.getScreenshotPage(PAGE_SIZE, offset)

                if (page.isEmpty()) {
                    _allPagesLoaded.value = true
                } else {
                    _loadedScreenshots.update { current -> current + page }
                    // If we got fewer items than a full page, we've reached the end
                    if (page.size < PAGE_SIZE) {
                        _allPagesLoaded.value = true
                    }
                }
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /**
     * Refreshes the screenshot list from scratch (e.g. after a new screenshot is added).
     * Resets pagination state and reloads the first page.
     */
    fun refresh() {
        _loadedScreenshots.value = emptyList()
        _allPagesLoaded.value = false
        loadNextPage()
    }

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
