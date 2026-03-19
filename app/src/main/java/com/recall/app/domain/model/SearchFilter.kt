package com.recall.app.domain.model

data class SearchFilter(
    val query: String = "",
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val startDate: Long? = null,
    val endDate: Long? = null
)
