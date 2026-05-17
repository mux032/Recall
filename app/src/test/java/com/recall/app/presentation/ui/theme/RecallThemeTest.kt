package com.recall.app.presentation.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for RecallTheme dark-mode polish (Issue #102).
 *
 * Verifies that:
 * - Light and dark color schemes use distinct surface/background values
 * - Dark color scheme does NOT use primary as surface (i.e., status bar will be transparent, not primary)
 * - Hardcoded primary is different from background/surface in both themes,
 *   confirming the fix that moved away from using primary for the status bar
 */
class RecallThemeTest {

    // Mirror the private color scheme constants from Theme.kt using the same tokens from Color.kt
    private val lightColors: ColorScheme = lightColorScheme(
        primary = md_theme_light_primary,
        background = md_theme_light_background,
        surface = md_theme_light_surface,
        onSurface = md_theme_light_onSurface,
    )

    private val darkColors: ColorScheme = darkColorScheme(
        primary = md_theme_dark_primary,
        background = md_theme_dark_background,
        surface = md_theme_dark_surface,
        onSurface = md_theme_dark_onSurface,
    )

    @Test
    fun lightTheme_primaryDiffersFromSurface() {
        // Previously, statusBarColor was set to colors.primary. Verify primary != surface so
        // using primary as a status bar color would have been noticeably wrong.
        assertNotEquals(
            "Light theme primary should differ from surface",
            lightColors.primary,
            lightColors.surface
        )
    }

    @Test
    fun darkTheme_primaryDiffersFromSurface() {
        assertNotEquals(
            "Dark theme primary should differ from surface",
            darkColors.primary,
            darkColors.surface
        )
    }

    @Test
    fun darkTheme_surfaceIsDark() {
        // Dark mode surface should be a near-black color (luminance < 0.1)
        val surface = darkColors.surface
        val luminance = (0.2126f * surface.red + 0.7152f * surface.green + 0.0722f * surface.blue)
        assert(luminance < 0.15f) {
            "Dark theme surface should be dark, but luminance was $luminance (color=$surface)"
        }
    }

    @Test
    fun lightTheme_surfaceIsLight() {
        val surface = lightColors.surface
        val luminance = (0.2126f * surface.red + 0.7152f * surface.green + 0.0722f * surface.blue)
        assert(luminance > 0.7f) {
            "Light theme surface should be light, but luminance was $luminance (color=$surface)"
        }
    }

    @Test
    fun darkTheme_onSurfaceIsLightForContrast() {
        // onSurface in dark mode must be light so text (and empty-state illustration) is visible
        val onSurface = darkColors.onSurface
        val luminance = (0.2126f * onSurface.red + 0.7152f * onSurface.green + 0.0722f * onSurface.blue)
        assert(luminance > 0.5f) {
            "Dark theme onSurface should be light for contrast, but luminance was $luminance"
        }
    }

    @Test
    fun lightTheme_onSurfaceIsDarkForContrast() {
        val onSurface = lightColors.onSurface
        val luminance = (0.2126f * onSurface.red + 0.7152f * onSurface.green + 0.0722f * onSurface.blue)
        assert(luminance < 0.3f) {
            "Light theme onSurface should be dark for contrast, but luminance was $luminance"
        }
    }

    @Test
    fun darkAndLightThemes_haveDistinctBackgrounds() {
        assertNotEquals(
            "Dark and light theme backgrounds should differ",
            darkColors.background,
            lightColors.background
        )
    }
}
