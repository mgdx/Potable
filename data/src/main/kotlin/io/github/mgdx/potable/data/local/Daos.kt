package io.github.mgdx.potable.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface FollowDao {
    @Query("SELECT * FROM suivi ORDER BY position, id")
    fun observeAll(): Flow<List<FollowEntity>>

    @Query("SELECT * FROM suivi ORDER BY position, id")
    suspend fun all(): List<FollowEntity>

    @Query("SELECT * FROM suivi WHERE id = :id")
    suspend fun byId(id: Long): FollowEntity?

    @Query("SELECT * FROM suivi WHERE code_commune = :communeCode AND code_reseau = :udiCode")
    suspend fun find(communeCode: String, udiCode: String): FollowEntity?

    @Insert
    suspend fun insert(follow: FollowEntity): Long

    @Query("DELETE FROM suivi WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE suivi SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Query("UPDATE suivi SET principale = (id = :id)")
    suspend fun makePrimary(id: Long)
}

@Dao
internal interface CacheDao {
    /** The newest cached sample of a unit, re-emitted on every change of the table. */
    @Query(
        "SELECT * FROM prelevement WHERE code_commune = :communeCode AND code_reseau = :udiCode " +
            "ORDER BY date DESC, code_prelevement DESC LIMIT 1",
    )
    fun observeLatestSample(communeCode: String, udiCode: String): Flow<SampleEntity?>

    @Query(
        "SELECT * FROM resultat WHERE code_commune = :communeCode AND code_reseau = :udiCode " +
            "AND code_prelevement = :sampleCode ORDER BY ordre",
    )
    suspend fun results(
        communeCode: String,
        udiCode: String,
        sampleCode: String,
    ): List<ResultEntity>

    @Insert
    suspend fun insertSample(sample: SampleEntity)

    @Insert
    suspend fun insertResults(results: List<ResultEntity>)

    @Query(
        "DELETE FROM resultat WHERE code_commune = :communeCode AND code_reseau = :udiCode " +
            "AND code_prelevement = :sampleCode",
    )
    suspend fun deleteResults(communeCode: String, udiCode: String, sampleCode: String)

    @Query(
        "DELETE FROM prelevement WHERE code_commune = :communeCode AND code_reseau = :udiCode " +
            "AND code_prelevement = :sampleCode",
    )
    suspend fun deleteSample(communeCode: String, udiCode: String, sampleCode: String)

    /**
     * Retention purge (SPEC §7): deletes the samples dated before [cutoff]
     * (epoch ms), except the newest of each unit, so that a unit
     * always keeps its latest analysis. Results follow by cascade.
     */
    @Query(
        "DELETE FROM prelevement WHERE date < :cutoff AND date < (" +
            "SELECT MAX(p.date) FROM prelevement AS p " +
            "WHERE p.code_commune = prelevement.code_commune " +
            "AND p.code_reseau = prelevement.code_reseau)",
    )
    suspend fun purgeOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM prelevement")
    suspend fun sampleCount(): Int

    @Query("SELECT * FROM synchro WHERE code_commune = :communeCode AND code_reseau = :udiCode")
    fun observeSync(communeCode: String, udiCode: String): Flow<SyncEntity?>

    @Query("SELECT * FROM synchro WHERE code_commune = :communeCode AND code_reseau = :udiCode")
    suspend fun sync(communeCode: String, udiCode: String): SyncEntity?

    /** Creates an empty sync row if the unit has none; keeps an existing one. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSyncIfAbsent(sync: SyncEntity)

    @Query(
        "UPDATE synchro SET dernier_succes = :at " +
            "WHERE code_commune = :communeCode AND code_reseau = :udiCode",
    )
    suspend fun setLastSuccess(communeCode: String, udiCode: String, at: Long)

    @Query(
        "UPDATE synchro SET dernier_prelevement_vu = :date " +
            "WHERE code_commune = :communeCode AND code_reseau = :udiCode",
    )
    suspend fun setLastSeenSampleDate(communeCode: String, udiCode: String, date: Long?)

    /** Forgets the date the alert task has seen for a unit (it is no longer followed). */
    @Query(
        "UPDATE synchro SET dernier_prelevement_vu = NULL " +
            "WHERE code_commune = :communeCode AND code_reseau = :udiCode",
    )
    suspend fun clearLastSeenSampleDate(communeCode: String, udiCode: String)

    /**
     * Every unit with something cached, and the last successful fetch of
     * each of its caches (latest sample, series, timeline), one row per
     * cache; re-emitted on every change of these tables.
     */
    @Query(
        "SELECT code_commune, code_reseau, dernier_succes FROM synchro " +
            "WHERE dernier_succes IS NOT NULL " +
            "UNION ALL SELECT code_commune, code_reseau, dernier_succes FROM serie " +
            "UNION ALL SELECT code_commune, code_reseau, dernier_succes FROM historique_synchro",
    )
    fun observeCachedUnits(): Flow<List<CachedUnitRow>>

    @Query("DELETE FROM resultat WHERE code_commune = :communeCode")
    suspend fun deleteCommuneResults(communeCode: String)

    @Query("DELETE FROM prelevement WHERE code_commune = :communeCode")
    suspend fun deleteCommuneSamples(communeCode: String)

    /**
     * Forgets a commune's fetches, keeping only the date the alert task has
     * seen for a followed unit ([SyncEntity.lastSeenSampleDate]).
     */
    @Query("UPDATE synchro SET dernier_succes = NULL WHERE code_commune = :communeCode")
    suspend fun clearCommuneLastSuccess(communeCode: String)

    @Query(
        "DELETE FROM synchro WHERE code_commune = :communeCode " +
            "AND dernier_prelevement_vu IS NULL",
    )
    suspend fun deleteCommuneSyncWithoutLastSeen(communeCode: String)

    @Query("DELETE FROM resultat")
    suspend fun deleteAllResults()

    @Query("DELETE FROM prelevement")
    suspend fun deleteAllSamples()

    @Query("DELETE FROM synchro")
    suspend fun deleteAllSync()
}

/** One cache of a unit and its last successful fetch (epoch ms), see [CacheDao.observeCachedUnits]. */
internal data class CachedUnitRow(
    @ColumnInfo(name = "code_commune") val communeCode: String,
    @ColumnInfo(name = "code_reseau") val udiCode: String,
    @ColumnInfo(name = "dernier_succes") val lastSuccess: Long?,
)
