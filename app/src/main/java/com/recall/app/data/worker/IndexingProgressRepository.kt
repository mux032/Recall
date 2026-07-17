package com.recall.app.data.worker

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for indexing pipeline progress.
 *
 * Injected as a [Singleton] via Hilt so both [IndexingPipelineWorker] and any UI
 * ViewModel can reference the same [StateFlow] without a static companion-object handle.
 * This makes the state lifecycle explicit and testable.
 */
@Singleton
class IndexingProgressRepository @Inject constructor() {

    private val _progress = MutableStateFlow(IndexingProgress(0, 0))

    /** Read-only view of the current indexing progress. */
    val progress: StateFlow<IndexingProgress> = _progress.asStateFlow()

    /** Called at the start of each pipeline run to clear stale values from a prior run. */
    fun reset() {
        _progress.value = IndexingProgress(0, 0)
    }

    /** Update progress. Thread-safe — [MutableStateFlow.value] is atomic. */
    fun update(completed: Int, total: Int) {
        _progress.value = IndexingProgress(completed, total)
    }
}
