package io.github.mgdx.potable.ui.udi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.potable.AppContainer
import io.github.mgdx.potable.core.model.Udi
import io.github.mgdx.potable.core.model.UdiChoice
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.data.WaterRepository
import io.github.mgdx.potable.data.follow.FollowRepository
import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.ui.common.LoadError
import io.github.mgdx.potable.ui.common.SLOW_LOAD_THRESHOLD
import io.github.mgdx.potable.ui.common.slowLoading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration

sealed interface UdiUiState {
    data object Loading : UdiUiState

    data class Error(val error: LoadError) : UdiUiState

    /** Hub'Eau lists no distribution unit for this commune. */
    data object None : UdiUiState

    /** A single unit: the screen goes straight on to the sheet. */
    data class Resolved(val udi: Udi) : UdiUiState

    /**
     * Several units, one of which is followed, was chosen last time and is
     * still listed, or has just been picked and remembered: the screen goes
     * on to its sheet in its place, and the sheet offers to change the unit.
     */
    data class Remembered(val udi: Udi) : UdiUiState

    /**
     * Several units: the user picks the one serving their area. [currentCode]
     * is the unit followed or chosen last time, marked in the list.
     */
    data class Choice(val udis: List<Udi>, val currentCode: String? = null) : UdiUiState
}

/**
 * Resolves the distribution unit(s) of a commune (SPEC §5 F2). With several,
 * the followed unit, else the one chosen last time, is taken without asking,
 * unless [forceChoice] (the user asked to change).
 */
class UdiViewModel(
    private val water: WaterRepository,
    private val preferences: AppPreferences,
    private val follows: FollowRepository,
    private val communeCode: String,
    private val forceChoice: Boolean = false,
    slowThreshold: Duration = SLOW_LOAD_THRESHOLD,
) : ViewModel() {
    private val _state = MutableStateFlow<UdiUiState>(UdiUiState.Loading)
    val state: StateFlow<UdiUiState> = _state.asStateFlow()

    /** True when the loading state has lasted [slowThreshold]: Hub'Eau is slow. */
    val isSlow: StateFlow<Boolean> =
        state.slowLoading(viewModelScope, slowThreshold) { it == UdiUiState.Loading }

    private val _query = MutableStateFlow("")

    /** Whether a picked unit is being written ([select]). */
    private var selecting = false

    /** What the user typed in the filter of a long choice ([showsUdiFilter]). */
    val query: StateFlow<String> = _query.asStateFlow()

    init {
        load()
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** Called by the Retry button only: there is no automatic retry. */
    fun retry() {
        load()
    }

    private fun load() {
        _state.value = UdiUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                when (val choice = water.udis(communeCode)) {
                    UdiChoice.None -> UdiUiState.None
                    is UdiChoice.Single -> UdiUiState.Resolved(choice.udi)
                    is UdiChoice.Multiple -> multiple(choice.udis)
                }
            } catch (e: DataError) {
                UdiUiState.Error(LoadError.from(e))
            }
        }
    }

    /**
     * Remembers the unit the user picked, for their next visit, then moves to
     * [UdiUiState.Remembered]: the screen leaves only once the choice is
     * written, since leaving clears this ViewModel and would cancel the write.
     */
    fun select(udi: Udi) {
        // A second tap while the first choice is written is ignored.
        if (_state.value !is UdiUiState.Choice || selecting) return
        selecting = true
        viewModelScope.launch {
            try {
                preferences.rememberUdi(communeCode, udi.code, udi.name)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Unwritable local data: the sheet still opens, the choice
                // will simply be offered again next time.
            }
            _state.value = UdiUiState.Remembered(udi)
        }
    }

    private suspend fun multiple(udis: List<Udi>): UdiUiState {
        val current = currentUdi(udis.map { it.code }.toSet())
        val remembered = udis.firstOrNull { it.code == current }
        return if (remembered != null && !forceChoice) {
            UdiUiState.Remembered(remembered)
        } else {
            UdiUiState.Choice(udis, current)
        }
    }

    /**
     * A followed unit wins over the one chosen last time; a unit that is no
     * longer listed is ignored, and the choice is offered again.
     */
    private suspend fun currentUdi(codes: Set<String>): String? = try {
        // The first in the user's order, should several units of the commune be followed.
        val followed = follows.follows.first()
            .firstOrNull { it.communeCode == communeCode && it.udiCode in codes }
        followed?.udiCode ?: preferences.rememberedUdi(communeCode)?.takeIf { it in codes }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Unreadable local data: the choice is simply offered.
        null
    }

    companion object {
        fun factory(
            container: AppContainer,
            communeCode: String,
            forceChoice: Boolean,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                UdiViewModel(
                    container.water,
                    container.preferences,
                    container.follows,
                    communeCode,
                    forceChoice,
                )
            }
        }
    }
}
