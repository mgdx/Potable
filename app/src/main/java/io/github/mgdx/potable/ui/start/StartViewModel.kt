package io.github.mgdx.potable.ui.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.potable.AppContainer
import io.github.mgdx.potable.data.follow.Follow
import io.github.mgdx.potable.data.follow.FollowRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface StartDestination {
    /** The follows are not read yet: nothing is shown, rather than the wrong screen. */
    data object Unknown : StartDestination

    /** Nothing is followed: the application opens on the search. */
    data object Search : StartDestination

    /** The application opens on the sheet of the primary follow (SPEC §5 F6). */
    data class Sheet(val follow: Follow) : StartDestination
}

/**
 * Decides, once per process, where the application opens. The decision is
 * never revised afterwards: the navigation graph is built from it.
 */
class StartViewModel(private val follows: FollowRepository) : ViewModel() {
    private val _destination = MutableStateFlow<StartDestination>(StartDestination.Unknown)
    val destination: StateFlow<StartDestination> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            _destination.value = try {
                val all = follows.follows.first()
                val primary = all.firstOrNull { it.isPrimary } ?: all.firstOrNull()
                if (primary == null) StartDestination.Search else StartDestination.Sheet(primary)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // An unreadable database must not keep the application blank.
                StartDestination.Search
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { StartViewModel(container.follows) }
        }
    }
}
