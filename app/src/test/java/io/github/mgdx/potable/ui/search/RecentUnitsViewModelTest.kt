package io.github.mgdx.potable.ui.search

import io.github.mgdx.potable.data.CachedSample
import io.github.mgdx.potable.data.WaterRepository
import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.data.preferences.RecentUnit
import io.github.mgdx.potable.location.Locator
import io.github.mgdx.potable.ui.FakeAppPreferences
import io.github.mgdx.potable.ui.FakeCommuneRepository
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.FakeHistoryRepository
import io.github.mgdx.potable.ui.FakeWaterRepository
import io.github.mgdx.potable.ui.MainDispatcherRule
import io.github.mgdx.potable.ui.sample
import io.github.mgdx.potable.ui.sheet.SheetUiState
import io.github.mgdx.potable.ui.sheet.SheetViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The search's "recent" list: filled by the sheet, shown and trimmed by the search. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentUnitsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val preferences = FakeAppPreferences()
    private val clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC)

    private val orleans = RecentUnit("45234", "Orléans", "045000123", "ORLEANS")
    private val paris = RecentUnit("75056", "Paris", "075000456", "PARIS SUD")

    private fun sheet() = SheetViewModel(
        FakeWaterRepository(),
        FakeFollowRepository(),
        FakeHistoryRepository(),
        orleans.communeCode,
        orleans.udiCode,
        orleans.communeName,
        orleans.udiName,
        clock,
        recents = preferences,
    )

    private fun search() = SearchViewModel(
        FakeCommuneRepository(emptyList()),
        Locator { error("not expected") },
        FakeFollowRepository(),
        preferences,
    )

    @Test
    fun `opening a sheet records its unit once, not on each reload`() = runTest(main.dispatcher) {
        val viewModel = sheet()
        advanceUntilIdle()
        assertEquals(listOf(orleans), preferences.recent.value)

        // Another unit opened since: a reload of this sheet does not move it back up.
        preferences.addRecentUnit(paris)
        viewModel.retry()
        viewModel.pullToRefresh()
        advanceUntilIdle()
        assertEquals(listOf(paris, orleans), preferences.recent.value)
    }

    @Test
    fun `the unit is recorded as recent before its cache is fetched`() = runTest(main.dispatcher) {
        // A DataStore write takes time: the fetch must not overtake it, or
        // the cache it writes could be pruned as unreferenced (SPEC §7).
        val slow = object : AppPreferences by preferences {
            override suspend fun addRecentUnit(unit: RecentUnit) {
                delay(100)
                preferences.addRecentUnit(unit)
            }
        }
        val recentAtFetch = mutableListOf<List<RecentUnit>>()
        val water = object : WaterRepository by FakeWaterRepository() {
            override suspend fun refreshCache(
                communeCode: String,
                udiCode: String,
                force: Boolean,
            ): Boolean {
                recentAtFetch += preferences.recent.value
                return true
            }
        }
        val history = FakeHistoryRepository()
        SheetViewModel(
            water,
            FakeFollowRepository(),
            history,
            orleans.communeCode,
            orleans.udiCode,
            orleans.communeName,
            orleans.udiName,
            clock,
            recents = slow,
        )
        advanceUntilIdle()

        assertEquals(listOf(listOf(orleans)), recentAtFetch)
    }

    @Test
    fun `a sheet whose cache was pruned records itself again if no longer recent`() =
        runTest(main.dispatcher) {
            val water = FakeWaterRepository()
            water.refreshedCache = CachedSample(sample(), Instant.parse("2026-10-25T09:00:00Z"))
            val viewModel = SheetViewModel(
                water,
                FakeFollowRepository(),
                FakeHistoryRepository(),
                orleans.communeCode,
                orleans.udiCode,
                orleans.communeName,
                orleans.udiName,
                clock,
                recents = preferences,
            )
            advanceUntilIdle()

            // Still recent, below another unit: a purge (settings) leaves the order.
            preferences.addRecentUnit(paris)
            water.cache.value = null
            advanceUntilIdle()
            assertEquals(listOf(paris, orleans), preferences.recent.value)

            // Pushed out of the list while below other sheets, then pruned:
            // reloaded, it is recorded again so that its cache is kept.
            preferences.removeRecentUnit(orleans.communeCode, orleans.udiCode)
            water.cache.value = null
            advanceUntilIdle()
            assertEquals(listOf(orleans, paris), preferences.recent.value)
            assertTrue(viewModel.state.value is SheetUiState.Loaded)
        }

    @Test
    fun `the search lists the recent units and removes one`() = runTest(main.dispatcher) {
        preferences.addRecentUnit(orleans)
        preferences.addRecentUnit(paris)
        val viewModel = search()
        advanceUntilIdle()
        assertEquals(listOf(paris, orleans), viewModel.recentUnits.value)

        viewModel.removeRecent(paris)
        advanceUntilIdle()
        assertEquals(listOf(orleans), viewModel.recentUnits.value)
    }
}
