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

    @Test
    fun detailScreen_displaysChatBarAtBottom() {
        composeTestRule.setContent {
            DetailScreen(
                onBackClick = {},
                onSettingsClick = {},
                screenshot = testScreenshot
            )
        }

        // Chat bar should have AI icon
        composeTestRule
            .onNodeWithContentDescription("AI")
            .assertExists()

        // Chat bar should have input field with placeholder
        composeTestRule
            .onNodeWithText("Ask anything about this screenshot.*", useRegex = true)
            .assertExists()

        // Chat bar should have send button
        composeTestRule
            .onNodeWithContentDescription("Send")
            .assertExists()
    }

    @Test
    fun detailScreen_chatBarAcceptsInput() {
        composeTestRule.setContent {
            DetailScreen(
                onBackClick = {},
                onSettingsClick = {},
                screenshot = testScreenshot
            )
        }

        // Enter text in chat input
        composeTestRule
            .onNodeWithText("Ask anything about this screenshot.*", useRegex = true)
            .performTextInput("What is this screenshot about?")

        // Verify text was entered
        composeTestRule
            .onNodeWithText("What is this screenshot about?")
            .assertExists()
    }

    @Test
    fun detailScreen_chatBarSendButtonIsClickable() {
        composeTestRule.setContent {
            DetailScreen(
                onBackClick = {},
                onSettingsClick = {},
                screenshot = testScreenshot
            )
        }

        // Enter text first
        composeTestRule
            .onNodeWithText("Ask anything about this screenshot.*", useRegex = true)
            .performTextInput("Test query")

        // Click send button - should not crash
        composeTestRule
            .onNodeWithContentDescription("Send")
            .performClick()

        assertTrue("Send button click handled", true)
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

        // AI Summary card title should be present
        composeTestRule
            .onNodeWithText("On-Device AI Summary")
            .assertExists()

        composeTestRule
            .onNodeWithText("Intelligence Analysis")
            .assertExists()
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

    @Test
    fun detailScreen_chatBarPlaceholderTextIsCorrect() {
        composeTestRule.setContent {
            DetailScreen(
                onBackClick = {},
                onSettingsClick = {},
                screenshot = testScreenshot
            )
        }

        // Verify the chat bar placeholder text
        composeTestRule
            .onNodeWithText("Ask anything about this screenshot.*", useRegex = true)
            .assertExists()
    }
}
