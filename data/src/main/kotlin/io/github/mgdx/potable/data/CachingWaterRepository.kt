package io.github.mgdx.potable.data

import androidx.room.withTransaction
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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * [WaterRepository] that answers [udis] and [latestSample] from [network]
 * (Hub'Eau, unchanged) and keeps the latest sample of every **followed** unit
 * in [database] (SPEC §7). Units that are not followed are never cached.
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

    override suspend fun udis(communeCode: String): UdiChoice = network.udis(communeCode)

    override suspend fun latestSample(communeCode: String, networkCode: String?): Sample? =
        network.latestSample(communeCode, networkCode)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun cachedLatestSample(communeCode: String, udiCode: String): Flow<CachedSample?> =
        combine(
            cache.observeSync(communeCode, udiCode),
            cache.observeLatestSample(communeCode, udiCode),
        ) { sync, sample -> sync to sample }
            .mapLatest { (sync, sample) ->
                val fetchedAt = sync?.lastSuccess ?: return@mapLatest null
                CachedSample(
                    sample = sample?.toSample(cache.results(communeCode, udiCode, sample.code)),
                    fetchedAt = Instant.ofEpochMilli(fetchedAt),
                )
            }.distinctUntilChanged()

    override suspend fun refreshFollowed(
        communeCode: String,
        udiCode: String,
        force: Boolean,
    ): Boolean = refreshing.withLock {
        if (follows.find(communeCode, udiCode) == null) return@withLock false
        if (!force && fetchedToday(cache.sync(communeCode, udiCode))) return@withLock false

        // Throws DataError: the cache and the sync state are left as they were.
        val sample = network.latestSample(communeCode, udiCode)
        val now = clock.instant()
        val retention = preferences.retentionYears.first()
        database.withTransaction {
            // The unit may have been unfollowed while the request ran.
            if (follows.find(communeCode, udiCode) == null) return@withTransaction
            if (sample != null) {
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

    override suspend fun purgeCache() {
        database.withTransaction {
            cache.deleteAllResults()
            cache.deleteAllSamples()
            cache.deleteAllSync()
            // The history cache too: same follows, same purge.
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
        val today = clock.instant().atZone(PARIS).toLocalDate()
        return Instant.ofEpochMilli(last).atZone(PARIS).toLocalDate() == today
    }

    private companion object {
        /** "Today" is the calendar day in France (SPEC §7). */
        val PARIS: ZoneId = ZoneId.of("Europe/Paris")
    }
}
