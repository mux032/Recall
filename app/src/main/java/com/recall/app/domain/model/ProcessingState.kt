package com.recall.app.domain.model

sealed class ProcessingState(val value: String) {

    /** Screenshot discovered from MediaStore; OCR has not yet been attempted. */
    object OcrPending : ProcessingState("OCR_PENDING")

    /** OCR text extracted successfully; embedding not yet generated.
     *  The embedding model may not be downloaded yet. */
    object OcrCompleted : ProcessingState("OCR_COMPLETED")

    /** Fully indexed: OCR text + embedding vector both present. */
    object OcrEmbCompleted : ProcessingState("OCR_EMB_COMPLETED")

    /** Permanently failed after exhausting all retry attempts. */
    object Failed : ProcessingState("FAILED")

    companion object {
        fun fromValue(value: String): ProcessingState = when (value) {
            "OCR_PENDING"       -> OcrPending
            "OCR_COMPLETED"     -> OcrCompleted
            "OCR_EMB_COMPLETED" -> OcrEmbCompleted
            "FAILED"            -> Failed
            // Legacy value migration (existing DB rows from old schema)
            "PENDING"           -> OcrPending
            "DONE"              -> OcrEmbCompleted
            "PROCESSING"        -> OcrPending
            else                -> OcrPending
        }
    }
}
