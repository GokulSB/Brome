package com.ripple.chat.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.ripple.chat.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun mainScreen_rendersWithoutCrashing() {
    composeTestRule.setContent {
      MainScreen(
        onNavigateToChat = { _, _, _, _, _ -> },
        onNavigateToSettings = {},
        onNavigateToProfile = {},
        onNavigateToThemes = {},
        onNavigateToTrash = {},
        onNavigateToFinanceLedger = {},
        onNavigateToNewMessage = {}
      )
    }
  }
}
