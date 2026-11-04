package io.github.mgdx.potable.ui.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.potable.core.PesticideCount
import io.github.mgdx.potable.core.PesticideCounts
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.ui.common.LoadError
import io.github.mgdx.potable.ui.sheet.SheetUiState
import io.github.mgdx.potable.ui.sheet.distinctSampleTimes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime

sealed interface SampleDetailUiState {
    /** The sheet is (re)loading its sample, after a retry or a process death. */
    data object Loading : SampleDetailUiState

    /** The sheet failed to load: its error, with the sheet's Retry. */
    data class Error(val error: LoadError) : SampleDetailUiState

    /** The sheet now shows no sample, or another one than this screen's. */
    data object Unavailable : SampleDetailUiState

    /**
     * [groups] are the results of [sample] that match the query, by family:
     * empty with results in [sample] means that nothing matches. [time] is its
     * published time when it tells it apart from the other samples of its day
     * shown with it ([distinctSampleTimes]), null otherwise.
     */
    data class Loaded(
        val sample: Sample,
        val groups: List<FamilyGroup>,
        val time: LocalTime? = distinctSampleTimes(listOf(sample.date)).single(),
    ) : SampleDetailUiState {
        /** The pesticides of the whole sample, whatever the query; null without any. */
        val pesticides: PesticideCount? = PesticideCounts.of(sample.results)
    }
}

/**
 * Every result of a sample, by family, with a text filter (SPEC §5 F4).
 *
 * The sample is not loaded again: it is the one the sheet already holds.
 * [sheetState] is the state of the sheet's [io.github.mgdx.potable.ui.sheet.SheetViewModel],
 * scoped to the sheet's back stack entry, which stays below this screen. After
 * a process death, the sheet loads again and this screen follows it.
 */
class SampleDetailViewModel(private val sampleCode: String, sheetState: StateFlow<SheetUiState>) :
    ViewModel() {
    private val _query = MutableStateFlow("")

    /** What the user typed, as typed. */
    val query: StateFlow<String> = _query.asStateFlow()

    val state: StateFlow<SampleDetailUiState> = combine(sheetState, _query, ::toState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, toState(sheetState.value, _query.value))

    fun onQueryChange(value: String) {
        _query.value = value
    }

    private fun toState(sheet: SheetUiState, query: String): SampleDetailUiState = when (sheet) {
        SheetUiState.Loading -> SampleDetailUiState.Loading
        is SheetUiState.Error -> SampleDetailUiState.Error(sheet.error)
        SheetUiState.NoSample -> SampleDetailUiState.Unavailable
        // Any of the samples of the day shown on the sheet, found by its code.
        is SheetUiState.Loaded -> {
            val index = sheet.samples.indexOfFirst { it.code == sampleCode }
            if (index < 0) {
                SampleDetailUiState.Unavailable
            } else {
                val sample = sheet.samples[index]
                SampleDetailUiState.Loaded(
                    sample = sample,
                    groups = groupByFamily(filterResults(sample.results, query)),
                    time = distinctSampleTimes(sheet.samples.map { it.date })[index],
                )
            }
        }
    }

    companion object {
        fun factory(
            sampleCode: String,
            sheetState: StateFlow<SheetUiState>,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { SampleDetailViewModel(sampleCode, sheetState) }
        }
    }
}
