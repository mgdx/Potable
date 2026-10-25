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
 * fetched again only if [force], if its last successful fetch was not today
 * (Europe/Paris), or if [since] goes back before what is cached; otherwise
 * the cache answers. A failed fetch with something cached returns the cache
 * (possibly shorter than asked); without cache, the [DataError] is thrown.
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

    /** Serialises fetches: a second caller waits, then finds today's fetch. */
    private val fetching = Mutex()

    override suspend fun parameterSeries(
        communeCode: String,
        udiCode: String,
        parameterCodes: Set<String>,
        since: LocalDate,
        force: Boolean,
    ): ParameterSeries? {
        val key = parameterCodes.sorted().joinToString(",")
        return fetching.withLock {
            val cached = history.series(communeCode, udiCode, key)
            if (cached != null && !force && isFresh(cached.lastSuccess, cached.depuis, since)) {
                return@withLock cachedSeries(cached, since)
            }
            val fetched =
                try {
                    network.parameterSeries(communeCode, udiCode, parameterCodes, since, force)
                } catch (e: DataError) {
                    if (cached == null) throw e
                    return@withLock cachedSeries(cached, since)
                }
            storeSeries(communeCode, udiCode, key, since, cached, fetched)
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
        return fetching.withLock {
            val sync = history.timelineSync(communeCode, udiCode)
            if (sync != null && !force && isFresh(sync.lastSuccess, sync.depuis, since)) {
                return@withLock cachedTimeline(communeCode, udiCode, since)
            }
            val fetched =
                try {
                    network.sampleTimeline(communeCode, udiCode, since, force)
                } catch (e: DataError) {
                    if (sync == null) throw e
                    return@withLock cachedTimeline(communeCode, udiCode, since)
                }
            storeTimeline(communeCode, udiCode, since, sync, fetched)
            fetched
        }
    }

    /**
     * Writes a fetched series over the cached points since [since], keeping
     * the older ones when the cache reaches [since] (it was fetched after
     * that day): the union is then contiguous and the coverage starts at the
     * earlier of both. Otherwise the old points are dropped, so that no gap
     * reads as an absence of measure.
     */
    private suspend fun storeSeries(
        communeCode: String,
        udiCode: String,
        key: String,
        since: LocalDate,
        cached: SeriesEntity?,
        fetched: ParameterSeries?,
    ) {
        val now = clock.instant()
        val from = since.startInstant().toEpochMilli()
        val retention = retentionCutoff(now)
        database.withTransaction {
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
    ) {
        val now = clock.instant()
        val from = since.startInstant().toEpochMilli()
        val retention = retentionCutoff(now)
        database.withTransaction {
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

    /** Fetched today (Paris) and covering [since]. */
    private fun isFresh(lastSuccess: Long, depuis: Long, since: LocalDate): Boolean {
        val today = clock.instant().atZone(PARIS).toLocalDate()
        val fetchedOn = Instant.ofEpochMilli(lastSuccess).atZone(PARIS).toLocalDate()
        return fetchedOn == today && depuis <= since.toEpochDay()
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
        fun LocalDate.startInstant(): Instant = atStartOfDay(ZoneOffset.UTC).toInstant()
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
