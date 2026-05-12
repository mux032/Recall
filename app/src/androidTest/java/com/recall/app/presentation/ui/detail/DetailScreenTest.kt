package com.recall.app.presentation.ui.detail

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.recall.app.domain.model.Screenshot
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for DetailScreen.
 * Tests the overall screen functionality including:
 * - Screenshot display
 * - Chat bar at bottom
 * - Extracted text section integration
 * - AI summary card
 */
@RunWith(AndroidJUnit4::class)
class DetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testScreenshot = Screenshot(
        id = "test_123",
        filePath = "/storage/screenshots/test.png",
        fileName = "test_screenshot.png",
        dateCreated = System.currentTimeMillis(),
        dateIndexed = System.currentTimeMillis(),
        width = 1080,
        height = 2340,
        ocrText = "Sample extracted text from screenshot",
        appName = "TestApp"
    )

    @Test
    fun detailScreen_displaysHeroScreenshotSection() {
        composeTestRule.setContent {
            DetailScreen(
                onBackClick = {},
                onSettingsClick = {},
                screenshot = testScreenshot
            )
        }

        // The screen should load without crashing
        composeTestRule
            .onNodeWithTag("detailScreen")
            .assertExists()
    }

    /**
     * Chat bar is hidden when BuildConfig.ENABLE_AI_CHAT = false (current build).
     * It will be re-enabled in Phase 7 when the AI chat backend is ready.
     */
    @Test
    fun detailScreen_chatBarIsHiddenWhenAiChatDisabled() {
        composeTestRule.setContent {
            DetailScreen(
                onBackClick = {},
                onSettingsClick = {},
                screenshot = testScreenshot
            )
        }

        // Chat bar input placeholder should NOT be present
        composeTestRule
            .onNodeWithText("Ask anything about this screenshot.*", useRegex = true)
            .assertDoesNotExist()

        // Send button should NOT be present
        composeTestRule
            .onNodeWithContentDescription("Send")
            .assertDoesNotExist()
    }

    @Test
    fun detailScreen_noTopAppBar() {
        composeTestRule.setContent {
            DetailScreen(
                onBackClick = {},
                onSettingsClick = {},
                screenshot = testScreenshot
            )
        }

        // Verify there's no top app bar with "Curator" title
        composeTestRule
            .onNodeWithText("Curator")
            .assertDoesNotExist()
    }

    @Test
    fun detailScreen_noBottomNavigationButtons() {
        composeTestRule.setContent {
            DetailScreen(
                onBackClick = {},
                onSettingsClick = {},
                screenshot = testScreenshot
            )
        }

        // Verify no Gallery, Intelligence, Settings navigation buttons
        composeTestRule
            .onNodeWithText("Gallery")
            .assertDoesNotExist()

        composeTestRule
            .onNodeWithText("Intelligence")
            .assertDoesNotExist()

        composeTestRule
            .onNodeWithText("Settings")
            .assertDoesNotExist()
    }

    @Test
    fun detailScreen_displaysScreenshotFileName() {
        composeTestRule.setContent {
            DetailScreen(
                onBackClick = {},
                onSettingsClick = {},
                screenshot = testScreenshot
            )
        }

        // File name should be displayed
        composeTestRule
            .onNodeWithText("test_screenshot.png")
            .assertExists()
    }

    @Test
    fun detailScreen_displaysCaptureDate() {
        composeTestRule.setContent {
            DetailScreen(
                onBackClick = {},
                onSettingsClick = {},
                screenshot = testScreenshot
            )
        }

        // "Captured on" text should be present
        composeTestRule
            .onNodeWithText("Captured on.*", useRegex = true)
            .assertExists()
    }

    @Test
    fun detailScreen_noSourceLinkButton() {
        composeTestRule.setContent {
            DetailScreen(
                onBackClick = {},
                onSettingsClick = {},
                screenshot = testScreenshot
            )
        }

        // Source Link button should not exist
        composeTestRule
            .onNodeWithText("Source Link")
            .assertDoesNotExist()
    }

    @Test
    fun detailScreen_noTextSelectionHighlightBox() {
        composeTestRule.setContent {
            DetailScreen(
                onBackClick = {},
                onSettingsClick = {},
                screenshot = testScreenshot
            )
        }

        // The simulated text selection highlight box should not exist
        composeTestRule
            .onNodeWithText("Copy", substring = true)
            .assertDoesNotExist()

        composeTestRule
            .onNodeWithText("Share", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun detailScreen_displaysAISummaryCard() {
        composeTestRule.setContent {
            DetailScreen(
                onBackClick = {},
                onSettingsClick = {},
                screenshot = testScreenshot
            )
        }

        // AI Summary card header should be present
        composeTestRule
            .onNodeWithText("On-Device AI Summary")
            .assertExists()

        composeTestRule
            .onNodeWithText("Intelligence Analysis")
            .assertExists()

        // Coming soon placeholder should be shown (no real summary available yet)
        composeTestRule
            .onNodeWithText("AI Summary coming soon")
            .assertExists()
    }

    @Test
    fun detailScreen_aiSummaryCard_doesNotShowHardcodedFintechText() {
        composeTestRule.setContent {
            DetailScreen(
                onBackClick = {},
                onSettingsClick = {},
                screenshot = testScreenshot
            )
        }

        // Hardcoded fintech marketing text must not appear
        composeTestRule
            .onNodeWithText("fintech dashboard", substring = true)
            .assertDoesNotExist()

        composeTestRule
            .onNodeWithText("portfolio liquidity", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun detailScreen_displaysExtractedTextSection() {
        composeTestRule.setContent {
            DetailScreen(
                onBackClick = {},
                onSettingsClick = {},
                screenshot = testScreenshot
            )
        }

        // Extracted Text section should be present
        composeTestRule
            .onNodeWithText("Extracted Text")
            .assertExists()
    }

    @Test
    fun detailScreen_displaysSuggestedActions() {
        composeTestRule.setContent {
            DetailScreen(
                onBackClick = {},
                onSettingsClick = {},
                screenshot = testScreenshot
            )
        }

        // Recommended Actions section should be present
        composeTestRule
            .onNodeWithText("Recommended Actions")
            .assertExists()
    }

    @Test
    fun detailScreen_withNullScreenshot_doesNotCrash() {
        composeTestRule.setContent {
            DetailScreen(
                onBackClick = {},
                onSettingsClick = {},
                screenshot = null
            )
        }

        // Screen should handle null screenshot gracefully
        composeTestRule
            .waitForIdle()

        assertTrue("Screen handles null screenshot", true)
    }

    /**
     * When ENABLE_AI_CHAT = false, the chat bar placeholder text should not be visible.
     */
    @Test
    fun detailScreen_chatBarPlaceholderText_notVisibleWhenDisabled() {
        composeTestRule.setContent {
            DetailScreen(
                onBackClick = {},
                onSettingsClick = {},
                screenshot = testScreenshot
            )
        }

        // Chat bar placeholder text should not be present when AI chat is disabled
        composeTestRule
            .onNodeWithText("Ask anything about this screenshot.*", useRegex = true)
            .assertDoesNotExist()
    }
}
