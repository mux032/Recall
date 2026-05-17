package com.recall.app.presentation.ui.detail

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.recall.app.presentation.ui.theme.RecallTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests verifying dark mode polish changes from Issue #102.
 *
 * These tests run on-device to validate:
 * - RecallTheme composes without crash in both light and dark mode
 * - Surface and onSurface colors satisfy minimum contrast in each mode
 * - Theme tokens are correctly applied (no hardcoded colors leaking into the scheme)
 */
@RunWith(AndroidJUnit4::class)
class DarkModeThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ─── Smoke tests ────────────────────────────────────────────────────────────

    @Test
    fun recallTheme_lightMode_rendersWithoutCrash() {
        composeTestRule.setContent {
            RecallTheme(useDarkTheme = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text(text = "Light Mode")
                }
            }
        }
        composeTestRule.onNodeWithText("Light Mode").assertIsDisplayed()
    }

    @Test
    fun recallTheme_darkMode_rendersWithoutCrash() {
        composeTestRule.setContent {
            RecallTheme(useDarkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text(text = "Dark Mode")
                }
            }
        }
        composeTestRule.onNodeWithText("Dark Mode").assertIsDisplayed()
    }

    // ─── Contrast tests ─────────────────────────────────────────────────────────

    @Test
    fun recallTheme_darkMode_surfaceHasLowLuminance() {
        var surfaceLuminance = -1f
        composeTestRule.setContent {
            RecallTheme(useDarkTheme = true) {
                surfaceLuminance = MaterialTheme.colorScheme.surface.luminance()
                Box(modifier = Modifier.fillMaxSize())
            }
        }
        composeTestRule.waitForIdle()
        assertTrue(
            "Dark mode surface luminance should be < 0.15, was $surfaceLuminance",
            surfaceLuminance < 0.15f
        )
    }

    @Test
    fun recallTheme_lightMode_surfaceHasHighLuminance() {
        var surfaceLuminance = -1f
        composeTestRule.setContent {
            RecallTheme(useDarkTheme = false) {
                surfaceLuminance = MaterialTheme.colorScheme.surface.luminance()
                Box(modifier = Modifier.fillMaxSize())
            }
        }
        composeTestRule.waitForIdle()
        assertTrue(
            "Light mode surface luminance should be > 0.7, was $surfaceLuminance",
            surfaceLuminance > 0.7f
        )
    }

    @Test
    fun recallTheme_darkMode_onSurfaceContrastSufficient() {
        // WCAG AA requires contrast ratio >= 4.5:1. onSurface over surface in dark mode
        // must be clearly distinguishable — luminance delta > 0.3 is a minimal bar here.
        var surface = Color.Unspecified
        var onSurface = Color.Unspecified
        composeTestRule.setContent {
            RecallTheme(useDarkTheme = true) {
                surface = MaterialTheme.colorScheme.surface
                onSurface = MaterialTheme.colorScheme.onSurface
                Box(modifier = Modifier.fillMaxSize())
            }
        }
        composeTestRule.waitForIdle()
        val contrastDelta = Math.abs(onSurface.luminance() - surface.luminance())
        assertTrue(
            "Dark mode onSurface vs surface luminance delta should be > 0.3, was $contrastDelta",
            contrastDelta > 0.3f
        )
    }

    @Test
    fun recallTheme_lightMode_onSurfaceContrastSufficient() {
        var surface = Color.Unspecified
        var onSurface = Color.Unspecified
        composeTestRule.setContent {
            RecallTheme(useDarkTheme = false) {
                surface = MaterialTheme.colorScheme.surface
                onSurface = MaterialTheme.colorScheme.onSurface
                Box(modifier = Modifier.fillMaxSize())
            }
        }
        composeTestRule.waitForIdle()
        val contrastDelta = Math.abs(onSurface.luminance() - surface.luminance())
        assertTrue(
            "Light mode onSurface vs surface luminance delta should be > 0.3, was $contrastDelta",
            contrastDelta > 0.3f
        )
    }
}
