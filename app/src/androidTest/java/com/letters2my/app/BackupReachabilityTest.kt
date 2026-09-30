package com.letters2my.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import org.junit.Rule
import org.junit.Test

/**
 * Test G — the Backup screen must be reachable from the UI.
 *
 * This is a regression guard: the Backup route previously existed but was not
 * reachable through any user-visible control. Reaching it now requires the
 * Settings card buttons to wire to the "backup" nav route.
 */
class BackupReachabilityTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun backupScreen_reachableFromSettings() {
        AppState.resetToOnboarded()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()

        compose.onNodeWithTag("nav-settings").performClick()
        compose.waitForIdle()

        // The Backup & Restore card is reachable by scrolling Settings.
        compose.onNodeWithText("Backup & Restore").performScrollTo().assertExists()
        compose.onNodeWithText("Create Backup").performScrollTo().performClick()
        compose.waitForIdle()

        // We are on the Backup screen, not still in Settings.
        compose.onNodeWithText("Portable encrypted archive (.letterstomy)").assertExists()
        compose.onNodeWithText("Backup passphrase").assertExists()

        scenario.close()
    }
}
