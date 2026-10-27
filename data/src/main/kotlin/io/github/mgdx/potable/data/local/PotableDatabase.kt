package io.github.mgdx.potable.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.mgdx.potable.data.UnitKey
import java.util.concurrent.atomic.AtomicLong

/**
 * The local store of SPEC §7: followed units and the cache of the units looked at,
 * never a national data set. Schemas are exported to `data/schemas/`.
 * Build it once per process, with [open].
 *
 * Versions: 1, follows and latest-sample cache; 2, adds the
 * history cache (`serie`, `serie_point`, `historique_prelevement`,
 * `historique_synchro`), new tables only, hence an automatic
 * migration that keeps every v1 row; 3, adds `prelevement.dernier` (1 by
 * default: every v2 row was written by a refresh) so that a sample kept from
 * the history is never taken for a latest one, automatic migration too.
 */
@Database(
    entities = [
        FollowEntity::class,
        SampleEntity::class,
        ResultEntity::class,
        SyncEntity::class,
        SeriesEntity::class,
        SeriesPointEntity::class,
        TimelineSampleEntity::class,
        TimelineSyncEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class PotableDatabase : RoomDatabase() {
    internal abstract fun follows(): FollowDao

    internal abstract fun cache(): CacheDao

    internal abstract fun history(): HistoryDao

    /**
     * Incremented by every purge of the cache: whole, of a commune, or of the
     * units no longer referenced. A fetch reads it before calling Hub'Eau
     * and writes its result only if its unit was not purged meanwhile
     * ([unchangedSince]): a purge made during the request is not undone by
     * it, and a purge of other units does not drop it.
     */
    internal val cacheGeneration = AtomicLong()

    /** Guards the generations below, at which the last purges happened. */
    private val purges = Any()
    private var allPurgedAt = 0L
    private val communesPurgedAt = HashMap<String, Long>()
    private val unitsPurgedAt = HashMap<UnitKey, Long>()

    /** Records a purge of the whole cache; called in its transaction. */
    internal fun markAllPurged() = synchronized(purges) {
        allPurgedAt = cacheGeneration.incrementAndGet()
        // Older marks are covered by this one.
        communesPurgedAt.clear()
        unitsPurgedAt.clear()
    }

    /** Records a purge of every unit of [communeCode]; called in its transaction. */
    internal fun markCommunePurged(communeCode: String) = synchronized(purges) {
        communesPurgedAt[communeCode] = cacheGeneration.incrementAndGet()
    }

    /** Records a purge of [units]; called in its transaction. */
    internal fun markUnitsPurged(units: Collection<UnitKey>) = synchronized(purges) {
        val generation = cacheGeneration.incrementAndGet()
        units.forEach { unitsPurgedAt[it] = generation }
    }

    /**
     * Whether neither the whole cache nor [communeCode] was purged since
     * [generation] was read from [cacheGeneration].
     */
    internal fun unchangedSince(generation: Long, communeCode: String): Boolean =
        synchronized(purges) {
            allPurgedAt <= generation && (communesPurgedAt[communeCode] ?: 0) <= generation
        }

    /**
     * Whether the unit [udiCode] of [communeCode] was not purged since
     * [generation] was read from [cacheGeneration], by any purge that
     * covers it.
     */
    internal fun unchangedSince(generation: Long, communeCode: String, udiCode: String): Boolean =
        synchronized(purges) {
            unchangedSince(generation, communeCode) &&
                (unitsPurgedAt[UnitKey(communeCode, udiCode)] ?: 0) <= generation
        }

    companion object {
        const val FILE_NAME = "potable.db"

        fun open(context: Context): PotableDatabase = Room
            .databaseBuilder(context.applicationContext, PotableDatabase::class.java, FILE_NAME)
            .build()
    }
}
