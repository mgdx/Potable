package io.github.mgdx.potable.ui.follows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.potable.AppContainer
import io.github.mgdx.potable.data.CommuneRepository
import io.github.mgdx.potable.data.follow.Follow
import io.github.mgdx.potable.data.follow.FollowRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface FollowsUiState {
    data object Loading : FollowsUiState

    /**
     * The follows, sorted by name; empty when nothing is followed.
     * [query] filters the list shown ([shown]); it is kept in memory only.
     * [postalCodes] maps a commune code to its postal codes, as far as the
     * referential has been read; a commune missing from it has none shown.
     */
    data class Loaded(
        val follows: List<Follow>,
        val query: String = "",
        val postalCodes: Map<String, List<String>> = emptyMap(),
    ) : FollowsUiState {
        /** The follows matching [query], all of them when it is blank. */
        val shown: List<Follow> = filterFollows(follows, query, postalCodes)
    }
}

/**
 * "My communes" (SPEC §5 F6): the followed units, in alphabetical order
 * ([FollowRepository.follows]), filtered by a query and unfollowed after a
 * confirmation. The postal codes come from the embedded referential
 * ([CommuneRepository.byCode]); the list does not wait for it.
 */
class FollowsViewModel(
    private val repository: FollowRepository,
    private val communes: CommuneRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val postalCodes = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    /** Commune codes already looked up in the referential, found or not. */
    private val lookedUp = mutableSetOf<String>()

    val state: StateFlow<FollowsUiState> = combine(
        repository.follows.onEach(::lookUpPostalCodes),
        query,
        postalCodes,
    ) { follows, query, postalCodes ->
        FollowsUiState.Loaded(follows, query, postalCodes)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FollowsUiState.Loading)

    /**
     * Reads the postal codes of the communes not looked up yet, in the
     * background: the referential takes a few seconds to read the first time.
     * A code unknown to it, or a referential that cannot be read, leaves the
     * commune without postal codes.
     */
    private fun lookUpPostalCodes(follows: List<Follow>) {
        val codes = follows.map { it.communeCode }.distinct().filter { lookedUp.add(it) }
        if (codes.isEmpty()) return
        viewModelScope.launch {
            val found = try {
                codes.mapNotNull { code ->
                    communes.byCode(code)?.let { code to it.postalCodes }
                }.toMap()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyMap()
            }
            postalCodes.update { it + found }
        }
    }

    private val _pendingRemoval = MutableStateFlow<Follow?>(null)

    /** The follow whose removal awaits confirmation, if any. */
    val pendingRemoval: StateFlow<Follow?> = _pendingRemoval.asStateFlow()

    /** Filters the list by commune or unit name or postal code; not saved anywhere. */
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
            initializer { FollowsViewModel(container.follows, container.communes) }
        }
    }
}
