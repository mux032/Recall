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
     * Check if permission is granted at runtime (actual Android permission state)
     */
    fun hasActualPermission(): Boolean {
        val androidPermission = getAndroidPermission()
        val checkResult = context.checkCallingOrSelfPermission(androidPermission)
        return checkResult == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get the appropriate permission string based on Android version
     */
    private fun getAndroidPermission(): String {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
}
