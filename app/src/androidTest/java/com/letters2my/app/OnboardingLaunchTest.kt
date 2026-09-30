package com.letters2my.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Smoke test for the instrumentation harness itself: proves the runner is
 * wired, the app launches, and the fresh-install onboarding gate is reached.
 *
 * Uses [createEmptyComposeRule] + manual [ActivityScenario] launch (rather than
 * createAndroidComposeRule) so preference/DB state can be cleared BEFORE the
 * activity starts. createAndroidComposeRule launches the activity during rule
 * setup, i.e. before @Before runs, which would make the first composition read
 * a stale `onboarded` flag.
 */
class OnboardingLaunchTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun freshInstall_showsOnboarding_and_completingIt_persists() {
        // Fresh install: no onboarding flag, no user data.
        AppState.resetAll()

        var scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            // Onboarding is the only thing on screen: it is the first-run gate.
            compose.onNodeWithText("Next").assertExists()

            // Walk the 3 steps: Next, Next, Get Started.
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithText("Get Started").performClick()

            // Completed -> flag persisted.
            assertTrue("onboarding flag should persist", AppState.isOnboarded())

            // Main shell (bottom navigation) is now visible.
            compose.onNodeWithTag("nav-letters").assertExists()
        } finally {
            scenario.close()
        }

        // Relaunch: onboarding must NOT reappear.
        scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.onNodeWithTag("nav-letters").assertExists()
        } finally {
            scenario.close()
        }
    }
}
