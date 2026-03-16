package com.recall.app.presentation.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recall.app.data.repository.ScreenshotRepository
import com.recall.app.domain.model.SearchResult
import com.recall.app.domain.repository.EmbeddingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Search screen.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val screenshotRepository: ScreenshotRepository,
    private val embeddingRepository: EmbeddingRepository
) : ViewModel() {
    
    private val _uiState = MutableLiveData(SearchUiState())
    val uiState: LiveData<SearchUiState> = _uiState
    
    private val _recentSearches = MutableLiveData<List<String>>(emptyList())
    val recentSearches: LiveData<List<String>> = _recentSearches
    
    // Dynamic suggested searches - changes each time app opens
    val suggestedSearches = listOf(
        "flight tickets",
        "recipes",
        "coding",
        "shopping",
        "travel",
        "messages",
        "documents",
        "maps",
        "photos",
        "videos"
    ).shuffled().take(4) // Random 4 suggestions each time
    
    private var searchJob: Job? = null
    
    init {
        loadRecentSearches()
    }
    
    private fun loadRecentSearches() {
        // Load from DataStore (for now, using in-memory list)
        _recentSearches.value = listOf("flight tickets", "recipes", "coding")
    }
    
    fun search(query: String) {
        searchJob?.cancel()
        
        if (query.isBlank()) {
            _uiState.value = _uiState.value?.copy(
                searchResults = emptyList(),
                isEmpty = true,
                isLoading = false
            )
            return
        }
        
        searchJob = viewModelScope.launch {
            _uiState.postValue(_uiState.value?.copy(isLoading = true, isEmpty = false))
            
            // Debounce search (reduced to 200ms for faster response)
            delay(200)
            
            try {
                // Always use text search for better matching
                val textResults = screenshotRepository.searchByText("%$query%")
                
                _uiState.postValue(
                    _uiState.value?.copy(
                        searchResults = textResults.map { it.toSearchResult(0.5f) },
                        isLoading = false,
                        isEmpty = textResults.isEmpty()
                    )
                )
                
                // Add to recent searches
                addToRecentSearches(query)
                
            } catch (e: Exception) {
                _uiState.postValue(
                    _uiState.value?.copy(
                        error = e.message,
                        isLoading = false,
                        isEmpty = true
                    )
                )
            }
        }
    }
    
    private fun addToRecentSearches(query: String) {
        val current = _recentSearches.value ?: emptyList()
        val updated = (listOf(query) + current.filter { it != query }).take(5)
        _recentSearches.value = updated
    }
    
    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
    }
    
    fun clearSearch() {
        _uiState.value = SearchUiState()
        searchJob?.cancel()
    }
}

data class SearchUiState(
    val isLoading: Boolean = false,
    val isEmpty: Boolean = true,
    val error: String? = null,
    val searchResults: List<SearchResult> = emptyList(),
    val query: String = ""
)

// Extension function to convert ScreenshotEntity to SearchResult
private fun com.recall.app.data.local.entity.ScreenshotEntity.toSearchResult(similarity: Float): SearchResult {
    return SearchResult(
        screenshotId = id,
        filePath = filePath,
        summary = summary,
        tags = tags,
        category = category,
        timestamp = timestamp,
        similarityScore = similarity,
        matchedTerms = emptyList()
    )
}
