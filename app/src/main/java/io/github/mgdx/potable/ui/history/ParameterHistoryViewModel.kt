package io.github.mgdx.potable.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.potable.AppContainer
import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.data.HistoryRepository
import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.ui.common.LoadError
import io.github.mgdx.potable.ui.common.SLOW_LOAD_THRESHOLD
import io.github.mgdx.potable.ui.common.slowLoading
import io.github.mgdx.potable.ui.sheet.FRANCE_ZONE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import kotlin.time.Duration

sealed interface HistoryUiState {
    data object Loading : HistoryUiState

    data class Error(val error: LoadError) : HistoryUiState

    /** Hub'Eau has no measure of this parameter over the window. */
    data object Empty : HistoryUiState

    /** At least one measure; [singleMeasure] when there is exactly one, so no chart. */
    data class Loaded(val series: ParameterSeries) : HistoryUiState {
        val singleMeasure: Boolean get() = series.points.size == 1
    }
}

/**
 * The history of one parameter of a unit (SPEC §5 F5) over a window of
 * [AppPreferences.YEAR_CHOICES] years, ending today in France. The window
 * starts at [AppPreferences.historyYears]; changing it here does not change
 * the setting. Refreshing asks Hub'Eau again (`force`); the data shown stay
 * on screen if that fails.
 */
class ParameterHistoryViewModel(
    private val history: HistoryRepository,
    preferences: AppPreferences,
    private val communeCode: String,
    private val udiCode: String,
    private val parameterCodes: Set<String>,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
    slowThreshold: Duration = SLOW_LOAD_THRESHOLD,
) : ViewModel() {
    // The window picked on this screen outlives the death of the process;
    // otherwise the screen would come back on the setting while a zoom of the
    // chart, saved by the UI, still spans the old window.
    private val _years = MutableStateFlow(
        savedState.get<Int>(YEARS_KEY)?.takeIf { it in AppPreferences.YEAR_CHOICES },
    )

    /** The window, in years; null until the setting is read. */
    val years: StateFlow<Int?> = _years.asStateFlow()

    private val _state = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)

    /** True while a refresh asked by the user runs over data already shown. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshError = MutableStateFlow<LoadError?>(null)

    /** Why the last refresh failed while data stayed shown; null otherwise. */
    val refreshError: StateFlow<LoadError?> = _refreshError.asStateFlow()

    /** True when the loading state has lasted [slowThreshold]: Hub'Eau is slow. */
    val isSlow: StateFlow<Boolean> =
        state.slowLoading(viewModelScope, slowThreshold) { it == HistoryUiState.Loading }

    private var loadJob: Job? = null

    init {
        if (_years.value != null) {
            load(force = false)
        } else {
            viewModelScope.launch {
                val setting = try {
                    preferences.historyYears.first()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    AppPreferences.DEFAULT_YEARS
                }
                if (_years.value == null) {
                    _years.value = setting.takeIf { it in AppPreferences.YEAR_CHOICES }
                        ?: AppPreferences.DEFAULT_YEARS
                    load(force = false)
                }
            }
        }
    }

    /** Shows the last [years] years; ignored outside [AppPreferences.YEAR_CHOICES]. */
    fun selectYears(years: Int) {
        if (years !in AppPreferences.YEAR_CHOICES || years == _years.value) return
        _years.value = years
        savedState[YEARS_KEY] = years
        load(force = false)
    }

    /** Called by the Retry button only: there is no automatic retry. */
    fun retry() {
        if (_years.value == null) return
        load(force = false)
    }

    /**
     * Pull-to-refresh or the Refresh action: asks Hub'Eau again, unless a
     * request is already running (a second pull, or the first load): it
     * would be cancelled on screen but still downloaded, then asked again.
     */
    fun refresh() {
        if (_years.value == null || loadJob?.isActive == true) return
        load(force = true)
    }

    private fun load(force: Boolean) {
        val years = _years.value ?: return
        loadJob?.cancel()
        val shown = _state.value.let { it is HistoryUiState.Loaded || it is HistoryUiState.Empty }
        // A new window replaces what is shown; a refresh keeps it on screen.
        val keepShown = force && shown
        _isRefreshing.value = keepShown
        if (!keepShown) _state.value = HistoryUiState.Loading
        _refreshError.value = null
        val since = today().minusYears(years.toLong())
        loadJob = viewModelScope.launch {
            val error = try {
                val series = history.parameterSeries(
                    communeCode,
                    udiCode,
                    parameterCodes,
                    since,
                    force,
                )
                _state.value = if (series == null || series.points.isEmpty()) {
                    HistoryUiState.Empty
                } else {
                    HistoryUiState.Loaded(series)
                }
                null
            } catch (e: DataError) {
                LoadError.from(e)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Never crash on a bug: the user is told, and can retry.
                LoadError.UNEXPECTED
            }
            when {
                error == null -> Unit
                keepShown -> _refreshError.value = error
                else -> _state.value = HistoryUiState.Error(error)
            }
            _isRefreshing.value = false
        }
    }

    private fun today(): LocalDate = clock.instant().atZone(FRANCE_ZONE).toLocalDate()

    companion object {
        private const val YEARS_KEY = "years"

        fun factory(
            container: AppContainer,
            communeCode: String,
            udiCode: String,
            parameterCodes: Set<String>,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ParameterHistoryViewModel(
                    container.history,
                    container.preferences,
                    communeCode,
                    udiCode,
                    parameterCodes,
                    Clock.systemUTC(),
                    createSavedStateHandle(),
                )
            }
        }
    }
}
