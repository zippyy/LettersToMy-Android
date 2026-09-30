package com.letters2my.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/**
 * Section-5 runtime acceptance: the backup/restore journey driven through the
 * real UI against a real SelfHostedSync server.
 *
 * Deliberately split into small independent tests rather than one long script:
 * a mid-flow navigation hiccup should not invalidate the whole journey. Every
 * helper ASSERTS it arrived where it thinks it did, so a failure names the step
 * that broke instead of surfacing as an unrelated missing node.
 *
 * Design notes (each corresponds to a real failure hit while writing this):
 *  - Saving is asynchronous (viewModelScope + Dispatchers.IO) and
 *    waitForIdle() does NOT wait for it, so saves await the editor's
 *    disappearance.
 *  - Selectors for repeating rows (one Restore/Delete per remote backup) must
 *    not be single-node matchers: the remote list is server state and
 *    accumulates across runs.
 *  - "Test Connection" is what persists URL/token (Create Backup only
 *    navigates), mirroring what a user does first.
 */
class BackupRestoreFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val args = InstrumentationRegistry.getArguments()

    private val baseUrl: String =
        args.getString("ltmServerUrl")?.takeIf { it.isNotBlank() } ?: "http://10.0.2.2:8080"

    private val token: String =
        args.getString("ltmServerToken")?.takeIf { it.isNotBlank() }
            ?: fail("ltmServerToken instrumentation argument is required").let { "" }

    private val passphrase = "Runtime-Acceptance-42"

    // ---------- helpers ----------

    private fun awaitText(substring: String, timeoutMs: Long = 30_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            compose.waitForIdle()
            if (compose.onAllNodesWithText(substring, substring = true)
                    .fetchSemanticsNodes().size >= 1
            ) return true
            Thread.sleep(250)
        }
        return false
    }

    private fun requireText(what: String, where: String) {
        if (!awaitText(what)) {
            // Dump the actual screen so a navigation failure is diagnosable
            // instead of surfacing as an opaque missing-node error.
            val tree = runCatching { compose.onRoot().printToString(maxDepth = 8) }
                .getOrElse { "semantics unavailable: $it" }
            fail("$where: never saw '$what'\n--- on-screen semantics ---\n${tree.take(5000)}")
        }
    }

    /**
     * Saving is asynchronous and waitForIdle() does NOT wait for it. Await the
     * editor's disappearance — works whether the save pops to Letters or back
     * to a detail screen.
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

    private fun seedLetter(title: String, body: String) {
        compose.onNodeWithContentDescription("New Letter").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("editor-title").performTextInput(title)
        compose.onNodeWithTag("editor-body").performTextInput(body)
        compose.onNodeWithText("Save Draft").performClick()
        if (!awaitEditorGone()) fail("editor did not close after saving '$title'")
    }

    /**
     * Navigate to the Settings root.
     *
     * The Settings TAB restores its previously saved sub-stack: the bottom bar
     * navigates with `popUpTo("letters") { saveState = true }` plus
     * `restoreState = true`, so if the Settings tab was previously drilled into
     * (e.g. Backup & Restore), tapping it returns to that sub-screen rather than
     * the Settings root. That is the app's intended tab behavior, so this helper
     * backs out to the root exactly as a user would instead of asserting the
     * root unconditionally.
     */
    private fun gotoSettings() {
        compose.waitForIdle()
        compose.onNodeWithTag("nav-settings").performClick()
        if (awaitText("Self-Hosted Server", timeoutMs = 4_000)) return
        repeat(3) {
            compose.onAllNodesWithContentDescription("Back").onFirst().performClick()
            compose.waitForIdle()
            if (awaitText("Self-Hosted Server", timeoutMs = 2_000)) return
        }
        requireText("Self-Hosted Server", "Settings root")
    }

    /** Persist server config through the real Test Connection path. */
    private fun configureServer() {
        compose.onNodeWithTag("settings-url").performScrollTo().performTextClearance()
        compose.onNodeWithTag("settings-url").performTextInput(baseUrl)
        compose.onNodeWithTag("settings-token").performScrollTo().performTextClearance()
        compose.onNodeWithTag("settings-token").performTextInput(token)
        compose.onNodeWithText("Test Connection").performScrollTo().performClick()
        requireText("Connected — ", "Test Connection")
    }

    /** Open the Backup screen from Settings and confirm it rendered. */
    private fun gotoBackup() {
        compose.onNodeWithText("Create Backup").performScrollTo().performClick()
        requireText("Portable encrypted archive (.letterstomy)", "Backup screen")
    }

    private fun uploadBackup() {
        compose.onNodeWithTag("backup-passphrase").performScrollTo().performTextClearance()
        compose.onNodeWithTag("backup-passphrase").performTextInput(passphrase)
        compose.onNodeWithText("Create & Upload Backup").performScrollTo().performClick()
        requireText("Backup uploaded:", "backup upload")
    }

    private fun scrollToRemoteList() {
        compose.onNodeWithTag("backup-list")
            .performScrollToNode(hasText("Remote Backups", substring = true))
        compose.waitForIdle()
    }

    private fun launchClean(): ActivityScenario<MainActivity> {
        AppState.resetToOnboarded()
        val s = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        return s
    }

    // ---------- tests ----------

    /** Create + upload + list + preview metadata. */
    @Test
    fun createUploadListAndPreview() {
        val scenario = launchClean()
        try {
            seedLetter("Audit Backup Epsilon", "backup proof body")
            gotoSettings()
            configureServer()
            gotoBackup()
            uploadBackup()

            scrollToRemoteList()
            // Count-agnostic: the remote list is server state and accumulates.
            compose.onNodeWithText("Remote Backups (", substring = true).assertExists()

            // Preview reports real metadata.
            compose.onAllNodesWithText("Restore").onFirst().performClick()
            requireText("Restore Archive", "restore preview dialog")
            compose.onNodeWithText("Letters: 1").assertExists()
            compose.onNodeWithText("Cancel").performClick()
            compose.waitForIdle()
        } finally {
            scenario.close()
        }
    }

    /** Wrong passphrase must fail visibly and must not mutate local data. */
    @Test
    fun wrongPassphraseFailsAndLeavesLocalDataIntact() {
        val scenario = launchClean()
        try {
            seedLetter("Audit Wrongpass Zeta", "wrongpass proof body")
            gotoSettings()
            configureServer()
            gotoBackup()
            uploadBackup()

            scrollToRemoteList()
            compose.onNodeWithTag("backup-passphrase").performScrollTo().performTextClearance()
            compose.onNodeWithTag("backup-passphrase").performTextInput("definitely-wrong-passphrase")
            compose.onAllNodesWithText("Restore").onFirst().performClick()
            requireText("Could not download/decrypt backup", "wrong-passphrase failure")

            // Local data untouched: the seeded letter is still listed.
            compose.onAllNodesWithText("Dismiss").onFirst().performClick()
            compose.onNodeWithTag("nav-letters").performClick()
            requireText("Audit Wrongpass Zeta", "local letters after failed restore")
        } finally {
            scenario.close()
        }
    }

    /** Delete a letter, restore it from the server archive, then delete the backup. */
    @Test
    fun deleteThenRestoreFromServer_thenDeleteRemoteBackup() {
        val scenario = launchClean()
        try {
            seedLetter("Audit Restore Delta", "restore proof body")
            gotoSettings()
            configureServer()
            gotoBackup()
            uploadBackup()

            // Delete the local copy.
            compose.onNodeWithTag("nav-letters").performClick()
            requireText("Audit Restore Delta", "letters list")
            compose.onNodeWithText("Audit Restore Delta").performClick()
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Delete").performClick()
            compose.waitForIdle()
            compose.onAllNodesWithText("Delete").onFirst().performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Audit Restore Delta").assertDoesNotExist()

            // Restore the valid archive.
            gotoSettings()
            gotoBackup()
            scrollToRemoteList()
            compose.onNodeWithTag("backup-passphrase").performScrollTo().performTextClearance()
            compose.onNodeWithTag("backup-passphrase").performTextInput(passphrase)
            compose.onAllNodesWithText("Restore").onFirst().performClick()
            requireText("Restore Archive", "restore preview for valid archive")
            compose.onNodeWithTag("restore-confirm").performClick()
            compose.waitForIdle()

            // The letter is back.
            compose.onNodeWithTag("nav-letters").performClick()
            if (!awaitText("Audit Restore Delta")) {
                fail("restored letter did not reappear after a valid restore")
            }

            // Delete the remote backup and confirm.
            gotoSettings()
            gotoBackup()
            scrollToRemoteList()
            compose.onAllNodesWithText("Delete").onFirst().performClick()
            requireText("deleted", "remote backup delete confirmation")
        } finally {
            scenario.close()
        }
    }
}
