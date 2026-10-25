package io.github.mgdx.potable.ui.udi

import io.github.mgdx.potable.core.model.UdiChoice
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.ui.FakeAppPreferences
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.FakeWaterRepository
import io.github.mgdx.potable.ui.MainDispatcherRule
import io.github.mgdx.potable.ui.common.LoadError
import io.github.mgdx.potable.ui.udi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class UdiViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val water = FakeWaterRepository()

    private val preferences = FakeAppPreferences()
    private val follows = FakeFollowRepository()

    private fun viewModel(forceChoice: Boolean = false) =
        UdiViewModel(water, preferences, follows, communeCode = "45234", forceChoice = forceChoice)

    @Test
    fun `loading is shown until the units arrive`() = runTest(main.dispatcher) {
        water.udiAnswers += Result.success(UdiChoice.None)
        val viewModel = viewModel()
        assertEquals(UdiUiState.Loading, viewModel.state.value)
        advanceUntilIdle()
        assertEquals(UdiUiState.None, viewModel.state.value)
    }

    @Test
    fun `a single unit is resolved without asking`() = runTest(main.dispatcher) {
        val only = udi("045000123")
        water.udiAnswers += Result.success(UdiChoice.Single(only))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(UdiUiState.Resolved(only), viewModel.state.value)
    }

    @Test
    fun `several units are offered as a choice`() = runTest(main.dispatcher) {
        val units = listOf(udi("045000123", "ORLEANS NORD"), udi("045000124", "ORLEANS SUD"))
        water.udiAnswers += Result.success(UdiChoice.Multiple(units))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(UdiUiState.Choice(units), viewModel.state.value)
    }

    @Test
    fun `a network error is shown, then retry loads the units`() = runTest(main.dispatcher) {
        val only = udi("045000123")
        water.udiAnswers += Result.failure(DataError.Network(IOException()))
        water.udiAnswers += Result.success(UdiChoice.Single(only))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(UdiUiState.Error(LoadError.NETWORK), viewModel.state.value)

        viewModel.retry()
        assertEquals(UdiUiState.Loading, viewModel.state.value)
        advanceUntilIdle()
        assertEquals(UdiUiState.Resolved(only), viewModel.state.value)
    }

    @Test
    fun `an HTTP error means Hub'Eau is unavailable`() = runTest(main.dispatcher) {
        water.udiAnswers += Result.failure(DataError.Http(503))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(UdiUiState.Error(LoadError.UNAVAILABLE), viewModel.state.value)
    }

    @Test
    fun `a format error means an unexpected response`() = runTest(main.dispatcher) {
        water.udiAnswers += Result.failure(DataError.Format(IllegalStateException()))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(UdiUiState.Error(LoadError.UNEXPECTED_RESPONSE), viewModel.state.value)
    }

    private val north = udi("045000123", "ORLEANS NORD")
    private val south = udi("045000124", "ORLEANS SUD")

    @Test
    fun `the unit chosen last time is taken if still listed`() = runTest(main.dispatcher) {
        preferences.remembered["45234"] = south.code
        water.udiAnswers += Result.success(UdiChoice.Multiple(listOf(north, south)))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(UdiUiState.Remembered(south), viewModel.state.value)
    }

    @Test
    fun `a remembered unit no longer listed gives the choice again`() = runTest(main.dispatcher) {
        preferences.remembered["45234"] = "045000999"
        water.udiAnswers += Result.success(UdiChoice.Multiple(listOf(north, south)))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(
            UdiUiState.Choice(listOf(north, south), currentCode = null),
            viewModel.state.value,
        )
    }

    @Test
    fun `the followed unit wins over the one chosen last time`() = runTest(main.dispatcher) {
        preferences.remembered["45234"] = south.code
        follows.follow("45234", "Orléans", north.code, north.name)
        water.udiAnswers += Result.success(UdiChoice.Multiple(listOf(north, south)))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(UdiUiState.Remembered(north), viewModel.state.value)
    }

    @Test
    fun `changing the unit offers the choice with the current one marked`() =
        runTest(main.dispatcher) {
            preferences.remembered["45234"] = south.code
            water.udiAnswers += Result.success(UdiChoice.Multiple(listOf(north, south)))
            val viewModel = viewModel(forceChoice = true)
            advanceUntilIdle()
            assertEquals(
                UdiUiState.Choice(listOf(north, south), currentCode = south.code),
                viewModel.state.value,
            )
        }

    @Test
    fun `the unit picked is remembered for the commune`() = runTest(main.dispatcher) {
        water.udiAnswers += Result.success(UdiChoice.Multiple(listOf(north, south)))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.select(north)
        advanceUntilIdle()
        assertEquals(mapOf("45234" to north.code), preferences.remembered)
    }

    @Test
    fun `the filter of the choice starts empty and keeps what is typed`() =
        runTest(main.dispatcher) {
            water.udiAnswers += Result.success(UdiChoice.Multiple(listOf(north, south)))
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals("", viewModel.query.value)
            viewModel.onQueryChange("sud")
            assertEquals("sud", viewModel.query.value)
            // The choice itself is unchanged: the screen filters what it shows.
            assertEquals(UdiUiState.Choice(listOf(north, south)), viewModel.state.value)
        }
}
