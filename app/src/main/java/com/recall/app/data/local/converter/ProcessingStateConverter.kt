package com.recall.app.data.local.converter

import androidx.room.TypeConverter
import com.recall.app.domain.model.ProcessingState

/**
 * Room TypeConverter for [ProcessingState].
 *
 * Stores the state as a compact [Int] in the database
 * (0 = OCR_PENDING, 1 = OCR_COMPLETED, 2 = OCR_EMB_COMPLETED, 3 = FAILED).
 * Integer storage is more space-efficient than strings and allows the database
 * engine to use numeric equality for index lookups.
 *
 * See [ProcessingState.VAL_*] constants for the canonical mapping.
 * Registered on [com.recall.app.data.local.RecallDatabase] via [@TypeConverters].
 */
class ProcessingStateConverter {

    @TypeConverter
    fun fromProcessingState(state: ProcessingState): Int = state.value

    @TypeConverter
    fun toProcessingState(value: Int): ProcessingState = ProcessingState.fromValue(value)
}
