package com.recall.app.domain.model

data class Screenshot(
    val id: String,
    val filePath: String,
    val fileName: String,
    val dateCreated: Long,
    val dateIndexed: Long,
    val width: Int,
    val height: Int,
    val ocrText: String? = null,
    val category: String = "Uncategorized",
    val tags: List<String> = emptyList(),
    val embedding: FloatArray? = null
)
