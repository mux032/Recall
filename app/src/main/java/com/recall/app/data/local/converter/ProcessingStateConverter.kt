package com.recall.app.data.local.converter

import androidx.room.TypeConverter
import com.recall.app.domain.model.ProcessingState

/**
 * Room TypeConverter for [ProcessingState].
 *
 * Stores the enum as its [ProcessingState.value] string in the database (e.g. "PENDING", "DONE")
 * and converts it back to the sealed class on read. Unknown values fall back to [ProcessingState.Pending]
 * via [ProcessingState.fromValue] — the same safe behaviour as the old manual helper property.
 *
 * Registered on [com.recall.app.data.local.RecallDatabase] via [@TypeConverters].
 */
class ProcessingStateConverter {

    @TypeConverter
    fun fromProcessingState(state: ProcessingState): String = state.value

    @TypeConverter
    fun toProcessingState(value: String): ProcessingState = ProcessingState.fromValue(value)
}
