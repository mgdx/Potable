package io.github.mgdx.potable.data

import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.core.model.UdiChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

/**
 * The latest sample of a followed unit as held in the local cache, and when
 * it was last fetched successfully from Hub'Eau (SPEC §7: the cache is the
 * display source; a banner states its freshness).
 */
data class CachedSample(val sample: Sample?, val fetchedAt: Instant)

interface WaterRepository {
    /** @throws DataError */
    suspend fun udis(communeCode: String): UdiChoice

    /** The latest sample, with its full results, straight from Hub'Eau. @throws DataError */
    suspend fun latestSample(communeCode: String, networkCode: String?): Sample?

    /**
     * The cached latest sample of a **followed** unit; emits null while
     * nothing has been fetched yet, and again after every refresh.
     */
    fun cachedLatestSample(communeCode: String, udiCode: String): Flow<CachedSample?> = flowOf(null)

    /**
     * Refreshes the cache of a followed unit from Hub'Eau when needed: always
     * if [force] (pull-to-refresh), otherwise only if the last successful
     * fetch was not today (Europe/Paris) — SPEC §7, never in a loop.
     * Applies the retention purge afterwards. Returns true if the network
     * was called. Does nothing for a unit that is not followed.
     * @throws DataError
     */
    suspend fun refreshFollowed(communeCode: String, udiCode: String, force: Boolean): Boolean =
        false

    /**
     * Deletes every cached sample, history and sync state, keeping the
     * follows (SPEC §5 F8), and the unit remembered for each commune that is
     * not followed ([io.github.mgdx.potable.data.preferences.AppPreferences.forgetRememberedUdis]).
     */
    suspend fun purgeCache() {}

    /**
     * Deletes at once the cached samples and history that predate the
     * retention setting (SPEC §7), keeping the newest sample of each unit;
     * otherwise this purge only runs after a refresh.
     */
    suspend fun purgeOlderThanRetention() {}
}
