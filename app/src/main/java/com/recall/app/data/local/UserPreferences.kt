package com.recall.app.data.local

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.recall.app.domain.model.CacheLimitOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User preferences manager using DataStore.
 * Provides type-safe access to user settings with Flow-based reactivity.
 */
@Singleton
class UserPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        private const val TAG = "UserPreferences"

        // Preference keys
        val VECTOR_CACHE_LIMIT_KEY = stringPreferencesKey("vector_cache_limit")
    }

    /**
     * Flow of the user's selected cache limit option.
     * Emits the current preference and updates when changed.
     */
    val vectorCacheLimitFlow: Flow<CacheLimitOption> = dataStore.data
        .map { preferences ->
            val value = preferences[VECTOR_CACHE_LIMIT_KEY] ?: CacheLimitOption.AUTO.name
            CacheLimitOption.fromString(value)
        }

    /**
     * Get the current cache limit option (suspend function).
     * Use this for one-time reads outside of Compose.
     * 
     * Issue #3 Fix: Added try-catch for DataStore error handling
     * Falls back to CacheLimitOption.AUTO on any error
     */
    suspend fun getVectorCacheLimit(): CacheLimitOption {
        return try {
            dataStore.data
                .map { preferences ->
                    val value = preferences[VECTOR_CACHE_LIMIT_KEY] ?: CacheLimitOption.AUTO.name
                    CacheLimitOption.fromString(value)
                }
                .first()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading vector cache limit from DataStore", e)
            // Fallback to default AUTO option on error
            CacheLimitOption.AUTO
        }
    }

    /**
     * Set the cache limit option.
     * This is a suspend function that writes to DataStore.
     * 
     * Issue #3 Fix: Added try-catch for DataStore error handling
     * Logs error but doesn't throw - fails gracefully
     */
    suspend fun setVectorCacheLimit(option: CacheLimitOption) {
        try {
            dataStore.edit { preferences ->
                preferences[VECTOR_CACHE_LIMIT_KEY] = option.name
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting vector cache limit in DataStore", e)
            // Fail gracefully - preference not saved, but app continues
        }
    }

    /**
     * Reset cache limit to auto (default).
     */
    suspend fun resetVectorCacheLimit() {
        setVectorCacheLimit(CacheLimitOption.AUTO)
    }
}
