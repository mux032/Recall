package com.recall.app.presentation.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.usecase.SearchScreenshotsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(val results: List<Screenshot>) : SearchState()
    data class Error(val message: String) : SearchState()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchScreenshotsUseCase: SearchScreenshotsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()

        if (query.isBlank()) {
            _state.value = SearchState.Idle
            return
        }

        searchJob = viewModelScope.launch {
            // Debounce the input
            delay(300)
            _state.value = SearchState.Loading
            try {
                val results = searchScreenshotsUseCase.execute(query)
                _state.value = SearchState.Success(results)
            } catch (e: Exception) {
                _state.value = SearchState.Error("Failed to search: ${e.message}")
            }
        }
    }
}
