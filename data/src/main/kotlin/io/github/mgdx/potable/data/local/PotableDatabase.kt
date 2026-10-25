package io.github.mgdx.potable.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
     * Incremented by every purge of the cache, whole or of a commune. A
     * fetch reads it before calling Hub'Eau and writes its result only if
     * it has not changed meanwhile ([unchangedSince]): a purge made during
     * the request is not undone by it.
     */
    internal val cacheGeneration = AtomicLong()

    /** Whether no purge happened since [generation] was read from [cacheGeneration]. */
    internal fun unchangedSince(generation: Long): Boolean = cacheGeneration.get() == generation

    companion object {
        const val FILE_NAME = "potable.db"

        fun open(context: Context): PotableDatabase = Room
            .databaseBuilder(context.applicationContext, PotableDatabase::class.java, FILE_NAME)
            .build()
    }
}
