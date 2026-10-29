package io.github.mgdx.potable.ui.sheet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.potable.AppContainer
import io.github.mgdx.potable.core.PesticideCount
import io.github.mgdx.potable.core.PesticideCounts
import io.github.mgdx.potable.core.UnfavourableDigest
import io.github.mgdx.potable.core.WatchedMeasure
import io.github.mgdx.potable.core.WatchedSubstance
import io.github.mgdx.potable.core.WatchedSubstances
import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.data.CachedSample
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.data.HistoryRepository
import io.github.mgdx.potable.data.SampleRepository
import io.github.mgdx.potable.data.WaterRepository
import io.github.mgdx.potable.data.follow.Follow
import io.github.mgdx.potable.data.follow.FollowRepository
import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.data.preferences.RecentUnit
import io.github.mgdx.potable.ui.common.LoadError
import io.github.mgdx.potable.ui.common.SLOW_LOAD_THRESHOLD
import io.github.mgdx.potable.ui.common.slowLoading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration

sealed interface SheetUiState {
    data object Loading : SheetUiState

    data class Error(val error: LoadError) : SheetUiState

    /** Hub'Eau has no sample for this commune and unit. */
    data object NoSample : SheetUiState

    /**
     * [samples] are those of the latest published day, newest first: usually
     * one, sometimes more (Hub'Eau publishes several the same day). [age] is
     * that of the day.
     */
    data class Loaded(val samples: List<Sample>, val age: RelativeAge) : SheetUiState {
        constructor(sample: Sample, age: RelativeAge) : this(listOf(sample), age)

        /** The most recent sample. */
        val sample: Sample get() = samples.first()
    }
}

/** The card of the watched substances of the sheet (SPEC §5 F3). */
sealed interface WatchedUiState {
    data object Loading : WatchedUiState

    /**
     * Nothing could be read, from the network or the cache: a discreet
     * error with Retry, the rest of the sheet unaffected.
     */
    data class Error(val error: LoadError) : WatchedUiState

    /**
     * The latest measure of each substance measured since [since] (the
     * history window, [years] years); empty when none was. [notAnalysed]
     * are the substances read without any measure since then
     * ([WatchedSubstances.notAnalysed]). [partialError] is set when some
     * substances could not be read: the others are shown. [pesticides]
     * counts the pesticides of the sample of the latest total, once read;
     * null until then, or when it could not be.
     */
    data class Loaded(
        val measures: List<WatchedMeasure>,
        val since: LocalDate,
        val years: Int,
        val partialError: LoadError? = null,
        val notAnalysed: List<WatchedSubstance> = emptyList(),
        val pesticides: PesticideCount? = null,
    ) : WatchedUiState
}

/**
 * How old the data shown are, as they come from the cache (SPEC §7):
 * [fetchedAt] is the last successful request to Hub'Eau, and [refreshFailed]
 * says that the latest attempt to refresh them failed.
 */
data class Freshness(val fetchedAt: Instant, val refreshFailed: Boolean)

/**
 * The "my water" sheet: the latest sample of a commune's unit (SPEC §5 F3).
 *
 * The cache is the display source for every unit, followed or not (SPEC §7
 * as amended, `docs/decisions.md`): it is refreshed when the sheet opens
 * (the repository decides whether the network is needed) and on
 * pull-to-refresh. Following only changes the follow button, and records
 * the alert baseline of the unit ([baseline]).
 *
 * Once a sample is shown, the timeline of the last 12 months is read in the
 * background ([recentUnfavourable]), from the history cache when it is fresh;
 * then the latest measure of each [WatchedSubstance] over the history window
 * ([watched]), from the same cache: the conclusion never waits for either.
 */
class SheetViewModel(
    private val water: WaterRepository,
    private val follows: FollowRepository,
    private val history: HistoryRepository,
    private val communeCode: String,
    private val udiCode: String,
    private val communeName: String,
    private val udiName: String,
    private val clock: Clock,
    slowThreshold: Duration = SLOW_LOAD_THRESHOLD,
    /** Where the unit is recorded as opened, for the search's "recent" list. */
    private val recents: AppPreferences? = null,
    /**
     * Records the alert baseline of the unit once followed
     * ([io.github.mgdx.potable.data.alert.AlertChecker.setBaseline]).
     */
    private val baseline: suspend (Follow) -> Unit = {},
    /** Where the sample of the latest pesticide total is read, to count its pesticides. */
    private val samples: SampleRepository? = null,
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

    private val _recentUnfavourable = MutableStateFlow<UnfavourableDigest?>(null)

    /**
     * The samples of the last [RECENT_MONTHS] months with an unfavourable
     * published outcome, read once the latest sample is shown; null while
     * unknown, when there is none, or when the timeline could not be read:
     * nothing is said then (SPEC §14).
     */
    val recentUnfavourable: StateFlow<UnfavourableDigest?> = _recentUnfavourable.asStateFlow()

    private val _watched = MutableStateFlow<WatchedUiState?>(null)

    /**
     * The watched substances, read once the latest sample is shown; null
     * until then. Their failure never touches [state].
     */
    val watched: StateFlow<WatchedUiState?> = _watched.asStateFlow()

    /** The history window in years, from the setting; null until it is read. */
    private val historyYears = MutableStateFlow<Int?>(null)

    private var refreshJob: Job? = null
    private var recentJob: Job? = null
    private var watchedJob: Job? = null

    /** The newest sample [watchedJob] was started for, and the window it used. */
    private var watchedFor: String? = null
    private var watchedYears: Int? = null

    /** The newest sample [recentJob] was started for: read again only when it changes. */
    private var recentFor: String? = null
    private var cached: CachedSample? = null
    private var refreshError: LoadError? = null

    /**
     * Records the unit in the recent list. Every fetch waits for it: the
     * cache keeps only the followed and recent units (SPEC §7), so what the
     * fetch writes is never pruned as unreferenced.
     */
    private var recorded: Job? = null

    init {
        // Once per opening of the sheet, not on each reload.
        recorded = record(onlyIfAbsent = false)
    }

    init {
        viewModelScope.launch {
            follows.follows
                .map { all -> all.any { it.communeCode == communeCode && it.udiCode == udiCode } }
                .catch { emit(false) }
                .distinctUntilChanged()
                .collect { _isFollowed.value = it }
        }
        viewModelScope.launch {
            water.cachedLatestSample(communeCode, udiCode)
                .catch {
                    // An unreadable cache: said so, unless something is shown.
                    refreshError = LoadError.UNEXPECTED
                    publishCache()
                }
                .collect(::onCache)
        }
        viewModelScope.launch {
            (recents?.historyYears ?: flowOf(AppPreferences.DEFAULT_YEARS))
                .catch { emit(AppPreferences.DEFAULT_YEARS) }
                .map { years ->
                    years.takeIf { it in AppPreferences.YEAR_CHOICES }
                        ?: AppPreferences.DEFAULT_YEARS
                }
                .distinctUntilChanged()
                .collect { years ->
                    historyYears.value = years
                    // The setting changed while the card was shown: read it again.
                    val sample = watchedFor
                    if (sample != null && watchedYears != null && watchedYears != years) {
                        loadWatched(sample, again = true)
                    }
                }
        }
        refresh(force = false)
    }

    /** The Retry button of the watched substances card: no automatic retry. */
    fun retryWatched() {
        val sample = watchedFor ?: return
        loadWatched(sample, again = true)
    }

    /** Called by the Retry button only: there is no automatic retry. */
    fun retry() {
        refreshError = null
        if (cached == null) _state.value = SheetUiState.Loading
        refresh(force = true)
    }

    /** Pull-to-refresh: asks Hub'Eau again, even if the cache is from today. */
    fun pullToRefresh() {
        // Nothing cached is shown: the loading state is the indicator.
        if (cached == null) retry() else refresh(force = true)
    }

    fun toggleFollow() {
        val followed = _isFollowed.value ?: return
        viewModelScope.launch {
            try {
                if (followed) {
                    val follow = follows.find(communeCode, udiCode) ?: return@launch
                    // The cache stays, the unit being among the recent
                    // ones: the data shown do not change.
                    follows.unfollow(follow.id)
                } else {
                    val follow = follows.follow(communeCode, communeName, udiCode, udiName)
                    // Once followed (a baseline is only kept for a followed
                    // unit): a sample published before the first check of
                    // the alerts is then announced. Best effort, never throws.
                    baseline(follow)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The button keeps reflecting the stored state, which did not change.
            }
        }
    }

    private fun onCache(value: CachedSample?) {
        val purged = value == null && cached != null
        cached = value
        if (purged) {
            // The cache was emptied (settings), or pruned while this sheet
            // was below others that pushed it out of the recent list: load
            // it again, recorded again as recent so that it stays cached.
            recorded = record(onlyIfAbsent = true)
            _state.value = SheetUiState.Loading
            _freshness.value = null
            refreshError = null
            refresh(force = false)
        } else {
            publishCache()
        }
    }

    private fun refresh(force: Boolean) {
        if (refreshJob?.isActive == true) return
        // Without a cache the loading state shows: no second indicator.
        if (force && cached != null) _isRefreshing.value = true
        refreshJob = viewModelScope.launch {
            recorded?.join()
            refreshError = try {
                water.refreshCache(communeCode, udiCode, force)
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
            _state.value = sampleState(cache.samples)
            _freshness.value = Freshness(cache.fetchedAt, refreshFailed = error != null)
            cache.sample?.let {
                loadRecentUnfavourable(it.code)
                loadWatched(it.code)
            }
        } else if (error != null) {
            val shown = _state.value is SheetUiState.Loaded || _state.value is SheetUiState.NoSample
            if (!shown) _state.value = SheetUiState.Error(error)
        }
    }

    /**
     * Reads the timeline of the last [RECENT_MONTHS] months, after the
     * latest sample [sampleCode] is shown, and keeps its unfavourable
     * samples ([UnfavourableDigest]). Best effort: a failure shows nothing.
     */
    private fun loadRecentUnfavourable(sampleCode: String) {
        if (recentFor == sampleCode) return
        recentFor = sampleCode
        recentJob?.cancel()
        recentJob = viewModelScope.launch {
            val since = clock.instant().atZone(FRANCE_ZONE).toLocalDate()
                .minusMonths(RECENT_MONTHS)
            recorded?.join()
            _recentUnfavourable.value = try {
                UnfavourableDigest.of(history.sampleTimeline(communeCode, udiCode, since))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Reads, [WATCHED_REQUESTS] substances at a time, the series of every
     * [WatchedSubstance] over the history window, from the history cache
     * when it is fresh, and keeps the latest measure of each
     * ([WatchedSubstances.latest]). Only once per newest sample [sampleCode]
     * unless [again]. A substance that cannot be read is left out; if none
     * can, the card shows an error.
     */
    private fun loadWatched(sampleCode: String, again: Boolean = false) {
        if (!again && watchedFor == sampleCode) return
        watchedFor = sampleCode
        watchedJob?.cancel()
        if (_watched.value !is WatchedUiState.Loaded) _watched.value = WatchedUiState.Loading
        watchedJob = viewModelScope.launch {
            val years = historyYears.filterNotNull().first()
            watchedYears = years
            val since = clock.instant().atZone(FRANCE_ZONE).toLocalDate()
                .minusYears(years.toLong())
            recorded?.join()
            // A request held by Hub'Eau then delays only its own substance
            // (docs/hubeau-api.md, "Blocages"); the bound spares the API.
            val gate = Semaphore(WATCHED_REQUESTS)
            val read = WatchedSubstances.readFor(communeCode)
            val reads = read.map { substance ->
                async {
                    gate.withPermit {
                        try {
                            Result.success(
                                history.parameterSeries(
                                    communeCode,
                                    udiCode,
                                    substance.codes,
                                    since,
                                ),
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    }
                }
            }.awaitAll()
            val series = LinkedHashMap<WatchedSubstance, ParameterSeries?>()
            var error: LoadError? = null
            for ((substance, outcome) in read.zip(reads)) {
                val e = outcome.exceptionOrNull()
                when {
                    e == null -> series[substance] = outcome.getOrNull()
                    e is DataError -> error = error ?: LoadError.from(e)
                    else -> error = error ?: LoadError.UNEXPECTED
                }
            }
            val measures = WatchedSubstances.latest(series)
            val notAnalysed = WatchedSubstances.notAnalysed(series, communeCode)
            val state = when {
                // "None measured" cannot be said when some could not be read.
                error != null && measures.isEmpty() -> WatchedUiState.Error(error)
                else -> WatchedUiState.Loaded(measures, since, years, error, notAnalysed)
            }
            _watched.value = state
            if (state is WatchedUiState.Loaded) countPesticides(state)
        }
    }

    /**
     * Adds to [loaded] the count of the pesticides of the sample of the
     * latest total, read from the cache or Hub'Eau ([samples]); any failure
     * leaves the card without it.
     */
    private suspend fun countPesticides(loaded: WatchedUiState.Loaded) {
        val repository = samples ?: return
        val total = loaded.measures.firstOrNull {
            it.substance == WatchedSubstance.TOTAL_PESTICIDES
        } ?: return
        val count = try {
            repository.sample(communeCode, udiCode, total.point.sampleCode)
                ?.let { PesticideCounts.of(it.results) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return
        if (_watched.value == loaded) _watched.value = loaded.copy(pesticides = count)
    }

    /**
     * Puts this unit at the top of the recent list, or, if [onlyIfAbsent],
     * only if it is no longer there; null without a store or a unit.
     */
    private fun record(onlyIfAbsent: Boolean): Job? {
        val store = recents ?: return null
        if (communeCode.isBlank() || udiCode.isBlank()) return null
        return viewModelScope.launch {
            try {
                val listed = if (onlyIfAbsent) store.recentUnits.first() else emptyList()
                if (listed.none { it.communeCode == communeCode && it.udiCode == udiCode }) {
                    store.addRecentUnit(RecentUnit(communeCode, communeName, udiCode, udiName))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Unwritable local data: the sheet shows all the same.
            }
        }
    }

    private fun sampleState(samples: List<Sample>): SheetUiState = if (samples.isEmpty()) {
        SheetUiState.NoSample
    } else {
        SheetUiState.Loaded(samples, RelativeAge.of(samples.first().date, clock))
    }

    companion object {
        /** The period of [recentUnfavourable]. */
        const val RECENT_MONTHS = 12L

        /** Series of [WatchedSubstance] read at once ([loadWatched]). */
        const val WATCHED_REQUESTS = 3

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
                    container.history,
                    communeCode,
                    udiCode,
                    communeName,
                    udiName,
                    Clock.systemUTC(),
                    recents = container.preferences,
                    baseline = { container.alertChecker.setBaseline(it) },
                    samples = container.samples,
                )
            }
        }
    }
}
