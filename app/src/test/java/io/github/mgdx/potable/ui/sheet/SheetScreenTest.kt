package io.github.mgdx.potable.ui.sheet

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.WatchedSubstance
import io.github.mgdx.potable.core.model.AnalysisResult
import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.core.model.SeriesPoint
import io.github.mgdx.potable.data.CachedSample
import io.github.mgdx.potable.data.HistoryRepository
import io.github.mgdx.potable.ui.ComposeHostRegistration
import io.github.mgdx.potable.ui.FakeAppPreferences
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.FakeHistoryRepository
import io.github.mgdx.potable.ui.FakeWaterRepository
import io.github.mgdx.potable.ui.common.nameRes
import io.github.mgdx.potable.ui.sample
import io.github.mgdx.potable.ui.string
import io.github.mgdx.potable.ui.theme.PotableTheme
import io.github.mgdx.potable.ui.waitForText
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The sheet of a unit (SPEC §5 F3), fed by in-memory repositories. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SheetScreenTest {
    @get:Rule(order = 0)
    val host = ComposeHostRegistration()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC)

    private val nitrates =
        AnalysisResult("1340", "Nitrates (en NO3)", "N", "12", 12.0, "mg/L", "<=50 mg/L", null, "L")

    private val demo = sample().copy(results = listOf(nitrates))

    /** The watched substances wait for [gate]; PFAS have a measure, the others none. */
    private val gate = CompletableDeferred<Unit>()
    private val history = FakeHistoryRepository().apply {
        series[WatchedSubstance.PFAS.codes] = Result.success(
            ParameterSeries(
                parameterCode = "8847",
                label = "Somme des PFAS",
                unit = "µg/L",
                limitText = "<=0,1 µg/L",
                referenceText = null,
                limit = null,
                reference = null,
                points = listOf(
                    SeriesPoint(
                        Instant.parse("2026-03-26T10:00:00Z"),
                        "S1",
                        null,
                        "0,012",
                        censored = false,
                    ),
                ),
            ),
        )
    }
    private val gatedHistory = object : HistoryRepository by history {
        override suspend fun parameterSeries(
            communeCode: String,
            udiCode: String,
            parameterCodes: Set<String>,
            since: LocalDate,
            force: Boolean,
        ): ParameterSeries? {
            gate.await()
            return history.parameterSeries(communeCode, udiCode, parameterCodes, since, force)
        }
    }

    private val shownResults = mutableListOf<Sample>()

    private fun show(sample: Sample) {
        val water = FakeWaterRepository().apply {
            cache.value = CachedSample(sample, Instant.parse("2026-10-25T09:59:00Z"))
        }
        val viewModel = SheetViewModel(
            water,
            FakeFollowRepository(),
            gatedHistory,
            "45234",
            "045000123",
            "Orléans",
            "ORLEANS",
            clock,
            recents = FakeAppPreferences(),
        )
        compose.setContent {
            PotableTheme {
                SheetScreen(
                    viewModel = viewModel,
                    communeName = "Orléans",
                    udiName = "ORLEANS",
                    onBack = {},
                    onShowAllResults = { shownResults += it },
                    onShowHistory = {},
                    onParameterHistory = { _, _ -> },
                    onMyCommunes = null,
                    onSettings = {},
                    onAbout = {},
                    onSearch = null,
                    onChangeUdi = null,
                )
            }
        }
        compose.waitForText(sample.conclusion!!)
    }

    @Test
    fun `the conclusion is shown as published, the full list opens from a button`() {
        show(demo)
        gate.complete(Unit)

        compose.onNodeWithText(demo.conclusion!!).assertExists()
        compose.onNodeWithText(string(R.string.sheet_show_all_results))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHasClickAction()
            .performScrollTo()
            .performClick()
        assertEquals(listOf(demo), shownResults)
    }

    @Test
    fun `the watched substances show their loading state, then their measures`() {
        show(demo)
        compose.onNodeWithText(string(R.string.sheet_watched_loading)).assertExists()
        compose.onNodeWithText(string(R.string.sheet_watched_intro)).assertDoesNotExist()

        gate.complete(Unit)
        compose.waitForText(string(R.string.sheet_watched_intro))
        compose.onNodeWithText(string(R.string.sheet_watched_loading)).assertDoesNotExist()
        compose.onNodeWithText(string(WatchedSubstance.PFAS.nameRes())).assertExists()
    }

    @Test
    fun `a compliance code S brings the not-assessed note`() {
        show(demo.copy(bacteriologicalReferences = Compliance.NOT_ASSESSED))
        gate.complete(Unit)

        compose.onNodeWithText(string(R.string.sheet_not_assessed_explanation)).assertExists()
    }

    @Test
    fun `without a code S there is no not-assessed note`() {
        // The demonstration sample: compliant, non-compliant and unknown codes.
        show(demo)
        gate.complete(Unit)

        compose.onNodeWithText(string(R.string.sheet_compliance_title)).assertExists()
        compose.onNodeWithText(string(R.string.sheet_not_assessed_explanation))
            .assertDoesNotExist()
    }
}
