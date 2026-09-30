package com.letters2my.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import org.junit.Rule
import org.junit.Test

/**
 * Test E — sealed-content privacy exercised through the REAL UI.
 *
 * A sealed (scheduled, locked) letter's BODY must not be discoverable by
 * search, because that would confirm its contents to a viewer who cannot open
 * it. Title/author stay searchable — the sealed row renders the title anyway.
 *
 * This drives the actual Compose search box rather than calling the filtering
 * function directly, so the UI wiring is covered too.
 */
class SealedPrivacyTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val secretBody = "SECRETBODYPHRASE9431"
    private val sealedTitle = "Audit Sealed Deliberate"

    @Test
    fun sealedBody_notDiscoverableBySearch_titleIs() {
        AppState.resetToOnboarded()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()

        // Create a sealed letter through the real editor path. The default
        // unlock rule is a specific date with no date chosen, which resolves
        // to "not yet unlocked" => sealed + scheduled (locked).
        compose.onNodeWithContentDescription("New Letter").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("editor-title").performTextInput(sealedTitle)
        compose.onNodeWithTag("editor-body").performTextInput(secretBody)
        compose.onNodeWithText("Seal Letter").performClick()
        compose.waitForIdle()

        // The row is present and marked Sealed, with no body preview.
        compose.onNodeWithText(sealedTitle).assertExists()
        compose.onNodeWithText("Sealed").assertExists()

        // Search the sealed BODY phrase: must NOT surface the letter.
        compose.onNodeWithTag("letter-search").performTextInput(secretBody)
        compose.waitForIdle()
        compose.onNodeWithText(sealedTitle).assertDoesNotExist()
        compose.onNodeWithText("No letters yet").assertExists()

        // Search the TITLE: must still match (permitted metadata).
        compose.onNodeWithTag("letter-search").performTextClearance()
        compose.onNodeWithTag("letter-search").performTextInput("Audit Sealed")
        compose.waitForIdle()
        compose.onNodeWithText(sealedTitle).assertExists()

        scenario.close()
    }
}
