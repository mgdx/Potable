package io.github.mgdx.potable.data

import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.core.model.UdiChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

/**
 * The latest samples of a unit as held in the local cache, and when they were
 * last fetched successfully from Hub'Eau (SPEC §7: the cache is the display
 * source; a banner states its freshness). [samples] are those of the newest
 * published day, newest first ([WaterRepository.latestSamples]); usually one,
 * none if Hub'Eau has no sample for the unit.
 */
data class CachedSample(val samples: List<Sample>, val fetchedAt: Instant) {
    /** A single latest sample, or none. */
    constructor(sample: Sample?, fetchedAt: Instant) : this(listOfNotNull(sample), fetchedAt)

    /** The most recent of [samples], the one the alerts work on. */
    val sample: Sample? get() = samples.firstOrNull()
}

/**
 * A commune with something in the cache (a latest sample, a series or a
 * timeline) for [udiCodes], and the most recent successful fetch of any of
 * them, if one is known. [sizeBytes] approximates what its cached rows hold
 * (text lengths plus 8 bytes per number, without SQLite's own overhead).
 */
data class CachedCommune(
    val communeCode: String,
    val udiCodes: Set<String>,
    val lastFetched: Instant?,
    val sizeBytes: Long = 0,
)

interface WaterRepository {
    /** @throws DataError */
    suspend fun udis(communeCode: String): UdiChoice

    /** The latest sample, with its full results, straight from Hub'Eau. @throws DataError */
    suspend fun latestSample(communeCode: String, networkCode: String?): Sample?

    /**
     * Every sample taken on the published day of the latest one (Hub'Eau
     * sometimes publishes two the same day), newest first, with their full
     * results, straight from Hub'Eau; empty if there is none. The first is
     * [latestSample]. The default asks [latestSample] only.
     * @throws DataError
     */
    suspend fun latestSamples(communeCode: String, networkCode: String?): List<Sample> =
        listOfNotNull(latestSample(communeCode, networkCode))

    /**
     * The cached latest samples ([latestSamples]) of any unit looked at,
     * followed or not; emits null while nothing has been fetched yet, and
     * again after every refresh.
     */
    fun cachedLatestSample(communeCode: String, udiCode: String): Flow<CachedSample?> = flowOf(null)

    /**
     * Refreshes the cache of a unit, followed or not, from Hub'Eau when
     * needed: always if [force] (pull-to-refresh), otherwise only if the last
     * successful fetch was not today (Europe/Paris) — SPEC §7, never in a
     * loop. Applies the retention purge afterwards. Returns true if the
     * network was called.
     * @throws DataError
     */
    suspend fun refreshCache(communeCode: String, udiCode: String, force: Boolean): Boolean = false

    /** The communes with something cached, by commune code; re-emitted on every change. */
    fun cachedCommunes(): Flow<List<CachedCommune>> = flowOf(emptyList())

    /**
     * Deletes every cached sample, history and sync state, keeping the
     * follows (SPEC §5 F8), and the unit remembered for each commune that is
     * not followed ([io.github.mgdx.potable.data.preferences.AppPreferences.forgetRememberedUdis]).
     */
    suspend fun purgeCache() {}

    /**
     * Deletes the whole cache of [communeCode] (every unit, history
     * included) and, if the commune is not followed, the unit remembered for
     * it. Never touches the follows, nor the date the alerts have seen for a
     * followed unit.
     */
    suspend fun purgeCommune(communeCode: String) {}

    /**
     * Deletes at once the cached samples and history that predate the
     * retention setting (SPEC §7), keeping the newest sample of each unit;
     * otherwise this purge only runs after a refresh.
     */
    suspend fun purgeOlderThanRetention() {}

    /**
     * Deletes the whole cache (latest samples, kept reports, series,
     * timeline, sync state) of every unit that is neither followed nor among
     * the recent ones (SPEC §7), unit by unit: another unit of the same
     * commune that is still referenced keeps its cache. Run by
     * [CacheJanitor]; never touches the follows, the recent list, the
     * remembered units, nor the date the alerts have seen.
     */
    suspend fun pruneUnreferenced() {}
}
