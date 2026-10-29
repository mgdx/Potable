package io.github.mgdx.potable.data.alert

import io.github.mgdx.potable.core.NewAnalysis
import io.github.mgdx.potable.core.Samples
import io.github.mgdx.potable.core.Unfavourable
import io.github.mgdx.potable.core.model.SampleSummary
import io.github.mgdx.potable.data.CachedSample
import io.github.mgdx.potable.data.CachingWaterRepository
import io.github.mgdx.potable.data.HubEauClient
import io.github.mgdx.potable.data.follow.Follow
import io.github.mgdx.potable.data.follow.FollowRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException

/**
 * [AlertChecker] over Hub'Eau. For every followed unit, two light requests:
 * the newest sample ([latestSampleSummary]) and the headers of the samples
 * of the alert window ([alertSampleSummaries]), from [ALERT_WINDOW_DAYS]
 * days before the last one seen to today. A sample is new when its code is
 * not among those already seen (`NewAnalysis.newSamples`), so that two
 * samples of the same day, a sample published late with an earlier date,
 * or several published between two checks are all judged. What was seen is
 * kept in the tables `synchro` and `prelevement_vu` through [water]
 * ([CachingWaterRepository.seenSamples]), which also refreshes the cache of
 * a unit with a new sample. Units are checked one after the other; nothing
 * is logged.
 */
class HubEauAlertChecker internal constructor(
    private val follows: FollowRepository,
    private val water: CachingWaterRepository,
    private val latest: suspend (communeCode: String, udiCode: String) -> SampleSummary?,
    private val window: suspend (
        communeCode: String,
        udiCode: String,
        since: LocalDate,
        until: LocalDate,
    ) -> List<SampleSummary>,
    private val clock: Clock = Clock.systemUTC(),
) : AlertChecker {
    constructor(client: HubEauClient, follows: FollowRepository, water: CachingWaterRepository) :
        this(follows, water, client::latestSampleSummary, client::alertSampleSummaries)

    /** A check and a baseline never interleave: both write what was seen. */
    private val running = Mutex()

    override suspend fun check(): List<AlertEvent> = running.withLock {
        followed().mapNotNull { follow -> bestEffort { check(follow) } }
    }

    override suspend fun setBaseline() {
        running.withLock {
            for (follow in followed()) {
                bestEffort {
                    val latest = latest(follow.communeCode, follow.udiCode) ?: return@bestEffort
                    recordBaseline(follow, latest)
                }
            }
        }
    }

    override suspend fun setBaseline(follow: Follow) {
        running.withLock {
            bestEffort {
                val (communeCode, udiCode) = follow.communeCode to follow.udiCode
                // Already a baseline (followed earlier, or checked): it stays.
                if (water.lastSeenSampleDate(communeCode, udiCode) != null) return@bestEffort
                // The sheet that was followed has usually cached its latest
                // samples: no request. Every sample of their day is among
                // them, so the codes are complete from that day on; an older
                // one is not judged until the next check records the window.
                val cached = water.cachedLatestSample(communeCode, udiCode).first()
                val newest = cached?.sample
                if (newest != null) {
                    // Ignored if the unit was unfollowed in the meantime.
                    water.recordSeenSamples(
                        communeCode,
                        udiCode,
                        lastSeen = newest.date,
                        samples = cached.summaries(),
                        codesSince = startOfDay(newest.date),
                    )
                    return@bestEffort
                }
                val latest = latest(communeCode, udiCode) ?: return@bestEffort
                recordBaseline(follow, latest)
            }
        }
    }

    /**
     * Records [latest] and the samples of the window before it as seen:
     * a baseline, never an alert.
     */
    private suspend fun recordBaseline(follow: Follow, latest: SampleSummary) {
        val (communeCode, udiCode) = follow.communeCode to follow.udiCode
        val since = windowStart(latest.date)
        val candidates = candidates(communeCode, udiCode, latest, since)
        water.recordSeenSamples(communeCode, udiCode, latest.date, candidates, since)
    }

    /** The event for [follow], or null; throws on a failed request, leaving what was seen. */
    private suspend fun check(follow: Follow): AlertEvent? {
        val (communeCode, udiCode) = follow.communeCode to follow.udiCode
        val latest = latest(communeCode, udiCode) ?: return null
        val seen = water.seenSamples(communeCode, udiCode)
        val lastSeen = seen.lastSeen
        if (lastSeen == null) {
            // First check of the unit: a baseline, never an alert (NewAnalysis.newSamples).
            recordBaseline(follow, latest)
            return null
        }
        val since = windowStart(lastSeen)
        val candidates = candidates(communeCode, udiCode, latest, since)
        // A sample older than the codes kept cannot be told apart: not judged.
        val judged = seen.codesSince?.let { from -> candidates.filter { !it.date.isBefore(from) } }
            ?: candidates
        val fresh = NewAnalysis.newSamples(lastSeen, seen.codes, judged)
        val newest = candidates.firstOrNull()?.date?.takeIf { it.isAfter(lastSeen) } ?: lastSeen
        water.recordSeenSamples(communeCode, udiCode, newest, candidates, windowStart(newest))
        if (fresh.isEmpty()) return null
        // The notification opens the unit's page: give it the new samples,
        // unless the app already cached them (the sheet was opened since their
        // publication), but announce them even if Hub'Eau fails this second time.
        bestEffort {
            val cached = water.cachedLatestSample(communeCode, udiCode).first()
            if (!cached.covers(candidates.first(), fresh)) {
                water.refreshCache(communeCode, udiCode, force = true)
            }
        }
        val unfavourable = fresh.filter(Unfavourable::isUnfavourable)
        return AlertEvent(
            follow,
            unfavourable.firstOrNull() ?: fresh.first(),
            unfavourable.isNotEmpty(),
        )
    }

    /**
     * The samples of the unit taken from [since] on, newest first: those of
     * the window, and [latest], which may measure none of its parameters.
     */
    private suspend fun candidates(
        communeCode: String,
        udiCode: String,
        latest: SampleSummary,
        since: Instant,
    ): List<SampleSummary> {
        val now = clock.instant()
        // From the day before (the filter reads days, the window starts at a
        // time) to the day after tomorrow, excluded: today in France whatever
        // the hour (`Samples.publishedDay`).
        val from = Samples.publishedDay(maxOf(since, now.minus(MAX_WINDOW))).minusDays(1)
        val until = Samples.publishedDay(now).plusDays(2)
        val rows = window(communeCode, udiCode, from, until)
        return (listOf(latest) + rows)
            .distinctBy { it.code }
            .filter { !it.date.isBefore(since) }
            .sortedWith(compareByDescending<SampleSummary> { it.date }.thenBy { it.code })
    }

    /**
     * Whether this cache already holds the newest sample of the unit,
     * [newest], and every sample of [fresh] taken on its day: the page the
     * notification opens shows them without fetching again.
     */
    private fun CachedSample?.covers(newest: SampleSummary, fresh: List<SampleSummary>): Boolean {
        val shown = this?.sample ?: return false
        if (shown.date.isBefore(newest.date)) return false
        val day = Samples.publishedDay(shown.date)
        val codes = samples.mapTo(HashSet()) { it.code }
        return fresh.filter { Samples.publishedDay(it.date) == day }.all { it.code in codes }
    }

    private fun CachedSample.summaries(): List<SampleSummary> = samples.map {
        SampleSummary(
            code = it.code,
            date = it.date,
            conclusion = it.conclusion,
            bacteriologicalLimits = it.bacteriologicalLimits,
            physicochemicalLimits = it.physicochemicalLimits,
            bacteriologicalReferences = it.bacteriologicalReferences,
            physicochemicalReferences = it.physicochemicalReferences,
        )
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

    companion object {
        /**
         * Days before the newest sample seen from which a unit's samples are
         * judged by their codes, and from which the codes seen are kept: a
         * sample published that late is still announced.
         */
        const val ALERT_WINDOW_DAYS = 92L

        private val WINDOW: Duration = Duration.ofDays(ALERT_WINDOW_DAYS)

        /**
         * Bound on the window: a unit not checked for long (or aged to 1970
         * by the debug tools) asks for a year of headers at most, never its
         * whole history.
         */
        private val MAX_WINDOW: Duration = Duration.ofDays(366)

        private fun windowStart(lastSeen: Instant): Instant = lastSeen.minus(WINDOW)

        /** The start of the published day of [date] ([Samples.publishedDay]). */
        private fun startOfDay(date: Instant): Instant =
            Samples.publishedDay(date).atStartOfDay(ZoneOffset.UTC).toInstant()
    }
}
