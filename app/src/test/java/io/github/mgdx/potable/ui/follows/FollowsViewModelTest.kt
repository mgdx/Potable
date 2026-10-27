package io.github.mgdx.potable.ui.follows

import io.github.mgdx.potable.core.model.Commune
import io.github.mgdx.potable.data.CommuneRepository
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.MainDispatcherRule
import io.github.mgdx.potable.ui.follow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * The referential by INSEE code; each lookup waits for [ready] (already
 * complete unless given), as the first read of the embedded file does, then
 * fails with [failure] if set.
 */
private class FakeReferential(
    vararg communes: Commune,
    val ready: CompletableDeferred<Unit> = CompletableDeferred(Unit),
) : CommuneRepository {
    private val byCode = communes.associateBy { it.code }
    var failure: Exception? = null

    override suspend fun search(query: String, limit: Int): List<Commune> = emptyList()

    override suspend fun nearest(
        latitude: Double,
        longitude: Double,
        maxDistanceKm: Double?,
    ): Commune? = null

    override suspend fun byCode(code: String): Commune? {
        ready.await()
        failure?.let { throw it }
        return byCode[code]
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FollowsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val first = follow(1, udiCode = "A")
    private val second = follow(2, udiCode = "B")
    private val third = follow(3, communeCode = "75056", udiCode = "C")
    private val unknown = follow(4, communeCode = "99999", udiCode = "D")
    private val repository = FakeFollowRepository(listOf(first, second, third))
    private val referential = FakeReferential(
        Commune("45234", "Orléans", listOf("45000", "45100"), latitude = 47.9, longitude = 1.9),
        Commune("75056", "Paris", listOf("75001", "75002"), latitude = 48.9, longitude = 2.3),
    )

    private fun FollowsViewModel.postalCodes() = (state.value as FollowsUiState.Loaded).postalCodes

    private fun FollowsViewModel.ids() =
        (state.value as FollowsUiState.Loaded).follows.map { it.id }

    private fun FollowsViewModel.shownIds() =
        (state.value as FollowsUiState.Loaded).shown.map { it.id }

    @Test
    fun `the follows are listed in order`() = runTest(main.dispatcher) {
        val viewModel = FollowsViewModel(repository, referential)
        assertEquals(FollowsUiState.Loading, viewModel.state.value)
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 3L), viewModel.ids())
    }

    @Test
    fun `nothing followed gives an empty list`() = runTest(main.dispatcher) {
        val viewModel = FollowsViewModel(FakeFollowRepository(), referential)
        advanceUntilIdle()
        assertEquals(FollowsUiState.Loaded(emptyList()), viewModel.state.value)
    }

    @Test
    fun `a query filters the list shown, not the follows`() = runTest(main.dispatcher) {
        val viewModel = FollowsViewModel(repository, referential)
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
        val viewModel = FollowsViewModel(repository, referential)
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

    @Test
    fun `the postal codes come from the referential, by commune code`() = runTest(main.dispatcher) {
        val viewModel = FollowsViewModel(repository, referential)
        advanceUntilIdle()
        assertEquals(
            mapOf("45234" to listOf("45000", "45100"), "75056" to listOf("75001", "75002")),
            viewModel.postalCodes(),
        )
    }

    @Test
    fun `the list does not wait for the referential`() = runTest(main.dispatcher) {
        val slow = FakeReferential(
            Commune("45234", "Orléans", listOf("45000"), latitude = 47.9, longitude = 1.9),
            ready = CompletableDeferred(),
        )
        val viewModel = FollowsViewModel(repository, slow)
        runCurrent()
        assertEquals(listOf(1L, 2L, 3L), viewModel.ids())
        assertEquals(emptyMap<String, List<String>>(), viewModel.postalCodes())
        slow.ready.complete(Unit)
        advanceUntilIdle()
        assertEquals(mapOf("45234" to listOf("45000")), viewModel.postalCodes())
    }

    @Test
    fun `a commune unknown to the referential has no postal code`() = runTest(main.dispatcher) {
        val viewModel = FollowsViewModel(FakeFollowRepository(listOf(unknown)), referential)
        advanceUntilIdle()
        assertEquals(listOf(4L), viewModel.ids())
        assertEquals(emptyMap<String, List<String>>(), viewModel.postalCodes())
    }

    @Test
    fun `an unreadable referential leaves the list without postal codes`() =
        runTest(main.dispatcher) {
            referential.failure = IOException("unreadable")
            val viewModel = FollowsViewModel(repository, referential)
            advanceUntilIdle()
            assertEquals(listOf(1L, 2L, 3L), viewModel.ids())
            assertEquals(emptyMap<String, List<String>>(), viewModel.postalCodes())
        }

    @Test
    fun `a query finds a commune by the start of its postal code`() = runTest(main.dispatcher) {
        val viewModel = FollowsViewModel(repository, referential)
        advanceUntilIdle()
        viewModel.setQuery("750")
        advanceUntilIdle()
        assertEquals(listOf(3L), viewModel.shownIds())
    }

    @Test
    fun `a followed commune added later gets its postal codes`() = runTest(main.dispatcher) {
        val follows = FakeFollowRepository(listOf(first))
        val viewModel = FollowsViewModel(follows, referential)
        advanceUntilIdle()
        follows.follow("75056", "Paris", "075000001", "PARIS EST")
        advanceUntilIdle()
        assertEquals(setOf("45234", "75056"), viewModel.postalCodes().keys)
    }
}
