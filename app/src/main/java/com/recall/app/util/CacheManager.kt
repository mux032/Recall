package com.recall.app.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Cache management utilities.
 */
object CacheManager {
    
    private val CACHE_SIZE_KEY = intPreferencesKey("cache_size")
    private val CACHE_ENABLED_KEY = booleanPreferencesKey("cache_enabled")
    private val LAST_CACHE_CLEAR_KEY = longPreferencesKey("last_cache_clear")
    
    // Default cache settings
    private const val DEFAULT_CACHE_SIZE_MB = 50
    private const val CACHE_CLEAR_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
    
    /**
     * Get cache size limit in MB.
     */
    suspend fun getCacheSizeLimit(dataStore: DataStore<Preferences>): Flow<Int> {
        return dataStore.data.map { preferences ->
            preferences[CACHE_SIZE_KEY] ?: DEFAULT_CACHE_SIZE_MB
        }
    }
    
    /**
     * Set cache size limit.
     */
    suspend fun setCacheSizeLimit(
        dataStore: DataStore<Preferences>,
        sizeMB: Int
    ) {
        dataStore.edit { preferences ->
            preferences[CACHE_SIZE_KEY] = sizeMB
        }
    }
    
    /**
     * Check if caching is enabled.
     */
    suspend fun isCacheEnabled(dataStore: DataStore<Preferences>): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[CACHE_ENABLED_KEY] ?: true
        }
    }
    
    /**
     * Enable/disable caching.
     */
    suspend fun setCacheEnabled(
        dataStore: DataStore<Preferences>,
        enabled: Boolean
    ) {
        dataStore.edit { preferences ->
            preferences[CACHE_ENABLED_KEY] = enabled
        }
    }
    
    /**
     * Get last cache clear timestamp.
     */
    suspend fun getLastCacheClear(dataStore: DataStore<Preferences>): Flow<Long> {
        return dataStore.data.map { preferences ->
            preferences[LAST_CACHE_CLEAR_KEY] ?: 0L
        }
    }
    
    /**
     * Update last cache clear timestamp.
     */
    suspend fun updateLastCacheClear(dataStore: DataStore<Preferences>) {
        dataStore.edit { preferences ->
            preferences[LAST_CACHE_CLEAR_KEY] = System.currentTimeMillis()
        }
    }
    
    /**
     * Check if cache should be cleared.
     */
    suspend fun shouldClearCache(dataStore: DataStore<Preferences>): Boolean {
        val lastClear = dataStore.data.map { prefs ->
            prefs[LAST_CACHE_CLEAR_KEY] ?: 0L
        }
        
        // This would need to be called from a coroutine
        // For now, return false
        return false
    }
    
    /**
     * Get cache optimization recommendations.
     */
    fun getCacheRecommendations(currentSizeMB: Int): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (currentSizeMB > 100) {
            recommendations.add("Cache size is large (${currentSizeMB}MB). Consider clearing.")
        }
        
        if (currentSizeMB > DEFAULT_CACHE_SIZE_MB) {
            recommendations.add("Cache exceeds recommended limit (${DEFAULT_CACHE_SIZE_MB}MB).")
        }
        
        return recommendations
    }
}
