package io.github.mgdx.potable.data

import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.core.model.UdiChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

/**
 * The latest sample of a unit as held in the local cache, and when it was
 * last fetched successfully from Hub'Eau (SPEC §7: the cache is the display
 * source; a banner states its freshness).
 */
data class CachedSample(val sample: Sample?, val fetchedAt: Instant)

/**
 * A commune with something in the cache (a latest sample, a series or a
 * timeline) for [udiCodes], and the most recent successful fetch of any of
 * them, if one is known.
 */
data class CachedCommune(
    val communeCode: String,
    val udiCodes: Set<String>,
    val lastFetched: Instant?,
)

interface WaterRepository {
    /** @throws DataError */
    suspend fun udis(communeCode: String): UdiChoice

    /** The latest sample, with its full results, straight from Hub'Eau. @throws DataError */
    suspend fun latestSample(communeCode: String, networkCode: String?): Sample?

    /**
     * The cached latest sample of any unit looked at, followed or not; emits
     * null while nothing has been fetched yet, and again after every refresh.
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
}
