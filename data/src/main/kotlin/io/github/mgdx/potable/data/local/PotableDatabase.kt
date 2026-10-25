package io.github.mgdx.potable.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The local store of SPEC §7: followed units and the cache of the units looked at,
 * never a national data set. Schemas are exported to `data/schemas/`.
 * Build it once per process, with [open].
 *
 * Versions: 1, follows and latest-sample cache; 2, adds the
 * history cache (`serie`, `serie_point`, `historique_prelevement`,
 * `historique_synchro`), new tables only, hence an automatic
 * migration that keeps every v1 row.
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
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class PotableDatabase : RoomDatabase() {
    internal abstract fun follows(): FollowDao

    internal abstract fun cache(): CacheDao

    internal abstract fun history(): HistoryDao

    companion object {
        const val FILE_NAME = "potable.db"

        fun open(context: Context): PotableDatabase = Room
            .databaseBuilder(context.applicationContext, PotableDatabase::class.java, FILE_NAME)
            .build()
    }
}
