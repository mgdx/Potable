package io.github.mgdx.potable.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

/** History cache of the units looked at (tables of `HistoryEntities.kt`). */
@Dao
internal interface HistoryDao {
    @Query(
        "SELECT * FROM serie WHERE code_commune = :communeCode AND code_reseau = :udiCode " +
            "AND cle_parametres = :parameterKey",
    )
    suspend fun series(communeCode: String, udiCode: String, parameterKey: String): SeriesEntity?

    @Query(
        "SELECT * FROM serie_point WHERE code_commune = :communeCode AND code_reseau = :udiCode " +
            "AND cle_parametres = :parameterKey AND date >= :from " +
            "ORDER BY date, code_prelevement",
    )
    suspend fun points(
        communeCode: String,
        udiCode: String,
        parameterKey: String,
        from: Long,
    ): List<SeriesPointEntity>

    /**
     * Inserts or updates the series row. An upsert, not a REPLACE: REPLACE
     * deletes the old row first, and its points would go with it by cascade.
     */
    @Upsert
    suspend fun upsertSeries(series: SeriesEntity)

    @Query(
        "DELETE FROM serie_point WHERE code_commune = :communeCode AND code_reseau = :udiCode " +
            "AND cle_parametres = :parameterKey AND date >= :from",
    )
    suspend fun deletePointsFrom(
        communeCode: String,
        udiCode: String,
        parameterKey: String,
        from: Long,
    )

    @Insert
    suspend fun insertPoints(points: List<SeriesPointEntity>)

    @Query(
        "SELECT * FROM historique_synchro " +
            "WHERE code_commune = :communeCode AND code_reseau = :udiCode",
    )
    suspend fun timelineSync(communeCode: String, udiCode: String): TimelineSyncEntity?

    @Query(
        "SELECT * FROM historique_prelevement " +
            "WHERE code_commune = :communeCode AND code_reseau = :udiCode AND date >= :from " +
            "ORDER BY date DESC, code_prelevement",
    )
    suspend fun timeline(
        communeCode: String,
        udiCode: String,
        from: Long,
    ): List<TimelineSampleEntity>

    @Upsert
    suspend fun upsertTimelineSync(sync: TimelineSyncEntity)

    @Insert
    suspend fun insertTimeline(samples: List<TimelineSampleEntity>)

    @Query(
        "DELETE FROM historique_prelevement " +
            "WHERE code_commune = :communeCode AND code_reseau = :udiCode AND date >= :from",
    )
    suspend fun deleteTimelineFrom(communeCode: String, udiCode: String, from: Long)

    /**
     * Retention purge (SPEC §7) of points and samples published before
     * [cutoff] (epoch ms); the coverage of every series and timeline then
     * starts no earlier than [cutoffDay] (epoch day), so that a longer window
     * goes back to the network instead of showing a gap.
     */
    @Query("DELETE FROM serie_point WHERE date < :cutoff")
    suspend fun purgePointsOlderThan(cutoff: Long)

    @Query("DELETE FROM historique_prelevement WHERE date < :cutoff")
    suspend fun purgeTimelineOlderThan(cutoff: Long)

    @Query("UPDATE serie SET depuis = :cutoffDay WHERE depuis < :cutoffDay")
    suspend fun clampSeriesCoverage(cutoffDay: Long)

    @Query("UPDATE historique_synchro SET depuis = :cutoffDay WHERE depuis < :cutoffDay")
    suspend fun clampTimelineCoverage(cutoffDay: Long)

    @Query("DELETE FROM serie_point WHERE code_commune = :communeCode")
    suspend fun deleteCommunePoints(communeCode: String)

    @Query("DELETE FROM serie WHERE code_commune = :communeCode")
    suspend fun deleteCommuneSeries(communeCode: String)

    @Query("DELETE FROM historique_prelevement WHERE code_commune = :communeCode")
    suspend fun deleteCommuneTimeline(communeCode: String)

    @Query("DELETE FROM historique_synchro WHERE code_commune = :communeCode")
    suspend fun deleteCommuneTimelineSync(communeCode: String)

    @Query("DELETE FROM serie")
    suspend fun deleteAllSeries()

    @Query("DELETE FROM serie_point")
    suspend fun deleteAllPoints()

    @Query("DELETE FROM historique_prelevement")
    suspend fun deleteAllTimeline()

    @Query("DELETE FROM historique_synchro")
    suspend fun deleteAllTimelineSync()

    @Query("SELECT COUNT(*) FROM serie_point")
    suspend fun pointCount(): Int

    @Query("SELECT COUNT(*) FROM historique_prelevement")
    suspend fun timelineCount(): Int
}
