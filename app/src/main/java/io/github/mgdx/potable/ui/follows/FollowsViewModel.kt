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
     * The follows, in the user's order; empty when nothing is followed.
     * [query] filters the list shown ([shown]); it is kept in memory only.
     */
    data class Loaded(val follows: List<Follow>, val query: String = "") : FollowsUiState {
        /** The follows matching [query], all of them when it is blank. */
        val shown: List<Follow> = filterFollows(follows, query)

        /** True while a query narrows the list: the order cannot be changed then. */
        val isFiltered: Boolean get() = query.isNotBlank()
    }
}

/**
 * "My communes" (SPEC §5 F6): the followed units, reordered with buttons
 * rather than drag and drop, so that every action is reachable by keyboard
 * and screen reader.
 */
class FollowsViewModel(private val repository: FollowRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<FollowsUiState> = combine(repository.follows, query) { follows, query ->
        FollowsUiState.Loaded(follows, query)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FollowsUiState.Loading)

    private val _pendingRemoval = MutableStateFlow<Follow?>(null)

    /** The follow whose removal awaits confirmation, if any. */
    val pendingRemoval: StateFlow<Follow?> = _pendingRemoval.asStateFlow()

    fun moveUp(follow: Follow) = moveBy(follow, -1)

    fun moveDown(follow: Follow) = moveBy(follow, 1)

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

    /**
     * Moves by index in the full list, which is the one shown: the buttons
     * are hidden while a query filters it.
     */
    private fun moveBy(follow: Follow, delta: Int) {
        val loaded = state.value as? FollowsUiState.Loaded ?: return
        if (loaded.isFiltered) return
        val follows = loaded.follows
        val index = follows.indexOfFirst { it.id == follow.id }
        if (index < 0) return
        val target = index + delta
        if (target !in follows.indices) return
        viewModelScope.launch { repository.move(follow.id, target) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { FollowsViewModel(container.follows) }
        }
    }
}
