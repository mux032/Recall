package com.recall.app.presentation.ui.home

import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.model.ScreenshotFilter
import com.recall.app.domain.usecase.GetAllScreenshotsUseCase
import com.recall.app.domain.usecase.searchhistory.ClearSearchHistoryUseCase
import com.recall.app.domain.usecase.searchhistory.DeleteSearchHistoryUseCase
import com.recall.app.domain.usecase.searchhistory.GetSearchHistoryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var getAllScreenshotsUseCase: GetAllScreenshotsUseCase
    private lateinit var getSearchHistoryUseCase: GetSearchHistoryUseCase
    private lateinit var deleteSearchHistoryUseCase: DeleteSearchHistoryUseCase
    private lateinit var clearSearchHistoryUseCase: ClearSearchHistoryUseCase
    private lateinit var viewModel: HomeViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        getAllScreenshotsUseCase = mock()
        getSearchHistoryUseCase = mock()
        deleteSearchHistoryUseCase = mock()
        clearSearchHistoryUseCase = mock()

        whenever(getAllScreenshotsUseCase()).thenReturn(flowOf(emptyList()))
        whenever(getSearchHistoryUseCase()).thenReturn(flowOf(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------
    // Initial state
    // -----------------------------------------------------------------------

    @Test
    fun `initial filter is ALL`() {
        viewModel = buildViewModel()
        assertEquals(ScreenshotFilter.ALL, viewModel.selectedFilter.value)
    }

    // -----------------------------------------------------------------------
    // setFilter — selection
    // -----------------------------------------------------------------------

    @Test
    fun `setFilter RECENT sets selectedFilter to RECENT`() {
        viewModel = buildViewModel()
        viewModel.setFilter(ScreenshotFilter.RECENT)
        assertEquals(ScreenshotFilter.RECENT, viewModel.selectedFilter.value)
    }

    @Test
    fun `setFilter BY_APP sets selectedFilter to BY_APP`() {
        viewModel = buildViewModel()
        viewModel.setFilter(ScreenshotFilter.BY_APP)
        assertEquals(ScreenshotFilter.BY_APP, viewModel.selectedFilter.value)
    }

    @Test
    fun `setFilter SUMMARIZED sets selectedFilter to SUMMARIZED`() {
        viewModel = buildViewModel()
        viewModel.setFilter(ScreenshotFilter.SUMMARIZED)
        assertEquals(ScreenshotFilter.SUMMARIZED, viewModel.selectedFilter.value)
    }

    @Test
    fun `setFilter same filter twice deselects it back to ALL`() {
        viewModel = buildViewModel()
        viewModel.setFilter(ScreenshotFilter.RECENT)
        assertEquals(ScreenshotFilter.RECENT, viewModel.selectedFilter.value)
        viewModel.setFilter(ScreenshotFilter.RECENT)
        assertEquals(ScreenshotFilter.ALL, viewModel.selectedFilter.value)
    }

    @Test
    fun `switching from one filter to another updates selectedFilter`() {
        viewModel = buildViewModel()
        viewModel.setFilter(ScreenshotFilter.RECENT)
        viewModel.setFilter(ScreenshotFilter.BY_APP)
        assertEquals(ScreenshotFilter.BY_APP, viewModel.selectedFilter.value)
    }

    // -----------------------------------------------------------------------
    // screenshots flow — filtering behaviour
    // -----------------------------------------------------------------------

    @Test
    fun `ALL filter returns all screenshots`() = runTest {
        val now = System.currentTimeMillis()
        val screenshots = listOf(
            buildScreenshot("1", dateCreated = now),
            buildScreenshot("2", dateCreated = now - 10 * 24 * 60 * 60 * 1000L) // 10 days ago
        )
        whenever(getAllScreenshotsUseCase()).thenReturn(flowOf(screenshots))
        viewModel = buildViewModel()
        // Activate the WhileSubscribed flow with a background collector
        val collected = mutableListOf<List<Screenshot>>()
        backgroundScope.launch { viewModel.screenshots.collect { collected.add(it) } }
        advanceUntilIdle()

        assertEquals(2, collected.last().size)
    }

    @Test
    fun `RECENT filter returns only screenshots within last 7 days`() = runTest {
        val now = System.currentTimeMillis()
        val recentScreenshot = buildScreenshot("1", dateCreated = now - 2 * 24 * 60 * 60 * 1000L)
        val oldScreenshot = buildScreenshot("2", dateCreated = now - 10 * 24 * 60 * 60 * 1000L)
        whenever(getAllScreenshotsUseCase()).thenReturn(flowOf(listOf(recentScreenshot, oldScreenshot)))
        viewModel = buildViewModel()
        val collected = mutableListOf<List<Screenshot>>()
        backgroundScope.launch { viewModel.screenshots.collect { collected.add(it) } }

        viewModel.setFilter(ScreenshotFilter.RECENT)
        advanceUntilIdle()

        val result = collected.last()
        assertEquals(1, result.size)
        assertEquals("1", result.first().id)
    }

    @Test
    fun `SUMMARIZED filter returns only screenshots with non-blank description`() = runTest {
        val summarized = buildScreenshot("1", description = "AI generated summary")
        val notSummarized = buildScreenshot("2", description = "")
        whenever(getAllScreenshotsUseCase()).thenReturn(flowOf(listOf(summarized, notSummarized)))
        viewModel = buildViewModel()
        val collected = mutableListOf<List<Screenshot>>()
        backgroundScope.launch { viewModel.screenshots.collect { collected.add(it) } }

        viewModel.setFilter(ScreenshotFilter.SUMMARIZED)
        advanceUntilIdle()

        val result = collected.last()
        assertEquals(1, result.size)
        assertEquals("1", result.first().id)
    }

    @Test
    fun `BY_APP filter returns only screenshots with non-blank appName`() = runTest {
        val withApp = buildScreenshot("1", appName = "com.whatsapp")
        val withoutApp = buildScreenshot("2", appName = "")
        whenever(getAllScreenshotsUseCase()).thenReturn(flowOf(listOf(withApp, withoutApp)))
        viewModel = buildViewModel()
        val collected = mutableListOf<List<Screenshot>>()
        backgroundScope.launch { viewModel.screenshots.collect { collected.add(it) } }

        viewModel.setFilter(ScreenshotFilter.BY_APP)
        advanceUntilIdle()

        val result = collected.last()
        assertEquals(1, result.size)
        assertEquals("1", result.first().id)
    }

    @Test
    fun `deselecting RECENT resets filter back to ALL`() {
        // Verify the toggle behaviour of setFilter:
        // selecting an active filter should deselect it (return to ALL).
        // The screenshots flow filtering is already covered by other tests;
        // here we only assert the filter state machine.
        viewModel = buildViewModel()

        viewModel.setFilter(ScreenshotFilter.RECENT)
        assertEquals(ScreenshotFilter.RECENT, viewModel.selectedFilter.value)

        viewModel.setFilter(ScreenshotFilter.RECENT) // tap again → deselect
        assertEquals(ScreenshotFilter.ALL, viewModel.selectedFilter.value)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun buildViewModel() = HomeViewModel(
        getAllScreenshotsUseCase,
        getSearchHistoryUseCase,
        deleteSearchHistoryUseCase,
        clearSearchHistoryUseCase
    )

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
