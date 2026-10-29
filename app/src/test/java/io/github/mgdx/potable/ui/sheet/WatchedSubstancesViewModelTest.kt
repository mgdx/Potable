package io.github.mgdx.potable.ui.sheet

import io.github.mgdx.potable.core.PesticideCount
import io.github.mgdx.potable.core.WatchedSubstance
import io.github.mgdx.potable.core.WatchedSubstances
import io.github.mgdx.potable.core.model.AnalysisResult
import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.core.model.SeriesPoint
import io.github.mgdx.potable.data.CachedSample
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.data.HistoryRepository
import io.github.mgdx.potable.data.SampleRepository
import io.github.mgdx.potable.ui.FakeAppPreferences
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.FakeHistoryRepository
import io.github.mgdx.potable.ui.FakeWaterRepository
import io.github.mgdx.potable.ui.MainDispatcherRule
import io.github.mgdx.potable.ui.common.LoadError
import io.github.mgdx.potable.ui.sample
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private class CountingSampleRepository : SampleRepository {
    var answer: Result<Sample?> = Result.success(null)
    val calls = mutableListOf<String>()

    override suspend fun sample(communeCode: String, udiCode: String, sampleCode: String): Sample? {
        calls += sampleCode
        return answer.getOrThrow()
    }
}

/** The watched substances card of the sheet (SPEC §5 F3). */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchedSubstancesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val water = FakeWaterRepository()
    private val follows = FakeFollowRepository()
    private val history = FakeHistoryRepository()
    private val preferences = FakeAppPreferences()
    private val clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC)
    private val fetchedAt = Instant.parse("2026-10-25T09:59:00Z")

    private val samples = CountingSampleRepository()

    /** The substances read for Orléans: chlordecone is read overseas only. */
    private val mainland = WatchedSubstances.readFor("45234")

    private fun viewModel(communeCode: String = "45234") = SheetViewModel(
        water,
        follows,
        history,
        communeCode,
        "045000123",
        "Orléans",
        "ORLEANS",
        clock,
        recents = preferences,
        samples = samples,
    )

    private fun pesticide(code: String, label: String, text: String, number: Double) =
        AnalysisResult(code, label, "N", text, number, "µg/L", "<=0,1 µg/L", null, "L")

    private fun series(code: String, vararg points: Pair<String, String>) = ParameterSeries(
        parameterCode = code,
        label = "Label $code",
        unit = "µg/L",
        limitText = "<=0,1 µg/L",
        referenceText = null,
        limit = null,
        reference = null,
        points = points.map { (date, text) ->
            SeriesPoint(Instant.parse(date), "S$date", null, text, censored = false)
        },
    )

    @Test
    fun `the latest measure of each substance is read after the sample, over the window`() =
        runTest(main.dispatcher) {
            history.series[WatchedSubstance.PFAS.codes] = Result.success(
                series(
                    "8847",
                    "2024-03-01T10:00:00Z" to "0,02",
                    "2026-03-26T10:00:00Z" to "0,012",
                ),
            )
            history.series[WatchedSubstance.LEAD.codes] =
                Result.success(series("1382", "2024-01-10T10:00:00Z" to "<1"))
            water.refreshAnswers += Result.failure(DataError.Network(IOException()))
            val viewModel = viewModel()
            advanceUntilIdle()
            // No sample shown: nothing asked.
            assertNull(viewModel.watched.value)
            assertTrue(history.seriesCalls.isEmpty())

            water.refreshedCache = CachedSample(sample(), fetchedAt)
            viewModel.retry()
            advanceUntilIdle()
            val loaded = viewModel.watched.value as WatchedUiState.Loaded
            assertEquals(
                listOf(WatchedSubstance.PFAS, WatchedSubstance.LEAD),
                loaded.measures.map { it.substance },
            )
            assertEquals(listOf("0,012", "<1"), loaded.measures.map { it.point.text })
            assertEquals(LocalDate.parse("2023-10-25"), loaded.since)
            assertNull(loaded.partialError)
            // Every substance, three years back from today in France.
            assertEquals(
                mainland.map { it.codes to LocalDate.parse("2023-10-25") },
                history.seriesCalls,
            )
            // Once per sample shown.
            viewModel.pullToRefresh()
            advanceUntilIdle()
            assertEquals(mainland.size, history.seriesCalls.size)
        }

    @Test
    fun `a substance held by Hub'Eau does not hold up the others`() = runTest(main.dispatcher) {
        val held = CompletableDeferred<Unit>()
        val holding = object : HistoryRepository by history {
            override suspend fun parameterSeries(
                communeCode: String,
                udiCode: String,
                parameterCodes: Set<String>,
                since: LocalDate,
                force: Boolean,
            ): ParameterSeries? {
                if (parameterCodes == WatchedSubstance.PFAS.codes) held.await()
                return history.parameterSeries(communeCode, udiCode, parameterCodes, since, force)
            }
        }
        water.refreshedCache = CachedSample(sample(), fetchedAt)
        val viewModel = SheetViewModel(
            water,
            follows,
            holding,
            "45234",
            "045000123",
            "Orléans",
            "ORLEANS",
            clock,
            recents = preferences,
        )
        advanceUntilIdle()

        // A4: every other substance was asked while the first one waits.
        assertEquals(mainland.size, history.seriesCalls.size + 1)
        assertEquals(WatchedUiState.Loading, viewModel.watched.value)

        held.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.watched.value is WatchedUiState.Loaded)
    }

    @Test
    fun `no substance measured gives an empty list, the none line`() = runTest(main.dispatcher) {
        water.refreshedCache = CachedSample(sample(), fetchedAt)
        val viewModel = viewModel()
        advanceUntilIdle()
        val loaded = viewModel.watched.value as WatchedUiState.Loaded
        assertTrue(loaded.measures.isEmpty())
        assertEquals(3, loaded.years)
        assertEquals(LocalDate.parse("2023-10-25"), loaded.since)
    }

    @Test
    fun `a failure only touches the card, and retry reads again`() = runTest(main.dispatcher) {
        water.refreshedCache = CachedSample(sample(), fetchedAt)
        for (substance in mainland) {
            history.series[substance.codes] = Result.failure(DataError.Network(IOException()))
        }
        val viewModel = viewModel()
        advanceUntilIdle()
        assertTrue(viewModel.state.value is SheetUiState.Loaded)
        assertEquals(WatchedUiState.Error(LoadError.NETWORK), viewModel.watched.value)

        history.series.clear()
        history.series[WatchedSubstance.ARSENIC.codes] =
            Result.success(series("1369", "2026-06-10T10:00:00Z" to "2"))
        viewModel.retryWatched()
        advanceUntilIdle()
        val loaded = viewModel.watched.value as WatchedUiState.Loaded
        assertEquals(listOf(WatchedSubstance.ARSENIC), loaded.measures.map { it.substance })
    }

    @Test
    fun `some substances failing still shows the others, with the error`() =
        runTest(main.dispatcher) {
            water.refreshedCache = CachedSample(sample(), fetchedAt)
            history.series[WatchedSubstance.PFAS.codes] = Result.failure(DataError.Http(503))
            history.series[WatchedSubstance.LEAD.codes] =
                Result.success(series("1382", "2026-01-10T10:00:00Z" to "2"))
            val viewModel = viewModel()
            advanceUntilIdle()
            val loaded = viewModel.watched.value as WatchedUiState.Loaded
            assertEquals(listOf(WatchedSubstance.LEAD), loaded.measures.map { it.substance })
            assertEquals(LoadError.UNAVAILABLE, loaded.partialError)
        }

    @Test
    fun `a failure with nothing measured elsewhere is an error, not the none line`() =
        runTest(main.dispatcher) {
            water.refreshedCache = CachedSample(sample(), fetchedAt)
            history.series[WatchedSubstance.TFA.codes] =
                Result.failure(DataError.Timeout(IOException()))
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(WatchedUiState.Error(LoadError.TOO_SLOW), viewModel.watched.value)
        }

    @Test
    fun `a new history window reads the card again`() = runTest(main.dispatcher) {
        water.refreshedCache = CachedSample(sample(), fetchedAt)
        val viewModel = viewModel()
        advanceUntilIdle()
        preferences.setHistoryYears(10)
        advanceUntilIdle()
        val loaded = viewModel.watched.value as WatchedUiState.Loaded
        assertEquals(10, loaded.years)
        assertEquals(LocalDate.parse("2016-10-25"), loaded.since)
        assertEquals(2 * mainland.size, history.seriesCalls.size)
    }

    @Test
    fun `substances read without any measure are said not analysed, failed ones are not`() =
        runTest(main.dispatcher) {
            water.refreshedCache = CachedSample(sample(), fetchedAt)
            history.series[WatchedSubstance.LEAD.codes] =
                Result.success(series("1382", "2024-01-10T10:00:00Z" to "<1"))
            history.series[WatchedSubstance.PFAS.codes] = Result.failure(DataError.Http(503))
            val viewModel = viewModel()
            advanceUntilIdle()
            val loaded = viewModel.watched.value as WatchedUiState.Loaded
            assertEquals(
                mainland - WatchedSubstance.LEAD - WatchedSubstance.PFAS,
                loaded.notAnalysed,
            )
        }

    @Test
    fun `chlordecone is read and said not analysed in the Antilles`() = runTest(main.dispatcher) {
        water.refreshedCache = CachedSample(sample(), fetchedAt)
        history.series[WatchedSubstance.LEAD.codes] =
            Result.success(series("1382", "2024-01-10T10:00:00Z" to "<1"))
        val viewModel = viewModel(communeCode = "97209")
        advanceUntilIdle()
        assertTrue(history.seriesCalls.any { it.first == WatchedSubstance.CHLORDECONE.codes })
        val loaded = viewModel.watched.value as WatchedUiState.Loaded
        assertTrue(WatchedSubstance.CHLORDECONE in loaded.notAnalysed)
    }

    @Test
    fun `the pesticides of the sample of the latest total are counted`() =
        runTest(main.dispatcher) {
            water.refreshedCache = CachedSample(sample(), fetchedAt)
            history.series[WatchedSubstance.TOTAL_PESTICIDES.codes] = Result.success(
                series(
                    "6276",
                    "2025-03-01T10:00:00Z" to "0,1",
                    "2026-03-26T10:00:00Z" to "0,54",
                ),
            )
            samples.answer = Result.success(
                sample().copy(
                    code = "S2026-03-26T10:00:00Z",
                    results = listOf(
                        pesticide("6276", "Total des pesticides analysés", "0,54", 0.54),
                        pesticide("6378", "Chloridazone desphényl", "0,397", 0.397),
                        pesticide("1107", "Atrazine", "<0,005", 0.0),
                    ),
                ),
            )
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(listOf("S2026-03-26T10:00:00Z"), samples.calls)
            val loaded = viewModel.watched.value as WatchedUiState.Loaded
            assertEquals(PesticideCount(analysed = 2, quantified = 1), loaded.pesticides)
        }

    @Test
    fun `a sample that cannot be read leaves the card without a count`() =
        runTest(main.dispatcher) {
            water.refreshedCache = CachedSample(sample(), fetchedAt)
            history.series[WatchedSubstance.TOTAL_PESTICIDES.codes] =
                Result.success(series("6276", "2026-03-26T10:00:00Z" to "0,54"))
            samples.answer = Result.failure(DataError.Network(IOException()))
            val viewModel = viewModel()
            advanceUntilIdle()
            val loaded = viewModel.watched.value as WatchedUiState.Loaded
            assertNull(loaded.pesticides)
            assertEquals(1, loaded.measures.size)
        }

    @Test
    fun `no total of pesticides, no sample read`() = runTest(main.dispatcher) {
        water.refreshedCache = CachedSample(sample(), fetchedAt)
        viewModel()
        advanceUntilIdle()
        assertTrue(samples.calls.isEmpty())
    }
}
