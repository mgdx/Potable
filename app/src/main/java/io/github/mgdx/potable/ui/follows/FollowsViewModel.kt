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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface FollowsUiState {
    data object Loading : FollowsUiState

    /** The follows, in the user's order; empty when nothing is followed. */
    data class Loaded(val follows: List<Follow>) : FollowsUiState
}

/**
 * "My communes" (SPEC §5 F6): the followed units, reordered with buttons
 * rather than drag and drop, so that every action is reachable by keyboard
 * and screen reader.
 */
class FollowsViewModel(private val repository: FollowRepository) : ViewModel() {
    val state: StateFlow<FollowsUiState> = repository.follows
        .map { FollowsUiState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FollowsUiState.Loading)

    private val _pendingRemoval = MutableStateFlow<Follow?>(null)

    /** The follow whose removal awaits confirmation, if any. */
    val pendingRemoval: StateFlow<Follow?> = _pendingRemoval.asStateFlow()

    fun moveUp(follow: Follow) = moveBy(follow, -1)

    fun moveDown(follow: Follow) = moveBy(follow, 1)

    fun setPrimary(follow: Follow) {
        viewModelScope.launch { repository.setPrimary(follow.id) }
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

    /** Moves by index in the list shown, which is what the user sees. */
    private fun moveBy(follow: Follow, delta: Int) {
        val follows = (state.value as? FollowsUiState.Loaded)?.follows ?: return
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
