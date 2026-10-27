package io.github.mgdx.potable.data

import androidx.room.withTransaction
import io.github.mgdx.potable.core.Samples
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.core.model.UdiChoice
import io.github.mgdx.potable.data.local.PotableDatabase
import io.github.mgdx.potable.data.local.SyncEntity
import io.github.mgdx.potable.data.local.resultEntities
import io.github.mgdx.potable.data.local.toEntity
import io.github.mgdx.potable.data.local.toSample
import io.github.mgdx.potable.data.preferences.AppPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * [WaterRepository] that answers [udis] and [latestSample] from [network]
 * (Hub'Eau, unchanged) and keeps in [database] the latest sample of every
 * unit looked at, followed or not (SPEC §7 as amended, `docs/decisions.md`).
 * Only the alert state ([lastSeenSampleDate]) is restricted to the followed
 * units.
 */
class CachingWaterRepository(
    private val network: WaterRepository,
    private val database: PotableDatabase,
    private val preferences: AppPreferences,
    private val clock: Clock = Clock.systemUTC(),
) : WaterRepository {
    private val follows = database.follows()
    private val cache = database.cache()

    /** Serialises refreshes: a second caller waits, then finds today's fetch. */
    private val refreshing = Mutex()

    /**
     * The units of the last communes asked for, and the day (Paris) they
     * were fetched, most recently used last; in memory only, guarded by
     * itself. Hub'Eau publishes them once a year.
     */
    private val udiChoices =
        object : LinkedHashMap<String, Pair<LocalDate, UdiChoice>>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, Pair<LocalDate, UdiChoice>>?,
            ): Boolean = size > MAX_UDI_CHOICES
        }

    /** Serialises [udis]: a second caller for the same commune finds the first answer. */
    private val fetchingUdis = Mutex()

    /**
     * The units of [communeCode], from Hub'Eau at most once a day (Paris,
     * SPEC §7) while the process lives: the unit screen, then "Change
     * distribution unit" from the sheet, ask only once. A failure is not
     * kept; a purge forgets them all.
     */
    override suspend fun udis(communeCode: String): UdiChoice = fetchingUdis.withLock {
        val today = today()
        synchronized(udiChoices) { udiChoices[communeCode] }
            ?.takeIf { (day, _) -> day == today }
            ?.let { (_, choice) -> return@withLock choice }
        val generation = database.cacheGeneration.get()
        val choice = network.udis(communeCode)
        synchronized(udiChoices) {
            if (database.unchangedSince(generation)) udiChoices[communeCode] = today to choice
        }
        choice
    }

    override suspend fun latestSample(communeCode: String, networkCode: String?): Sample? =
        network.latestSample(communeCode, networkCode)

    override suspend fun latestSamples(communeCode: String, networkCode: String?): List<Sample> =
        network.latestSamples(communeCode, networkCode)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun cachedLatestSample(communeCode: String, udiCode: String): Flow<CachedSample?> =
        combine(
            cache.observeSync(communeCode, udiCode),
            cache.observeLatestSamples(communeCode, udiCode),
        ) { sync, samples -> sync to samples }
            .mapLatest { (sync, rows) ->
                val fetchedAt = sync?.lastSuccess ?: return@mapLatest null
                val samples = rows.map { it.toSample(cache.results(communeCode, udiCode, it.code)) }
                CachedSample(
                    samples = Samples.latestDay(samples),
                    fetchedAt = Instant.ofEpochMilli(fetchedAt),
                )
            }.distinctUntilChanged()

    override suspend fun refreshCache(
        communeCode: String,
        udiCode: String,
        force: Boolean,
    ): Boolean = refreshing.withLock {
        if (!force && fetchedToday(cache.sync(communeCode, udiCode))) return@withLock false

        // Throws DataError: the cache and the sync state are left as they were.
        val generation = database.cacheGeneration.get()
        val samples = network.latestSamples(communeCode, udiCode)
        val now = clock.instant()
        val retention = preferences.retentionYears.first()
        database.withTransaction {
            // The cache was purged while the request ran: it stays purged.
            if (!database.unchangedSince(generation)) return@withTransaction
            for (sample in samples) {
                cache.deleteResults(communeCode, udiCode, sample.code)
                cache.deleteSample(communeCode, udiCode, sample.code)
                cache.insertSample(sample.toEntity(communeCode, udiCode))
                cache.insertResults(sample.resultEntities(communeCode, udiCode))
            }
            cache.insertSyncIfAbsent(SyncEntity(communeCode, udiCode, null, null))
            cache.setLastSuccess(communeCode, udiCode, now.toEpochMilli())
            cache.purgeOlderThan(sampleCutoff(now, retention))
        }
        true
    }

    /**
     * Applies the retention now (SPEC §7), to the latest samples and to the
     * history: what predates it is deleted, except the newest sample of
     * each unit. Called when the user changes the retention, so that the
     * cache does not wait for the next refresh.
     */
    override suspend fun purgeOlderThanRetention() {
        val now = clock.instant()
        val retention = preferences.retentionYears.first()
        database.withTransaction {
            cache.purgeOlderThan(sampleCutoff(now, retention))
            database.history().purgeHistoryBefore(historyRetentionCutoff(now, retention))
        }
    }

    /** Epoch ms before which a cached sample is past a retention of [years]. */
    private fun sampleCutoff(now: Instant, years: Int): Long =
        now.atZone(PARIS).minusYears(years.toLong()).toInstant().toEpochMilli()

    override fun cachedCommunes(): Flow<List<CachedCommune>> = cache.observeCachedUnits()
        .map { rows ->
            rows.groupBy { it.communeCode }
                .map { (communeCode, units) ->
                    CachedCommune(
                        communeCode = communeCode,
                        udiCodes = units.mapTo(sortedSetOf()) { it.udiCode },
                        lastFetched = units.mapNotNull { it.lastSuccess }.maxOrNull()
                            ?.let(Instant::ofEpochMilli),
                        sizeBytes = units.sumOf { it.size },
                    )
                }
                .sortedBy { it.communeCode }
        }
        .distinctUntilChanged()

    override suspend fun purgeCommune(communeCode: String) {
        synchronized(udiChoices) { udiChoices.remove(communeCode) }
        val followed = database.withTransaction {
            database.cacheGeneration.incrementAndGet()
            cache.deleteCommuneResults(communeCode)
            cache.deleteCommuneSamples(communeCode)
            // A followed unit keeps the date its alerts have seen.
            cache.clearCommuneLastSuccess(communeCode)
            cache.deleteCommuneSyncWithoutLastSeen(communeCode)
            val history = database.history()
            history.deleteCommunePoints(communeCode)
            history.deleteCommuneSeries(communeCode)
            history.deleteCommuneTimeline(communeCode)
            history.deleteCommuneTimelineSync(communeCode)
            follows.all().any { it.communeCode == communeCode }
        }
        // Same rule as purgeCache: a followed commune keeps its choice.
        if (!followed) preferences.forgetRememberedUdi(communeCode)
    }

    override suspend fun purgeCache() {
        synchronized(udiChoices) { udiChoices.clear() }
        database.withTransaction {
            database.cacheGeneration.incrementAndGet()
            cache.deleteAllResults()
            cache.deleteAllSamples()
            cache.deleteAllSync()
            // The history cache too.
            val history = database.history()
            history.deleteAllPoints()
            history.deleteAllSeries()
            history.deleteAllTimeline()
            history.deleteAllTimelineSync()
        }
        // The units remembered for communes looked at without being followed
        // (audit V5): a followed commune keeps its choice.
        preferences.forgetRememberedUdis(follows.all().map { it.communeCode }.toSet())
    }

    /**
     * Date of the newest sample the alert task (SPEC §5 F7) has
     * seen for a followed unit, or null. A refresh never writes it: only the
     * alert task does, through [setLastSeenSampleDate], so that a sample
     * displayed in the app is still announced.
     */
    suspend fun lastSeenSampleDate(communeCode: String, udiCode: String): Instant? =
        cache.sync(communeCode, udiCode)?.lastSeenSampleDate?.let(Instant::ofEpochMilli)

    /** Records [date] as seen by the alert task; ignored for a unit that is not followed. */
    suspend fun setLastSeenSampleDate(communeCode: String, udiCode: String, date: Instant?) {
        database.withTransaction {
            if (follows.find(communeCode, udiCode) == null) return@withTransaction
            cache.insertSyncIfAbsent(SyncEntity(communeCode, udiCode, null, null))
            cache.setLastSeenSampleDate(communeCode, udiCode, date?.toEpochMilli())
        }
    }

    private fun fetchedToday(sync: SyncEntity?): Boolean {
        val last = sync?.lastSuccess ?: return false
        return Instant.ofEpochMilli(last).atZone(PARIS).toLocalDate() == today()
    }

    private fun today(): LocalDate = clock.instant().atZone(PARIS).toLocalDate()

    private companion object {
        /** Communes whose units [udis] keeps: a few searches' worth. */
        const val MAX_UDI_CHOICES = 16

        /** "Today" is the calendar day in France (SPEC §7). */
        val PARIS: ZoneId = ZoneId.of("Europe/Paris")
    }
}
