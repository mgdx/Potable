package io.github.mgdx.potable.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.robolectric.Shadows.shadowOf

/** How long a screen test waits for a state that comes from a coroutine. */
private const val WAIT_TIMEOUT_MS = 5_000L

/**
 * Declares the empty [ComponentActivity] that `createComposeRule()` hosts
 * the screen in, to Robolectric's package manager. ui-test-manifest would
 * declare it in the merged manifest, hence as an exported activity of the
 * debug APK. Applied before the compose rule (`@get:Rule(order = 0)`).
 */
class ComposeHostRegistration : ExternalResource() {
    override fun before() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application.packageManager)
            .addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
    }
}

/** A string of the application, in the locale the test runs in. */
fun string(id: Int, vararg args: Any): String =
    ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

/** Waits until a node shows [text] (substring when [substring]). */
fun ComposeContentTestRule.waitForText(text: String, substring: Boolean = false) =
    waitUntil(WAIT_TIMEOUT_MS) {
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }
