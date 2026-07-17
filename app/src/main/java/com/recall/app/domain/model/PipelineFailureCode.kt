package com.recall.app.domain.model

/**
 * Terminal failure codes stored in [com.recall.app.data.local.entity.ScreenshotEntity.pipelineCode].
 *
 * A value of [NONE] (0) means the row has not permanently failed — pipeline state is derived
 * entirely from the presence or absence of [ScreenshotEntity.ocrText] and
 * [ScreenshotEntity.embeddingByteArray].
 *
 * Rules:
 * - Never renumber an existing constant — only append new ones.
 * - Codes are write-once: once set to a non-zero value the pipeline will not retry that row.
 */
object PipelineFailureCode {
    /** No failure — row is eligible for normal pipeline processing. */
    const val NONE              = 0

    /** OCR permanently failed after exhausting all retry attempts. */
    const val OCR_FAILED        = 1

    /** Embedding permanently failed after exhausting all retry attempts. */
    const val EMBEDDING_FAILED  = 2

    // Reserve 3+ for future pipeline stages (e.g. index, categorisation).
}
