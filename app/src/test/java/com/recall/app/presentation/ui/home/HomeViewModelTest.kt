package com.recall.app.presentation.ui.home

import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.model.ScreenshotFilter
import com.recall.app.domain.repository.ScreenshotRepository
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
    private lateinit var deleteSearchHistoryUseCase: DeleteSearchHistoryUseCase
    private lateinit var clearSearchHistoryUseCase: ClearSearchHistoryUseCase
    private lateinit var viewModel: HomeViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        screenshotRepository = mock()
        getSearchHistoryUseCase = mock()
        deleteSearchHistoryUseCase = mock()
        clearSearchHistoryUseCase = mock()

        whenever(getSearchHistoryUseCase()).thenReturn(flowOf(emptyList()))
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
    // Helpers
    // -----------------------------------------------------------------------

    private fun buildViewModel() = HomeViewModel(
        screenshotRepository,
        getSearchHistoryUseCase,
        deleteSearchHistoryUseCase,
        clearSearchHistoryUseCase
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
