package com.recall.app.presentation.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.recall.app.domain.model.Screenshot
import com.recall.app.presentation.ui.theme.Inter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

/**
 * UI item representation for the home screen grid.
 * This sealed class ensures stable keys for LazyVerticalStaggeredGrid items,
 * preventing duplicate rendering issues.
 */
sealed class UiItem {
    data class Header(val label: String) : UiItem()
    data class Screenshot(val screenshot: com.recall.app.domain.model.Screenshot) : UiItem()
}

/**
 * Data class for timeline section with header and screenshots.
 * This structure ensures headers act as section dividers with screenshots below.
 */
data class TimelineSection(
    val label: String,
    val screenshots: List<com.recall.app.domain.model.Screenshot>,
    val subLabel: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onScreenshotClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val screenshots by viewModel.screenshots.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isHistoryDrawerVisible by remember { mutableStateOf(false) }

    // CRITICAL FIX: Group screenshots by timeline section with proper structure
    // This ensures headers act as section dividers with screenshots displayed BELOW each header
    //
    // Previous issue: Using LazyVerticalStaggeredGrid with mixed Header and Screenshot items
    // caused them to flow into columns side-by-side (masonry layout behavior).
    //
    // Solution: Use LazyColumn with TimelineSection structure where each section contains:
    // 1. A full-width DateSectionHeader
    // 2. A nested LazyVerticalStaggeredGrid containing only screenshots for that section
    val timelineSections = remember(screenshots) {
        // Step 1: Remove duplicates by ID (in case of database duplicates)
        val uniqueScreenshots = screenshots.distinctBy { it.id }

        // Step 2: Group by timeline label
        val groupedScreenshots = uniqueScreenshots.groupBy { screenshot ->
            getTimelineLabel(screenshot.timestamp)
        }

        // Step 3: Sort groups in chronological order (newest first)
        val sortedGroups = groupedScreenshots.toSortedMap { a, b ->
            val order = listOf("Today", "Yesterday", "This Week", "Last Week", "This Month", "Older")
            val indexA = order.indexOf(a).takeIf { it >= 0 } ?: order.size
            val indexB = order.indexOf(b).takeIf { it >= 0 } ?: order.size
            indexA.compareTo(indexB)
        }

        // Step 4: Create TimelineSection list with stable structure and computed sub-labels
        sortedGroups.map { (label, screenshotsForLabel) ->
            // Compute sub-label based on actual screenshot dates in this group
            val subLabel = computeTimelineSubLabel(label, screenshotsForLabel)
            TimelineSection(
                label = label,
                screenshots = screenshotsForLabel,
                subLabel = subLabel
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            topBar = {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    CuratorTopAppBar(
                        onHistoryClick = { isHistoryDrawerVisible = true },
                        onSettingsClick = onSettingsClick
                    )
                    CuratorSmartFilters()
                }
            },
            bottomBar = {
                CuratorBottomSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { onSearchClick() }
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
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
                        screenshotRows.forEachIndexed { rowIndex, rowScreenshots ->
                            item(key = "row-${section.label}-$rowIndex") {
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
                }
            }
        }

        // Search History Drawer
        SearchHistoryDrawer(
            isVisible = isHistoryDrawerVisible,
            onDismissRequest = { isHistoryDrawerVisible = false },
            onHistoryItemClick = { /* Handle history item click */ },
            onHistoryItemDelete = { /* Handle history item delete */ },
            onClearAllHistory = { /* Handle clear all history */ }
        )
    }
}

@Composable
fun CuratorTopAppBar(
    onHistoryClick: () -> Unit,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // History Button
                IconButton(
                    onClick = onHistoryClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    content = {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "History",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )

                // Title
                Text(
                    text = "Recall",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )
            }

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
fun CuratorSmartFilters() {
    val filters = listOf(
        FilterChipData("Recent", Icons.Default.History, isSelected = true),
        FilterChipData("By App", Icons.Default.History, isSelected = false),
        FilterChipData("Summarized", Icons.Default.AutoAwesome, isSelected = false)
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        lazyListItems(filters) { filter ->
            SmartFilterChip(
                label = filter.label,
                icon = filter.icon,
                isSelected = filter.isSelected,
                onClick = { }
            )
        }
    }
}

data class FilterChipData(
    val label: String,
    val icon: ImageVector,
    val isSelected: Boolean
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

/**
 * Returns a timeline label based on the timestamp.
 * Groups: Today, Yesterday, This Week, Last Week, This Month, Older
 *
 * Timeline categories (mutually exclusive, each screenshot assigned to exactly ONE category):
 * - Today: Screenshots taken today (since midnight local time)
 * - Yesterday: Screenshots taken yesterday (previous day)
 * - This Week: Screenshots taken 2-6 days ago (within last 7 days, excluding today/yesterday)
 * - Last Week: Screenshots taken 7-13 days ago
 * - This Month: Screenshots taken 14+ days ago but still in the same calendar month
 * - Older: Screenshots from previous months (more than ~30 days ago or different month)
 */
private fun getTimelineLabel(timestamp: Long): String {
    val now = LocalDate.now()
    // Convert timestamp (milliseconds since epoch) to LocalDate in system timezone
    val screenshotDate = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

    val daysDifference = ChronoUnit.DAYS.between(screenshotDate, now)

    return when {
        // Today: 0 days difference
        daysDifference == 0L -> "Today"
        // Yesterday: 1 day difference
        daysDifference == 1L -> "Yesterday"
        // This Week: 2-6 days ago (within the last week, but not today/yesterday)
        daysDifference in 2..6 -> "This Week"
        // Last Week: 7-13 days ago
        daysDifference in 7..13 -> "Last Week"
        // This Month: 14+ days ago but still in the same calendar month and year
        // Note: This catches items that are too old for "Last Week" but still in current month
        screenshotDate.monthValue == now.monthValue && screenshotDate.year == now.year -> "This Month"
        // Older: Everything else (previous months or years)
        else -> "Older"
    }
}

/**
 * Returns a formatted sub-label for the timeline group.
 * Uses actual screenshot dates for "This Month" and "Older" categories.
 */
private fun computeTimelineSubLabel(label: String, screenshots: List<com.recall.app.domain.model.Screenshot>): String {
    val now = LocalDate.now(ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("MMM dd")

    return when (label) {
        "Today" -> now.format(formatter)
        "Yesterday" -> now.minusDays(1).format(formatter)
        "This Week" -> {
            val oldestDate = screenshots.maxOfOrNull { screenshot ->
                Instant.ofEpochMilli(screenshot.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .toEpochDay()
            }?.let { epochDay ->
                LocalDate.ofEpochDay(epochDay)
            } ?: now.minusDays(6)
            oldestDate.format(formatter) + " - " + now.format(formatter)
        }
        "Last Week" -> {
            val lastWeekStart = now.minusDays(13)
            val lastWeekEnd = now.minusDays(7)
            lastWeekStart.format(formatter) + " - " + lastWeekEnd.format(formatter)
        }
        "This Month" -> {
            // Get the actual month from the oldest screenshot in this group
            val oldestScreenshotDate = screenshots.minByOrNull { it.timestamp }?.let { screenshot ->
                Instant.ofEpochMilli(screenshot.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            } ?: now
            
            oldestScreenshotDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        }
        "Older" -> {
            // Get the actual month from the oldest screenshot in this group
            val oldestScreenshotDate = screenshots.minByOrNull { it.timestamp }?.let { screenshot ->
                Instant.ofEpochMilli(screenshot.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            } ?: now
            
            oldestScreenshotDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        }
        else -> ""
    }
}

/**
 * Returns a formatted sub-label for the timeline group.
 * @deprecated Use computeTimelineSubLabel instead which uses actual screenshot dates
 */
@Deprecated("Use computeTimelineSubLabel instead")
private fun getTimelineSubLabel(label: String): String {
    val now = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("MMM dd")

    return when (label) {
        "Today" -> now.format(formatter)
        "Yesterday" -> now.minusDays(1).format(formatter)
        "This Week" -> now.minusDays(6).format(formatter) + " - " + now.format(formatter)
        "Last Week" -> {
            val lastWeekStart = now.minusDays(13)
            val lastWeekEnd = now.minusDays(7)
            lastWeekStart.format(formatter) + " - " + lastWeekEnd.format(formatter)
        }
        "This Month" -> now.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        "Older" -> ""
        else -> ""
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
            contentDescription = "Screenshot ${screenshot.id}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
        )

        // Gradient Overlay at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f)
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
                        color = Color.White.copy(alpha = 0.7f),
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
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CuratorBottomSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AI Icon
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Search Input
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = "Search your screenshots...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = Inter),
                    singleLine = true
                )

                // Send Button
                IconButton(
                    onClick = onSearch,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    content = {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
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
