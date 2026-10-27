package io.github.mgdx.potable.data

import androidx.room.withTransaction
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.data.local.PotableDatabase
import io.github.mgdx.potable.data.local.resultEntities
import io.github.mgdx.potable.data.local.toEntity
import io.github.mgdx.potable.data.local.toSample
import io.github.mgdx.potable.data.preferences.AppPreferences
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.ZoneId

/**
 * [SampleRepository] that answers from the cache (tables `prelevement` and
 * `resultat`) when the sample of the unit is there, whether a refresh or an
 * earlier report put it there, and otherwise asks [network] and keeps its
 * answer (SPEC §7 as amended, `docs/decisions.md`). A sample stored here is
 * not one of the unit's latest ([io.github.mgdx.potable.data.local.SampleEntity.latest]
 * is false), so that the sheet never takes it for one; it goes with the
 * retention purge, [WaterRepository.purgeCommune] and [WaterRepository.purgeCache].
 */
class CachingSampleRepository(
    private val network: SampleRepository,
    private val database: PotableDatabase,
    private val preferences: AppPreferences,
    private val clock: Clock = Clock.systemUTC(),
) : SampleRepository {
    private val cache = database.cache()

    override suspend fun sample(communeCode: String, udiCode: String, sampleCode: String): Sample? {
        cache.sample(communeCode, udiCode, sampleCode)?.let {
            return it.toSample(cache.results(communeCode, udiCode, sampleCode))
        }
        val generation = database.cacheGeneration.get()
        val fetched = network.sample(communeCode, udiCode, sampleCode) ?: return null
        val now = clock.instant()
        val retention = preferences.retentionYears.first()
        database.withTransaction {
            // Purged while the request ran: it stays purged.
            if (!database.unchangedSince(generation, communeCode, udiCode)) return@withTransaction
            // A refresh may have stored it meanwhile, as one of the latest.
            if (cache.sample(communeCode, udiCode, sampleCode) != null) return@withTransaction
            cache.insertSample(fetched.toEntity(communeCode, udiCode, latest = false))
            cache.insertResults(fetched.resultEntities(communeCode, udiCode))
            cache.purgeOlderThan(
                now.atZone(PARIS).minusYears(retention.toLong()).toInstant().toEpochMilli(),
            )
        }
        return fetched
    }

    private companion object {
        /** The retention is counted in France, as for the latest samples (SPEC §7). */
        val PARIS: ZoneId = ZoneId.of("Europe/Paris")
    }
}
