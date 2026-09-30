package com.letters2my.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import org.junit.Rule
import org.junit.Test

/**
 * Test B — the five real navigation destinations are reachable from the
 * bottom bar and each renders its own screen (asserted on visible content,
 * not internal nav state).
 */
class NavigationTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private fun launch(): ActivityScenario<MainActivity> {
        AppState.resetToOnboarded()
        val s = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        return s
    }

    @Test
    fun allDestinations_reachable_from_bottomBar() {
        val scenario = launch()
        try {
            // Each destination is asserted on content UNIQUE to that screen.
            // The screen title is deliberately not used: the bottom bar merges
            // the same label into its nav item, so onNodeWithText("Letters")
            // matches two nodes and assertExists() requires exactly one.

            // Letters is the start destination.
            compose.onNodeWithText("No letters yet").assertExists()

            compose.onNodeWithTag("nav-timeline").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Nothing scheduled yet").assertExists()

            compose.onNodeWithTag("nav-family").performClick()
            compose.waitForIdle()
            // Family actually rendered its Children section header.
            compose.onNodeWithText("Children").assertExists()

            compose.onNodeWithTag("nav-people").performClick()
            compose.waitForIdle()
            // People's body is config-dependent ("Self-hosted not configured"
            // vs "Members (n)"), and even the Invite FAB is conditional, so
            // assert the screen root instead of either branch.
            compose.onNodeWithTag("people-screen").assertExists()

            compose.onNodeWithTag("nav-settings").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Self-Hosted Server").assertExists()

            // Back to Letters.
            compose.onNodeWithTag("nav-letters").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("No letters yet").assertExists()
        } finally {
            scenario.close()
        }
    }
}
