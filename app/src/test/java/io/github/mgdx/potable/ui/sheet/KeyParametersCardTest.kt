package io.github.mgdx.potable.ui.sheet

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import io.github.mgdx.potable.core.model.AnalysisResult
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.ui.ComposeHostRegistration
import io.github.mgdx.potable.ui.sample
import io.github.mgdx.potable.ui.theme.PotableTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** The featured parameters on the sheet (SPEC §4.3). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyParametersCardTest {
    @get:Rule(order = 0)
    val host = ComposeHostRegistration()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private fun hardness(text: String, number: Double) =
        AnalysisResult("1345", "Titre hydrotimétrique", "N", text, number, "°f", null, null, "L")

    private val nitrates =
        AnalysisResult("1340", "Nitrates (en NO3)", "N", "12", 12.0, "mg/L", "<=50 mg/L", null, "L")

    private fun show(vararg samples: Sample) {
        compose.setContent {
            PotableTheme { KeyParametersCard(samples.toList()) { _, _ -> } }
        }
    }

    private fun count(text: String) =
        compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().size

    @Test
    fun `hardness in french degrees is also given in german degrees and mmol per litre`() {
        show(sample().copy(results = listOf(hardness("36,4", 36.4))))
        assertEquals(1, count("36,4 °f"))
        assertEquals(1, count("i.e. 20.4 °dH · 3.6 mmol/L"))
    }

    @Test
    fun `a censored hardness is not converted`() {
        show(sample().copy(results = listOf(hardness("<1", 1.0))))
        assertEquals(0, count("°dH"))
    }

    /** Hardness taken from an earlier sample of the same day (FeaturedParameters.presentIn). */
    @Test
    fun `hardness of an earlier sample of the day is converted too`() {
        val latest = sample(Instant.parse("2026-10-01T09:00:00Z")).copy(results = listOf(nitrates))
        val earlier = sample().copy(results = listOf(hardness("12,10", 12.1)))
        show(latest, earlier)
        assertEquals(1, count("i.e. 6.8 °dH · 1.2 mmol/L"))
    }
}
