package com.recall.app.util

import android.content.Context
import android.os.Debug
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Performance monitoring utilities.
 */
object PerformanceMonitor {
    
    private val PROCESSING_TIME_KEY = floatPreferencesKey("processing_time")
    private val SEARCH_LATENCY_KEY = floatPreferencesKey("search_latency")
    private val MEMORY_USAGE_KEY = longPreferencesKey("memory_usage")
    private val SCREENSHOTS_PROCESSED_KEY = intPreferencesKey("screenshots_processed")
    
    /**
     * Get current memory usage in bytes.
     */
    fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
    
    /**
     * Get memory usage in MB.
     */
    fun getMemoryUsageMB(): Double {
        return getMemoryUsage() / (1024.0 * 1024.0)
    }
    
    /**
     * Get native heap memory usage.
     */
    fun getNativeHeapMemoryUsage(): Long {
        return Debug.getNativeHeapAllocatedSize()
    }
    
    /**
     * Record processing time.
     */
    suspend fun recordProcessingTime(
        dataStore: DataStore<Preferences>,
        timeMs: Float
    ) {
        dataStore.edit { preferences ->
            preferences[PROCESSING_TIME_KEY] = timeMs
        }
    }
    
    /**
     * Get average processing time.
     */
    fun getProcessingTimeFlow(dataStore: DataStore<Preferences>): Flow<Float?> {
        return dataStore.data.map { preferences ->
            preferences[PROCESSING_TIME_KEY]
        }
    }
    
    /**
     * Record search latency.
     */
    suspend fun recordSearchLatency(
        dataStore: DataStore<Preferences>,
        latencyMs: Float
    ) {
        dataStore.edit { preferences ->
            preferences[SEARCH_LATENCY_KEY] = latencyMs
        }
    }
    
    /**
     * Get search latency.
     */
    fun getSearchLatencyFlow(dataStore: DataStore<Preferences>): Flow<Float?> {
        return dataStore.data.map { preferences ->
            preferences[SEARCH_LATENCY_KEY]
        }
    }
    
    /**
     * Increment screenshots processed count.
     */
    suspend fun incrementScreenshotsProcessed(dataStore: DataStore<Preferences>) {
        dataStore.edit { preferences ->
            val current = preferences[SCREENSHOTS_PROCESSED_KEY] ?: 0
            preferences[SCREENSHOTS_PROCESSED_KEY] = current + 1
        }
    }
    
    /**
     * Get screenshots processed count.
     */
    fun getScreenshotsProcessedFlow(dataStore: DataStore<Preferences>): Flow<Int> {
        return dataStore.data.map { preferences ->
            preferences[SCREENSHOTS_PROCESSED_KEY] ?: 0
        }
    }
    
    /**
     * Get performance metrics summary.
     */
    fun getPerformanceSummary(): String {
        val memoryMB = getMemoryUsageMB()
        val nativeHeapMB = getNativeHeapMemoryUsage() / (1024.0 * 1024.0)
        
        return """
            Memory Usage: ${"%.2f".format(memoryMB)} MB
            Native Heap: ${"%.2f".format(nativeHeapMB)} MB
        """.trimIndent()
    }
    
    /**
     * Check if memory usage is high.
     */
    fun isMemoryHigh(thresholdMB: Double = 100.0): Boolean {
        return getMemoryUsageMB() > thresholdMB
    }
    
    /**
     * Suggest garbage collection if memory is high.
     */
    fun suggestGCIfNeeded(thresholdMB: Double = 100.0) {
        if (isMemoryHigh(thresholdMB)) {
            System.gc()
        }
    }
}
