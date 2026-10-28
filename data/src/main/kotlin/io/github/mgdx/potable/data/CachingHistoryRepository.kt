package io.github.mgdx.potable.data

import androidx.room.withTransaction
import io.github.mgdx.potable.core.Thresholds
import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.core.model.SampleSummary
import io.github.mgdx.potable.core.model.SeriesPoint
import io.github.mgdx.potable.data.local.HistoryDao
import io.github.mgdx.potable.data.local.PotableDatabase
import io.github.mgdx.potable.data.local.SeriesEntity
import io.github.mgdx.potable.data.local.SeriesPointEntity
import io.github.mgdx.potable.data.local.TimelineSampleEntity
import io.github.mgdx.potable.data.local.TimelineSyncEntity
import io.github.mgdx.potable.data.local.toCode
import io.github.mgdx.potable.data.preferences.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * [HistoryRepository] that keeps in [database] the history of every unit
 * looked at, followed or not (SPEC §7 as amended, `docs/decisions.md`).
 *
 * A unit's series (per set of parameter codes) or timeline is
 * fetched again only if [force] or if its last successful fetch was not today
 * (Europe/Paris); otherwise the cache answers. When [since] goes back before
 * what a cache of today covers, only the missing older days are asked for
 * ([HistoryRepository.parameterSeriesBefore]) and joined to it: the sheet's
 * twelve months, then the timeline's three years, cost one year and two.
 *
 * A failed fetch throws its [DataError], except for an ordinary opening
 * (not [force]) whose whole window the cache covers: the cache answers, so
 * that the history stays readable offline (SPEC §7). The answer is thus
 * never shorter than asked, and a refresh that fails says so
 * (`docs/decisions.md`, 2026-10-28).
 * After every successful fetch the retention purge runs on the history
 * tables.
 *
 * Days are those of the published date read in UTC, as Hub'Eau's date
 * filters read them (`docs/hubeau-api.md`).
 */
class CachingHistoryRepository(
    private val network: HistoryRepository,
    private val database: PotableDatabase,
    private val preferences: AppPreferences,
    private val clock: Clock = Clock.systemUTC(),
) : HistoryRepository {
    private val history = database.history()

    /**
     * Serialises the fetches of one series or timeline: a second caller
     * waits, then finds today's fetch. Other series and timelines do not
     * wait (each writes its own rows; a purge in between is caught by
     * [PotableDatabase.unchangedSince]). One small [Mutex] per key looked
     * at in the process, never removed.
     */
    private val locks = HashMap<String, Mutex>()

    private fun lock(key: String): Mutex = synchronized(locks) { locks.getOrPut(key) { Mutex() } }

    override suspend fun parameterSeries(
        communeCode: String,
        udiCode: String,
        parameterCodes: Set<String>,
        since: LocalDate,
        force: Boolean,
    ): ParameterSeries? {
        val key = parameterCodes.sorted().joinToString(",")
        return lock("series|$communeCode|$udiCode|$key").withLock {
            val cached = history.series(communeCode, udiCode, key)
            val covers = cached != null && cached.depuis <= since.toEpochDay()
            if (cached != null && !force && fetchedToday(cached.lastSuccess)) {
                if (covers) return@withLock cachedSeries(cached, since)
                return@withLock extendSeries(cached, parameterCodes, since)
            }
            val generation = database.cacheGeneration.get()
            val fetched =
                try {
                    network.parameterSeries(communeCode, udiCode, parameterCodes, since, force)
                } catch (e: DataError) {
                    // Offline opening of a covered window: the cache, whole.
                    if (force || !covers) throw e
                    return@withLock cachedSeries(cached, since)
                }
            storeSeries(communeCode, udiCode, key, since, cached, fetched, generation)
            // The fetch is complete since [since]; the cache may already be
            // purged of what predates the retention.
            fetched
        }
    }

    override suspend fun sampleTimeline(
        communeCode: String,
        udiCode: String,
        since: LocalDate,
        force: Boolean,
    ): List<SampleSummary> {
        return lock("timeline|$communeCode|$udiCode").withLock {
            val sync = history.timelineSync(communeCode, udiCode)
            val covers = sync != null && sync.depuis <= since.toEpochDay()
            if (sync != null && !force && fetchedToday(sync.lastSuccess)) {
                if (covers) return@withLock cachedTimeline(communeCode, udiCode, since)
                return@withLock extendTimeline(sync, since)
            }
            val generation = database.cacheGeneration.get()
            val fetched =
                try {
                    network.sampleTimeline(communeCode, udiCode, since, force)
                } catch (e: DataError) {
                    // Offline opening of a covered window: the cache, whole.
                    if (force || !covers) throw e
                    return@withLock cachedTimeline(communeCode, udiCode, since)
                }
            storeTimeline(communeCode, udiCode, since, sync, fetched, generation)
            fetched
        }
    }

    /**
     * A series fetched today that starts after [since]: asks only for the
     * days before its coverage and joins them to it, in the cache and in
     * the answer. A failed request throws its [DataError]: the cache alone
     * would be shorter than asked.
     * @throws DataError
     */
    private suspend fun extendSeries(
        cached: SeriesEntity,
        parameterCodes: Set<String>,
        since: LocalDate,
    ): ParameterSeries? {
        val until = LocalDate.ofEpochDay(cached.depuis)
        val generation = database.cacheGeneration.get()
        val older =
            network.parameterSeriesBefore(
                cached.communeCode,
                cached.udiCode,
                parameterCodes,
                since,
                until,
            )
        val from = since.startUtc()
        val olderPoints = older?.points.orEmpty()
            .filter { !it.date.isBefore(from) && it.date.isBefore(until.startUtc()) }
        val recent = cachedSeries(cached, since)
        val retention = retentionCutoff(clock.instant())
        database.withTransaction {
            if (!database.unchangedSince(generation, cached.communeCode, cached.udiCode)) {
                return@withTransaction
            }
            val current = history.series(cached.communeCode, cached.udiCode, cached.parameterKey)
            if (current != cached) return@withTransaction
            // The labels stay the newest published, unless only older measures exist.
            val labelled = if (current.libelle == null && older != null) {
                current.copy(
                    parameterCode = older.parameterCode,
                    libelle = older.label,
                    unite = older.unit,
                    limite = older.limitText,
                    reference = older.referenceText,
                )
            } else {
                current
            }
            history.upsertSeries(labelled.copy(depuis = since.toEpochDay()))
            history.deletePointsBetween(
                current.communeCode,
                current.udiCode,
                current.parameterKey,
                from.toEpochMilli(),
                until.startUtc().toEpochMilli(),
            )
            history.insertPoints(
                olderPoints.map {
                    SeriesPointEntity(
                        communeCode = current.communeCode,
                        udiCode = current.udiCode,
                        parameterKey = current.parameterKey,
                        sampleCode = it.sampleCode,
                        date = it.date.toEpochMilli(),
                        value = it.value,
                        text = it.text,
                        censure = it.censored,
                    )
                },
            )
            purge(retention)
        }
        return when {
            recent != null -> recent.copy(points = olderPoints + recent.points)
            older != null && olderPoints.isNotEmpty() -> older.copy(points = olderPoints)
            else -> null
        }
    }

    /** Same as [extendSeries], for the timeline. */
    private suspend fun extendTimeline(
        sync: TimelineSyncEntity,
        since: LocalDate,
    ): List<SampleSummary> {
        val (communeCode, udiCode) = sync.communeCode to sync.udiCode
        val until = LocalDate.ofEpochDay(sync.depuis)
        val generation = database.cacheGeneration.get()
        val older = network.sampleTimelineBefore(communeCode, udiCode, since, until)
        val from = since.startUtc()
        val olderSamples = older
            .filter { !it.date.isBefore(from) && it.date.isBefore(until.startUtc()) }
            .distinctBy { it.code }
        val recent = cachedTimeline(communeCode, udiCode, since)
        val retention = retentionCutoff(clock.instant())
        database.withTransaction {
            if (!database.unchangedSince(generation, communeCode, udiCode)) return@withTransaction
            if (history.timelineSync(communeCode, udiCode) != sync) return@withTransaction
            history.upsertTimelineSync(sync.copy(depuis = since.toEpochDay()))
            history.deleteTimelineBetween(
                communeCode,
                udiCode,
                from.toEpochMilli(),
                until.startUtc().toEpochMilli(),
            )
            history.insertTimeline(olderSamples.map { it.toEntity(communeCode, udiCode) })
            purge(retention)
        }
        // Newest first, as the cache and Hub'Eau give them.
        return recent + olderSamples.sortedByDescending { it.date }
    }

    /**
     * Writes a fetched series over the cached points since [since], keeping
     * the older ones when the cache reaches [since] (it was fetched after
     * that day): the union is then contiguous and the coverage starts at the
     * earlier of both. Otherwise the old points are dropped, so that no gap
     * reads as an absence of measure. Nothing is written if the cache was
     * purged since [generation] was read (the request ran meanwhile).
     */
    private suspend fun storeSeries(
        communeCode: String,
        udiCode: String,
        key: String,
        since: LocalDate,
        cached: SeriesEntity?,
        fetched: ParameterSeries?,
        generation: Long,
    ) {
        val now = clock.instant()
        val from = since.startInstant().toEpochMilli()
        val retention = retentionCutoff(now)
        database.withTransaction {
            if (!database.unchangedSince(generation, communeCode, udiCode)) return@withTransaction
            val keepOlder = cached != null && cached.lastSuccess >= from
            val depuis = if (keepOlder) {
                minOf(
                    since.toEpochDay(),
                    cached.depuis,
                )
            } else {
                since.toEpochDay()
            }
            history.upsertSeries(
                SeriesEntity(
                    communeCode = communeCode,
                    udiCode = udiCode,
                    parameterKey = key,
                    parameterCode = fetched?.parameterCode ?: cached?.parameterCode,
                    libelle = fetched?.label ?: cached?.libelle,
                    unite = if (fetched != null) fetched.unit else cached?.unite,
                    limite = if (fetched != null) fetched.limitText else cached?.limite,
                    reference = if (fetched != null) fetched.referenceText else cached?.reference,
                    depuis = depuis,
                    lastSuccess = now.toEpochMilli(),
                ),
            )
            history.deletePointsFrom(
                communeCode,
                udiCode,
                key,
                if (keepOlder) from else Long.MIN_VALUE,
            )
            val points = fetched?.points.orEmpty().filter { it.date.toEpochMilli() >= from }
            history.insertPoints(
                points.map {
                    SeriesPointEntity(
                        communeCode = communeCode,
                        udiCode = udiCode,
                        parameterKey = key,
                        sampleCode = it.sampleCode,
                        date = it.date.toEpochMilli(),
                        value = it.value,
                        text = it.text,
                        censure = it.censored,
                    )
                },
            )
            purge(retention)
        }
    }

    /** Same merge as [storeSeries]. */
    private suspend fun storeTimeline(
        communeCode: String,
        udiCode: String,
        since: LocalDate,
        sync: TimelineSyncEntity?,
        fetched: List<SampleSummary>,
        generation: Long,
    ) {
        val now = clock.instant()
        val from = since.startInstant().toEpochMilli()
        val retention = retentionCutoff(now)
        database.withTransaction {
            if (!database.unchangedSince(generation, communeCode, udiCode)) return@withTransaction
            val keepOlder = sync != null && sync.lastSuccess >= from
            history.upsertTimelineSync(
                TimelineSyncEntity(
                    communeCode = communeCode,
                    udiCode = udiCode,
                    depuis = if (keepOlder) {
                        minOf(
                            since.toEpochDay(),
                            sync.depuis,
                        )
                    } else {
                        since.toEpochDay()
                    },
                    lastSuccess = now.toEpochMilli(),
                ),
            )
            history.deleteTimelineFrom(
                communeCode,
                udiCode,
                if (keepOlder) from else Long.MIN_VALUE,
            )
            history.insertTimeline(
                fetched
                    .filter { it.date.toEpochMilli() >= from }
                    .distinctBy { it.code }
                    .map { it.toEntity(communeCode, udiCode) },
            )
            purge(retention)
        }
    }

    private suspend fun purge(cutoff: LocalDate) = history.purgeHistoryBefore(cutoff)

    private suspend fun retentionCutoff(now: Instant): LocalDate =
        historyRetentionCutoff(now, preferences.retentionYears.first())

    private suspend fun cachedSeries(series: SeriesEntity, since: LocalDate): ParameterSeries? {
        val label = series.libelle ?: return null
        val points =
            history.points(
                series.communeCode,
                series.udiCode,
                series.parameterKey,
                since.startInstant().toEpochMilli(),
            )
        return ParameterSeries(
            parameterCode = series.parameterCode ?: series.parameterKey,
            label = label,
            unit = series.unite,
            limitText = series.limite,
            referenceText = series.reference,
            limit = Thresholds.parse(series.limite),
            reference = Thresholds.parse(series.reference),
            points = points.map {
                SeriesPoint(
                    date = Instant.ofEpochMilli(it.date),
                    sampleCode = it.sampleCode,
                    value = it.value,
                    text = it.text,
                    censored = it.censure,
                )
            },
        )
    }

    private suspend fun cachedTimeline(
        communeCode: String,
        udiCode: String,
        since: LocalDate,
    ): List<SampleSummary> =
        history.timeline(communeCode, udiCode, since.startInstant().toEpochMilli()).map {
            SampleSummary(
                code = it.code,
                date = Instant.ofEpochMilli(it.date),
                conclusion = it.conclusion,
                bacteriologicalLimits = Compliance.fromCode(it.bacteriologicalLimits),
                physicochemicalLimits = Compliance.fromCode(it.physicochemicalLimits),
                bacteriologicalReferences = Compliance.fromCode(it.bacteriologicalReferences),
                physicochemicalReferences = Compliance.fromCode(it.physicochemicalReferences),
            )
        }

    /** Fetched today (Paris). */
    private fun fetchedToday(lastSuccess: Long): Boolean {
        val today = clock.instant().atZone(PARIS).toLocalDate()
        return Instant.ofEpochMilli(lastSuccess).atZone(PARIS).toLocalDate() == today
    }

    private fun SampleSummary.toEntity(communeCode: String, udiCode: String) = TimelineSampleEntity(
        communeCode = communeCode,
        udiCode = udiCode,
        code = code,
        date = date.toEpochMilli(),
        conclusion = conclusion,
        bacteriologicalLimits = bacteriologicalLimits.toCode(),
        physicochemicalLimits = physicochemicalLimits.toCode(),
        bacteriologicalReferences = bacteriologicalReferences.toCode(),
        physicochemicalReferences = physicochemicalReferences.toCode(),
    )

    private companion object {
        /** "Today" is the calendar day in France (SPEC §7). */
        val PARIS: ZoneId = ZoneId.of("Europe/Paris")

        /** Start of a day as Hub'Eau's date filters read it (published date, UTC). */
        fun LocalDate.startInstant(): Instant = startUtc()
    }
}

/**
 * First day the history keeps under a retention of [years] (SPEC §7): the
 * same calendar day, [years] earlier, in France.
 */
internal fun historyRetentionCutoff(now: Instant, years: Int): LocalDate =
    now.atZone(ZoneId.of("Europe/Paris")).toLocalDate().minusYears(years.toLong())

/**
 * Retention purge (SPEC §7) of the history tables: before [cutoff] (a day of
 * the published date read in UTC), nothing is kept, and the coverage of the
 * series and timelines is clamped to it. Shared by the history fetches and
 * [CachingWaterRepository.purgeOlderThanRetention].
 */
internal suspend fun HistoryDao.purgeHistoryBefore(cutoff: LocalDate) {
    val millis = cutoff.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    purgePointsOlderThan(millis)
    purgeTimelineOlderThan(millis)
    clampSeriesCoverage(cutoff.toEpochDay())
    clampTimelineCoverage(cutoff.toEpochDay())
}
