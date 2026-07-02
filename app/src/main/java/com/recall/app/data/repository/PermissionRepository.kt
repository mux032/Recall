package com.recall.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val PERMISSION_GRANTED_KEY = booleanPreferencesKey("permission_granted")
    }

    val isPermissionGranted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PERMISSION_GRANTED_KEY] ?: false
    }

    suspend fun setPermissionGranted(granted: Boolean) {
        dataStore.edit { preferences ->
            preferences[PERMISSION_GRANTED_KEY] = granted
        }
    }

    /**
     * Single authoritative runtime permission check covering all supported API levels:
     * - API 33+ (Tiramisu): READ_MEDIA_IMAGES
     * - API 31-32 (S, Sv2): READ_EXTERNAL_STORAGE (still required on Android 12/12L)
     * - API 29-30 (Q, R): no runtime permission needed for MediaStore image access
     * - API < 29: READ_EXTERNAL_STORAGE
     *
     * Use this instead of inline permission checks scattered across the codebase.
     */
    fun hasActualPermission(): Boolean {
        val granted = android.content.pm.PackageManager.PERMISSION_GRANTED
        return when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU ->
                context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == granted
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ->
                context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == granted
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q ->
                true // API 29-30: MediaStore image access requires no runtime permission
            else ->
                context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == granted
        }
    }
}
