package io.github.mgdx.potable.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.potable.AppContainer
import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.SampleSummary
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import kotlin.time.Duration

/** One of the four compliances published with each sample. */
enum class ComplianceKind {
    BACTERIOLOGICAL_LIMITS,
    PHYSICOCHEMICAL_LIMITS,
    BACTERIOLOGICAL_REFERENCES,
    PHYSICOCHEMICAL_REFERENCES,
}

/**
 * A sample of the timeline, with the compliances published as
 * [Compliance.NON_COMPLIANT] for it, in a fixed order. Nothing is inferred:
 * the list only restitutes the published codes (SPEC §14).
 */
data class TimelineEntry(val sample: SampleSummary, val nonCompliances: List<ComplianceKind>) {
    companion object {
        fun of(sample: SampleSummary): TimelineEntry {
            val compliances = listOf(
                ComplianceKind.BACTERIOLOGICAL_LIMITS to sample.bacteriologicalLimits,
                ComplianceKind.PHYSICOCHEMICAL_LIMITS to sample.physicochemicalLimits,
                ComplianceKind.BACTERIOLOGICAL_REFERENCES to sample.bacteriologicalReferences,
                ComplianceKind.PHYSICOCHEMICAL_REFERENCES to sample.physicochemicalReferences,
            )
            val nonCompliances = compliances
                .filter { (_, compliance) -> compliance == Compliance.NON_COMPLIANT }
                .map { (kind, _) -> kind }
            return TimelineEntry(sample, nonCompliances)
        }
    }
}

sealed interface TimelineUiState {
    data object Loading : TimelineUiState

    data class Error(val error: LoadError) : TimelineUiState

    /** Hub'Eau has no sample for this unit since [since]. */
    data class Empty(val since: LocalDate) : TimelineUiState

    /** The samples since [since], newest first. */
    data class Loaded(val since: LocalDate, val entries: List<TimelineEntry>) : TimelineUiState
}

/**
 * The timeline of the conclusions of a unit's samples (SPEC §5 F5), over a
 * window of 1, 3, 5 or 10 years counted back from today in France. The
 * window starts at the history setting and can be changed on the screen
 * without changing the setting.
 *
 * Pull-to-refresh forces the request, for any unit (its history is cached,
 * SPEC §7); so does Retry. There is no automatic retry.
 */
class TimelineViewModel(
    private val history: HistoryRepository,
    private val preferences: AppPreferences,
    private val communeCode: String,
    private val udiCode: String,
    private val clock: Clock,
    slowThreshold: Duration = SLOW_LOAD_THRESHOLD,
) : ViewModel() {
    private val _state = MutableStateFlow<TimelineUiState>(TimelineUiState.Loading)
    val state: StateFlow<TimelineUiState> = _state.asStateFlow()

    /** True when the loading state has lasted [slowThreshold]: Hub'Eau is slow. */
    val isSlow: StateFlow<Boolean> =
        state.slowLoading(viewModelScope, slowThreshold) { it == TimelineUiState.Loading }

    private val _years = MutableStateFlow<Int?>(null)

    /** The window, in years; null until the setting is read. */
    val years: StateFlow<Int?> = _years.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)

    /**
     * True during a pull-to-refresh of data on screen. Never while the
     * loading state shows: one indicator at a time.
     */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshFailed = MutableStateFlow<LoadError?>(null)

    /** Why the latest pull-to-refresh failed, the data shown being kept; null otherwise. */
    val refreshFailed: StateFlow<LoadError?> = _refreshFailed.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            val initial = try {
                preferences.historyYears.first()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                AppPreferences.DEFAULT_YEARS
            }
            // The user may have picked a window while the setting was read.
            if (_years.value == null) {
                _years.value = initial
                load(force = false)
            }
        }
    }

    /** Changes the window shown, not the setting. */
    fun selectYears(years: Int) {
        if (years !in AppPreferences.YEAR_CHOICES || years == _years.value) return
        _years.value = years
        load(force = false)
    }

    /** Called by the Retry button only: asks Hub'Eau again. */
    fun retry() {
        if (_years.value == null) return
        load(force = true)
    }

    /** Pull-to-refresh: asks Hub'Eau again, even if the cache is from today. */
    fun refresh() {
        // A request already running answers this pull too: restarting it
        // would download the same window twice.
        if (loadJob?.isActive == true) return
        val shown = _state.value is TimelineUiState.Loaded || _state.value is TimelineUiState.Empty
        // Nothing is shown: the loading state is the indicator.
        if (!shown) return retry()
        val years = _years.value ?: return
        _isRefreshing.value = true
        loadJob = viewModelScope.launch {
            try {
                _state.value = fetch(years, force = true)
                _refreshFailed.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _refreshFailed.value = e.toLoadError()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun load(force: Boolean) {
        val years = _years.value ?: return
        loadJob?.cancel()
        _isRefreshing.value = false
        _refreshFailed.value = null
        _state.value = TimelineUiState.Loading
        loadJob = viewModelScope.launch {
            _state.value = try {
                fetch(years, force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TimelineUiState.Error(e.toLoadError())
            }
        }
    }

    private suspend fun fetch(years: Int, force: Boolean): TimelineUiState {
        val since = clock.instant().atZone(FRANCE_ZONE).toLocalDate().minusYears(years.toLong())
        val samples = history.sampleTimeline(communeCode, udiCode, since, force)
        return if (samples.isEmpty()) {
            TimelineUiState.Empty(since)
        } else {
            TimelineUiState.Loaded(
                since,
                samples.sortedByDescending { it.date }.map(TimelineEntry::of),
            )
        }
    }

    /** Never crash on a bug: the user is told, and can retry. */
    private fun Exception.toLoadError(): LoadError =
        if (this is DataError) LoadError.from(this) else LoadError.UNEXPECTED

    companion object {
        fun factory(
            container: AppContainer,
            communeCode: String,
            udiCode: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TimelineViewModel(
                    container.history,
                    container.preferences,
                    communeCode,
                    udiCode,
                    Clock.systemUTC(),
                )
            }
        }
    }
}
