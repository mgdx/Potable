package io.github.mgdx.potable.ui.common

import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.core.model.UdiChoice
import io.github.mgdx.potable.data.CachedSample
import io.github.mgdx.potable.data.WaterRepository
import io.github.mgdx.potable.ui.FakeAppPreferences
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.MainDispatcherRule
import io.github.mgdx.potable.ui.follow
import io.github.mgdx.potable.ui.sample
import io.github.mgdx.potable.ui.sheet.SheetUiState
import io.github.mgdx.potable.ui.sheet.SheetViewModel
import io.github.mgdx.potable.ui.udi.UdiUiState
import io.github.mgdx.potable.ui.udi.UdiViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds

/** Every request waits until the test answers it, as a slow Hub'Eau would. */
private class SlowWaterRepository : WaterRepository {
    var udiAnswer = CompletableDeferred<UdiChoice>()
    var sampleAnswer = CompletableDeferred<Sample?>()
    var refreshAnswer = CompletableDeferred<Unit>()
    val cache = MutableStateFlow<CachedSample?>(null)
    val refreshCalls = mutableListOf<Boolean>()

    override suspend fun udis(communeCode: String): UdiChoice = udiAnswer.await()

    override suspend fun latestSample(communeCode: String, networkCode: String?): Sample? =
        sampleAnswer.await()

    override fun cachedLatestSample(communeCode: String, udiCode: String): Flow<CachedSample?> =
        cache

    override suspend fun refreshFollowed(
        communeCode: String,
        udiCode: String,
        force: Boolean,
    ): Boolean {
        refreshCalls += force
        refreshAnswer.await()
        return true
    }
}

/** The "Hub'Eau is slow to answer" message, in virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class SlowLoadingTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val water = SlowWaterRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC)

    private fun sheet(follows: FakeFollowRepository = FakeFollowRepository()) = SheetViewModel(
        water,
        follows,
        "45234",
        "045000123",
        "Orléans",
        "ORLEANS",
        clock,
        slowThreshold = 10.seconds,
    )

    private fun TestScope.waitFor(seconds: Int) {
        advanceTimeBy(seconds.seconds)
        runCurrent()
    }

    @Test
    fun `the sheet says Hub'Eau is slow after ten seconds only`() = runTest(main.dispatcher) {
        val viewModel = sheet()
        runCurrent()
        assertEquals(SheetUiState.Loading, viewModel.state.value)
        waitFor(9)
        assertFalse(viewModel.isSlow.value)
        waitFor(2)
        assertTrue(viewModel.isSlow.value)

        water.sampleAnswer.complete(sample())
        runCurrent()
        assertTrue(viewModel.state.value is SheetUiState.Loaded)
        assertFalse(viewModel.isSlow.value)
    }

    @Test
    fun `a new load counts from zero again`() = runTest(main.dispatcher) {
        val viewModel = sheet()
        runCurrent()
        waitFor(11)
        assertTrue(viewModel.isSlow.value)
        water.sampleAnswer.completeExceptionally(IllegalStateException())
        runCurrent()
        assertFalse(viewModel.isSlow.value)

        water.sampleAnswer = CompletableDeferred()
        viewModel.retry()
        runCurrent()
        assertEquals(SheetUiState.Loading, viewModel.state.value)
        waitFor(5)
        assertFalse(viewModel.isSlow.value)
        waitFor(6)
        assertTrue(viewModel.isSlow.value)
    }

    @Test
    fun `the choice of unit says Hub'Eau is slow too`() = runTest(main.dispatcher) {
        val viewModel = UdiViewModel(
            water,
            FakeAppPreferences(),
            FakeFollowRepository(),
            "45234",
            slowThreshold = 10.seconds,
        )
        runCurrent()
        waitFor(9)
        assertFalse(viewModel.isSlow.value)
        waitFor(2)
        assertTrue(viewModel.isSlow.value)
        water.udiAnswer.complete(UdiChoice.None)
        runCurrent()
        assertEquals(UdiUiState.None, viewModel.state.value)
        assertFalse(viewModel.isSlow.value)
    }

    @Test
    fun `a followed sheet without cache shows a single indicator on retry`() =
        runTest(main.dispatcher) {
            val follows = FakeFollowRepository(listOf(follow(1, isPrimary = true)))
            water.refreshAnswer.completeExceptionally(IllegalStateException())
            val viewModel = sheet(follows)
            advanceUntilIdle()
            assertEquals(SheetUiState.Error(LoadError.UNEXPECTED), viewModel.state.value)

            // Retry and pull-to-refresh: the loading state shows, not the pull indicator.
            water.refreshAnswer = CompletableDeferred()
            viewModel.retry()
            runCurrent()
            assertEquals(SheetUiState.Loading, viewModel.state.value)
            assertFalse(viewModel.isRefreshing.value)
            viewModel.pullToRefresh()
            runCurrent()
            assertFalse(viewModel.isRefreshing.value)
            assertEquals(listOf(false, true), water.refreshCalls)

            water.cache.value = CachedSample(sample(), clock.instant())
            water.refreshAnswer.complete(Unit)
            advanceUntilIdle()
            assertTrue(viewModel.state.value is SheetUiState.Loaded)

            // Data on screen: now the pull indicator is the only one.
            water.refreshAnswer = CompletableDeferred()
            viewModel.pullToRefresh()
            runCurrent()
            assertTrue(viewModel.isRefreshing.value)
            assertTrue(viewModel.state.value is SheetUiState.Loaded)
            water.refreshAnswer.complete(Unit)
            advanceUntilIdle()
            assertFalse(viewModel.isRefreshing.value)
        }
}
