package io.github.mgdx.potable.data.alert

import io.github.mgdx.potable.core.NewAnalysis
import io.github.mgdx.potable.core.Unfavourable
import io.github.mgdx.potable.core.model.SampleSummary
import io.github.mgdx.potable.data.CachingWaterRepository
import io.github.mgdx.potable.data.HubEauClient
import io.github.mgdx.potable.data.follow.Follow
import io.github.mgdx.potable.data.follow.FollowRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * [AlertChecker] over Hub'Eau: one light request per followed unit
 * ([latestSampleSummary]), the last sample seen kept in the table `synchro`
 * through [water] ([CachingWaterRepository.lastSeenSampleDate]), which also
 * refreshes the cache of a unit with a new sample. Units are checked one
 * after the other; nothing is logged.
 */
class HubEauAlertChecker internal constructor(
    private val follows: FollowRepository,
    private val water: CachingWaterRepository,
    private val latest: suspend (communeCode: String, udiCode: String) -> SampleSummary?,
) : AlertChecker {
    constructor(client: HubEauClient, follows: FollowRepository, water: CachingWaterRepository) :
        this(follows, water, client::latestSampleSummary)

    /** A check and a baseline never interleave: both write the last seen dates. */
    private val running = Mutex()

    override suspend fun check(): List<AlertEvent> = running.withLock {
        followed().mapNotNull { follow -> bestEffort { check(follow) } }
    }

    override suspend fun setBaseline() {
        running.withLock {
            for (follow in followed()) {
                bestEffort {
                    val latest = latest(follow.communeCode, follow.udiCode) ?: return@bestEffort
                    water.setLastSeenSampleDate(follow.communeCode, follow.udiCode, latest.date)
                }
            }
        }
    }

    /** The event for [follow], or null; throws on a failed request, leaving its last seen date. */
    private suspend fun check(follow: Follow): AlertEvent? {
        val (communeCode, udiCode) = follow.communeCode to follow.udiCode
        val latest = latest(communeCode, udiCode) ?: return null
        val lastSeen = water.lastSeenSampleDate(communeCode, udiCode)
        if (lastSeen == null) {
            // First check of the unit: a baseline, never an alert (NewAnalysis.isNew).
            water.setLastSeenSampleDate(communeCode, udiCode, latest.date)
            return null
        }
        if (!NewAnalysis.isNew(lastSeen, latest.date)) return null
        water.setLastSeenSampleDate(communeCode, udiCode, latest.date)
        // The notification opens the unit's page: give it the new sample,
        // but announce it even if Hub'Eau fails this second time.
        bestEffort { water.refreshCache(communeCode, udiCode, force = true) }
        return AlertEvent(follow, latest, Unfavourable.isUnfavourable(latest))
    }

    private suspend fun followed(): List<Follow> = bestEffort { follows.follows.first() }.orEmpty()

    /**
     * [block]'s result, or null if it failed: any failure (Hub'Eau, database)
     * skips the unit until the next check. Cancellation still propagates.
     */
    private suspend fun <T> bestEffort(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
