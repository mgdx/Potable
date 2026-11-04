package io.github.mgdx.potable.ui.sheet

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.model.AnalysisResult
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.ui.ComposeHostRegistration
import io.github.mgdx.potable.ui.sample
import io.github.mgdx.potable.ui.string
import io.github.mgdx.potable.ui.theme.PotableTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalTime
import java.util.Locale

/** The conclusions of several samples of the same day on the sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SameDayConclusionsCardTest {
    @get:Rule(order = 0)
    val host = ComposeHostRegistration()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val compliant = "Eau d'alimentation conforme aux exigences de qualité en vigueur " +
        "pour l'ensemble des paramètres mesurés."

    private val nitrates =
        AnalysisResult("1340", "Nitrates (en NO3)", "N", "12", 12.0, "mg/L", "<=50 mg/L", null, "L")

    private val opened = mutableListOf<String>()

    /** Paris (75056), 2026-06-30: four samples, the same conclusion. */
    private val paris = listOf("16:14", "13:40", "13:29", "12:02").mapIndexed { index, time ->
        sample(Instant.parse("2026-06-30T$time:00Z")).copy(
            code = "0750021700$index",
            conclusion = compliant,
            results = listOf(nitrates),
        )
    }

    private fun show(samples: List<Sample>) {
        compose.setContent {
            PotableTheme {
                SameDayConclusionsCard(samples, RelativeAge.Months(4)) { opened += it.code }
            }
        }
    }

    private fun count(text: String) =
        compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().size

    private fun takenAt(time: String) = string(
        R.string.sheet_sample_taken_at,
        formatSampleTime(LocalTime.parse(time), Locale.getDefault()),
    )

    private val opensResults = SemanticsMatcher("opens the results") {
        it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label ==
            string(R.string.sheet_show_all_results)
    }

    @Test
    fun `the same conclusion is shown once, with a line per sample that opens it`() {
        show(paris)
        assertEquals(1, count(compliant))
        assertEquals(1, count("Same conclusion for the 4 samples of that day"))
        assertEquals(0, count(string(R.string.sheet_show_all_results)))
        val lines = compose.onAllNodes(hasClickAction().and(opensResults))
        assertEquals(4, lines.fetchSemanticsNodes().size)

        compose.onNodeWithText(takenAt("13:29")).performClick()
        assertEquals(listOf(paris[2].code), opened)
    }

    @Test
    fun `different conclusions keep a block each`() {
        val other = compliant.replace("en vigueur ", "")
        show(listOf(paris[0], paris[1].copy(conclusion = other)))
        assertEquals(1, count(compliant))
        assertEquals(1, count(other))
        assertEquals(0, count("Same conclusion"))
        assertEquals(2, count(string(R.string.sheet_show_all_results)))
        assertEquals(1, count(takenAt("16:14")))
        assertEquals(1, count(takenAt("13:40")))
    }

    @Test
    fun `a group and a sample alone keep the order of the samples`() {
        val other = "Eau non conforme aux limites de qualité."
        val samples = listOf(paris[0].copy(conclusion = other)) + paris.drop(1)
        show(samples)
        assertEquals(1, count("Same conclusion for the 3 samples of that day"))
        val texts = compose
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        assertTrue(texts.indexOf(other) in 0 until texts.indexOf(compliant))
        // The sample alone keeps its button.
        assertEquals(1, count(string(R.string.sheet_show_all_results)))
    }
}
