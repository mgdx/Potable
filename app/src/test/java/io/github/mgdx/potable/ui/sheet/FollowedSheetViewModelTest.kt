package io.github.mgdx.potable.ui.sheet

import io.github.mgdx.potable.data.CachedSample
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.FakeWaterRepository
import io.github.mgdx.potable.ui.MainDispatcherRule
import io.github.mgdx.potable.ui.common.LoadError
import io.github.mgdx.potable.ui.follow
import io.github.mgdx.potable.ui.sample
import io.github.mgdx.potable.ui.sample.SampleDetailUiState
import io.github.mgdx.potable.ui.sample.SampleDetailViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
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
import java.time.ZoneOffset

/** The sheet of a followed unit reads the cache (SPEC §7). */
@OptIn(ExperimentalCoroutinesApi::class)
class FollowedSheetViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val water = FakeWaterRepository()
    private val follows = FakeFollowRepository(listOf(follow(1, isPrimary = true)))
    private val clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC)

    private val latest = sample(date = Instant.parse("2026-10-01T08:00:00Z"))
    private val loaded = SheetUiState.Loaded(latest, RelativeAge.Weeks(3))
    private val fetchedAt = Instant.parse("2026-10-20T07:00:00Z")

    private fun viewModel() =
        SheetViewModel(water, follows, "45234", "045000123", "Orléans", "ORLEANS", clock)

    @Test
    fun `the cache is shown with its date, and refreshed without force`() =
        runTest(main.dispatcher) {
            water.cache.value = CachedSample(latest, fetchedAt)
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(loaded, viewModel.state.value)
            assertEquals(Freshness(fetchedAt, refreshFailed = false), viewModel.freshness.value)
            assertEquals(listOf(false), water.refreshCalls)
            // Never the direct network read for a followed unit.
            assertTrue(water.sampleCalls.isEmpty())
            assertEquals(true, viewModel.isFollowed.value)
        }

    @Test
    fun `an empty cache is filled by the refresh`() = runTest(main.dispatcher) {
        val now = Instant.parse("2026-10-25T09:59:00Z")
        water.refreshedCache = CachedSample(latest, now)
        val viewModel = viewModel()
        assertEquals(SheetUiState.Loading, viewModel.state.value)
        advanceUntilIdle()
        assertEquals(loaded, viewModel.state.value)
        assertEquals(Freshness(now, refreshFailed = false), viewModel.freshness.value)
    }

    @Test
    fun `a failed refresh keeps the cache, with the banner stressed`() = runTest(main.dispatcher) {
        water.cache.value = CachedSample(latest, fetchedAt)
        water.refreshAnswers += Result.failure(DataError.Network(IOException()))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(loaded, viewModel.state.value)
        assertEquals(Freshness(fetchedAt, refreshFailed = true), viewModel.freshness.value)
    }

    @Test
    fun `a failed refresh without cache is the error screen`() = runTest(main.dispatcher) {
        water.refreshAnswers += Result.failure(DataError.Network(IOException()))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(SheetUiState.Error(LoadError.NETWORK), viewModel.state.value)
        assertNull(viewModel.freshness.value)

        // Retry asks Hub'Eau again, whatever the date of the last request.
        water.refreshedCache = CachedSample(latest, fetchedAt)
        viewModel.retry()
        assertEquals(SheetUiState.Loading, viewModel.state.value)
        advanceUntilIdle()
        assertEquals(loaded, viewModel.state.value)
        assertEquals(listOf(false, true), water.refreshCalls)
    }

    @Test
    fun `pull-to-refresh forces the request and clears a failure`() = runTest(main.dispatcher) {
        water.cache.value = CachedSample(latest, fetchedAt)
        water.refreshAnswers += Result.failure(DataError.Http(503))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(Freshness(fetchedAt, refreshFailed = true), viewModel.freshness.value)

        val now = Instant.parse("2026-10-25T09:59:00Z")
        water.refreshedCache = CachedSample(latest, now)
        viewModel.pullToRefresh()
        assertTrue(viewModel.isRefreshing.value)
        advanceUntilIdle()
        assertFalse(viewModel.isRefreshing.value)
        assertEquals(listOf(false, true), water.refreshCalls)
        assertEquals(Freshness(now, refreshFailed = false), viewModel.freshness.value)
    }

    @Test
    fun `a cache holding no sample gives the empty state with its date`() =
        runTest(main.dispatcher) {
            water.cache.value = CachedSample(null, fetchedAt)
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(SheetUiState.NoSample, viewModel.state.value)
            assertEquals(Freshness(fetchedAt, refreshFailed = false), viewModel.freshness.value)
        }

    @Test
    fun `a purged cache is loaded again`() = runTest(main.dispatcher) {
        water.cache.value = CachedSample(latest, fetchedAt)
        val viewModel = viewModel()
        advanceUntilIdle()

        water.refreshedCache = CachedSample(latest, Instant.parse("2026-10-25T09:59:00Z"))
        water.cache.value = null
        advanceUntilIdle()
        assertEquals(listOf(false, false), water.refreshCalls)
        assertEquals(loaded, viewModel.state.value)
    }

    @Test
    fun `the sample detail follows a sheet read from the cache`() = runTest(main.dispatcher) {
        water.cache.value = CachedSample(latest, fetchedAt)
        val sheet = viewModel()
        advanceUntilIdle()
        val detail = SampleDetailViewModel(latest.code, sheet.state)
        assertEquals(SampleDetailUiState.Loaded(latest, emptyList()), detail.state.value)
    }

    @Test
    fun `unfollowing from the sheet keeps the data shown`() = runTest(main.dispatcher) {
        water.cache.value = CachedSample(latest, fetchedAt)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.toggleFollow()
        advanceUntilIdle()
        assertTrue(follows.current.isEmpty())
        assertEquals(false, viewModel.isFollowed.value)
        assertEquals(loaded, viewModel.state.value)
        assertTrue(water.sampleCalls.isEmpty())
    }
}

/** Following from the sheet of a unit read from Hub'Eau. */
@OptIn(ExperimentalCoroutinesApi::class)
class FollowFromSheetViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val water = FakeWaterRepository()
    private val follows = FakeFollowRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `following stores the unit and switches the sheet to the cache`() =
        runTest(main.dispatcher) {
            val latest = sample()
            water.sampleAnswers += Result.success(latest)
            val viewModel =
                SheetViewModel(water, follows, "45234", "045000123", "Orléans", "ORLEANS", clock)
            advanceUntilIdle()
            assertEquals(false, viewModel.isFollowed.value)
            assertNull(viewModel.freshness.value)
            assertTrue(water.refreshCalls.isEmpty())

            val fetchedAt = Instant.parse("2026-10-25T09:59:00Z")
            water.refreshedCache = CachedSample(latest, fetchedAt)
            viewModel.toggleFollow()
            advanceUntilIdle()
            val follow = follows.current.single()
            assertEquals("45234", follow.communeCode)
            assertEquals("Orléans", follow.communeName)
            assertEquals("045000123", follow.udiCode)
            assertEquals("ORLEANS", follow.udiName)
            assertEquals(true, viewModel.isFollowed.value)
            assertEquals(listOf(false), water.refreshCalls)
            assertEquals(Freshness(fetchedAt, refreshFailed = false), viewModel.freshness.value)
        }
}
