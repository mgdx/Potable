package io.github.mgdx.potable.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.potable.AppContainer
import io.github.mgdx.potable.core.model.Commune
import io.github.mgdx.potable.data.CommuneRepository
import io.github.mgdx.potable.location.LocationOutcome
import io.github.mgdx.potable.location.Locator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

/** Live, offline search of the embedded communes, and "near me" (SPEC §5 F1). */
class SearchViewModel(private val communes: CommuneRepository, private val locator: Locator) :
    ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _nearby = MutableStateFlow<NearbyState>(NearbyState.Idle)
    val nearby: StateFlow<NearbyState> = _nearby.asStateFlow()

    /** Whether the embedded referential has been read; until then a search waits for it. */
    private var referentialLoaded = false

    private var locating: Job? = null

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
            initializer { SearchViewModel(container.communes, container.locator) }
        }
    }
}
