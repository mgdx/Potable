package io.github.mgdx.potable.ui.substances

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import io.github.mgdx.potable.AppContainer
import io.github.mgdx.potable.core.SubstanceSearch
import io.github.mgdx.potable.core.SubstanceSearchResult
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.data.SubstanceSearchRepository
import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.ui.common.LoadError
import io.github.mgdx.potable.ui.common.SLOW_LOAD_THRESHOLD
import io.github.mgdx.potable.ui.common.slowLoading
import io.github.mgdx.potable.ui.navigation.SubstanceSearchRoute
import io.github.mgdx.potable.ui.sheet.FRANCE_ZONE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

sealed interface SubstanceSearchUiState {
    /** Nothing searched: the query is shorter than [SubstanceSearch.MIN_QUERY_LENGTH]. */
    data object Idle : SubstanceSearchUiState

    data class Loading(val query: String) : SubstanceSearchUiState

    data class Error(val query: String, val error: LoadError) : SubstanceSearchUiState

    /** No parameter whose label holds [query] was measured since [since]. */
    data class Empty(val query: String, val since: LocalDate) : SubstanceSearchUiState

    data class Loaded(val query: String, val since: LocalDate, val result: SubstanceSearchResult) :
        SubstanceSearchUiState
}

/**
 * Search of a unit's history by parameter label (SPEC §3: "chercher un
 * pesticide donné"), over the window of the history setting counted back from
 * today in France.
 *
 * A query of at least [SubstanceSearch.MIN_QUERY_LENGTH] characters is sent
 * once typing pauses for [debounce], or at once by the keyboard's search key
 * ([submit]). A new query cancels the request still running for the previous
 * one. Nothing is cached: Retry asks Hub'Eau again.
 */
class SubstanceSearchViewModel(
    private val repository: SubstanceSearchRepository,
    private val preferences: AppPreferences,
    private val communeCode: String,
    private val udiCode: String,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
    private val debounce: Duration = SEARCH_DEBOUNCE,
    slowThreshold: Duration = SLOW_LOAD_THRESHOLD,
) : ViewModel() {
    // The text typed outlives the death of the process; its results are asked again.
    private val _query = MutableStateFlow(savedState.get<String>(QUERY_KEY).orEmpty())

    /** The text of the field, as typed. */
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SubstanceSearchUiState>(SubstanceSearchUiState.Idle)
    val state: StateFlow<SubstanceSearchUiState> = _state.asStateFlow()

    /** True when the loading state has lasted [slowThreshold]: Hub'Eau is slow. */
    val isSlow: StateFlow<Boolean> =
        state.slowLoading(viewModelScope, slowThreshold) { it is SubstanceSearchUiState.Loading }

    private var searchJob: Job? = null

    /** The normalized query of [searchJob], waiting or running; null when there is none. */
    private var pendingQuery: String? = null

    init {
        val restored = SubstanceSearch.normalize(_query.value)
        if (SubstanceSearch.isSearchable(restored)) launchSearch(restored, wait = false)
    }

    /** The field changed: the search runs once typing pauses. */
    fun onQueryChange(text: String) {
        val typed = text.take(MAX_INPUT_LENGTH)
        _query.value = typed
        savedState[QUERY_KEY] = typed
        val words = SubstanceSearch.normalize(typed)
        // A space more or less: the same search, left to run.
        if (words == pendingQuery) return
        if (!SubstanceSearch.isSearchable(words)) {
            searchJob?.cancel()
            pendingQuery = null
            _state.value = SubstanceSearchUiState.Idle
            return
        }
        launchSearch(words, wait = true)
    }

    /** The keyboard's search key: the search runs at once, without waiting for the pause. */
    fun submit() {
        val words = SubstanceSearch.normalize(_query.value)
        if (!SubstanceSearch.isSearchable(words)) return
        val running = searchJob?.isActive == true &&
            _state.value.let {
                it is SubstanceSearchUiState.Loading && it.query == words
            }
        val done = searchJob?.isActive != true &&
            words == pendingQuery &&
            _state.value !is SubstanceSearchUiState.Error
        if (running || done) return
        launchSearch(words, wait = false)
    }

    /** Called by the Retry button only: asks Hub'Eau again. */
    fun retry() {
        val failed = _state.value as? SubstanceSearchUiState.Error ?: return
        launchSearch(failed.query, wait = false)
    }

    private fun launchSearch(words: String, wait: Boolean) {
        searchJob?.cancel()
        pendingQuery = words
        searchJob = viewModelScope.launch {
            if (wait) delay(debounce)
            _state.value = SubstanceSearchUiState.Loading(words)
            _state.value = try {
                val since = since()
                val result = repository.search(communeCode, udiCode, words, since)
                if (result.matches.isEmpty()) {
                    SubstanceSearchUiState.Empty(words, since)
                } else {
                    SubstanceSearchUiState.Loaded(words, since, result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DataError) {
                SubstanceSearchUiState.Error(words, LoadError.from(e))
            } catch (_: Exception) {
                // Never crash on a bug: the user is told, and can retry.
                SubstanceSearchUiState.Error(words, LoadError.UNEXPECTED)
            }
        }
    }

    /** Start of the window: the history setting, counted back from today in France. */
    private suspend fun since(): LocalDate {
        val years = try {
            preferences.historyYears.first()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppPreferences.DEFAULT_YEARS
        }
        return clock.instant().atZone(FRANCE_ZONE).toLocalDate().minusYears(years.toLong())
    }

    companion object {
        /** Pause in typing after which the query is sent. */
        val SEARCH_DEBOUNCE: Duration = 600.milliseconds

        /** Longest text kept: the query must fit in a URL of 2 083 characters. */
        const val MAX_INPUT_LENGTH = 100

        private const val QUERY_KEY = "query"

        /**
         * Reads the unit from the [SubstanceSearchRoute] of the back stack
         * entry the ViewModel is created for.
         */
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val savedState = createSavedStateHandle()
                val route = savedState.toRoute<SubstanceSearchRoute>()
                SubstanceSearchViewModel(
                    container.substances,
                    container.preferences,
                    route.communeCode,
                    route.udiCode,
                    Clock.systemUTC(),
                    savedState,
                )
            }
        }
    }
}
