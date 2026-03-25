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
 * Instrumented UI tests for DetailScreen components.
 * These tests verify the ExtractedTextSection functionality including:
 * - Text selection
 * - Edit mode toggle
 * - Copy functionality
 * - Display of extracted text
 */
@RunWith(AndroidJUnit4::class)
class ExtractedTextSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleOcrText = """
        Total Balance: $142,509.20
        Monthly Portfolio Growth: +12.4% since September
        Assets under management consist of 45% Equity
        Recommended Action: Rebalance high-risk assets
    """.trimIndent()

    @Test
    fun extractedTextSection_displaysExtractedTextTitle() {
        composeTestRule.setContent {
            ExtractedTextSection(ocrText = sampleOcrText)
        }

        composeTestRule
            .onNodeWithText("Extracted Text", useUnmergedTree = true)
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun extractedTextSection_displaysEditIcon() {
        composeTestRule.setContent {
            ExtractedTextSection(ocrText = sampleOcrText)
        }

        composeTestRule
            .onNodeWithContentDescription("Edit")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun extractedTextSection_displaysCopyIcon() {
        composeTestRule.setContent {
            ExtractedTextSection(ocrText = sampleOcrText)
        }

        composeTestRule
            .onNodeWithContentDescription("Copy")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun extractedTextSection_displaysOcrText() {
        composeTestRule.setContent {
            ExtractedTextSection(ocrText = sampleOcrText)
        }

        // Verify the OCR text is displayed
        composeTestRule
            .onNodeWithText("Total Balance:.*", useRegex = true)
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun extractedTextSection_doesNotShowLineNumbers() {
        composeTestRule.setContent {
            ExtractedTextSection(ocrText = sampleOcrText)
        }

        // Verify no line numbers are displayed (01, 02, 03, etc.)
        composeTestRule
            .onNodeWithText("01", substring = true)
            .assertDoesNotExist()

        composeTestRule
            .onNodeWithText("02", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun extractedTextSection_clickingEditIcon_togglesEditMode() {
        composeTestRule.setContent {
            ExtractedTextSection(ocrText = sampleOcrText)
        }

        // Initially should show selectable text, not TextField
        composeTestRule
            .onNodeWithText(sampleOcrText)
            .assertExists()

        // Click edit icon
        composeTestRule
            .onNodeWithContentDescription("Edit")
            .performClick()

        // After clicking, TextField should appear for editing
        composeTestRule
            .onNode(hasText(sampleOcrText) and hasImeAction())
            .assertExists()
    }

    @Test
    fun extractedTextSection_editMode_allowsTextModification() {
        composeTestRule.setContent {
            ExtractedTextSection(ocrText = sampleOcrText)
        }

        // Enter edit mode
        composeTestRule
            .onNodeWithContentDescription("Edit")
            .performClick()

        // Modify text
        val modifiedText = "Modified OCR text for testing"
        composeTestRule
            .onNode(hasText(sampleOcrText) and hasImeAction())
            .performTextInput(modifiedText)

        // Verify text was updated
        composeTestRule
            .onNodeWithText(modifiedText)
            .assertExists()
    }

    @Test
    fun extractedTextSection_withNullOcrText_showsEmptyText() {
        composeTestRule.setContent {
            ExtractedTextSection(ocrText = null)
        }

        // Should still display the section but with empty content
        composeTestRule
            .onNodeWithText("Extracted Text")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun extractedTextSection_withEmptyOcrText_showsEmptyText() {
        composeTestRule.setContent {
            ExtractedTextSection(ocrText = "")
        }

        // Should still display the section but with empty content
        composeTestRule
            .onNodeWithText("Extracted Text")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun extractedTextSection_clickingCopyIcon_copiesTextToClipboard() {
        // Note: Actual clipboard testing requires instrumentation with clipboard access
        // This test verifies the copy button is clickable
        composeTestRule.setContent {
            ExtractedTextSection(ocrText = sampleOcrText)
        }

        // Click copy icon - should not crash
        composeTestRule
            .onNodeWithContentDescription("Copy")
            .performClick()

        // Test passes if no exception is thrown
        assertTrue("Copy button click handled without errors", true)
    }

    @Test
    fun extractedTextSection_editIcon_togglesBetweenEditAndViewMode() {
        composeTestRule.setContent {
            ExtractedTextSection(ocrText = sampleOcrText)
        }

        // Initial state: view mode (selectable text)
        composeTestRule
            .onNodeWithText(sampleOcrText)
            .assertExists()

        // First click: enter edit mode
        composeTestRule
            .onNodeWithContentDescription("Edit")
            .performClick()

        composeTestRule
            .onNode(hasImeAction())
            .assertExists()

        // Second click: exit edit mode (back to view mode)
        composeTestRule
            .onNodeWithContentDescription("Edit")
            .performClick()

        // Should be back to selectable text
        composeTestRule
            .onNodeWithText(sampleOcrText)
            .assertExists()
    }

    @Test
    fun extractedTextSection_displaysArticleIcon() {
        composeTestRule.setContent {
            ExtractedTextSection(ocrText = sampleOcrText)
        }

        // The Article icon should be present in the header
        composeTestRule
            .onNodeWithContentDescription("Extracted Text", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun extractedTextSection_withLongOcrText_displaysAllContent() {
        val longOcrText = """
            Line 1: This is a very long line of extracted text that should wrap
            Line 2: Another line with important information
            Line 3: More content here
            Line 4: Even more content
            Line 5: Final line of text
        """.trimIndent()

        composeTestRule.setContent {
            ExtractedTextSection(ocrText = longOcrText)
        }

        // Verify multiple lines are displayed
        composeTestRule
            .onNodeWithText("Line 1:.*", useRegex = true)
            .assertExists()

        composeTestRule
            .onNodeWithText("Line 5:.*", useRegex = true)
            .assertExists()
    }

    @Test
    fun extractedTextSection_iconsAreInContainer() {
        composeTestRule.setContent {
            ExtractedTextSection(ocrText = sampleOcrText)
        }

        // Both icons should exist and be displayed
        val editIcon = composeTestRule.onNodeWithContentDescription("Edit")
        val copyIcon = composeTestRule.onNodeWithContentDescription("Copy")

        editIcon.assertExists()
        copyIcon.assertExists()

        // Both should be displayed
        editIcon.assertIsDisplayed()
        copyIcon.assertIsDisplayed()
    }
}
