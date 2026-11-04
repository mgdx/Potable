package io.github.mgdx.potable.ui.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.mgdx.potable.R
import io.github.mgdx.potable.alert.NotificationAccess
import io.github.mgdx.potable.ui.ComposeHostRegistration
import io.github.mgdx.potable.ui.FakeAppPreferences
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.FakeHistoryRepository
import io.github.mgdx.potable.ui.FakeWaterRepository
import io.github.mgdx.potable.ui.string
import io.github.mgdx.potable.ui.theme.PotableTheme
import io.github.mgdx.potable.ui.waitForText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The offer to switch the alerts on, made once per install, waits for an answer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlertOfferSnackbarTest {
    @get:Rule(order = 0)
    val host = ComposeHostRegistration()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val access = object : NotificationAccess {
        override val canRequest: Boolean = true

        override fun canPost(): Boolean = true
    }

    private val viewModel = SheetViewModel(
        FakeWaterRepository(),
        FakeFollowRepository(),
        FakeHistoryRepository(),
        "45234",
        "045000123",
        "Orléans",
        "ORLEANS",
        Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC),
        recents = FakeAppPreferences(),
        notifications = access,
    )

    private val dismiss =
        hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)

    private fun offer() {
        compose.setContent {
            PotableTheme {
                val snackbar = remember { SnackbarHostState() }
                Box {
                    SnackbarHost(snackbar)
                    AlertOfferSnackbar(viewModel, snackbar)
                }
            }
        }
        compose.runOnIdle { viewModel.toggleFollow() }
        compose.waitForText(string(R.string.sheet_alerts_offer))
    }

    @Test
    fun `the offer stays until it is answered`() {
        offer()
        compose.mainClock.advanceTimeBy(60_000)
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.sheet_alerts_offer)).assertExists()
        compose.onNodeWithText(string(R.string.sheet_alerts_offer_action)).assertExists()
    }

    @Test
    fun `the offer has a close button`() {
        offer()
        compose.onNode(dismiss).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.sheet_alerts_offer)).assertDoesNotExist()
    }
}
