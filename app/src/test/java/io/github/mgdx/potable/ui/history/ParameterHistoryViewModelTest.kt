package io.github.mgdx.potable.ui.history

import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.core.model.SampleSummary
import io.github.mgdx.potable.core.model.SeriesPoint
import io.github.mgdx.potable.core.model.Threshold
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.data.HistoryRepository
import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.ui.MainDispatcherRule
import io.github.mgdx.potable.ui.common.LoadError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds

/** One parameter call per queued answer; with nothing queued, a call waits for [gate]. */
private class SeriesRepository : HistoryRepository {
    val answers = ArrayDeque<Result<ParameterSeries?>>()

    /** `since` and `force` of each call. */
    val calls = mutableListOf<Pair<LocalDate, Boolean>>()
    val codes = mutableListOf<Set<String>>()
    var gate: CompletableDeferred<ParameterSeries?>? = null

    override suspend fun parameterSeries(
        communeCode: String,
        udiCode: String,
        parameterCodes: Set<String>,
        since: LocalDate,
        force: Boolean,
    ): ParameterSeries? {
        calls += since to force
        codes += parameterCodes
        val answer = answers.removeFirstOrNull() ?: return gate!!.await()
        return answer.getOrThrow()
    }

    override suspend fun sampleTimeline(
        communeCode: String,
        udiCode: String,
        since: LocalDate,
        force: Boolean,
    ): List<SampleSummary> = emptyList()
}

private class WindowPreferences(years: Int) : AppPreferences {
    override val disclaimerAccepted: Flow<Boolean> = flowOf(true)

    override suspend fun acceptDisclaimer() = Unit

    override val historyYears: Flow<Int> = flowOf(years)
}

private fun measure(date: String, value: Double) =
    SeriesPoint(Instant.parse(date), "s-$date", value, value.toString(), censored = false)

private fun series(vararg points: SeriesPoint) = ParameterSeries(
    parameterCode = "1340",
    label = "Nitrates (en NO3)",
    unit = "mg/L",
    limitText = "<=50 mg/L",
    referenceText = null,
    limit = Threshold(null, 50.0, "mg/L"),
    reference = null,
    points = points.toList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ParameterHistoryViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val history = SeriesRepository()

    // 2026-10-25 in Paris even though it is still the 24th in UTC.
    private val clock = Clock.fixed(Instant.parse("2026-10-24T23:30:00Z"), ZoneOffset.UTC)

    private val two = series(
        measure("2025-03-01T08:00:00Z", 21.0),
        measure("2026-09-01T08:00:00Z", 23.0),
    )

    private fun viewModel(years: Int = 3) = ParameterHistoryViewModel(
        history,
        WindowPreferences(years),
        "45234",
        "045000123",
        setOf("1340"),
        clock,
        slowThreshold = 10.seconds,
    )

    @Test
    fun `the window starts at the setting, ending today in France`() = runTest(main.dispatcher) {
        history.answers += Result.success(two)
        val viewModel = viewModel(years = 5)
        advanceUntilIdle()
        assertEquals(5, viewModel.years.value)
        assertEquals(listOf(LocalDate.of(2021, 10, 25) to false), history.calls)
        assertEquals(listOf(setOf("1340")), history.codes)
        assertEquals(HistoryUiState.Loaded(two), viewModel.state.value)
        assertFalse((viewModel.state.value as HistoryUiState.Loaded).singleMeasure)
    }

    @Test
    fun `a setting outside the choices reads as three years`() = runTest(main.dispatcher) {
        history.answers += Result.success(two)
        val viewModel = viewModel(years = 7)
        advanceUntilIdle()
        assertEquals(AppPreferences.DEFAULT_YEARS, viewModel.years.value)
        assertEquals(LocalDate.of(2023, 10, 25), history.calls.single().first)
    }

    @Test
    fun `changing the window loads it again, without force`() = runTest(main.dispatcher) {
        history.answers += Result.success(two)
        history.answers += Result.success(null)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.selectYears(1)
        assertEquals(HistoryUiState.Loading, viewModel.state.value)
        advanceUntilIdle()
        assertEquals(1, viewModel.years.value)
        assertEquals(LocalDate.of(2025, 10, 25) to false, history.calls.last())
        assertEquals(HistoryUiState.Empty, viewModel.state.value)

        // The same window, or one not offered, asks nothing.
        viewModel.selectYears(1)
        viewModel.selectYears(4)
        advanceUntilIdle()
        assertEquals(2, history.calls.size)
    }

    @Test
    fun `no measure is the empty state`() = runTest(main.dispatcher) {
        history.answers += Result.success(series())
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(HistoryUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `a single measure is said so`() = runTest(main.dispatcher) {
        val one = series(measure("2026-09-01T08:00:00Z", 23.0))
        history.answers += Result.success(one)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertTrue((viewModel.state.value as HistoryUiState.Loaded).singleMeasure)
    }

    @Test
    fun `an error is shown with retry, which asks again`() = runTest(main.dispatcher) {
        history.answers += Result.failure(DataError.Network(IOException("offline")))
        history.answers += Result.success(two)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(HistoryUiState.Error(LoadError.NETWORK), viewModel.state.value)
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(HistoryUiState.Loaded(two), viewModel.state.value)
        assertEquals(listOf(false, false), history.calls.map { it.second })
    }

    @Test
    fun `an unexpected exception does not crash the screen`() = runTest(main.dispatcher) {
        history.answers += Result.failure(IllegalStateException("bug"))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(HistoryUiState.Error(LoadError.UNEXPECTED), viewModel.state.value)
    }

    @Test
    fun `refresh forces Hub'Eau and keeps the data on screen`() = runTest(main.dispatcher) {
        history.answers += Result.success(two)
        val viewModel = viewModel()
        advanceUntilIdle()
        val gate = CompletableDeferred<ParameterSeries?>()
        history.gate = gate
        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.isRefreshing.value)
        assertEquals(HistoryUiState.Loaded(two), viewModel.state.value)
        val three = series(*(two.points + measure("2026-10-01T08:00:00Z", 22.0)).toTypedArray())
        gate.complete(three)
        advanceUntilIdle()
        assertFalse(viewModel.isRefreshing.value)
        assertEquals(HistoryUiState.Loaded(three), viewModel.state.value)
        assertEquals(true, history.calls.last().second)
    }

    @Test
    fun `a failed refresh keeps the data and says so`() = runTest(main.dispatcher) {
        history.answers += Result.success(two)
        history.answers += Result.failure(DataError.Http(500))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(HistoryUiState.Loaded(two), viewModel.state.value)
        assertEquals(LoadError.UNAVAILABLE, viewModel.refreshError.value)
        assertFalse(viewModel.isRefreshing.value)

        history.answers += Result.success(two)
        viewModel.refresh()
        advanceUntilIdle()
        assertNull(viewModel.refreshError.value)
    }

    @Test
    fun `a slow answer is said so`() = runTest(main.dispatcher) {
        history.gate = CompletableDeferred()
        val viewModel = viewModel()
        runCurrent()
        assertFalse(viewModel.isSlow.value)
        advanceTimeBy(11.seconds)
        assertTrue(viewModel.isSlow.value)
        history.gate!!.complete(two)
        advanceUntilIdle()
        assertFalse(viewModel.isSlow.value)
    }
}
