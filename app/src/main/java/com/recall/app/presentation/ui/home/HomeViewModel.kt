package com.recall.app.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recall.app.MainActivity
import com.recall.app.RecallApplication
import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.model.ScreenshotFilter
import com.recall.app.domain.model.SearchHistoryItem
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.recall.app.data.local.ModelDownloadState
import com.recall.app.data.local.ModelRepository
import com.recall.app.data.local.UserPreferences
import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.nlp.VectorIndexOptimized
import com.recall.app.data.worker.BackgroundOcrWorker
import com.recall.app.domain.model.IndexingStats
import com.recall.app.domain.repository.ScreenshotRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import com.recall.app.domain.usecase.searchhistory.AddSearchHistoryUseCase
import com.recall.app.domain.usecase.searchhistory.ClearSearchHistoryUseCase
import com.recall.app.domain.usecase.searchhistory.DeleteSearchHistoryUseCase
import com.recall.app.domain.usecase.searchhistory.GetSearchHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val screenshotRepository: ScreenshotRepository,
    private val getSearchHistoryUseCase: GetSearchHistoryUseCase,
    private val addSearchHistoryUseCase: AddSearchHistoryUseCase,
    private val deleteSearchHistoryUseCase: DeleteSearchHistoryUseCase,
    private val clearSearchHistoryUseCase: ClearSearchHistoryUseCase,
    private val vectorIndex: VectorIndexOptimized,
    private val modelRepository: ModelRepository,
    private val screenshotDao: ScreenshotDao,
    private val userPreferences: UserPreferences
) : ViewModel() {

    companion object {
        /** 7 days in milliseconds — the window for the RECENT filter. */
        private const val RECENT_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L

        /**
         * Poll interval for [isVectorIndexReady].
         *
         * Polling is intentional here: [VectorIndexOptimized.isReady] is a plain in-memory
         * boolean with no callback or Flow API — there is no reactive alternative.
         * The check is fast (~microseconds) and stops automatically when the screen is
         * not visible ([SharingStarted.WhileSubscribed]).
         *
         * Compare with [isIndexingActive] which uses WorkManager's built-in Flow API
         * and does NOT poll.
         */
        private const val VECTOR_INDEX_POLL_INTERVAL_MS = 2_000L

        /** WorkManager unique work name for manual resume of indexing. */
        private const val RESUME_WORK_NAME = "manual_resume_ocr"

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

    /**
     * Guards [toggleIndexing] against concurrent invocations.
     *
     * WorkManager's cancel/enqueue calls are fire-and-forget — they return before the
     * worker state change propagates back through [getWorkInfosByTagFlow] to
     * [isIndexingActive]. In the gap between a tap and the StateFlow update, a second
     * tap would read the stale [isIndexingActive.value] and enter the wrong branch.
     *
     * [java.util.concurrent.atomic.AtomicBoolean.compareAndSet] gives a lock-free,
     * thread-safe test-and-set: only the first caller proceeds; all concurrent callers
     * return immediately.
     */
    private val _toggleInFlight = java.util.concurrent.atomic.AtomicBoolean(false)

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
    // AI readiness — drives the search bar icon (AutoAwesome vs plain Search)
    // -----------------------------------------------------------------------

    /**
     * True when AI semantic search is available (model downloaded or vector index loaded).
     * Mirrors the same logic in [SearchViewModel] so both screens stay in sync.
     */
    val isVectorIndexReady: StateFlow<Boolean> = combine(
        flow {
            while (true) {
                emit(vectorIndex.isReady())
                delay(VECTOR_INDEX_POLL_INTERVAL_MS)
            }
        },
        modelRepository.downloadState
    ) { indexReady, downloadState ->
        indexReady || downloadState == ModelDownloadState.READY
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = vectorIndex.isReady()
    )

    // -----------------------------------------------------------------------
    // Processing status banner (Issue #106)
    // -----------------------------------------------------------------------

    /**
     * Live indexing progress derived directly from the DB.
     * Room re-emits on every screenshot table change, so progress bars update in real-time.
     */
    val indexingStats: StateFlow<IndexingStats> = screenshotDao.getIndexingStats()
        .map { raw ->
            IndexingStats(
                total = raw.total,
                ocrDoneCount = raw.ocrDoneCount,
                embeddingDoneCount = raw.embeddingDoneCount
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = IndexingStats.IDLE
        )

    /**
     * True when at least one indexing worker is RUNNING or ENQUEUED.
     *
     * Every indexing worker — periodic OCR, launch-time scan chain, initial deep scan, and
     * manual resume — carries the shared [RecallApplication.INDEXING_TAG] tag. Observing
     * that single tag is sufficient to cover all sources with no risk of a missed worker.
     *
     * Falls back to a static `false` flow if WorkManager is unavailable (unit tests).
     */
    val isIndexingActive: StateFlow<Boolean> = run {
        val workManager = runCatching { WorkManager.getInstance(context) }.getOrNull()
        if (workManager == null) {
            kotlinx.coroutines.flow.flowOf(false)
        } else {
            fun isActive(infos: List<WorkInfo>) =
                infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

            workManager.getWorkInfosByTagFlow(RecallApplication.INDEXING_TAG).map(::isActive)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    /**
     * True when the processing status banner should be shown.
     *
     * Logic:
     * - Hide when everything is fully indexed (no work to show)
     * - Show when indexing is actively running — even if the user previously dismissed,
     *   a new run re-shows the banner so progress is always visible
     * - Hide when indexing is idle AND the user has manually dismissed
     *
     * In plain terms: "dismiss" means "hide until the next run starts, not forever."
     */
    val isBannerVisible: StateFlow<Boolean> = combine(
        indexingStats,
        userPreferences.bannerDismissedAtFlow,
        isIndexingActive
    ) { stats, dismissedAt, active ->
        if (stats.isFullyIndexed) return@combine false   // nothing to index → always hide
        val userDismissed = dismissedAt != 0L
        !userDismissed || active   // active=true overrides dismissal — new run re-shows
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    /** Dismiss the processing status banner until the next app launch. */
    fun dismissBanner() {
        viewModelScope.launch {
            userPreferences.setBannerDismissedAt(System.currentTimeMillis())
        }
    }

    /**
     * Toggle indexing on/off.
     *
     * **Pause:** Cancels every active indexing worker — periodic OCR, launch-time scan,
     * initial deep scan, and any manual resume — by cancelling the shared
     * [RecallApplication.INDEXING_TAG] tag plus explicitly cancelling the periodic
     * unique work name (tag cancellation alone doesn't remove it from the periodic schedule).
     * The periodic worker is re-registered on the next cold launch via
     * [RecallApplication.scheduleBackgroundOcrProcessing] with [ExistingPeriodicWorkPolicy.KEEP].
     *
     * **Resume:** Enqueues [BackgroundOcrWorker] directly. A full [ScanExistingWorker]
     * rescan is intentionally skipped — the DB already knows which screenshots are PENDING.
     * Banner reset is handled automatically by the `init` block's false→true collector.
     */
    fun toggleIndexing() {
        // Reject concurrent taps: only one toggle may be in-flight at a time.
        // compareAndSet(false, true) succeeds only for the first caller; every
        // subsequent caller while the coroutine is running gets false and returns.
        if (!_toggleInFlight.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
                val workManager = runCatching { WorkManager.getInstance(context) }.getOrNull()
                    ?: return@launch  // WorkManager unavailable (test env or not initialized)

                if (isIndexingActive.value) {
                    // Cancel every worker carrying the shared indexing tag
                    workManager.cancelAllWorkByTag(RecallApplication.INDEXING_TAG)
                    // Also cancel by unique work name — periodic workers require this to stop
                    // being re-enqueued on their next interval
                    workManager.cancelUniqueWork("background_ocr_work")
                    workManager.cancelUniqueWork("launch_scan_chain")
                    workManager.cancelUniqueWork(RESUME_WORK_NAME)
                    workManager.cancelUniqueWork(MainActivity.INITIAL_SCAN_WORK_NAME)
                } else {
                    // Resume: process whatever is currently PENDING in the DB.
                    // resetBannerDismissed() is intentionally omitted — the init block collector
                    // already clears it on the isIndexingActive false→true transition.
                    val ocrRequest = OneTimeWorkRequestBuilder<BackgroundOcrWorker>()
                        .addTag(RecallApplication.INDEXING_TAG)
                        .build()
                    workManager.enqueueUniqueWork(
                        RESUME_WORK_NAME,
                        ExistingWorkPolicy.REPLACE,
                        ocrRequest
                    )
                }
            } finally {
                // Always release the guard — even if WorkManager throws — so the button
                // is never permanently locked out.
                _toggleInFlight.set(false)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Initialisation — load first page eagerly
    // -----------------------------------------------------------------------

    init {
        loadNextPage()

        // Observe DB row count reactively so that screenshots inserted by ScanExistingWorker
        // (which runs *after* the first loadNextPage() snapshot) appear immediately in the grid.
        // When the count grows beyond what we've already loaded, reset and reload from scratch.
        viewModelScope.launch {
            var previousCount = -1
            screenshotDao.getScreenshotCountFlow().collect { dbCount ->
                if (previousCount >= 0 && dbCount > previousCount) {
                    // The DB gained new rows since the last emission (ScanExistingWorker
                    // inserted screenshots while the paginated snapshot was stale) —
                    // reload from the top so they appear immediately.
                    // Note: comparing against previousCount, not _loadedScreenshots.value.size,
                    // because the paginated window is always smaller than the total and would
                    // trigger spurious reloads on every DB write (OCR updates, etc.).
                    refresh()
                }
                previousCount = dbCount
            }
        }

        // When a new indexing run starts (false → true), clear the dismissed timestamp so:
        // 1. The banner re-shows immediately (handled by isBannerVisible logic above)
        // 2. The next manual dismiss works cleanly from a fresh state
        viewModelScope.launch {
            var previous = false
            isIndexingActive.collect { active ->
                if (active && !previous) {
                    userPreferences.resetBannerDismissed()
                }
                previous = active
            }
        }
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

    fun addSearchHistory(query: String) {
        viewModelScope.launch {
            addSearchHistoryUseCase(query)
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
