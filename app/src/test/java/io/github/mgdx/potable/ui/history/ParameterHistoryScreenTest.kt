package io.github.mgdx.potable.ui.history

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.core.model.SeriesPoint
import io.github.mgdx.potable.ui.ComposeHostRegistration
import io.github.mgdx.potable.ui.string
import io.github.mgdx.potable.ui.theme.PotableTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.temporal.ChronoUnit

/** The history screen: a long list of measures, and the selected one of the chart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParameterHistoryScreenTest {
    @get:Rule(order = 0)
    val host = ComposeHostRegistration()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val start = Instant.parse("2015-01-01T08:00:00Z")

    private fun series(unit: String, points: List<SeriesPoint>) = ParameterSeries(
        parameterCode = "1340",
        label = "Nitrates (en NO3)",
        unit = unit,
        points = points,
        limitText = null,
        referenceText = null,
        limit = null,
        reference = null,
    )

    /** One measure a day, its value (1000 + i) unique and found by its text. */
    private val long = series(
        "mg/L",
        (0 until 5_000).map { i ->
            val value = 1_000 + i
            SeriesPoint(
                start.plus(i.toLong(), ChronoUnit.DAYS),
                "s-$i",
                value.toDouble(),
                value.toString(),
                false,
            )
        },
    )

    @Test
    fun `a long list composes only the measures in view`() {
        compose.setContent {
            PotableTheme {
                LoadedHistory(long, years = 10, onSelectYears = {}, refreshFailed = false) {}
            }
        }
        val show = hasText(string(R.string.history_show_list))
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(show)
        compose.onNode(show).performClick()
        // Newest first: the newest measure is at the top of the list...
        compose.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("5999 mg/L", substring = true))
        // ...and a measure of ten years ago is not composed until scrolled to.
        val old = hasText("1010 mg/L", substring = true)
        compose.onAllNodes(old).assertCountEquals(0)
        val rows = compose.onAllNodesWithText(" mg/L", substring = true)
            .fetchSemanticsNodes().size
        assertTrue("$rows rows composed", rows < 100)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(old)
        compose.onNode(old).assertExists()
    }

    private fun showSelected(point: SeriesPoint) {
        val series = series(
            "µg/L",
            listOf(
                SeriesPoint(start, "a", 0.02, "0,020", false),
                point,
                SeriesPoint(start.plus(20, ChronoUnit.DAYS), "c", 0.03, "0,030", false),
            ),
        )
        val model = ChartModel.of(series)!!
        val selected = model.points.indexOfFirst { series.points[it.index] == point }
        compose.setContent {
            PotableTheme {
                HistoryChart(series, model, selected, onSelect = {}, onOpenSample = {})
            }
        }
    }

    private fun stateContains(text: String) = SemanticsMatcher("state contains $text") {
        it.config.getOrNull(SemanticsProperties.StateDescription)?.contains(text) == true
    }

    @Test
    fun `the tooltip shows a measured value as published, and says it in words`() {
        showSelected(SeriesPoint(start.plus(10, ChronoUnit.DAYS), "b", 0.048, "0,048", false))
        val spoken = "0.048 micrograms per litre"
        compose.onNode(hasText("0,048 µg/L", substring = true) and hasContentDescription(spoken))
            .assertExists()
        compose.onAllNodesWithText("microgram", substring = true).assertCountEquals(0)
        compose.onNode(stateContains(spoken)).assertExists()
    }

    @Test
    fun `the tooltip shows a bound as published, and says it in words`() {
        showSelected(SeriesPoint(start.plus(10, ChronoUnit.DAYS), "b", 0.5, "<0,500", true))
        val spoken = "below the threshold of"
        compose.onNode(
            hasText("<0,500 µg/L", substring = true) and
                hasContentDescription(spoken, substring = true),
        ).assertExists()
        compose.onAllNodesWithText("threshold", substring = true).assertCountEquals(0)
        compose.onNode(stateContains(spoken)).assertExists()
    }
}
