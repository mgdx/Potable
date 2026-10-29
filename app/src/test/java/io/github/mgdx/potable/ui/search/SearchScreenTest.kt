package io.github.mgdx.potable.ui.search

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.model.Commune
import io.github.mgdx.potable.location.LocationOutcome
import io.github.mgdx.potable.location.Locator
import io.github.mgdx.potable.ui.ComposeHostRegistration
import io.github.mgdx.potable.ui.FakeAppPreferences
import io.github.mgdx.potable.ui.FakeCommuneRepository
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.commune
import io.github.mgdx.potable.ui.string
import io.github.mgdx.potable.ui.theme.PotableTheme
import io.github.mgdx.potable.ui.waitForText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The commune search (SPEC §5 F1), on an in-memory referential. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchScreenTest {
    @get:Rule(order = 0)
    val host = ComposeHostRegistration()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val orleans = commune("45234", "Orléans")
    private val paris = commune("75056", "Paris")

    @Test
    fun `a typed name shows the commune, which opens the choice of its unit`() {
        val viewModel = SearchViewModel(
            FakeCommuneRepository(listOf(orleans, paris)),
            Locator { LocationOutcome.Unavailable },
            FakeFollowRepository(),
            FakeAppPreferences(),
        )
        val selected = mutableListOf<Pair<Commune, Boolean>>()
        compose.setContent {
            PotableTheme {
                SearchScreen(
                    viewModel = viewModel,
                    onCommuneSelected = { commune, choose -> selected += commune to choose },
                    onKnownUdiSelected = { _, _, _ -> },
                    onRecentSelected = {},
                    onMyCommunes = {},
                    onSettings = {},
                    onAbout = {},
                )
            }
        }

        compose.onNodeWithText(string(R.string.search_label)).assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("orl")
        compose.waitForText(orleans.name)
        compose.onNodeWithText(paris.name).assertDoesNotExist()

        compose.onNodeWithText(orleans.name).performClick()
        // No unit known for the commune: the unit is to be chosen (UdiScreen).
        assertEquals(listOf(orleans to false), selected)
    }
}
