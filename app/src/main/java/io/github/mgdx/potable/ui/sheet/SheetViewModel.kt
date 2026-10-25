package io.github.mgdx.potable.ui.sheet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.potable.AppContainer
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.data.CachedSample
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.data.WaterRepository
import io.github.mgdx.potable.data.follow.FollowRepository
import io.github.mgdx.potable.ui.common.LoadError
import io.github.mgdx.potable.ui.common.SLOW_LOAD_THRESHOLD
import io.github.mgdx.potable.ui.common.slowLoading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration

sealed interface SheetUiState {
    data object Loading : SheetUiState

    data class Error(val error: LoadError) : SheetUiState

    /** Hub'Eau has no sample for this commune and unit. */
    data object NoSample : SheetUiState

    data class Loaded(val sample: Sample, val age: RelativeAge) : SheetUiState
}

/**
 * How old the data shown are, when they come from the cache of a followed
 * unit (SPEC §7): [fetchedAt] is the last successful request to Hub'Eau, and
 * [refreshFailed] says that the latest attempt to refresh them failed.
 */
data class Freshness(val fetchedAt: Instant, val refreshFailed: Boolean)

/**
 * The "my water" sheet: the latest sample of a commune's unit (SPEC §5 F3).
 *
 * For a followed unit, the cache is the display source; it is refreshed when
 * the sheet opens (the repository decides whether the network is needed) and
 * on pull-to-refresh. A unit that is not followed is read from Hub'Eau.
 * Following or unfollowing from the sheet switches the source.
 */
class SheetViewModel(
    private val water: WaterRepository,
    private val follows: FollowRepository,
    private val communeCode: String,
    private val udiCode: String,
    private val communeName: String,
    private val udiName: String,
    private val clock: Clock,
    slowThreshold: Duration = SLOW_LOAD_THRESHOLD,
) : ViewModel() {
    private val _state = MutableStateFlow<SheetUiState>(SheetUiState.Loading)
    val state: StateFlow<SheetUiState> = _state.asStateFlow()

    /** True when the loading state has lasted [slowThreshold]: Hub'Eau is slow. */
    val isSlow: StateFlow<Boolean> =
        state.slowLoading(viewModelScope, slowThreshold) { it == SheetUiState.Loading }

    private val _freshness = MutableStateFlow<Freshness?>(null)

    /** Set while the data shown come from the cache; null otherwise. */
    val freshness: StateFlow<Freshness?> = _freshness.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)

    /**
     * True during a pull-to-refresh of data on screen. Never while the
     * loading state shows: one indicator at a time.
     */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isFollowed = MutableStateFlow<Boolean?>(null)

    /** Whether this unit is followed; null until the follows are read. */
    val isFollowed: StateFlow<Boolean?> = _isFollowed.asStateFlow()

    private var remoteJob: Job? = null
    private var cacheJob: Job? = null
    private var refreshJob: Job? = null
    private var cached: CachedSample? = null
    private var refreshError: LoadError? = null

    init {
        viewModelScope.launch {
            follows.follows
                .map { all -> all.any { it.communeCode == communeCode && it.udiCode == udiCode } }
                .catch { emit(false) }
                .distinctUntilChanged()
                .collect { followed ->
                    _isFollowed.value = followed
                    if (followed) showCache() else leaveCache()
                }
        }
    }

    /** Called by the Retry button only: there is no automatic retry. */
    fun retry() {
        if (cacheJob == null) {
            loadRemote()
        } else {
            refreshError = null
            if (cached == null) _state.value = SheetUiState.Loading
            refresh(force = true)
        }
    }

    /** Pull-to-refresh: asks Hub'Eau again, even if the cache is from today. */
    fun pullToRefresh() {
        if (cacheJob == null) return
        // Nothing cached is shown: the loading state is the indicator.
        if (cached == null) retry() else refresh(force = true)
    }

    fun toggleFollow() {
        val followed = _isFollowed.value ?: return
        viewModelScope.launch {
            try {
                if (followed) {
                    val follow = follows.find(communeCode, udiCode) ?: return@launch
                    // The data shown stay on screen: the cache, deleted with
                    // the follow, is no longer listened to.
                    stopCache()
                    follows.unfollow(follow.id)
                } else {
                    follows.follow(communeCode, communeName, udiCode, udiName)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The button keeps reflecting the stored state, which did not change.
            }
        }
    }

    private fun showCache() {
        if (cacheJob != null) return
        remoteJob?.cancel()
        cached = null
        refreshError = null
        cacheJob = viewModelScope.launch {
            water.cachedLatestSample(communeCode, udiCode)
                .catch {
                    // An unreadable cache: said so, unless something is shown.
                    refreshError = LoadError.UNEXPECTED
                    publishCache()
                }
                .collect(::onCache)
        }
        refresh(force = false)
    }

    private fun onCache(value: CachedSample?) {
        val purged = value == null && cached != null
        cached = value
        if (purged) {
            // The cache was emptied (settings): load it again.
            _state.value = SheetUiState.Loading
            _freshness.value = null
            refreshError = null
            refresh(force = false)
        } else {
            publishCache()
        }
    }

    private fun leaveCache() {
        stopCache()
        val shown = _state.value is SheetUiState.Loaded || _state.value is SheetUiState.NoSample
        if (!shown && remoteJob?.isActive != true) loadRemote()
    }

    private fun stopCache() {
        cacheJob?.cancel()
        cacheJob = null
        refreshJob?.cancel()
        refreshJob = null
        _isRefreshing.value = false
    }

    private fun refresh(force: Boolean) {
        if (refreshJob?.isActive == true) return
        // Without a cache the loading state shows: no second indicator.
        if (force && cached != null) _isRefreshing.value = true
        refreshJob = viewModelScope.launch {
            refreshError = try {
                water.refreshFollowed(communeCode, udiCode, force)
                null
            } catch (e: DataError) {
                LoadError.from(e)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                LoadError.UNEXPECTED
            } finally {
                _isRefreshing.value = false
            }
            publishCache()
        }
    }

    /**
     * The cache, when there is one, is always what is shown, with its
     * freshness; an error is shown only when there is nothing to show.
     */
    private fun publishCache() {
        val cache = cached
        val error = refreshError
        if (cache != null) {
            _state.value = sampleState(cache.sample)
            _freshness.value = Freshness(cache.fetchedAt, refreshFailed = error != null)
        } else if (error != null) {
            val shown = _state.value is SheetUiState.Loaded || _state.value is SheetUiState.NoSample
            if (!shown) _state.value = SheetUiState.Error(error)
        }
    }

    private fun loadRemote() {
        remoteJob?.cancel()
        _state.value = SheetUiState.Loading
        _freshness.value = null
        remoteJob = viewModelScope.launch {
            _state.value = try {
                sampleState(water.latestSample(communeCode, udiCode))
            } catch (e: DataError) {
                SheetUiState.Error(LoadError.from(e))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Never crash on a bug: the user is told, and can retry.
                SheetUiState.Error(LoadError.UNEXPECTED)
            }
        }
    }

    private fun sampleState(sample: Sample?): SheetUiState = if (sample == null) {
        SheetUiState.NoSample
    } else {
        SheetUiState.Loaded(sample, RelativeAge.of(sample.date, clock))
    }

    companion object {
        fun factory(
            container: AppContainer,
            communeCode: String,
            udiCode: String,
            communeName: String,
            udiName: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SheetViewModel(
                    container.water,
                    container.follows,
                    communeCode,
                    udiCode,
                    communeName,
                    udiName,
                    Clock.systemUTC(),
                )
            }
        }
    }
}
