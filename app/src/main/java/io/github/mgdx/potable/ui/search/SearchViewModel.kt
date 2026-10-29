package io.github.mgdx.potable.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.potable.AppContainer
import io.github.mgdx.potable.core.model.Commune
import io.github.mgdx.potable.data.CommuneRepository
import io.github.mgdx.potable.data.follow.Follow
import io.github.mgdx.potable.data.follow.FollowRepository
import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.data.preferences.RecentUnit
import io.github.mgdx.potable.data.preferences.RememberedUdi
import io.github.mgdx.potable.location.LocationOutcome
import io.github.mgdx.potable.location.Locator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface SearchUiState {
    /** Nothing typed yet: the screen explains what can be searched. */
    data object Idle : SearchUiState

    /** A search waits for the referential, or runs with nothing to show yet. */
    data object Loading : SearchUiState

    /**
     * [parentNames] maps the [Commune.hubEauCode] of results to the name of
     * that parent commune, when the referential knows it.
     */
    data class Results(
        val communes: List<Commune>,
        val parentNames: Map<String, String> = emptyMap(),
    ) : SearchUiState

    data class NoResults(val query: String) : SearchUiState
}

/** The "near me" lookup (SPEC §5 F1). */
sealed interface NearbyState {
    data object Idle : NearbyState

    data object Locating : NearbyState

    /** The commune the device is in, proposed to the user, who selects it or not. */
    data class Found(val commune: Commune, val parentName: String? = null) : NearbyState

    data class Failed(val reason: NearbyFailure) : NearbyState
}

enum class NearbyFailure { PERMISSION_DENIED, LOCATION_DISABLED, UNAVAILABLE, NO_COMMUNE }

/** A unit of a commune the user already knows: followed, or chosen last time. */
data class KnownUdi(val code: String, val name: String, val isFollowed: Boolean)

/**
 * The known units of each commune that has at least two, by the code Hub'Eau
 * publishes it under: the followed ones in their order, then the one chosen
 * last time, without duplicates. A remembered unit stored without its name
 * (before names were kept) is left out.
 */
internal fun knownUdis(
    follows: List<Follow>,
    remembered: Map<String, RememberedUdi>,
): Map<String, List<KnownUdi>> {
    val byCommune = LinkedHashMap<String, MutableList<KnownUdi>>()
    follows.forEach { follow ->
        val units = byCommune.getOrPut(follow.communeCode) { mutableListOf() }
        if (units.none { it.code == follow.udiCode }) {
            units += KnownUdi(follow.udiCode, follow.udiName, isFollowed = true)
        }
    }
    remembered.forEach { (communeCode, udi) ->
        val name = udi.name ?: return@forEach
        val units = byCommune.getOrPut(communeCode) { mutableListOf() }
        if (units.none { it.code == udi.code }) {
            units += KnownUdi(udi.code, name, isFollowed = false)
        }
    }
    return byCommune.filterValues { it.size >= 2 }
}

/** A row of the search results. */
sealed interface SearchRow {
    val commune: Commune

    /**
     * A commune. [choose]: it has several known units ([knownUdis]), so the
     * full choice of unit opens rather than one of them taken for the user.
     */
    data class Place(override val commune: Commune, val choose: Boolean) : SearchRow

    /** One known unit of [commune], whose sheet opens directly. */
    data class KnownUnit(override val commune: Commune, val udi: KnownUdi) : SearchRow
}

/**
 * The rows of the results: each commune, and, after the first commune
 * published under a code with several known units, one row per unit. The
 * arrondissements of Paris, Lyon and Marseille share their city's code: its
 * units are listed once, not under each of them.
 */
internal fun searchRows(
    communes: List<Commune>,
    known: Map<String, List<KnownUdi>>,
): List<SearchRow> {
    val listed = mutableSetOf<String>()
    return communes.flatMap { commune ->
        val units = known[commune.queryCode].orEmpty()
        val place = SearchRow.Place(commune, choose = units.isNotEmpty())
        if (units.isEmpty() || !listed.add(commune.queryCode)) {
            listOf(place)
        } else {
            listOf(place) + units.map { SearchRow.KnownUnit(commune, it) }
        }
    }
}

/** Live, offline search of the embedded communes, and "near me" (SPEC §5 F1). */
class SearchViewModel(
    private val communes: CommuneRepository,
    private val locator: Locator,
    follows: FollowRepository,
    private val preferences: AppPreferences,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _nearby = MutableStateFlow<NearbyState>(NearbyState.Idle)
    val nearby: StateFlow<NearbyState> = _nearby.asStateFlow()

    /** Whether the embedded referential has been read; until then a search waits for it. */
    private var referentialLoaded = false

    private var locating: Job? = null

    /**
     * The communes with at least two known units, by [Commune.queryCode]
     * ([knownUdis]): the results offer each of them by name (SPEC §5 F2).
     */
    val knownUdis: StateFlow<Map<String, List<KnownUdi>>> =
        combine(
            follows.follows.catch { emit(emptyList()) },
            preferences.rememberedUdis.catch { emit(emptyMap()) },
            ::knownUdis,
        ).stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * The communes whose unit was chosen among several (only such a choice is
     * remembered, `UdiViewModel`), by the code Hub'Eau publishes them under.
     */
    private val communesWithChoice: StateFlow<Set<String>> = preferences.rememberedUdis
        .map { it.keys }
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * Whether the sheet of a recent unit of [communeCode] offers to change
     * the unit: only for a commune known to have several, without asking
     * Hub'Eau. A commune served by a single unit offers nothing to change.
     */
    fun offersUnitChoice(communeCode: String): Boolean =
        communeCode in communesWithChoice.value || communeCode in knownUdis.value

    /** The units last opened, most recent first, offered while nothing is typed (SPEC §5 F1). */
    val recentUnits: StateFlow<List<RecentUnit>> = preferences.recentUnits
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            // Reading the referential takes seconds on a phone: start as soon
            // as the screen opens rather than at the first keystroke.
            communes.preload()
            referentialLoaded = true
        }
        viewModelScope.launch {
            _query
                .map { it.trim() }
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isEmpty()) {
                        _state.value = SearchUiState.Idle
                        return@collectLatest
                    }
                    // While the referential loads, say so at once. Otherwise keep
                    // the previous results on screen while the next ones are
                    // computed, rather than flashing a progress indicator.
                    if (!referentialLoaded) _state.value = SearchUiState.Loading
                    delay(DEBOUNCE_MILLIS)
                    if (_state.value !is SearchUiState.Results) {
                        _state.value = SearchUiState.Loading
                    }
                    val found = communes.search(query)
                    _state.value = if (found.isEmpty()) {
                        SearchUiState.NoResults(query)
                    } else {
                        SearchUiState.Results(found, parentNames(found))
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        if (query.isNotBlank() && query.trim() != _query.value.trim()) {
            // Typing takes over from "near me".
            locating?.cancel()
            _nearby.value = NearbyState.Idle
        }
        _query.value = query
    }

    /** Call once the coarse location permission is granted. */
    fun locateNearby() {
        locating?.cancel()
        _nearby.value = NearbyState.Locating
        locating = viewModelScope.launch {
            // The position stays in this local value and is dropped as soon as
            // the commune is known: never stored, logged nor sent (SPEC §10).
            val outcome = withTimeoutOrNull(LOCATE_TIMEOUT_MILLIS) { locator.locate() }
            _nearby.value = when (outcome) {
                is LocationOutcome.Position ->
                    communes.nearest(outcome.latitude, outcome.longitude, NEARBY_MAX_DISTANCE_KM)
                        ?.let { NearbyState.Found(it, parentNames(listOf(it))[it.hubEauCode]) }
                        ?: NearbyState.Failed(NearbyFailure.NO_COMMUNE)
                LocationOutcome.PermissionMissing ->
                    NearbyState.Failed(NearbyFailure.PERMISSION_DENIED)
                LocationOutcome.Disabled -> NearbyState.Failed(NearbyFailure.LOCATION_DISABLED)
                LocationOutcome.Unavailable, null -> NearbyState.Failed(NearbyFailure.UNAVAILABLE)
            }
        }
    }

    private suspend fun parentNames(found: List<Commune>): Map<String, String> =
        found.mapNotNull { it.hubEauCode }.distinct().mapNotNull { code ->
            communes.byCode(code)?.let { code to it.name }
        }.toMap()

    /** Remembers [udi], picked from the results, as the choice for [communeCode]. */
    fun rememberChoice(communeCode: String, udi: KnownUdi) {
        viewModelScope.launch {
            try {
                preferences.rememberUdi(communeCode, udi.code, udi.name)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Unwritable local data: the sheet opens all the same.
            }
        }
    }

    /** Takes [unit] off the "recent" list. */
    fun removeRecent(unit: RecentUnit) {
        viewModelScope.launch {
            try {
                preferences.removeRecentUnit(unit.communeCode, unit.udiCode)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Unwritable local data: the entry stays listed.
            }
        }
    }

    fun onLocationPermissionDenied() {
        locating?.cancel()
        _nearby.value = NearbyState.Failed(NearbyFailure.PERMISSION_DENIED)
    }

    companion object {
        const val DEBOUNCE_MILLIS = 150L

        /** Longest wait for a position before giving up. */
        const val LOCATE_TIMEOUT_MILLIS = 15_000L

        /**
         * Farther than this from the closest centroid, the device is taken
         * to be outside France and no commune is proposed.
         */
        const val NEARBY_MAX_DISTANCE_KM = 20.0

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    container.communes,
                    container.locator,
                    container.follows,
                    container.preferences,
                )
            }
        }
    }
}
