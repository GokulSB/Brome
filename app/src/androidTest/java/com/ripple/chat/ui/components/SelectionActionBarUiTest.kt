package com.ripple.chat.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectionActionBarUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun testFiveButtonsRenderedWithCorrectAccessibilityDescriptions() {
        var selectAllToggled = false
        var markReadClicked = false
        var starClicked = false
        var archiveClicked = false
        var deleteClicked = false

        composeTestRule.setContent {
            TideSelectionActionBar(
                selectedCount = 2,
                isAllSelected = false,
                onSelectAllToggle = { selectAllToggled = true },
                allStarred = false,
                isArchiveScreen = false,
                hasUnreadSelected = true,
                onMarkReadClick = { markReadClicked = true },
                onMarkUnreadClick = {},
                onStarClick = { starClicked = true },
                onArchiveClick = { archiveClicked = true },
                onDeleteClick = { deleteClicked = true }
            )
        }

        // Verify the 5 icon buttons exist and have correct accessibility content descriptions
        composeTestRule.onNodeWithContentDescription("Select all").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Mark as read").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Star").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Archive").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Delete").assertIsDisplayed()

        // Click Select all button
        composeTestRule.onNodeWithContentDescription("Select all").performClick()
        assert(selectAllToggled)

        // Click Archive button
        composeTestRule.onNodeWithContentDescription("Archive").performClick()
        assert(archiveClicked)

        // Click Delete button
        composeTestRule.onNodeWithContentDescription("Delete").performClick()
        assert(deleteClicked)
    }

    @Test
    fun testDeselectAllAndUnarchiveStates() {
        composeTestRule.setContent {
            TideSelectionActionBar(
                selectedCount = 5,
                isAllSelected = true,
                onSelectAllToggle = {},
                allStarred = true,
                isArchiveScreen = true,
                hasUnreadSelected = false,
                onMarkReadClick = {},
                onMarkUnreadClick = {},
                onStarClick = {},
                onArchiveClick = {},
                onDeleteClick = {}
            )
        }

        // Verify state swaps: "Deselect all", "Mark as unread", "Unstar", "Unarchive", "Delete"
        composeTestRule.onNodeWithContentDescription("Deselect all").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Mark as unread").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Unstar").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Unarchive").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Delete").assertIsDisplayed()
    }
}
