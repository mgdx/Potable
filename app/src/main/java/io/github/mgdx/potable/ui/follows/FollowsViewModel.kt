package io.github.mgdx.potable.ui.follows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.potable.AppContainer
import io.github.mgdx.potable.data.follow.Follow
import io.github.mgdx.potable.data.follow.FollowRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface FollowsUiState {
    data object Loading : FollowsUiState

    /**
     * The follows, sorted by name; empty when nothing is followed.
     * [query] filters the list shown ([shown]); it is kept in memory only.
     */
    data class Loaded(val follows: List<Follow>, val query: String = "") : FollowsUiState {
        /** The follows matching [query], all of them when it is blank. */
        val shown: List<Follow> = filterFollows(follows, query)
    }
}

/**
 * "My communes" (SPEC §5 F6): the followed units, in alphabetical order
 * ([FollowRepository.follows]), filtered by a query and unfollowed after a
 * confirmation.
 */
class FollowsViewModel(private val repository: FollowRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<FollowsUiState> = combine(repository.follows, query) { follows, query ->
        FollowsUiState.Loaded(follows, query)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FollowsUiState.Loading)

    private val _pendingRemoval = MutableStateFlow<Follow?>(null)

    /** The follow whose removal awaits confirmation, if any. */
    val pendingRemoval: StateFlow<Follow?> = _pendingRemoval.asStateFlow()

    /** Filters the list by commune or unit name; not saved anywhere. */
    fun setQuery(value: String) {
        query.value = value
    }

    fun requestRemoval(follow: Follow) {
        _pendingRemoval.value = follow
    }

    fun cancelRemoval() {
        _pendingRemoval.value = null
    }

    fun confirmRemoval() {
        val follow = _pendingRemoval.value ?: return
        _pendingRemoval.value = null
        viewModelScope.launch { repository.unfollow(follow.id) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { FollowsViewModel(container.follows) }
        }
    }
}
