package io.github.mgdx.potable.ui.udi

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.RegionalHealthAgencies
import io.github.mgdx.potable.core.model.UdiChoice
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.ui.ComposeHostRegistration
import io.github.mgdx.potable.ui.FakeAppPreferences
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.FakeWaterRepository
import io.github.mgdx.potable.ui.common.LoadError
import io.github.mgdx.potable.ui.common.messageRes
import io.github.mgdx.potable.ui.string
import io.github.mgdx.potable.ui.theme.PotableTheme
import io.github.mgdx.potable.ui.waitForText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** A commune that Hub'Eau lists no unit for, and a network error, kept apart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UdiScreenTest {
    @get:Rule(order = 0)
    val host = ComposeHostRegistration()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val water = FakeWaterRepository()

    // Rochefourchat (Drôme): ARS Auvergne-Rhône-Alpes.
    private val communeCode = "26271"
    private val agency = RegionalHealthAgencies.forCommune(communeCode)!!.name

    private var backs = 0

    private fun show() {
        val viewModel =
            UdiViewModel(water, FakeAppPreferences(), FakeFollowRepository(), communeCode)
        compose.setContent {
            PotableTheme {
                UdiScreen(
                    viewModel = viewModel,
                    communeName = "Rochefourchat",
                    onBack = { backs++ },
                    onUdiResolved = {},
                    onUdiRemembered = {},
                )
            }
        }
    }

    @Test
    fun `no unit explains the possible causes and names the health agency only`() {
        water.udiAnswers += Result.success(UdiChoice.None)
        show()

        compose.waitForText(string(R.string.udi_none))
        compose.onNodeWithText(string(R.string.udi_none_causes_intro)).assertExists()
        compose.onNodeWithText(string(R.string.udi_none_cause_neighbour)).assertExists()
        compose.onNodeWithText(string(R.string.udi_none_cause_private)).assertExists()
        compose.onNodeWithText(string(R.string.udi_none_cause_unpublished)).assertExists()
        compose.onNodeWithText(string(R.string.sheet_contacts_title)).assertExists()
        compose.onNodeWithText(agency).assertExists()
        compose.onNodeWithText(string(R.string.sheet_contacts_service)).assertDoesNotExist()

        compose.onNodeWithText(string(R.string.udi_none_search_other))
            .performScrollTo()
            .performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `a network error is not shown as a commune without unit`() {
        water.udiAnswers += Result.failure(DataError.Network(IOException()))
        show()

        compose.waitForText(string(LoadError.NETWORK.messageRes()))
        compose.onNodeWithText(string(R.string.action_retry)).assertExists()
        compose.onNodeWithText(string(R.string.udi_none)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.udi_none_causes_intro)).assertDoesNotExist()
        compose.onNodeWithText(agency).assertDoesNotExist()
    }
}
