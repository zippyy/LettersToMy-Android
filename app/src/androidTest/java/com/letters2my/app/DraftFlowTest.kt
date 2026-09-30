package com.letters2my.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
 * Test D + F — create a draft, edit it, and prove the edited values persist;
 * then delete one letter and prove unrelated content survives.
 */
class DraftFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    /**
     * Saving is asynchronous (viewModelScope + Dispatchers.IO) and
     * waitForIdle() does NOT wait for it, so assertions must await the editor
     * actually closing.
     *
     * Await the editor's DISAPPEARANCE rather than the bottom bar's
     * reappearance: saving a NEW letter pops to Letters (bar visible), but
     * saving an EDIT pops back to the detail screen, where the bar stays hidden.
     */
    private fun awaitEditorGone(timeoutMs: Long = 15_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            compose.waitForIdle()
            if (compose.onAllNodesWithTag("editor-title").fetchSemanticsNodes().isEmpty()) return true
            Thread.sleep(200)
        }
        return false
    }

    private fun ActivityScenario<MainActivity>.editorCreate(title: String, body: String) {
        compose.onNodeWithContentDescription("New Letter").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("editor-title").performTextInput(title)
        compose.onNodeWithTag("editor-body").performTextInput(body)
        compose.onNodeWithText("Save Draft").performClick()
        awaitEditorGone()
    }

    @Test
    fun createEditDraft_persistsEditedValues() {
        AppState.resetToOnboarded()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()

        scenario.editorCreate("Audit Draft Alpha", "first body text")
        compose.onNodeWithText("Audit Draft Alpha").assertExists()

        // Open it, edit, save.
        compose.onNodeWithText("Audit Draft Alpha").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Edit").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("editor-title").performTextClearance()
        compose.onNodeWithTag("editor-title").performTextInput("Audit Draft Alpha EDITED")
        compose.onNodeWithTag("editor-body").performTextClearance()
        compose.onNodeWithTag("editor-body").performTextInput("second body text")
        compose.onNodeWithText("Save Draft").performClick()
        awaitEditorGone()

        // Detail shows the edited title.
        compose.onNodeWithText("Audit Draft Alpha EDITED").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()

        // Relaunch: edited value is the persisted one.
        scenario.close()
        val s2 = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        compose.onNodeWithText("Audit Draft Alpha EDITED").assertExists()
        s2.close()
    }

    @Test
    fun deleteOneLetter_leavesUnrelatedLetterIntact() {
        AppState.resetToOnboarded()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()

        scenario.editorCreate("Audit Keep Beta", "keep this body")
        scenario.editorCreate("Audit Delete Gamma", "delete this body")
        compose.onNodeWithText("Audit Keep Beta").assertExists()
        compose.onNodeWithText("Audit Delete Gamma").assertExists()

        // Delete Gamma from its detail screen.
        compose.onNodeWithText("Audit Delete Gamma").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Delete").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Delete Draft?").assertExists()
        compose.onNodeWithText("Delete").performClick()
        compose.waitForIdle()

        // Gamma gone, Beta untouched.
        compose.onNodeWithText("Audit Keep Beta").assertExists()
        compose.onNodeWithText("Audit Delete Gamma").assertDoesNotExist()
        scenario.close()
    }
}
