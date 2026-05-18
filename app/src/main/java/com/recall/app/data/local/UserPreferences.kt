package com.recall.app.data.local

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.recall.app.domain.model.CacheLimitOption
import com.recall.app.domain.model.IndexingInterval
import com.recall.app.domain.model.ThemeMode
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
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val BANNER_DISMISSED_AT_KEY = longPreferencesKey("banner_dismissed_at")
        val INDEXING_INTERVAL_KEY = stringPreferencesKey("indexing_interval")
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

    // ─── Theme mode ───────────────────────────────────────────────────────────

    /**
     * Flow of the user's selected theme mode.
     * Emits [ThemeMode.SYSTEM] by default (follows device setting).
     */
    val themeModeFlow: Flow<ThemeMode> = dataStore.data
        .map { preferences ->
            val value = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
            ThemeMode.fromString(value)
        }

    /**
     * Persist the user's chosen [ThemeMode].
     * Fails gracefully — logs error but does not throw.
     */
    suspend fun setThemeMode(mode: ThemeMode) {
        try {
            dataStore.edit { preferences ->
                preferences[THEME_MODE_KEY] = mode.name
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting theme mode in DataStore", e)
        }
    }

    /**
     * Reset theme mode to [ThemeMode.SYSTEM] (default).
     */
    suspend fun resetThemeMode() {
        setThemeMode(ThemeMode.SYSTEM)
    }

    // ─── Processing status banner ─────────────────────────────────────────────

    /**
     * Epoch-ms timestamp of when the user last dismissed the [ProcessingStatusBanner].
     * Emits 0L if the banner has never been dismissed.
     */
    val bannerDismissedAtFlow: Flow<Long> = dataStore.data
        .map { preferences -> preferences[BANNER_DISMISSED_AT_KEY] ?: 0L }

    /**
     * Record the epoch-ms timestamp at which the user dismissed the banner.
     * The banner will not reappear until a new worker run starts after this time.
     */
    suspend fun setBannerDismissedAt(timestampMs: Long) {
        try {
            dataStore.edit { preferences ->
                preferences[BANNER_DISMISSED_AT_KEY] = timestampMs
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving banner dismissed timestamp", e)
        }
    }

    /**
     * Clear the dismissed timestamp so the banner can reappear on the next indexing run.
     */
    suspend fun resetBannerDismissed() {
        setBannerDismissedAt(0L)
    }

    // ─── Indexing interval ────────────────────────────────────────────────────

    /**
     * Flow of the user-selected background indexing interval.
     * Defaults to [IndexingInterval.DEFAULT] if never set.
     */
    val indexingIntervalFlow: Flow<IndexingInterval> = dataStore.data
        .map { preferences ->
            IndexingInterval.fromName(
                preferences[INDEXING_INTERVAL_KEY] ?: IndexingInterval.DEFAULT.name
            )
        }

    /**
     * Persist the user's chosen [IndexingInterval].
     * The caller is responsible for re-scheduling the WorkManager periodic worker
     * after this call so the new interval takes effect.
     */
    suspend fun setIndexingInterval(interval: IndexingInterval) {
        try {
            dataStore.edit { preferences ->
                preferences[INDEXING_INTERVAL_KEY] = interval.name
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving indexing interval", e)
        }
    }

    /** Reset to [IndexingInterval.DEFAULT]. */
    suspend fun resetIndexingInterval() {
        setIndexingInterval(IndexingInterval.DEFAULT)
    }
}
