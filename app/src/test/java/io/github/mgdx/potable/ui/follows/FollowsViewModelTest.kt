package io.github.mgdx.potable.ui.follows

import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.MainDispatcherRule
import io.github.mgdx.potable.ui.follow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FollowsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val first = follow(1, udiCode = "A")
    private val second = follow(2, udiCode = "B")
    private val third = follow(3, udiCode = "C")
    private val repository = FakeFollowRepository(listOf(first, second, third))

    private fun FollowsViewModel.ids() =
        (state.value as FollowsUiState.Loaded).follows.map { it.id }

    private fun FollowsViewModel.shownIds() =
        (state.value as FollowsUiState.Loaded).shown.map { it.id }

    @Test
    fun `the follows are listed in order`() = runTest(main.dispatcher) {
        val viewModel = FollowsViewModel(repository)
        assertEquals(FollowsUiState.Loading, viewModel.state.value)
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 3L), viewModel.ids())
    }

    @Test
    fun `nothing followed gives an empty list`() = runTest(main.dispatcher) {
        val viewModel = FollowsViewModel(FakeFollowRepository())
        advanceUntilIdle()
        assertEquals(FollowsUiState.Loaded(emptyList()), viewModel.state.value)
    }

    @Test
    fun `a query filters the list shown, not the follows`() = runTest(main.dispatcher) {
        val viewModel = FollowsViewModel(repository)
        advanceUntilIdle()
        viewModel.setQuery("udi b")
        advanceUntilIdle()
        assertEquals(listOf(2L), viewModel.shownIds())
        assertEquals(listOf(1L, 2L, 3L), viewModel.ids())

        viewModel.setQuery("")
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 3L), viewModel.shownIds())
    }

    @Test
    fun `removal waits for the confirmation`() = runTest(main.dispatcher) {
        val viewModel = FollowsViewModel(repository)
        advanceUntilIdle()
        viewModel.requestRemoval(second)
        assertEquals(second, viewModel.pendingRemoval.value)
        viewModel.cancelRemoval()
        advanceUntilIdle()
        assertNull(viewModel.pendingRemoval.value)
        assertEquals(listOf(1L, 2L, 3L), viewModel.ids())

        viewModel.requestRemoval(second)
        viewModel.confirmRemoval()
        advanceUntilIdle()
        assertNull(viewModel.pendingRemoval.value)
        assertEquals(listOf(1L, 3L), viewModel.ids())
    }
}
