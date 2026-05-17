package com.recall.app.presentation.ui.home

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.navigation.NavBackStackEntry
import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.model.ScreenshotFilter
import com.recall.app.presentation.ui.components.RecallSearchBar
import com.recall.app.presentation.ui.theme.Inter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

private const val TAG = "SearchFlow"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSearchClick: (String) -> Unit,
    onScreenshotClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    navBackStackEntry: NavBackStackEntry? = null,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val screenshots by viewModel.screenshots.collectAsState()
    val timelineSections by viewModel.timelineSections.collectAsState()

    // Refresh the screenshot list when we return from DetailScreen after a deletion
    val screenshotDeleted = navBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow("screenshot_deleted", false)
        ?.collectAsState()
    LaunchedEffect(screenshotDeleted?.value) {
        if (screenshotDeleted?.value == true) {
            viewModel.refresh()
            navBackStackEntry?.savedStateHandle?.set("screenshot_deleted", false)
        }
    }
    val searchHistory by viewModel.searchHistory.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val allPagesLoaded by viewModel.allPagesLoaded.collectAsState()
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var isSearchBarFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val searchInteractionSource = remember { MutableInteractionSource() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Trigger loadNextPage() when the user scrolls within 5 items of the bottom
    LaunchedEffect(listState.firstVisibleItemIndex, screenshots.size) {
        val totalItems = listState.layoutInfo.totalItemsCount
        val lastVisible = listState.firstVisibleItemIndex + listState.layoutInfo.visibleItemsInfo.size
        if (totalItems > 0 && lastVisible >= totalItems - 5 && !allPagesLoaded) {
            viewModel.loadNextPage()
        }
    }

    // timelineSections are now computed in HomeViewModel via buildTimelineSections()
    // and exposed as a StateFlow — no computation needed here.

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            topBar = {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    CuratorTopAppBar(
                        onSettingsClick = onSettingsClick
                    )
                    CuratorSmartFilters(
                        selectedFilter = selectedFilter,
                        onFilterSelected = { viewModel.setFilter(it) }
                    )
                }
            },
            bottomBar = {
                // Keep focus state in sync with interactionSource
                val isFocused by searchInteractionSource.collectIsFocusedAsState()
                LaunchedEffect(isFocused) { isSearchBarFocused = isFocused }

                Column {
                    // Inline history dropdown — slides up above the search bar when focused
                    SearchHistoryDropdown(
                        isVisible = isSearchBarFocused,
                        historyItems = searchHistory,
                        onItemClick = { query ->
                            searchQuery = TextFieldValue(query, TextRange(query.length))
                            isSearchBarFocused = false
                            viewModel.addSearchHistory(query)
                            onSearchClick(query)
                        },
                        onItemDelete = { item -> viewModel.deleteHistoryItem(item.id) },
                        onClearAll = { viewModel.clearAllHistory() }
                    )
                    RecallSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = {
                            val q = searchQuery.text
                            Log.d(TAG, "onSearch called with query: '$q'")
                            if (q.isNotEmpty()) {
                                Log.d(TAG, "Navigating to SearchScreen with query: '$q'")
                                isSearchBarFocused = false
                                focusManager.clearFocus()
                                viewModel.addSearchHistory(q)
                                onSearchClick(q)
                            } else {
                                Log.w(TAG, "Empty query, not navigating")
                            }
                        },
                        interactionSource = searchInteractionSource
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            focusManager.clearFocus()
                        }
                    }
            ) {
            if (screenshots.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No screenshots found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Your captured memories will appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                // Calculate grid columns based on screen width
                // Using a flat LazyColumn structure to avoid nested scrolling issues
                val numColumns = 2 // Fixed 2-column grid for consistent layout
                
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    timelineSections.forEach { section ->
                        // Section Header (full-width)
                        item(key = "header-${section.label}") {
                            DateSectionHeader(
                                label = section.label,
                                subLabel = section.subLabel,
                                showSubLabel = true
                            )
                        }

                        // Screenshot Grid Items for this section
                        // Flatten the grid into individual items to avoid nested scrollable containers
                        val screenshotRows = section.screenshots.chunked(numColumns)
                        screenshotRows.forEachIndexed { _, rowScreenshots ->
                            // Use the first screenshot ID in the row as a stable key.
                            // Previously used rowIndex which shifts when items are inserted/deleted,
                            // forcing Compose to recreate all rows below the insertion point.
                            item(key = "row-${rowScreenshots.first().id}") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowScreenshots.forEach { screenshot ->
                                        ScreenshotItem(
                                            screenshot = screenshot,
                                            onClick = { onScreenshotClick(screenshot.id) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    // Fill remaining space if row has only one item
                                    if (rowScreenshots.size < numColumns) {
                                        repeat(numColumns - rowScreenshots.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Loading footer — shown while fetching the next page
                    item(key = "footer-loading") {
                        if (isLoadingMore) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
            } // end tap-intercepting Box
        }

    }
}

@Composable
fun CuratorTopAppBar(
    onSettingsClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title
            Text(
                text = "Recall",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
            )

            // Settings Button
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                content = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun CuratorSmartFilters(
    selectedFilter: ScreenshotFilter,
    onFilterSelected: (ScreenshotFilter) -> Unit
) {
    // Each entry maps a display label + icon to its ScreenshotFilter value.
    val filters = listOf(
        FilterChipData("All",       Icons.Default.GridView,    ScreenshotFilter.ALL),
        FilterChipData("Recent",    Icons.Default.History,     ScreenshotFilter.RECENT),
        FilterChipData("By App",    Icons.Default.Apps,        ScreenshotFilter.BY_APP),
        FilterChipData("Summarized",Icons.Default.AutoAwesome, ScreenshotFilter.SUMMARIZED)
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        lazyListItems(filters) { chip ->
            SmartFilterChip(
                label = chip.label,
                icon = chip.icon,
                isSelected = selectedFilter == chip.filter,
                onClick = { onFilterSelected(chip.filter) }
            )
        }
    }
}

data class FilterChipData(
    val label: String,
    val icon: ImageVector,
    val filter: ScreenshotFilter
)

@Composable
fun SmartFilterChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = backgroundColor,
        contentColor = contentColor,
        border = if (!isSelected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        } else {
            null
        },
        shadowElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun DateSectionHeader(
    label: String,
    subLabel: String = "",
    showSubLabel: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.width(12.dp))

        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )

        if (showSubLabel && subLabel.isNotEmpty()) {
            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ScreenshotItem(
    screenshot: Screenshot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        // Screenshot Image
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(screenshot.filePath)
                .crossfade(true)
                .build(),
            contentDescription = screenshotContentDescription(screenshot),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
        )

        // Gradient Overlay at bottom — reduced opacity in dark mode
        val isDark = isSystemInDarkTheme()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = if (isDark) 0.4f else 0.6f)
                        ),
                        startY = 0f,
                        endY = 120f
                    )
                )
        )

        // Summarized Badge (if applicable)
        if (screenshot.description.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.TopStart),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "SUMMARIZED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // App Info and Description at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (screenshot.appName.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // App Icon Placeholder
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = screenshot.appName.first().uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "${screenshot.appName} • ${formatTimeAgo(screenshot.timestamp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (screenshot.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = screenshot.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> "${diff / 604800_000}w ago"
    }
}

/**
 * Generates a meaningful TalkBack content description for a screenshot image.
 *
 * Priority:
 * 1. First [CONTENT_DESCRIPTION_MAX_CHARS] characters of OCR text — gives context about content.
 * 2. Time-based fallback — used when OCR text is not yet available or blank.
 *
 * Examples:
 * - `"Screenshot: Invoice #1234 Total due: $99.99 Pay by Nov 30…"`
 * - `"Screenshot from 2h ago"`
 */
internal fun screenshotContentDescription(screenshot: com.recall.app.domain.model.Screenshot): String {
    val ocrSnippet = screenshot.ocrText
        ?.trim()
        ?.take(CONTENT_DESCRIPTION_MAX_CHARS)
        ?.let { if (it.isNotBlank()) "Screenshot: $it" else null }

    return ocrSnippet ?: "Screenshot from ${formatTimeAgo(screenshot.timestamp)}"
}

/** Maximum number of OCR text characters used in a screenshot content description. */
internal const val CONTENT_DESCRIPTION_MAX_CHARS = 100
