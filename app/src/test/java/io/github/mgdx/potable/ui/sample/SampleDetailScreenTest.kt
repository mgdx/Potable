package io.github.mgdx.potable.ui.sample

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.FeaturedParameter
import io.github.mgdx.potable.core.WatchedSubstance
import io.github.mgdx.potable.core.model.AnalysisResult
import io.github.mgdx.potable.ui.ComposeHostRegistration
import io.github.mgdx.potable.ui.common.explanationRes
import io.github.mgdx.potable.ui.sample
import io.github.mgdx.potable.ui.sheet.RelativeAge
import io.github.mgdx.potable.ui.sheet.SheetUiState
import io.github.mgdx.potable.ui.string
import io.github.mgdx.potable.ui.theme.PotableTheme
import io.github.mgdx.potable.ui.waitForText
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Every parameter of a sample (SPEC §5 F4), from the sheet's loaded state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SampleDetailScreenTest {
    @get:Rule(order = 0)
    val host = ComposeHostRegistration()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val nitrates =
        AnalysisResult("1340", "Nitrates (en NO3)", "N", "12", 12.0, "mg/L", "<=50 mg/L", null, "L")

    /** A parameter without any editorial text. */
    private val conductivity =
        AnalysisResult("1303", "Conductivité à 25°C", "N", "450", 450.0, "µS/cm", null, null, "L")

    private val demo = sample().copy(results = listOf(nitrates, conductivity))

    /** Every editorial text the list could show under a parameter. */
    private val editorialTexts: List<String>
        get() = (
            FeaturedParameter.entries.map { it.explanationRes() } +
                WatchedSubstance.entries.map { it.explanationRes() }
            ).map { string(it) }

    /** The row of [result], scrolled to: one clickable element merging its texts. */
    private fun row(result: AnalysisResult): SemanticsNodeInteraction {
        val row = hasText(result.parameterLabel) and hasClickAction()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(row)
        return compose.onNode(row)
    }

    @Before
    fun show() {
        val age = RelativeAge.of(
            demo.date,
            Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC),
        )
        val viewModel = SampleDetailViewModel(
            demo.code,
            MutableStateFlow(SheetUiState.Loaded(demo, age)),
        )
        compose.setContent {
            PotableTheme {
                SampleDetailScreen(
                    viewModel = viewModel,
                    onRetry = {},
                    onBack = {},
                    onOpenHistory = {},
                )
            }
        }
        compose.waitForText(nitrates.parameterLabel)
    }

    @Test
    fun `the nitrates carry their legend, a parameter without editorial text none`() {
        row(nitrates).assert(hasText(string(R.string.featured_nitrates_explanation)))
        editorialTexts.forEach { text ->
            row(conductivity).assert(!hasText(text))
        }
    }

    @Test
    fun `the text search filters the list`() {
        row(conductivity).assertExists()

        compose.onNode(hasSetTextAction()).performTextInput("nitr")

        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodes(hasText(conductivity.parameterLabel)).fetchSemanticsNodes().isEmpty()
        }
        row(nitrates).assertExists()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
