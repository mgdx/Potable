package io.github.mgdx.potable.ui.disclaimer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.potable.AppContainer
import io.github.mgdx.potable.data.preferences.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DisclaimerState {
    /** The preference is not read yet: nothing is shown, rather than the wrong screen. */
    UNKNOWN,

    /** The first-launch notice must be acknowledged before the search. */
    REQUIRED,

    ACCEPTED,
}

/** Shows the SPEC §14 notice until the user acknowledges it, once. */
class DisclaimerViewModel(private val preferences: AppPreferences) : ViewModel() {
    val state: StateFlow<DisclaimerState> = preferences.disclaimerAccepted
        .map { accepted -> if (accepted) DisclaimerState.ACCEPTED else DisclaimerState.REQUIRED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DisclaimerState.UNKNOWN)

    fun accept() {
        viewModelScope.launch { preferences.acceptDisclaimer() }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DisclaimerViewModel(container.preferences) }
        }
    }
}
