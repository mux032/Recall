package com.recall.app.domain.model

data class SearchUiState(
    val query: String = "",
    val results: List<Screenshot> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val filter: SearchFilter = SearchFilter()
)
