package com.recall.app.domain.model

/**
 * Pipeline stage for a screenshot, stored as a compact integer in the database.
 *
 * Integer values are space-efficient (4 bytes vs ~15 bytes for the longest string)
 * and allow O(1) equality checks in SQL without string collation overhead.
 *
 * Values are intentionally stable — never renumber an existing constant, only append new ones.
 */
sealed class ProcessingState(val value: Int) {

    /** Screenshot discovered from MediaStore; OCR has not yet been attempted. */
    object OcrPending : ProcessingState(0)

    /** OCR text extracted successfully; embedding not yet generated.
     *  The embedding model may not be downloaded yet. */
    object OcrCompleted : ProcessingState(1)

    /** Fully indexed: OCR text + embedding vector both present. */
    object OcrEmbCompleted : ProcessingState(2)

    /** Permanently failed after exhausting all retry attempts. */
    object Failed : ProcessingState(3)

    companion object {
        // Named constants for use in raw @Query SQL annotations.
        // Reference these instead of bare literals so a future value change is caught at compile time.
        const val VAL_OCR_PENDING      = 0
        const val VAL_OCR_COMPLETED    = 1
        const val VAL_OCR_EMB_COMPLETED = 2
        const val VAL_FAILED           = 3

        fun fromValue(value: Int): ProcessingState = when (value) {
            VAL_OCR_PENDING       -> OcrPending
            VAL_OCR_COMPLETED     -> OcrCompleted
            VAL_OCR_EMB_COMPLETED -> OcrEmbCompleted
            VAL_FAILED            -> Failed
            else                  -> OcrPending
        }

        /** Convenience overload for legacy callers that still hold a String processingState. */
        fun fromValue(value: String): ProcessingState = when (value) {
            "OCR_PENDING"       -> OcrPending
            "OCR_COMPLETED"     -> OcrCompleted
            "OCR_EMB_COMPLETED" -> OcrEmbCompleted
            "FAILED"            -> Failed
            // Legacy DB values from the old string-based schema
            "PENDING", "PROCESSING" -> OcrPending
            "DONE"                  -> OcrEmbCompleted
            else -> fromValue(value.toIntOrNull() ?: VAL_OCR_PENDING)
        }
    }
}
