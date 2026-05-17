package com.recall.app.presentation.ui.search

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Download
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.recall.app.presentation.ui.home.SearchHistoryDropdown
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
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
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isSearchBarFocused by interactionSource.collectIsFocusedAsState()

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
            Column {
                // Show history dropdown when bar is focused and query is empty
                SearchHistoryDropdown(
                    isVisible = isSearchBarFocused && searchQuery.text.isEmpty(),
                    historyItems = searchHistory,
                    onItemClick = { query ->
                        viewModel.onQueryChange(
                            TextFieldValue(query, androidx.compose.ui.text.TextRange(query.length))
                        )
                    },
                    onItemDelete = { item -> viewModel.deleteHistoryItem(item.id) },
                    onClearAll = { viewModel.clearAllHistory() }
                )
                SearchBottomBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.onQueryChange(it) },
                    onSearchClick = {
                        if (searchQuery.text.isNotEmpty()) {
                            viewModel.onQueryChange(searchQuery)
                        }
                    },
                    focusRequester = focusRequester,
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
fun SearchBottomBar(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearchClick: () -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    var focusRequested by remember { mutableStateOf(false) }
    
    // Request focus only once when the search bar is first composed
    LaunchedEffect(focusRequested) {
        if (!focusRequested) {
            focusRequester.requestFocus()
            focusRequested = true
        }
    }

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
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AI Icon
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

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
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .focusable(),
                    interactionSource = interactionSource,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearchClick() }
                    )
                )

                // Search Button
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
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
