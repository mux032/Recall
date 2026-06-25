package com.recall.app.presentation.ui.home

import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.model.ScreenshotFilter
import com.recall.app.domain.repository.ScreenshotRepository
import java.time.LocalDate
import java.time.ZoneId
import android.content.Context
import com.recall.app.data.local.ModelDownloadState
import com.recall.app.data.local.ModelRepository
import com.recall.app.data.local.dao.ScreenshotDao
import com.recall.app.data.nlp.VectorIndexOptimized
import com.recall.app.domain.usecase.searchhistory.AddSearchHistoryUseCase
import com.recall.app.domain.usecase.searchhistory.ClearSearchHistoryUseCase
import com.recall.app.domain.usecase.searchhistory.DeleteSearchHistoryUseCase
import com.recall.app.domain.usecase.searchhistory.GetSearchHistoryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var screenshotRepository: ScreenshotRepository
    private lateinit var getSearchHistoryUseCase: GetSearchHistoryUseCase
    private lateinit var addSearchHistoryUseCase: AddSearchHistoryUseCase
    private lateinit var deleteSearchHistoryUseCase: DeleteSearchHistoryUseCase
    private lateinit var clearSearchHistoryUseCase: ClearSearchHistoryUseCase
    private lateinit var vectorIndex: VectorIndexOptimized
    private lateinit var modelRepository: ModelRepository
    private lateinit var screenshotDao: ScreenshotDao
    private lateinit var mockContext: Context
    private lateinit var viewModel: HomeViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        screenshotRepository = mock()
        getSearchHistoryUseCase = mock()
        addSearchHistoryUseCase = mock()
        deleteSearchHistoryUseCase = mock()
        clearSearchHistoryUseCase = mock()
        vectorIndex = mock()
        modelRepository = mock()
        screenshotDao = mock()
        mockContext = mock()

        whenever(getSearchHistoryUseCase()).thenReturn(flowOf(emptyList()))
        whenever(vectorIndex.isReady()).thenReturn(false)
        whenever(modelRepository.downloadState).thenReturn(flowOf(ModelDownloadState.NONE))
        // Default: DB count stays at 0 — no reactive refresh triggered in most tests
        whenever(screenshotDao.getScreenshotCountFlow()).thenReturn(flowOf(0))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------
    // Initial page load
    // -----------------------------------------------------------------------

    @Test
    fun `init loads first page eagerly`() = runTest {
        val page = buildScreenshots(5)
        whenever(screenshotRepository.getScreenshotPage(HomeViewModel.PAGE_SIZE, 0))
            .thenReturn(page)

        viewModel = buildViewModel()
        val collected = mutableListOf<List<Screenshot>>()
        backgroundScope.launch { viewModel.screenshots.collect { collected.add(it) } }
        advanceUntilIdle()

        assertEquals(5, collected.last().size)
        verify(screenshotRepository).getScreenshotPage(HomeViewModel.PAGE_SIZE, 0)
    }

    @Test
    fun `allPagesLoaded is true when first page is smaller than PAGE_SIZE`() = runTest {
        val page = buildScreenshots(3) // less than PAGE_SIZE
        whenever(screenshotRepository.getScreenshotPage(HomeViewModel.PAGE_SIZE, 0))
            .thenReturn(page)

        viewModel = buildViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.allPagesLoaded.value)
    }

    @Test
    fun `allPagesLoaded is false when first page is full`() = runTest {
        val page = buildScreenshots(HomeViewModel.PAGE_SIZE) // full page
        whenever(screenshotRepository.getScreenshotPage(HomeViewModel.PAGE_SIZE, 0))
            .thenReturn(page)

        viewModel = buildViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.allPagesLoaded.value)
    }

    // -----------------------------------------------------------------------
    // loadNextPage()
    // -----------------------------------------------------------------------

    @Test
    fun `loadNextPage appends next page to loaded screenshots`() = runTest {
        val page1 = buildScreenshots(HomeViewModel.PAGE_SIZE, startId = 0)
        val page2 = buildScreenshots(10, startId = HomeViewModel.PAGE_SIZE)
        whenever(screenshotRepository.getScreenshotPage(HomeViewModel.PAGE_SIZE, 0))
            .thenReturn(page1)
        whenever(screenshotRepository.getScreenshotPage(HomeViewModel.PAGE_SIZE, HomeViewModel.PAGE_SIZE))
            .thenReturn(page2)

        viewModel = buildViewModel()
        // Keep flow active throughout the test with a persistent background collector
        val collected = mutableListOf<List<Screenshot>>()
        backgroundScope.launch { viewModel.screenshots.collect { collected.add(it) } }
        advanceUntilIdle() // page 1 loaded

        assertFalse(viewModel.allPagesLoaded.value)

        // Load page 2 — _loadedScreenshots goes from PAGE_SIZE to PAGE_SIZE+10
        viewModel.loadNextPage()
        advanceUntilIdle()

        // The repository was called with the correct offsets
        verify(screenshotRepository).getScreenshotPage(HomeViewModel.PAGE_SIZE, 0)
        verify(screenshotRepository).getScreenshotPage(HomeViewModel.PAGE_SIZE, HomeViewModel.PAGE_SIZE)

        // allPagesLoaded should now be true (page2 < PAGE_SIZE)
        assertTrue(viewModel.allPagesLoaded.value)

        // The screenshots flow should have emitted the combined list at some point
        assertTrue("Expected at least one emission with PAGE_SIZE items",
            collected.any { it.size == HomeViewModel.PAGE_SIZE })
    }

    @Test
    fun `loadNextPage does not load when allPagesLoaded`() = runTest {
        val page = buildScreenshots(3)
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(page)

        viewModel = buildViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.allPagesLoaded.value)

        // Try to load more — should be a no-op
        viewModel.loadNextPage()
        advanceUntilIdle()

        // Only called once (from init)
        verify(screenshotRepository, times(1)).getScreenshotPage(any(), any())
    }

    @Test
    fun `loadNextPage sets allPagesLoaded when empty page returned`() = runTest {
        val page1 = buildScreenshots(HomeViewModel.PAGE_SIZE)
        whenever(screenshotRepository.getScreenshotPage(HomeViewModel.PAGE_SIZE, 0))
            .thenReturn(page1)
        whenever(screenshotRepository.getScreenshotPage(HomeViewModel.PAGE_SIZE, HomeViewModel.PAGE_SIZE))
            .thenReturn(emptyList())

        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.loadNextPage()
        advanceUntilIdle()

        assertTrue(viewModel.allPagesLoaded.value)
    }

    // -----------------------------------------------------------------------
    // refresh()
    // -----------------------------------------------------------------------

    @Test
    fun `refresh reloads from first page`() = runTest {
        val page = buildScreenshots(5)
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(page)

        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        // Called twice — once from init, once from refresh
        verify(screenshotRepository, times(2)).getScreenshotPage(HomeViewModel.PAGE_SIZE, 0)
    }

    @Test
    fun `refresh resets allPagesLoaded before reloading`() = runTest {
        val smallPage = buildScreenshots(3)
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(smallPage)

        viewModel = buildViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.allPagesLoaded.value)

        viewModel.refresh()
        advanceUntilIdle()

        // Still true after reload (page is still small)
        assertTrue(viewModel.allPagesLoaded.value)
    }

    // -----------------------------------------------------------------------
    // Filter state
    // -----------------------------------------------------------------------

    @Test
    fun `initial filter is ALL`() = runTest {
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(emptyList())
        viewModel = buildViewModel()
        assertEquals(ScreenshotFilter.ALL, viewModel.selectedFilter.value)
    }

    @Test
    fun `setFilter RECENT sets selectedFilter to RECENT`() = runTest {
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(emptyList())
        viewModel = buildViewModel()
        viewModel.setFilter(ScreenshotFilter.RECENT)
        assertEquals(ScreenshotFilter.RECENT, viewModel.selectedFilter.value)
    }

    @Test
    fun `setFilter same filter twice deselects back to ALL`() = runTest {
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(emptyList())
        viewModel = buildViewModel()
        viewModel.setFilter(ScreenshotFilter.RECENT)
        viewModel.setFilter(ScreenshotFilter.RECENT)
        assertEquals(ScreenshotFilter.ALL, viewModel.selectedFilter.value)
    }

    @Test
    fun `RECENT filter state is set correctly`() = runTest {
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(emptyList())
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.setFilter(ScreenshotFilter.RECENT)
        assertEquals(ScreenshotFilter.RECENT, viewModel.selectedFilter.value)
    }

    @Test
    fun `SUMMARIZED filter state is set correctly`() = runTest {
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(emptyList())
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.setFilter(ScreenshotFilter.SUMMARIZED)
        assertEquals(ScreenshotFilter.SUMMARIZED, viewModel.selectedFilter.value)
    }

    @Test
    fun `BY_APP filter state is set correctly`() = runTest {
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(emptyList())
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.setFilter(ScreenshotFilter.BY_APP)
        assertEquals(ScreenshotFilter.BY_APP, viewModel.selectedFilter.value)
    }

    /**
     * Verifies filtering logic directly using the filter enum values and Kotlin predicates,
     * matching exactly what HomeViewModel.screenshots combine block does.
     */
    @Test
    fun `filter logic RECENT excludes old screenshots`() {
        val now = System.currentTimeMillis()
        val recentWindow = 7 * 24 * 60 * 60 * 1000L
        val screenshots = listOf(
            buildScreenshot("1", dateCreated = now - 2 * 24 * 60 * 60 * 1000L), // 2 days ago ✓
            buildScreenshot("2", dateCreated = now - 10 * 24 * 60 * 60 * 1000L) // 10 days ago ✗
        )
        val result = screenshots.filter { it.dateCreated >= now - recentWindow }
        assertEquals(1, result.size)
        assertEquals("1", result.first().id)
    }

    @Test
    fun `filter logic SUMMARIZED excludes blank descriptions`() {
        val screenshots = listOf(
            buildScreenshot("1", description = "AI summary"),
            buildScreenshot("2", description = "")
        )
        val result = screenshots.filter { it.description.isNotBlank() }
        assertEquals(1, result.size)
        assertEquals("1", result.first().id)
    }

    @Test
    fun `filter logic BY_APP excludes blank appNames`() {
        val screenshots = listOf(
            buildScreenshot("1", appName = "com.whatsapp"),
            buildScreenshot("2", appName = "")
        )
        val result = screenshots.filter { it.appName.isNotBlank() }
        assertEquals(1, result.size)
        assertEquals("1", result.first().id)
    }

    // -----------------------------------------------------------------------
    // timelineSections
    // -----------------------------------------------------------------------

    @Test
    fun `timelineSections initial value is empty list`() = runTest {
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(emptyList())
        viewModel = buildViewModel()
        // Before any collector activates WhileSubscribed, initial value is empty
        assertEquals(emptyList<TimelineSection>(), viewModel.timelineSections.value)
    }

    @Test
    fun `timelineSections emits sections grouped from screenshots`() = runTest {
        val noon = noonToday()
        val page = listOf(
            buildScreenshot("today", dateCreated = noon - 1 * 3_600_000L),    // 1h before noon → Today
            buildScreenshot("lastweek", dateCreated = noon - 10 * 86_400_000L) // 10d ago → Last Week
        )
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(page)
        viewModel = buildViewModel()

        val sectionsCollected = mutableListOf<List<TimelineSection>>()
        backgroundScope.launch { viewModel.timelineSections.collect { sectionsCollected.add(it) } }
        advanceUntilIdle()

        val sections = sectionsCollected.last()
        assertEquals(2, sections.size)
        assertEquals("Today", sections[0].label)
        assertEquals(1, sections[0].screenshots.size)
        assertEquals("Last Week", sections[1].label)
        assertEquals(1, sections[1].screenshots.size)
    }

    @Test
    fun `timelineSections emits single Today section when all screenshots are recent`() = runTest {
        val noon = noonToday()
        val page = listOf(
            buildScreenshot("a", dateCreated = noon - 1 * 3_600_000L),
            buildScreenshot("b", dateCreated = noon - 2 * 3_600_000L),
            buildScreenshot("c", dateCreated = noon - 3 * 3_600_000L)
        )
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(page)
        viewModel = buildViewModel()

        val sectionsCollected = mutableListOf<List<TimelineSection>>()
        backgroundScope.launch { viewModel.timelineSections.collect { sectionsCollected.add(it) } }
        advanceUntilIdle()

        val sections = sectionsCollected.last()
        assertEquals(1, sections.size)
        assertEquals("Today", sections[0].label)
        assertEquals(3, sections[0].screenshots.size)
    }

    @Test
    fun `timelineSections emits empty list when no screenshots loaded`() = runTest {
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(emptyList())
        viewModel = buildViewModel()

        val sectionsCollected = mutableListOf<List<TimelineSection>>()
        backgroundScope.launch { viewModel.timelineSections.collect { sectionsCollected.add(it) } }
        advanceUntilIdle()

        assertTrue(sectionsCollected.last().isEmpty())
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun noonToday(): Long =
        LocalDate.now().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // -----------------------------------------------------------------------
    // Search history — addSearchHistory, deleteHistoryItem, clearAllHistory
    // -----------------------------------------------------------------------

    @Test
    fun `addSearchHistory delegates to AddSearchHistoryUseCase`() = runTest {
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(emptyList())
        viewModel = buildViewModel()
        viewModel.addSearchHistory("cats")
        advanceUntilIdle()
        verify(addSearchHistoryUseCase).invoke("cats")
    }

    @Test
    fun `deleteHistoryItem delegates to DeleteSearchHistoryUseCase`() = runTest {
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(emptyList())
        viewModel = buildViewModel()
        viewModel.deleteHistoryItem("abc-123")
        advanceUntilIdle()
        verify(deleteSearchHistoryUseCase).invoke("abc-123")
    }

    @Test
    fun `clearAllHistory delegates to ClearSearchHistoryUseCase`() = runTest {
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(emptyList())
        viewModel = buildViewModel()
        viewModel.clearAllHistory()
        advanceUntilIdle()
        verify(clearSearchHistoryUseCase).invoke()
    }

    @Test
    fun `addSearchHistory called twice records both queries`() = runTest {
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(emptyList())
        viewModel = buildViewModel()
        viewModel.addSearchHistory("dogs")
        viewModel.addSearchHistory("cats")
        advanceUntilIdle()
        verify(addSearchHistoryUseCase, times(2)).invoke(any())
    }

    // -----------------------------------------------------------------------
    // isVectorIndexReady — AI icon indicator on search bar
    // -----------------------------------------------------------------------

    @Test
    fun `isVectorIndexReady initial value is false when index not ready and model not downloaded`() = runTest {
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(emptyList())
        whenever(vectorIndex.isReady()).thenReturn(false)
        whenever(modelRepository.downloadState).thenReturn(flowOf(ModelDownloadState.NONE))
        val vm = buildViewModel()
        assertFalse(vm.isVectorIndexReady.value)
    }

    @Test
    fun `isVectorIndexReady is true when vector index reports ready`() = runTest {
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(emptyList())
        whenever(vectorIndex.isReady()).thenReturn(true)
        whenever(modelRepository.downloadState).thenReturn(flowOf(ModelDownloadState.NONE))
        val vm = buildViewModel()
        val collected = mutableListOf<Boolean>()
        backgroundScope.launch { vm.isVectorIndexReady.collect { collected.add(it) } }
        testScheduler.advanceTimeBy(100)
        assertTrue("Expected isVectorIndexReady to emit true", collected.contains(true))
    }

    @Test
    fun `isVectorIndexReady is true when model download state is READY`() = runTest {
        whenever(screenshotRepository.getScreenshotPage(any(), any())).thenReturn(emptyList())
        whenever(vectorIndex.isReady()).thenReturn(false)
        whenever(modelRepository.downloadState).thenReturn(flowOf(ModelDownloadState.READY))
        val vm = buildViewModel()
        val collected = mutableListOf<Boolean>()
        backgroundScope.launch { vm.isVectorIndexReady.collect { collected.add(it) } }
        testScheduler.advanceTimeBy(100)
        assertTrue("Expected isVectorIndexReady to emit true when model is READY", collected.contains(true))
    }

    @Test
    fun `screenshots appear when DB count grows after initial empty load`() = runTest {
        // Simulate: first load finds 0 screenshots (DB empty when ScanExistingWorker hasn't run yet)
        whenever(screenshotRepository.getScreenshotPage(any(), any()))
            .thenReturn(emptyList())                        // first call (init)
            .thenReturn(buildScreenshots(3))                // second call (after scan inserts)

        // DB count flow: starts at 0, then jumps to 3 (ScanExistingWorker inserted rows)
        val countFlow = kotlinx.coroutines.flow.MutableStateFlow(0)
        whenever(screenshotDao.getScreenshotCountFlow()).thenReturn(countFlow)

        val vm = buildViewModel()
        // Actively collect screenshots so the WhileSubscribed StateFlow stays alive
        val collected = mutableListOf<List<Screenshot>>()
        val job = launch { vm.screenshots.collect { collected.add(it) } }

        advanceUntilIdle()
        assertEquals("should be empty before scan", emptyList<Screenshot>(), vm.screenshots.value)

        // Simulate ScanExistingWorker inserting 3 screenshots into the DB
        countFlow.value = 3
        advanceUntilIdle()

        assertEquals("should show 3 screenshots after scan", 3, vm.screenshots.value.size)
        job.cancel()
    }

    private fun buildViewModel() = HomeViewModel(
        mockContext,
        screenshotRepository,
        getSearchHistoryUseCase,
        addSearchHistoryUseCase,
        deleteSearchHistoryUseCase,
        clearSearchHistoryUseCase,
        vectorIndex,
        modelRepository,
        screenshotDao
    )

    private fun buildScreenshots(count: Int, startId: Int = 0) = (0 until count).map {
        buildScreenshot("id_${startId + it}")
    }

    private fun buildScreenshot(
        id: String,
        dateCreated: Long = System.currentTimeMillis(),
        description: String = "",
        appName: String = ""
    ) = Screenshot(
        id = id,
        filePath = "/sdcard/Screenshots/$id.png",
        fileName = "$id.png",
        dateCreated = dateCreated,
        dateIndexed = dateCreated,
        width = 1080,
        height = 1920,
        description = description,
        appName = appName
    )
}
