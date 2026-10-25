package io.github.mgdx.potable.ui.sheet

import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.FakeWaterRepository
import io.github.mgdx.potable.ui.MainDispatcherRule
import io.github.mgdx.potable.ui.common.LoadError
import io.github.mgdx.potable.ui.sample
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SheetViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val water = FakeWaterRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC)

    private val follows = FakeFollowRepository()

    private fun viewModel() =
        SheetViewModel(water, follows, "45234", "045000123", "Orléans", "ORLEANS", clock)

    @Test
    fun `the latest sample of the unit is loaded with its age`() = runTest(main.dispatcher) {
        val latest = sample(date = Instant.parse("2026-10-01T08:00:00Z"))
        water.sampleAnswers += Result.success(latest)
        val viewModel = viewModel()
        assertEquals(SheetUiState.Loading, viewModel.state.value)
        advanceUntilIdle()
        assertEquals(SheetUiState.Loaded(latest, RelativeAge.Weeks(3)), viewModel.state.value)
        assertEquals(listOf("45234" to "045000123"), water.sampleCalls)
    }

    @Test
    fun `no sample gives the dedicated empty state`() = runTest(main.dispatcher) {
        water.sampleAnswers += Result.success(null)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(SheetUiState.NoSample, viewModel.state.value)
    }

    @Test
    fun `a network error is shown, then retry loads the sample`() = runTest(main.dispatcher) {
        val latest = sample()
        water.sampleAnswers += Result.failure(DataError.Network(IOException()))
        water.sampleAnswers += Result.success(latest)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(SheetUiState.Error(LoadError.NETWORK), viewModel.state.value)
        // No automatic retry: the repository is not called again on its own.
        assertEquals(1, water.sampleCalls.size)

        viewModel.retry()
        advanceUntilIdle()
        assertEquals(SheetUiState.Loaded(latest, RelativeAge.Weeks(3)), viewModel.state.value)
    }

    @Test
    fun `an unexpected exception is an error state, not a crash`() = runTest(main.dispatcher) {
        val latest = sample()
        water.sampleAnswers += Result.failure(IllegalStateException("bug"))
        water.sampleAnswers += Result.success(latest)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(SheetUiState.Error(LoadError.UNEXPECTED), viewModel.state.value)

        viewModel.retry()
        advanceUntilIdle()
        assertEquals(SheetUiState.Loaded(latest, RelativeAge.Weeks(3)), viewModel.state.value)
    }

    @Test
    fun `an HTTP error means Hub'Eau is unavailable`() = runTest(main.dispatcher) {
        water.sampleAnswers += Result.failure(DataError.Http(500))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(SheetUiState.Error(LoadError.UNAVAILABLE), viewModel.state.value)
    }
}
