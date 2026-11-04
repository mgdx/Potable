package io.github.mgdx.potable.ui.search

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.model.Commune
import io.github.mgdx.potable.data.preferences.RecentUnit
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

    @Test
    fun `homonyms are told apart by their department`() {
        val tarn = Commune("81202", "Parisot", listOf("81310"), 43.8, 1.8, population = 900)
        val tarnEtGaronne = Commune("82137", "Parisot", listOf("82160"), 44.3, 1.8)
        // A former commune of Corsica: no postal code, the department alone.
        val corsica = Commune("2A999", "Parisotta", emptyList(), 41.9, 8.7, hubEauCode = "2A004")
        val preferences = FakeAppPreferences()
        preferences.recent.value =
            listOf(RecentUnit("97411", "Saint-Denis", "974000123", "SAINT-DENIS"))
        val viewModel = SearchViewModel(
            FakeCommuneRepository(listOf(tarn, tarnEtGaronne, corsica)),
            Locator { LocationOutcome.Unavailable },
            FakeFollowRepository(),
            preferences,
        )
        setContent(viewModel)

        // While nothing is typed, the recent units say their department too.
        compose.waitForText("La Réunion (974)")

        compose.onNode(hasSetTextAction()).performTextInput("parisot")
        compose.waitForText("81310 · Tarn (81)")
        compose.onNodeWithText("82160 · Tarn-et-Garonne (82)").assertExists()
        compose.onNodeWithText("Corse-du-Sud (2A)").assertExists()
    }

    private fun setContent(viewModel: SearchViewModel) {
        compose.setContent {
            PotableTheme {
                SearchScreen(
                    viewModel = viewModel,
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
}
