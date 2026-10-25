package io.github.mgdx.potable.ui.timeline

import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.core.model.SampleSummary
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.data.HistoryRepository
import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.ui.FakeFollowRepository
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

/** Each call takes the next queued answer; a call with nothing queued waits for [gate]. */
private class FakeHistoryRepository : HistoryRepository {
    val answers = ArrayDeque<Result<List<SampleSummary>>>()
    val calls = mutableListOf<Pair<LocalDate, Boolean>>()
    var gate: CompletableDeferred<List<SampleSummary>>? = null

    override suspend fun parameterSeries(
        communeCode: String,
        udiCode: String,
        parameterCodes: Set<String>,
        since: LocalDate,
        force: Boolean,
    ): ParameterSeries? = null

    override suspend fun sampleTimeline(
        communeCode: String,
        udiCode: String,
        since: LocalDate,
        force: Boolean,
    ): List<SampleSummary> {
        calls += since to force
        val answer = answers.removeFirstOrNull() ?: return gate!!.await()
        return answer.getOrThrow()
    }
}

private class YearsPreferences(years: Int) : AppPreferences {
    override val disclaimerAccepted: Flow<Boolean> = flowOf(true)

    override suspend fun acceptDisclaimer() = Unit

    override val historyYears: Flow<Int> = flowOf(years)
}

private fun summary(
    code: String,
    date: String,
    bacteriologicalLimits: Compliance = Compliance.COMPLIANT,
    physicochemicalReferences: Compliance = Compliance.COMPLIANT,
) = SampleSummary(
    code = code,
    date = Instant.parse(date),
    conclusion = "Eau d'alimentation conforme aux exigences de qualité en vigueur.",
    bacteriologicalLimits = bacteriologicalLimits,
    physicochemicalLimits = Compliance.COMPLIANT,
    bacteriologicalReferences = Compliance.NOT_ASSESSED,
    physicochemicalReferences = physicochemicalReferences,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val history = FakeHistoryRepository()
    private val follows = FakeFollowRepository()

    // 2026-10-25 in Paris even though it is still the 24th in UTC.
    private val clock = Clock.fixed(Instant.parse("2026-10-24T23:30:00Z"), ZoneOffset.UTC)

    private val older = summary("A", "2025-03-01T08:00:00Z")
    private val newer = summary(
        "B",
        "2026-09-01T08:00:00Z",
        bacteriologicalLimits = Compliance.NON_COMPLIANT,
        physicochemicalReferences = Compliance.NON_COMPLIANT,
    )

    private fun viewModel(years: Int = 3) = TimelineViewModel(
        history,
        YearsPreferences(years),
        follows,
        "45234",
        "045000123",
        clock,
        slowThreshold = 10.seconds,
    )

    @Test
    fun `the window starts at the setting, counted back from today in France`() =
        runTest(main.dispatcher) {
            history.answers += Result.success(listOf(older, newer))
            val viewModel = viewModel(years = 5)
            assertEquals(TimelineUiState.Loading, viewModel.state.value)
            advanceUntilIdle()
            assertEquals(5, viewModel.years.value)
            assertEquals(listOf(LocalDate.of(2021, 10, 25) to false), history.calls)
            val loaded = viewModel.state.value as TimelineUiState.Loaded
            assertEquals(LocalDate.of(2021, 10, 25), loaded.since)
            // Newest first, whatever the order received.
            assertEquals(listOf("B", "A"), loaded.entries.map { it.sample.code })
        }

    @Test
    fun `only the compliances published as non-compliant are listed`() = runTest(main.dispatcher) {
        history.answers += Result.success(listOf(newer, older))
        val viewModel = viewModel()
        advanceUntilIdle()
        val entries = (viewModel.state.value as TimelineUiState.Loaded).entries
        assertEquals(
            listOf(
                ComplianceKind.BACTERIOLOGICAL_LIMITS,
                ComplianceKind.PHYSICOCHEMICAL_REFERENCES,
            ),
            entries[0].nonCompliances,
        )
        // Not assessed, compliant: nothing to point out.
        assertTrue(entries[1].nonCompliances.isEmpty())
    }

    @Test
    fun `changing the window loads it again without force`() = runTest(main.dispatcher) {
        history.answers += Result.success(listOf(older, newer))
        history.answers += Result.success(listOf(newer))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.selectYears(1)
        assertEquals(TimelineUiState.Loading, viewModel.state.value)
        advanceUntilIdle()
        assertEquals(1, viewModel.years.value)
        assertEquals(
            listOf(LocalDate.of(2023, 10, 25) to false, LocalDate.of(2025, 10, 25) to false),
            history.calls,
        )
        val loaded = viewModel.state.value as TimelineUiState.Loaded
        assertEquals(listOf("B"), loaded.entries.map { it.sample.code })

        // The same window, or one not offered, does nothing.
        viewModel.selectYears(1)
        viewModel.selectYears(2)
        advanceUntilIdle()
        assertEquals(2, history.calls.size)
    }

    @Test
    fun `no sample in the window is the empty state`() = runTest(main.dispatcher) {
        history.answers += Result.success(emptyList())
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(TimelineUiState.Empty(LocalDate.of(2023, 10, 25)), viewModel.state.value)
    }

    @Test
    fun `an error offers Retry, which forces the request`() = runTest(main.dispatcher) {
        history.answers += Result.failure(DataError.Network(IOException()))
        history.answers += Result.success(listOf(older))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(TimelineUiState.Error(LoadError.NETWORK), viewModel.state.value)

        viewModel.retry()
        assertEquals(TimelineUiState.Loading, viewModel.state.value)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is TimelineUiState.Loaded)
        assertEquals(listOf(false, true), history.calls.map { it.second })
    }

    @Test
    fun `a bug is an error state, not a crash`() = runTest(main.dispatcher) {
        history.answers += Result.failure(IllegalStateException())
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(TimelineUiState.Error(LoadError.UNEXPECTED), viewModel.state.value)
    }

    @Test
    fun `pull-to-refresh forces the request and keeps the data on failure`() =
        runTest(main.dispatcher) {
            history.answers += Result.success(listOf(older))
            history.answers += Result.failure(DataError.Http(503))
            history.answers += Result.success(listOf(newer, older))
            val viewModel = viewModel()
            advanceUntilIdle()
            val first = viewModel.state.value

            viewModel.refresh()
            assertTrue(viewModel.isRefreshing.value)
            advanceUntilIdle()
            assertFalse(viewModel.isRefreshing.value)
            assertEquals(first, viewModel.state.value)
            assertEquals(LoadError.UNAVAILABLE, viewModel.refreshFailed.value)

            viewModel.refresh()
            advanceUntilIdle()
            assertNull(viewModel.refreshFailed.value)
            assertEquals(2, (viewModel.state.value as TimelineUiState.Loaded).entries.size)
            assertEquals(listOf(false, true, true), history.calls.map { it.second })
        }

    @Test
    fun `with nothing shown, refreshing is a retry with a single indicator`() =
        runTest(main.dispatcher) {
            history.answers += Result.failure(DataError.Network(IOException()))
            history.gate = CompletableDeferred()
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.refresh()
            runCurrent()
            assertEquals(TimelineUiState.Loading, viewModel.state.value)
            assertFalse(viewModel.isRefreshing.value)
            assertEquals(listOf(false, true), history.calls.map { it.second })
            history.gate!!.complete(listOf(older))
            advanceUntilIdle()
            assertTrue(viewModel.state.value is TimelineUiState.Loaded)
        }

    @Test
    fun `pull-to-refresh is offered for a followed unit only`() = runTest(main.dispatcher) {
        history.answers += Result.success(listOf(older))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertFalse(viewModel.isFollowed.value)

        follows.follow("45234", "Orléans", "045000123", "ORLEANS")
        advanceUntilIdle()
        assertTrue(viewModel.isFollowed.value)
    }

    @Test
    fun `a load longer than the threshold says Hub'Eau is slow`() = runTest(main.dispatcher) {
        history.gate = CompletableDeferred()
        val viewModel = viewModel()
        runCurrent()
        assertEquals(TimelineUiState.Loading, viewModel.state.value)
        advanceTimeBy(9.seconds)
        runCurrent()
        assertFalse(viewModel.isSlow.value)
        advanceTimeBy(2.seconds)
        runCurrent()
        assertTrue(viewModel.isSlow.value)

        history.gate!!.complete(listOf(older))
        runCurrent()
        assertFalse(viewModel.isSlow.value)
        assertTrue(viewModel.state.value is TimelineUiState.Loaded)
    }

    @Test
    fun `a followed unit is detected among the follows`() = runTest(main.dispatcher) {
        follows.follow("45234", "Orléans", "045000123", "ORLEANS")
        history.answers += Result.success(listOf(older))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertTrue(viewModel.isFollowed.value)
    }
}
