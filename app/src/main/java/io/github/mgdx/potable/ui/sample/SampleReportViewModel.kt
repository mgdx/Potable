package io.github.mgdx.potable.ui.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.potable.AppContainer
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.data.SampleRepository
import io.github.mgdx.potable.ui.common.LoadError
import io.github.mgdx.potable.ui.common.SLOW_LOAD_THRESHOLD
import io.github.mgdx.potable.ui.common.slowLoading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * The full report of one sample of a unit, whatever its age, opened from the
 * history of a parameter (SPEC §5 F4, F5): loaded by its `code_prelevement`
 * (from the cache, or Hub'Eau), then shown like the sheet's sample; its
 * results, with the text filter, are [SampleReportResultsViewModel]'s, which
 * reads this one's [state] and loads nothing.
 * [SampleDetailUiState.Unavailable] means that Hub'Eau publishes no row of
 * it. A failed load waits for Retry: there is no automatic retry.
 */
class SampleReportViewModel(
    private val samples: SampleRepository,
    private val communeCode: String,
    private val udiCode: String,
    private val sampleCode: String,
    slowThreshold: Duration = SLOW_LOAD_THRESHOLD,
) : ViewModel() {
    /** What was loaded: null while loading. */
    private val loaded = MutableStateFlow<Loaded?>(null)

    /** The sample, its results by family, unfiltered. */
    val state: StateFlow<SampleDetailUiState> = loaded.map(::toState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SampleDetailUiState.Loading)

    /** True when the loading state has lasted [slowThreshold]: Hub'Eau is slow. */
    val isSlow: StateFlow<Boolean> =
        state.slowLoading(viewModelScope, slowThreshold) { it == SampleDetailUiState.Loading }

    private var loadJob: Job? = null

    init {
        load()
    }

    /** Called by the Retry button only. */
    fun retry() {
        if (loaded.value is Loaded.Failed) load()
    }

    private fun load() {
        loadJob?.cancel()
        loaded.value = null
        loadJob = viewModelScope.launch {
            loaded.value = try {
                samples.sample(communeCode, udiCode, sampleCode)
                    ?.let { Loaded.Found(it) }
                    ?: Loaded.NotFound
            } catch (e: DataError) {
                Loaded.Failed(LoadError.from(e))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Never crash on a bug: the user is told, and can retry.
                Loaded.Failed(LoadError.UNEXPECTED)
            }
        }
    }

    private fun toState(result: Loaded?): SampleDetailUiState = when (result) {
        null -> SampleDetailUiState.Loading
        is Loaded.Failed -> SampleDetailUiState.Error(result.error)
        Loaded.NotFound -> SampleDetailUiState.Unavailable
        is Loaded.Found -> SampleDetailUiState.Loaded(
            sample = result.sample,
            groups = groupByFamily(result.sample.results),
        )
    }

    private sealed interface Loaded {
        data class Found(val sample: Sample) : Loaded

        data object NotFound : Loaded

        data class Failed(val error: LoadError) : Loaded
    }

    companion object {
        fun factory(
            container: AppContainer,
            communeCode: String,
            udiCode: String,
            sampleCode: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SampleReportViewModel(container.samples, communeCode, udiCode, sampleCode)
            }
        }
    }
}

/**
 * Every result of the sample of a report, by family, with a text filter
 * (SPEC §5 F4). Nothing is loaded: [report] is the state of the report's
 * [SampleReportViewModel], scoped to the report's back stack entry, which
 * stays below this screen; this screen follows it (a retry, a reload after a
 * process death).
 */
class SampleReportResultsViewModel(report: StateFlow<SampleDetailUiState>) : ViewModel() {
    private val _query = MutableStateFlow("")

    /** What the user typed, as typed. */
    val query: StateFlow<String> = _query.asStateFlow()

    val state: StateFlow<SampleDetailUiState> = combine(report, _query, ::toState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, toState(report.value, _query.value))

    fun onQueryChange(value: String) {
        _query.value = value
    }

    private fun toState(report: SampleDetailUiState, query: String): SampleDetailUiState =
        when (report) {
            is SampleDetailUiState.Loaded -> report.copy(
                groups = groupByFamily(filterResults(report.sample.results, query)),
            )
            else -> report
        }

    companion object {
        fun factory(report: StateFlow<SampleDetailUiState>): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SampleReportResultsViewModel(report) }
            }
    }
}
