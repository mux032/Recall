package com.recall.app.presentation.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recall.app.domain.model.HistoryIconType
import com.recall.app.domain.model.SearchHistoryItem

@Composable
fun SearchHistoryDrawer(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    historyItems: List<SearchHistoryItem> = getSampleHistoryItems(),
    onHistoryItemClick: (SearchHistoryItem) -> Unit,
    onHistoryItemDelete: (SearchHistoryItem) -> Unit,
    onClearAllHistory: () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(300)
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(300)
        )
    ) {
        Box {
            // Scrim (Backdrop)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(onClick = onDismissRequest)
            )

            // Drawer
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 340.dp)
                    .align(Alignment.CenterStart),
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Drawer Header
                    DrawerHeader(
                        onCloseClick = onDismissRequest
                    )

                    // History Content
                    HistoryContent(
                        historyItems = historyItems,
                        onHistoryItemClick = onHistoryItemClick,
                        onHistoryItemDelete = onHistoryItemDelete
                    )

                    // Smart Insight
                    SmartInsight()

                    // Drawer Footer
                    DrawerFooter(
                        onClearAllClick = onClearAllHistory
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader(
    onCloseClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Search History",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Revisit your curated insights",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            IconButton(
                onClick = onCloseClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun HistoryContent(
    historyItems: List<SearchHistoryItem>,
    onHistoryItemClick: (SearchHistoryItem) -> Unit,
    onHistoryItemDelete: (SearchHistoryItem) -> Unit
) {
    val groupedItems = remember(historyItems) {
        historyItems.groupBy { item ->
            when {
                item.timeLabel.contains("Today") -> "Today"
                item.timeLabel.contains("Yesterday") -> "Yesterday"
                else -> "Earlier"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
        groupedItems.forEach { (timeGroup, items) ->
            item {
                Text(
                    text = timeGroup,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (timeGroup == "Today") {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            items(items) { historyItem ->
                HistoryItem(
                    item = historyItem,
                    onClick = { onHistoryItemClick(historyItem) },
                    onDelete = { onHistoryItemDelete(historyItem) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        }
    }
}

@Composable
private fun HistoryItem(
    item: SearchHistoryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val containerColor = when (item.iconType) {
        HistoryIconType.RECEIPT_LONG -> MaterialTheme.colorScheme.secondaryContainer
        HistoryIconType.FLIGHT_TAKEOFF -> MaterialTheme.colorScheme.tertiaryContainer
        HistoryIconType.AUTO_AWESOME -> MaterialTheme.colorScheme.surfaceContainerHighest
        HistoryIconType.DESCRIPTION -> MaterialTheme.colorScheme.surfaceContainerHighest
        HistoryIconType.SEARCH -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val iconContentColor = when (item.iconType) {
        HistoryIconType.RECEIPT_LONG -> MaterialTheme.colorScheme.onSecondaryContainer
        HistoryIconType.FLIGHT_TAKEOFF -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon Container
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(containerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getIconForType(item.iconType),
                        contentDescription = null,
                        tint = iconContentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Text Content
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.query,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            // Delete Button
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SmartInsight() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Smart Curation",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Create a travel dashboard for your frequent Tokyo searches?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { },
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text(
                        text = "Explore Dashboard",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerFooter(
    onClearAllClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(bottomEnd = 32.dp)
    ) {
        Column {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            )

            Button(
                onClick = onClearAllClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Clear All History",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun getIconForType(type: HistoryIconType): ImageVector {
    return when (type) {
        HistoryIconType.RECEIPT_LONG -> Icons.Default.ReceiptLong
        HistoryIconType.FLIGHT_TAKEOFF -> Icons.Default.FlightTakeoff
        HistoryIconType.AUTO_AWESOME -> Icons.Default.AutoAwesome
        HistoryIconType.DESCRIPTION -> Icons.Default.Description
        HistoryIconType.SEARCH -> Icons.Default.Search
    }
}

@Composable
fun getSampleHistoryItems(): List<SearchHistoryItem> {
    return listOf(
        SearchHistoryItem(
            id = "1",
            query = "receipts from coffee shops",
            timestamp = System.currentTimeMillis() - (2 * 60 * 60 * 1000),
            iconType = HistoryIconType.RECEIPT_LONG,
            timeLabel = "2 hours ago"
        ),
        SearchHistoryItem(
            id = "2",
            query = "flights to Tokyo",
            timestamp = System.currentTimeMillis() - (4 * 60 * 60 * 1000),
            iconType = HistoryIconType.FLIGHT_TAKEOFF,
            timeLabel = "4 hours ago"
        ),
        SearchHistoryItem(
            id = "3",
            query = "summarize recent tech news",
            timestamp = System.currentTimeMillis() - (24 * 60 * 60 * 1000) - (4 * 60 * 60 * 1000),
            iconType = HistoryIconType.AUTO_AWESOME,
            timeLabel = "Yesterday, 4:12 PM"
        ),
        SearchHistoryItem(
            id = "4",
            query = "tax documents 2023",
            timestamp = System.currentTimeMillis() - (24 * 60 * 60 * 1000) - (10 * 60 * 60 * 1000),
            iconType = HistoryIconType.DESCRIPTION,
            timeLabel = "Yesterday, 10:45 AM"
        )
    )
}
