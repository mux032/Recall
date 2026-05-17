package com.recall.app.presentation.ui.search

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Download
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import com.recall.app.presentation.ui.components.RecallSearchBar
import com.recall.app.presentation.ui.home.SearchHistoryDropdown
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.recall.app.domain.model.Screenshot
import com.recall.app.presentation.ui.home.screenshotContentDescription

private const val TAG = "SearchFlow"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onScreenshotClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isVectorIndexReady by viewModel.isVectorIndexReady.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val interactionSource = remember { MutableInteractionSource() }

    // Log search query changes for debugging
    LaunchedEffect(searchQuery) {
        Log.d(TAG, "searchQuery updated to: '${searchQuery.text}'")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            val isFocused by interactionSource.collectIsFocusedAsState()
            Column {
                SearchHistoryDropdown(
                    isVisible = isFocused && searchQuery.text.isEmpty(),
                    historyItems = searchHistory,
                    onItemClick = { query ->
                        viewModel.onQueryChange(
                            TextFieldValue(query, androidx.compose.ui.text.TextRange(query.length))
                        )
                    },
                    onItemDelete = { item -> viewModel.deleteHistoryItem(item.id) },
                    onClearAll = { viewModel.clearAllHistory() }
                )
                RecallSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.onQueryChange(it) },
                    onSearch = {
                        if (searchQuery.text.isNotEmpty()) {
                            viewModel.onQueryChange(searchQuery)
                        }
                    },
                    interactionSource = interactionSource
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Result count label
            val currentState = state
            if (currentState is SearchState.Success && searchQuery.text.isNotEmpty()) {
                Text(
                    text = if (currentState.results.isEmpty())
                        "No results for \"${searchQuery.text}\""
                    else
                        "${currentState.results.size} result${if (currentState.results.size == 1) "" else "s"} for \"${searchQuery.text}\"",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp)
                        .padding(top = 8.dp, bottom = 4.dp)
                )
            }

            // AI search unavailable banner — shown when no model is downloaded
            if (!isVectorIndexReady) {
                AiSearchUnavailableBanner(onSettingsClick = onSettingsClick)
            }

            Box(modifier = Modifier.fillMaxSize()) {
            when (val currentState = state) {
                is SearchState.Idle -> {
                    Text(
                        text = "Type to search your memory.",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is SearchState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is SearchState.Success -> {
                    if (currentState.results.isEmpty()) {
                        Text(
                            text = "No results found for \"${searchQuery.text}\".",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalItemSpacing = 8.dp
                        ) {
                            staggeredItems(currentState.results, key = { it.id }) { screenshot ->
                                SearchResultItem(
                                    screenshot = screenshot,
                                    onClick = { onScreenshotClick(screenshot.id) }
                                )
                            }
                        }
                    }
                }
                is SearchState.Error -> {
                    Text(
                        text = currentState.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            } // end Box
        } // end Column
    }
}

/**
 * Banner shown in [SearchScreen] when the vector index is not ready (no model downloaded).
 * Informs the user that AI semantic search is unavailable and provides a shortcut to Settings.
 *
 * FTS (keyword) search continues to work normally below this banner.
 * The banner disappears automatically once the model is downloaded and the index is bootstrapped.
 */
@Composable
internal fun AiSearchUnavailableBanner(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            androidx.compose.material3.Text(
                text = "AI search unavailable — download the model in Settings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.TextButton(
                onClick = onSettingsClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                androidx.compose.material3.Text(
                    text = "Settings",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun SearchResultItem(screenshot: Screenshot, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(screenshot.filePath)
                .crossfade(true)
                .build(),
            contentDescription = screenshotContentDescription(screenshot),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth()
        )
        // AI match badge logic can go here later
    }
}
