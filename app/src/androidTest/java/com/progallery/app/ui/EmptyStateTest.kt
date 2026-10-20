package com.progallery.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.progallery.app.ui.components.EmptyState
import org.junit.Rule
import org.junit.Test

/** Instrumented Compose test for a reusable UI component. */
class EmptyStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyState_showsTitleAndSubtitle() {
        composeRule.setContent {
            EmptyState(
                icon = Icons.Filled.PhotoLibrary,
                title = "No photos found",
                subtitle = "Photos on this device will appear here.",
            )
        }
        composeRule.onNodeWithText("No photos found").assertIsDisplayed()
        composeRule.onNodeWithText("Photos on this device will appear here.").assertIsDisplayed()
    }
}
