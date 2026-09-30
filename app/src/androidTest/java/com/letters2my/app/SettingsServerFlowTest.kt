package com.letters2my.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/**
 * Server-dependent runtime flows against a REAL SelfHostedSync server, reached
 * from the device over the emulator's host-loopback alias (10.0.2.2).
 *
 * This is the layer the headless JVM E2E cannot prove: on the host JVM no
 * Android network policy is applied, so a cleartext / NetworkSecurityPolicy
 * failure would only ever surface here, in the real app process.
 *
 * Arguments come from the instrumentation bundle, not system properties:
 *   -Pandroid.testInstrumentationRunnerArguments.ltmServerUrl=http://10.0.2.2:8080
 *   -Pandroid.testInstrumentationRunnerArguments.ltmServerToken=<token>
 *
 * A missing token is a hard failure, not a skip: a silently skipped suite
 * would report green while proving nothing.
 */
class SettingsServerFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val args = InstrumentationRegistry.getArguments()

    private val baseUrl: String =
        args.getString("ltmServerUrl")?.takeIf { it.isNotBlank() } ?: "http://10.0.2.2:8080"

    private val validToken: String =
        args.getString("ltmServerToken")?.takeIf { it.isNotBlank() }
            ?: fail(
                "ltmServerToken instrumentation argument is required for " +
                    "SettingsServerFlowTest. Pass " +
                    "-Pandroid.testInstrumentationRunnerArguments.ltmServerToken=<token> " +
                    "against a real SelfHostedSync server."
            ).let { "" }

    private fun openSettings() {
        compose.onNodeWithTag("nav-settings").performClick()
        compose.waitForIdle()
    }

    private fun typeConnection(url: String, token: String) {
        compose.onNodeWithTag("settings-url").performScrollTo().performTextClearance()
        compose.onNodeWithTag("settings-url").performTextInput(url)
        compose.onNodeWithTag("settings-token").performScrollTo().performTextClearance()
        compose.onNodeWithTag("settings-token").performTextInput(token)
    }

    private fun awaitText(substring: String, timeoutMs: Long = 25_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            compose.waitForIdle()
            val n = compose.onAllNodesWithText(substring, substring = true)
                .fetchSemanticsNodes().size
            if (n >= 1) return true
            Thread.sleep(250)
        }
        return false
    }

    /** Cleartext HTTP to the host alias must work from the real app process. */
    @Test
    fun reachableServer_withValidToken_showsConnected() {
        AppState.resetToOnboarded()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        openSettings()

        typeConnection(baseUrl, validToken)
        compose.onNodeWithText("Test Connection").performScrollTo().performClick()

        val ok = awaitText("Connected — ")
        if (!ok) {
            fail(
                "Expected a Connected state from $baseUrl. If the server is up and " +
                    "the token is valid this indicates a real connection-path defect " +
                    "(cleartext policy, base-URL normalization, or API version check)."
            )
        }
        scenario.close()
    }

    /** A wrong token must surface a visible authentication error. */
    @Test
    fun validUrl_withInvalidToken_showsAuthError() {
        AppState.resetToOnboarded()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        openSettings()

        typeConnection(baseUrl, "definitely-not-a-valid-token")
        compose.onNodeWithText("Test Connection").performScrollTo().performClick()

        if (!awaitText("Authentication failed")) {
            fail("Expected a visible 'Authentication failed' error for an invalid token")
        }
        scenario.close()
    }

    /** An unreachable server must report offline AND leave the app usable. */
    @Test
    fun unreachableServer_reportsOffline_andLeavesAppUsable() {
        AppState.resetToOnboarded()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        openSettings()

        // Port 9 on the host alias: nothing listens -> connection refused.
        typeConnection("http://10.0.2.2:9", "irrelevant-token")
        compose.onNodeWithText("Test Connection").performScrollTo().performClick()

        if (!awaitText("Server unreachable")) {
            fail("Expected a visible 'Server unreachable' state for a dead endpoint")
        }

        // Unrelated functionality still works while the server is down.
        compose.onNodeWithTag("nav-letters").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No letters yet").assertExists()
        scenario.close()
    }
}
