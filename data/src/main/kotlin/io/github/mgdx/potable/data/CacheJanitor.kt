package io.github.mgdx.potable.data

import io.github.mgdx.potable.data.follow.FollowRepository
import io.github.mgdx.potable.data.preferences.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * A distribution unit as the cache keys it: the code Hub'Eau publishes the
 * commune under and the unit's code.
 */
data class UnitKey(val communeCode: String, val udiCode: String)

/**
 * Keeps the cache to the units that are followed or among the recent ones
 * (SPEC §7): [WaterRepository.pruneUnreferenced] runs once when [start]ed
 * (the orphans left by earlier versions), then every time the union of
 * both lists changes, whatever changed it (a recent removed by hand or
 * pushed out by a sixth one, a unit unfollowed). A unit that leaves one
 * list but stays in the other changes nothing.
 */
class CacheJanitor(
    private val follows: FollowRepository,
    private val preferences: AppPreferences,
    private val water: WaterRepository,
) {
    /** Watches both lists in [scope] until it ends. */
    fun start(scope: CoroutineScope): Job = scope.launch {
        combine(
            follows.follows.map { all ->
                all.mapTo(HashSet()) { UnitKey(it.communeCode, it.udiCode) }
            },
            preferences.recentUnits.map { all ->
                all.mapTo(HashSet()) { UnitKey(it.communeCode, it.udiCode) }
            },
        ) { followed, recent -> followed + recent }
            .distinctUntilChanged()
            // The prune reads both lists itself: only the latest change matters.
            .conflate()
            // An unreadable list: nothing is pruned until the next start.
            .catch { }
            .collect {
                try {
                    water.pruneUnreferenced()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The next change, or the next start, tries again.
                }
            }
    }
}
