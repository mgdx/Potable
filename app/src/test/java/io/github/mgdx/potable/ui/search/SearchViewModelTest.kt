package io.github.mgdx.potable.ui.search

import io.github.mgdx.potable.core.model.Commune
import io.github.mgdx.potable.data.CommuneRepository
import io.github.mgdx.potable.location.LocationOutcome
import io.github.mgdx.potable.location.Locator
import io.github.mgdx.potable.ui.FakeAppPreferences
import io.github.mgdx.potable.ui.FakeFollowRepository
import io.github.mgdx.potable.ui.MainDispatcherRule
import io.github.mgdx.potable.ui.commune
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val orleans = commune("45234", "Orléans")
    private val olivet = commune("45232", "Olivet")
    private val repository = GatedCommuneRepository(listOf(orleans, olivet), nearest = orleans)

    /** Never called by these tests unless "near me" is used. */
    private val noLocator = Locator { error("not expected") }

    private val follows = FakeFollowRepository()
    private val preferences = FakeAppPreferences()

    private fun viewModel(locator: Locator = noLocator) =
        SearchViewModel(repository, locator, follows, preferences)

    @Test
    fun `starts idle and preloads the referential`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(SearchUiState.Idle, viewModel.state.value)
        assertEquals(1, repository.loads)
        assertEquals(emptyList<String>(), repository.queries)
    }

    @Test
    fun `typing shows the matching communes after the debounce`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        viewModel.onQueryChange("orl")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS - 1)
        runCurrent()
        assertEquals(emptyList<String>(), repository.queries)
        advanceUntilIdle()
        assertEquals(SearchUiState.Results(listOf(orleans)), viewModel.state.value)
    }

    @Test
    fun `fast typing searches only the last query`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        viewModel.onQueryChange("o")
        viewModel.onQueryChange("ol")
        viewModel.onQueryChange("oli")
        advanceUntilIdle()
        assertEquals(listOf("oli"), repository.queries)
        assertEquals(SearchUiState.Results(listOf(olivet)), viewModel.state.value)
    }

    @Test
    fun `an unknown name gives no results`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        viewModel.onQueryChange("  zzz ")
        advanceUntilIdle()
        assertEquals(SearchUiState.NoResults("zzz"), viewModel.state.value)
    }

    @Test
    fun `clearing the field returns to idle`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        viewModel.onQueryChange("orl")
        advanceUntilIdle()
        viewModel.onQueryChange("")
        advanceUntilIdle()
        assertEquals(SearchUiState.Idle, viewModel.state.value)
        assertEquals("", viewModel.query.value)
    }

    @Test
    fun `typing while the referential loads shows the loading state at once`() =
        runTest(main.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            repository.gate = gate
            val viewModel = viewModel()
            runCurrent()
            viewModel.onQueryChange("orl")
            runCurrent()
            assertEquals(SearchUiState.Loading, viewModel.state.value)
            advanceTimeBy(SearchViewModel.DEBOUNCE_MILLIS * 10)
            assertEquals(SearchUiState.Loading, viewModel.state.value)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(SearchUiState.Results(listOf(orleans)), viewModel.state.value)
        }

    @Test
    fun `once loaded, typing keeps the hint until the debounce ends`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onQueryChange("orl")
        runCurrent()
        assertEquals(SearchUiState.Idle, viewModel.state.value)
        advanceUntilIdle()
        assertEquals(SearchUiState.Results(listOf(orleans)), viewModel.state.value)
    }

    @Test
    fun `typing then clearing before the debounce returns to idle`() = runTest(main.dispatcher) {
        repository.gate = CompletableDeferred()
        val viewModel = viewModel()
        runCurrent()
        viewModel.onQueryChange("o")
        runCurrent()
        viewModel.onQueryChange("")
        runCurrent()
        assertEquals(SearchUiState.Idle, viewModel.state.value)
    }

    @Test
    fun `near me proposes the nearest commune`() = runTest(main.dispatcher) {
        val viewModel = viewModel(Locator { LocationOutcome.Position(47.9, 1.9) })
        viewModel.locateNearby()
        assertEquals(NearbyState.Locating, viewModel.nearby.value)
        advanceUntilIdle()
        assertEquals(NearbyState.Found(orleans), viewModel.nearby.value)
        assertEquals(listOf(47.9 to 1.9), repository.nearestCalls)
        assertEquals(
            listOf<Double?>(SearchViewModel.NEARBY_MAX_DISTANCE_KM),
            repository.maxDistances,
        )
    }

    @Test
    fun `a place published under its parent commune shows the parent's name`() =
        runTest(main.dispatcher) {
            val paris = commune("75056", "Paris")
            val paris15 = commune("75115", "Paris 15e Arrondissement").copy(hubEauCode = "75056")
            val repository = GatedCommuneRepository(listOf(paris, paris15), nearest = paris15)
            val locator = Locator { LocationOutcome.Position(48.84, 2.29) }
            val viewModel = SearchViewModel(repository, locator, follows, preferences)
            viewModel.onQueryChange("15e")
            advanceUntilIdle()
            assertEquals(
                SearchUiState.Results(listOf(paris15), mapOf("75056" to "Paris")),
                viewModel.state.value,
            )
            viewModel.onQueryChange("")
            viewModel.locateNearby()
            advanceUntilIdle()
            assertEquals(NearbyState.Found(paris15, "Paris"), viewModel.nearby.value)
        }

    private fun known(code: String, name: String = "UDI $code", followed: Boolean = false) =
        KnownUdi(code, name, followed)

    @Test
    fun `a commune with a followed unit and another chosen last time lists both`() =
        runTest(main.dispatcher) {
            follows.follow("75056", "Paris", "075000221", "PARIS CENTRE")
            val preferences = FakeAppPreferences(
                remembered = mapOf("75056" to "075000222"),
                rememberedNames = mapOf("75056" to "PARIS SUD"),
            )
            val viewModel = SearchViewModel(repository, noLocator, follows, preferences)
            advanceUntilIdle()
            assertEquals(
                mapOf(
                    "75056" to listOf(
                        known("075000221", "PARIS CENTRE", followed = true),
                        known("075000222", "PARIS SUD"),
                    ),
                ),
                viewModel.knownUdis.value,
            )
        }

    @Test
    fun `a single known unit, or a remembered one without a name, lists nothing`() =
        runTest(main.dispatcher) {
            follows.follow("75056", "Paris", "075000221", "PARIS CENTRE")
            follows.follow("13055", "Marseille", "013000001", "MARSEILLE NORD")
            val preferences = FakeAppPreferences(
                // Paris: stored before names were kept. Marseille: the followed unit.
                remembered = mapOf("75056" to "075000222", "13055" to "013000001"),
                rememberedNames = mapOf("13055" to "MARSEILLE NORD"),
            )
            val viewModel = SearchViewModel(repository, noLocator, follows, preferences)
            advanceUntilIdle()
            assertEquals(emptyMap<String, List<KnownUdi>>(), viewModel.knownUdis.value)
        }

    @Test
    fun `a unit picked from the results is remembered with its name`() = runTest(main.dispatcher) {
        follows.follow("75056", "Paris", "075000221", "PARIS CENTRE")
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(emptyMap<String, List<KnownUdi>>(), viewModel.knownUdis.value)

        viewModel.rememberChoice("75056", known("075000222", "PARIS SUD"))
        advanceUntilIdle()
        assertEquals(mapOf("75056" to "075000222"), preferences.remembered)
        assertEquals(mapOf("75056" to "PARIS SUD"), preferences.rememberedNames)
        // Back on the results, both units are offered.
        assertEquals(2, viewModel.knownUdis.value.getValue("75056").size)
    }

    @Test
    fun `the known units follow the first row of their commune, once`() {
        val paris = commune("75056", "Paris")
        val paris15 = commune("75115", "Paris 15e Arrondissement").copy(hubEauCode = "75056")
        val units = listOf(known("075000221", followed = true), known("075000222"))
        val rows = searchRows(listOf(paris15, paris, orleans), mapOf("75056" to units))
        assertEquals(
            listOf(
                SearchRow.Place(paris15, choose = true),
                SearchRow.KnownUnit(paris15, units[0]),
                SearchRow.KnownUnit(paris15, units[1]),
                SearchRow.Place(paris, choose = true),
                SearchRow.Place(orleans, choose = false),
            ),
            rows,
        )
    }

    @Test
    fun `without known units each commune is one row`() {
        assertEquals(
            listOf(SearchRow.Place(orleans, false), SearchRow.Place(olivet, false)),
            searchRows(listOf(orleans, olivet), emptyMap()),
        )
    }

    @Test
    fun `a refused permission says so and leaves the search usable`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        viewModel.onLocationPermissionDenied()
        assertEquals(
            NearbyState.Failed(NearbyFailure.PERMISSION_DENIED),
            viewModel.nearby.value,
        )
        viewModel.onQueryChange("oli")
        advanceUntilIdle()
        assertEquals(NearbyState.Idle, viewModel.nearby.value)
        assertEquals(SearchUiState.Results(listOf(olivet)), viewModel.state.value)
    }

    @Test
    fun `a permission revoked meanwhile is reported as refused`() = runTest(main.dispatcher) {
        val viewModel = viewModel(Locator { LocationOutcome.PermissionMissing })
        viewModel.locateNearby()
        advanceUntilIdle()
        assertEquals(
            NearbyState.Failed(NearbyFailure.PERMISSION_DENIED),
            viewModel.nearby.value,
        )
    }

    @Test
    fun `location turned off is reported`() = runTest(main.dispatcher) {
        val viewModel = viewModel(Locator { LocationOutcome.Disabled })
        viewModel.locateNearby()
        advanceUntilIdle()
        assertEquals(
            NearbyState.Failed(NearbyFailure.LOCATION_DISABLED),
            viewModel.nearby.value,
        )
    }

    @Test
    fun `no position within the time limit gives up`() = runTest(main.dispatcher) {
        val viewModel = viewModel(Locator { awaitCancellation() })
        viewModel.locateNearby()
        advanceTimeBy(SearchViewModel.LOCATE_TIMEOUT_MILLIS - 1)
        runCurrent()
        assertEquals(NearbyState.Locating, viewModel.nearby.value)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(NearbyState.Failed(NearbyFailure.UNAVAILABLE), viewModel.nearby.value)
        assertEquals(emptyList<Pair<Double, Double>>(), repository.nearestCalls)
    }

    @Test
    fun `a position matching no commune is reported`() = runTest(main.dispatcher) {
        repository.nearest = null
        val viewModel = viewModel(Locator { LocationOutcome.Position(0.0, 0.0) })
        viewModel.locateNearby()
        advanceUntilIdle()
        assertEquals(NearbyState.Failed(NearbyFailure.NO_COMMUNE), viewModel.nearby.value)
    }
}

/**
 * Searches by substring; loading the referential (preload, search) waits for
 * [gate] when one is set, as the real repository does on its mutex.
 */
private class GatedCommuneRepository(private val communes: List<Commune>, var nearest: Commune?) :
    CommuneRepository {
    var gate: CompletableDeferred<Unit>? = null
    var loads = 0
    val queries = mutableListOf<String>()
    val nearestCalls = mutableListOf<Pair<Double, Double>>()
    val maxDistances = mutableListOf<Double?>()

    override suspend fun preload() {
        gate?.await()
        loads++
    }

    override suspend fun search(query: String, limit: Int): List<Commune> {
        gate?.await()
        queries += query
        return communes.filter { it.name.contains(query, ignoreCase = true) }.take(limit)
    }

    override suspend fun nearest(
        latitude: Double,
        longitude: Double,
        maxDistanceKm: Double?,
    ): Commune? {
        nearestCalls += latitude to longitude
        maxDistances += maxDistanceKm
        return nearest
    }

    override suspend fun byCode(code: String): Commune? = communes.find { it.code == code }
}
