package com.recall.app.domain.model

/**
 * Represents the processing state of a screenshot in the OCR pipeline.
 * 
 * Using a sealed class instead of magic strings provides:
 * - Compile-time safety (typos are caught by the compiler)
 * - Centralized state management
 * - Clear documentation of all possible states
 * 
 * @property value The string value stored in the database
 */
sealed class ProcessingState(val value: String) {
    /** Screenshot is queued for OCR processing */
    object Pending : ProcessingState("PENDING")
    
    /** OCR processing completed successfully */
    object Done : ProcessingState("DONE")
    
    /** OCR processing failed (e.g., file not found, empty result) */
    object Failed : ProcessingState("FAILED")
    
    /** Screenshot is currently being processed */
    object Processing : ProcessingState("PROCESSING")
    
    companion object {
        /**
         * Convert a string value to ProcessingState.
         * Returns Pending for unknown values.
         */
        fun fromValue(value: String): ProcessingState = when (value) {
            "PENDING" -> Pending
            "DONE" -> Done
            "FAILED" -> Failed
            "PROCESSING" -> Processing
            else -> Pending
        }
    }
}
