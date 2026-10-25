package io.github.mgdx.potable.ui.start

import io.github.mgdx.potable.data.follow.Follow
import io.github.mgdx.potable.data.follow.FollowRepository
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.MainDispatcherRule
import io.github.mgdx.potable.ui.follow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `nothing is decided before the follows are read`() = runTest(main.dispatcher) {
        val viewModel = StartViewModel(FakeFollowRepository())
        assertEquals(StartDestination.Unknown, viewModel.destination.value)
    }

    @Test
    fun `without follows the application opens on the search`() = runTest(main.dispatcher) {
        val viewModel = StartViewModel(FakeFollowRepository())
        advanceUntilIdle()
        assertEquals(StartDestination.Search, viewModel.destination.value)
    }

    @Test
    fun `with follows the application opens on the primary sheet`() = runTest(main.dispatcher) {
        val primary = follow(2, udiCode = "B", isPrimary = true)
        val viewModel = StartViewModel(
            FakeFollowRepository(listOf(follow(1, udiCode = "A"), primary)),
        )
        advanceUntilIdle()
        assertEquals(StartDestination.Sheet(primary), viewModel.destination.value)
    }

    @Test
    fun `unreadable follows open the search`() = runTest(main.dispatcher) {
        val failing = object : FollowRepository by FakeFollowRepository() {
            override val follows: Flow<List<Follow>> = flow { error("database") }
        }
        val viewModel = StartViewModel(failing)
        advanceUntilIdle()
        assertEquals(StartDestination.Search, viewModel.destination.value)
    }
}
