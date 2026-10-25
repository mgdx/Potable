package io.github.mgdx.potable.ui.sample

import io.github.mgdx.potable.core.ParameterFamily
import io.github.mgdx.potable.ui.MainDispatcherRule
import io.github.mgdx.potable.ui.common.LoadError
import io.github.mgdx.potable.ui.sample
import io.github.mgdx.potable.ui.sheet.RelativeAge
import io.github.mgdx.potable.ui.sheet.SheetUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SampleDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val loaded = sample().copy(results = listOf(nitrates, ecoli, freeChlorine, enterococci))
    private val sheet =
        MutableStateFlow<SheetUiState>(SheetUiState.Loaded(loaded, RelativeAge.Today))

    private fun viewModel() = SampleDetailViewModel(loaded.code, sheet)

    @Test
    fun `the sheet's sample is shown at once, grouped by family`() {
        val state = viewModel().state.value
        assertEquals(
            SampleDetailUiState.Loaded(
                loaded,
                listOf(
                    FamilyGroup(ParameterFamily.MICROBIOLOGY, listOf(ecoli, enterococci)),
                    FamilyGroup(ParameterFamily.PHYSICOCHEMISTRY, listOf(nitrates)),
                    FamilyGroup(ParameterFamily.DISINFECTION, listOf(freeChlorine)),
                ),
            ),
            state,
        )
    }

    @Test
    fun `typing filters the results by label`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        viewModel.onQueryChange("Entérocoques")
        assertEquals("Entérocoques", viewModel.query.value)
        advanceUntilIdle()
        assertEquals(
            SampleDetailUiState.Loaded(
                loaded,
                listOf(FamilyGroup(ParameterFamily.MICROBIOLOGY, listOf(enterococci))),
            ),
            viewModel.state.value,
        )

        viewModel.onQueryChange("pesticide")
        advanceUntilIdle()
        assertEquals(SampleDetailUiState.Loaded(loaded, emptyList()), viewModel.state.value)
    }

    @Test
    fun `the screen follows the sheet when it loads again`() = runTest(main.dispatcher) {
        sheet.value = SheetUiState.Loading
        val viewModel = viewModel()
        assertEquals(SampleDetailUiState.Loading, viewModel.state.value)

        sheet.value = SheetUiState.Error(LoadError.NETWORK)
        advanceUntilIdle()
        assertEquals(SampleDetailUiState.Error(LoadError.NETWORK), viewModel.state.value)

        sheet.value = SheetUiState.Loaded(loaded, RelativeAge.Today)
        advanceUntilIdle()
        assertEquals(loaded, (viewModel.state.value as SampleDetailUiState.Loaded).sample)
    }

    @Test
    fun `another sample on the sheet makes this one unavailable`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        sheet.value = SheetUiState.Loaded(loaded.copy(code = "00999"), RelativeAge.Today)
        advanceUntilIdle()
        assertEquals(SampleDetailUiState.Unavailable, viewModel.state.value)

        sheet.value = SheetUiState.NoSample
        advanceUntilIdle()
        assertEquals(SampleDetailUiState.Unavailable, viewModel.state.value)
    }
}
