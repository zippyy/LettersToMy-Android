package com.letters2my.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import org.junit.Rule
import org.junit.Test

/**
 * Test C — create a recipient (child) through the UI and prove it persists
 * across an application relaunch.
 */
class RecipientFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun createChild_persistsAcrossRelaunch() {
        AppState.resetToOnboarded()

        var scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()

        compose.onNodeWithTag("nav-family").performClick()
        compose.waitForIdle()

        // Empty state for children.
        compose.onNodeWithText("No children yet. Tap + to add your first child.").assertExists()

        compose.onNodeWithContentDescription("Add Child").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Add Child").assertExists()

        compose.onNodeWithTag("child-name").performTextInput("Audit Child One")
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()

        // Visible in the Children section.
        compose.onNodeWithText("Audit Child One").assertExists()
        scenario.close()

        // Relaunch: still there (Room persistence, not in-memory UI state).
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        compose.onNodeWithTag("nav-family").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Audit Child One").assertExists()
        scenario.close()
    }
}
