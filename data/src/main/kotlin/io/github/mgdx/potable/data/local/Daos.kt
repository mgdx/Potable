package io.github.mgdx.potable.data.local

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
     * (epoch ms), except the newest of each unit, so that a followed unit
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

    /** Deletes a unit's whole cache: samples, results, sync state. */
    @Query("DELETE FROM resultat WHERE code_commune = :communeCode AND code_reseau = :udiCode")
    suspend fun deleteUnitResults(communeCode: String, udiCode: String)

    @Query("DELETE FROM prelevement WHERE code_commune = :communeCode AND code_reseau = :udiCode")
    suspend fun deleteUnitSamples(communeCode: String, udiCode: String)

    @Query("DELETE FROM synchro WHERE code_commune = :communeCode AND code_reseau = :udiCode")
    suspend fun deleteUnitSync(communeCode: String, udiCode: String)

    @Query("DELETE FROM resultat")
    suspend fun deleteAllResults()

    @Query("DELETE FROM prelevement")
    suspend fun deleteAllSamples()

    @Query("DELETE FROM synchro")
    suspend fun deleteAllSync()
}
