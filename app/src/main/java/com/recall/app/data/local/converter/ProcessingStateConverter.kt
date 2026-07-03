package com.recall.app.data.local.converter

import androidx.room.TypeConverter
import com.recall.app.domain.model.ProcessingState

/**
 * Room TypeConverter for [ProcessingState].
 *
 * Stores the state as its [ProcessingState.value] string in the database
 * (e.g. "OCR_PENDING", "OCR_COMPLETED", "OCR_EMB_COMPLETED", "FAILED")
 * and converts it back to the sealed class on read. Unknown values — including
 * legacy "PENDING", "DONE", and "PROCESSING" strings from older DB rows —
 * fall back gracefully via [ProcessingState.fromValue].
 *
 * Registered on [com.recall.app.data.local.RecallDatabase] via [@TypeConverters].
 */
class ProcessingStateConverter {

    @TypeConverter
    fun fromProcessingState(state: ProcessingState): String = state.value

    @TypeConverter
    fun toProcessingState(value: String): ProcessingState = ProcessingState.fromValue(value)
}
