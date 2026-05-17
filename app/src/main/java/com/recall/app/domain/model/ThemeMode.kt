package com.recall.app.domain.model

/**
 * User-selectable app theme mode.
 *
 * - [SYSTEM] — follow the device's dark/light mode setting (default)
 * - [LIGHT]  — always use the light theme regardless of system setting
 * - [DARK]   — always use the dark theme regardless of system setting
 */
enum class ThemeMode(val displayName: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromString(value: String): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
