package com.recall.app.presentation.ui.search

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.usecase.SearchScreenshotsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import android.util.Log
import javax.inject.Inject

sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(val results: List<Screenshot>) : SearchState()
    data class Error(val message: String) : SearchState()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchScreenshotsUseCase: SearchScreenshotsUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "SearchViewModel"
        private const val DEBOUNCE_DELAY_MS = 300L
    }

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    // Note: Navigation uses String (URL-safe), ViewModel converts to TextFieldValue
    // for cursor position control. This separation is intentional.
    private val _searchQuery = MutableStateFlow(TextFieldValue(""))
    val searchQuery: StateFlow<TextFieldValue> = _searchQuery.asStateFlow()

    private var searchJob: Job? = null

    // SupervisorJob to prevent child coroutine failures from cancelling the scope
    private val searchScope = kotlinx.coroutines.CoroutineScope(
        SupervisorJob() + viewModelScope.coroutineContext
    )

    init {
        // Read query from navigation arguments
        // Note: SavedStateHandle automatically URL-decodes parameters, so no manual decoding needed
        val query = savedStateHandle.get<String>("query") ?: ""
        Log.d(TAG, "SearchViewModel init: query from nav args = '$query'")
        if (query.isNotEmpty()) {
            // Set cursor position to the end of the query text
            _searchQuery.value = TextFieldValue(query, selection = TextRange(query.length))
            Log.d(TAG, "Initiating search for query: '$query' (no debounce)")
            // For initial query from navigation, skip debounce and search immediately
            performSearch(query, debounce = false)
        }
    }

    fun onQueryChange(query: TextFieldValue) {
        _searchQuery.value = query
        searchJob?.cancel()

        if (query.text.isBlank()) {
            _state.value = SearchState.Idle
            return
        }

        // For subsequent queries, use debounce to avoid excessive searches
        performSearch(query.text, debounce = true)
    }

    private fun performSearch(query: String, debounce: Boolean = true) {
        // Cancel any previous search job
        searchJob?.cancel()

        // Use supervisorScope to prevent coroutine cancellation from propagating
        searchJob = searchScope.launch {
            try {
                // Debounce the input for subsequent searches (not for initial query)
                if (debounce) {
                    delay(DEBOUNCE_DELAY_MS)
                }

                Log.d(TAG, "Starting search for query: '$query'")
                _state.value = SearchState.Loading

                val results = searchScreenshotsUseCase.execute(query)

                if (results.isEmpty()) {
                    _state.value = SearchState.Success(emptyList())
                    Log.d(TAG, "Search completed: no results found")
                } else {
                    _state.value = SearchState.Success(results)
                    Log.d(TAG, "Search completed: ${results.size} results found")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Expected when user types quickly - don't log as error
                Log.d(TAG, "Search cancelled (user is still typing)")
                // Don't update state on cancellation
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OutOfMemoryError during search", e)
                _state.value = SearchState.Error(
                    "Low memory: AI search unavailable. Try closing other apps."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                _state.value = SearchState.Error("Failed to search: ${e.message ?: "Unknown error"}")
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared, cancelling search scope")
        searchJob?.cancel()
        searchScope.cancel()
    }
}
