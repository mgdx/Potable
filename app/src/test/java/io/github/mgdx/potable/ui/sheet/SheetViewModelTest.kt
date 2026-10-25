package io.github.mgdx.potable.ui.sheet

import io.github.mgdx.potable.core.UnfavourableDigest
import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.SampleSummary
import io.github.mgdx.potable.data.CachedSample
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.FakeHistoryRepository
import io.github.mgdx.potable.ui.FakeWaterRepository
import io.github.mgdx.potable.ui.MainDispatcherRule
import io.github.mgdx.potable.ui.common.LoadError
import io.github.mgdx.potable.ui.sample
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

/** The sheet of a unit that is not followed: read from the cache as well (SPEC §7). */
@OptIn(ExperimentalCoroutinesApi::class)
class SheetViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val water = FakeWaterRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC)
    private val fetchedAt = Instant.parse("2026-10-25T09:59:00Z")

    private val follows = FakeFollowRepository()
    private val history = FakeHistoryRepository()

    private fun viewModel() =
        SheetViewModel(water, follows, history, "45234", "045000123", "Orléans", "ORLEANS", clock)

    private fun summary(code: String, date: String, conclusion: String, limits: Compliance) =
        SampleSummary(
            code = code,
            date = Instant.parse(date),
            conclusion = conclusion,
            bacteriologicalLimits = limits,
            physicochemicalLimits = Compliance.COMPLIANT,
            bacteriologicalReferences = Compliance.COMPLIANT,
            physicochemicalReferences = Compliance.COMPLIANT,
        )

    @Test
    fun `the unfavourable samples of the last 12 months are read once the sample is shown`() =
        runTest(main.dispatcher) {
            val advisedAgainst =
                "Eau non conforme aux limites de qualité. Consommation déconseillée."
            val recent =
                summary("R", "2026-06-02T10:00:00Z", advisedAgainst, Compliance.COMPLIANT)
            history.timeline = Result.success(
                listOf(
                    summary("OK", "2026-10-01T08:00:00Z", "Eau conforme.", Compliance.COMPLIANT),
                    recent,
                    summary("N", "2026-01-10T09:00:00Z", "Eau.", Compliance.NON_COMPLIANT),
                ),
            )
            water.refreshAnswers += Result.failure(DataError.Network(IOException()))
            val viewModel = viewModel()
            advanceUntilIdle()
            // Nothing shown, nothing asked.
            assertTrue(history.timelineCalls.isEmpty())
            assertNull(viewModel.recentUnfavourable.value)

            water.refreshedCache = CachedSample(sample(), fetchedAt)
            viewModel.retry()
            advanceUntilIdle()
            assertEquals(UnfavourableDigest(2, recent), viewModel.recentUnfavourable.value)
            // Twelve months back from today in France; once per sample shown.
            assertEquals(listOf(LocalDate.parse("2025-10-25")), history.timelineCalls)
            viewModel.pullToRefresh()
            advanceUntilIdle()
            assertEquals(1, history.timelineCalls.size)
        }

    @Test
    fun `no unfavourable sample, or a timeline that fails, shows nothing`() =
        runTest(main.dispatcher) {
            water.refreshedCache = CachedSample(sample(), fetchedAt)
            history.timeline = Result.success(
                listOf(
                    summary("OK", "2026-10-01T08:00:00Z", "Eau conforme.", Compliance.COMPLIANT),
                ),
            )
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(1, history.timelineCalls.size)
            assertNull(viewModel.recentUnfavourable.value)

            history.timeline = Result.failure(DataError.Http(503))
            val failing = viewModel()
            advanceUntilIdle()
            assertNull(failing.recentUnfavourable.value)
            assertTrue(failing.state.value is SheetUiState.Loaded)
        }

    @Test
    fun `the latest sample of the unit is cached, then shown with its age`() =
        runTest(main.dispatcher) {
            val latest = sample(date = Instant.parse("2026-10-01T08:00:00Z"))
            water.refreshedCache = CachedSample(latest, fetchedAt)
            val viewModel = viewModel()
            assertEquals(SheetUiState.Loading, viewModel.state.value)
            advanceUntilIdle()
            assertEquals(SheetUiState.Loaded(latest, RelativeAge.Weeks(3)), viewModel.state.value)
            assertEquals(Freshness(fetchedAt, refreshFailed = false), viewModel.freshness.value)
            assertEquals(false, viewModel.isFollowed.value)
            assertEquals(listOf(false), water.refreshCalls)
            // Never the direct network read.
            assertTrue(water.sampleCalls.isEmpty())
        }

    @Test
    fun `a unit already looked at is shown from the cache, offline too`() =
        runTest(main.dispatcher) {
            val latest = sample()
            water.cache.value = CachedSample(latest, Instant.parse("2026-10-20T07:00:00Z"))
            water.refreshAnswers += Result.failure(DataError.Network(IOException()))
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(SheetUiState.Loaded(latest, RelativeAge.Weeks(3)), viewModel.state.value)
            assertEquals(
                Freshness(Instant.parse("2026-10-20T07:00:00Z"), refreshFailed = true),
                viewModel.freshness.value,
            )
        }

    @Test
    fun `every sample of the latest day is shown, newest first`() = runTest(main.dispatcher) {
        val afternoon = sample(date = Instant.parse("2026-10-01T15:10:00Z"))
        val morning = afternoon.copy(code = "00122", date = Instant.parse("2026-10-01T08:30:00Z"))
        water.refreshedCache = CachedSample(listOf(afternoon, morning), fetchedAt)
        val viewModel = viewModel()
        advanceUntilIdle()
        val loaded = viewModel.state.value as SheetUiState.Loaded
        assertEquals(listOf(afternoon, morning), loaded.samples)
        assertEquals(afternoon, loaded.sample)
        assertEquals(RelativeAge.Weeks(3), loaded.age)
    }

    @Test
    fun `no sample gives the dedicated empty state`() = runTest(main.dispatcher) {
        water.refreshedCache = CachedSample(null, fetchedAt)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(SheetUiState.NoSample, viewModel.state.value)
    }

    @Test
    fun `a network error is shown, then retry loads the sample`() = runTest(main.dispatcher) {
        val latest = sample()
        water.refreshAnswers += Result.failure(DataError.Network(IOException()))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(SheetUiState.Error(LoadError.NETWORK), viewModel.state.value)
        // No automatic retry: the repository is not called again on its own.
        assertEquals(1, water.refreshCalls.size)

        water.refreshedCache = CachedSample(latest, fetchedAt)
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(SheetUiState.Loaded(latest, RelativeAge.Weeks(3)), viewModel.state.value)
        assertEquals(listOf(false, true), water.refreshCalls)
    }

    @Test
    fun `an unexpected exception is an error state, not a crash`() = runTest(main.dispatcher) {
        val latest = sample()
        water.refreshAnswers += Result.failure(IllegalStateException("bug"))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(SheetUiState.Error(LoadError.UNEXPECTED), viewModel.state.value)

        water.refreshedCache = CachedSample(latest, fetchedAt)
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(SheetUiState.Loaded(latest, RelativeAge.Weeks(3)), viewModel.state.value)
    }

    @Test
    fun `an HTTP error means Hub'Eau is unavailable`() = runTest(main.dispatcher) {
        water.refreshAnswers += Result.failure(DataError.Http(500))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(SheetUiState.Error(LoadError.UNAVAILABLE), viewModel.state.value)
    }
}
