package io.github.mgdx.potable.ui.disclaimer

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.mgdx.potable.AppContainer
import io.github.mgdx.potable.R
import io.github.mgdx.potable.location.LocationOutcome
import io.github.mgdx.potable.location.Locator
import io.github.mgdx.potable.ui.ComposeHostRegistration
import io.github.mgdx.potable.ui.FakeAppPreferences
import io.github.mgdx.potable.ui.FakeCommuneRepository
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.search.SearchScreen
import io.github.mgdx.potable.ui.search.SearchViewModel
import io.github.mgdx.potable.ui.string
import io.github.mgdx.potable.ui.theme.PotableTheme
import io.github.mgdx.potable.ui.waitForText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The first-launch notice (SPEC §14), on the real preferences of a fresh
 * install (DataStore in Robolectric's temporary files): acknowledged, it
 * gives way to the search.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DisclaimerScreenTest {
    @get:Rule(order = 0)
    val host = ComposeHostRegistration()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    @Test
    fun `accepting the notice opens the search`() {
        val container = AppContainer(ApplicationProvider.getApplicationContext<Context>())
        val search = SearchViewModel(
            FakeCommuneRepository(emptyList()),
            Locator { LocationOutcome.Unavailable },
            FakeFollowRepository(),
            FakeAppPreferences(),
        )
        compose.setContent {
            PotableTheme {
                DisclaimerGate(container) {
                    SearchScreen(
                        viewModel = search,
                        onCommuneSelected = { _, _ -> },
                        onKnownUdiSelected = { _, _, _ -> },
                        onRecentSelected = {},
                        onMyCommunes = {},
                        onSettings = {},
                        onAbout = {},
                    )
                }
            }
        }

        compose.waitForText(string(R.string.disclaimer_title))
        compose.onNodeWithText(string(R.string.search_label)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.disclaimer_next)).performClick()
        compose.onNodeWithText(string(R.string.intro_alerts_title)).assertExists()
        compose.onNodeWithText(string(R.string.disclaimer_accept)).performClick()

        compose.waitForText(string(R.string.search_label))
        compose.onNodeWithText(string(R.string.disclaimer_title)).assertDoesNotExist()
    }
}
