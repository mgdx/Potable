package io.github.mgdx.potable.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface FollowDao {
    @Query("SELECT * FROM suivi ORDER BY id")
    fun observeAll(): Flow<List<FollowEntity>>

    @Query("SELECT * FROM suivi ORDER BY id")
    suspend fun all(): List<FollowEntity>

    @Query("SELECT * FROM suivi WHERE id = :id")
    suspend fun byId(id: Long): FollowEntity?

    @Query("SELECT * FROM suivi WHERE code_commune = :communeCode AND code_reseau = :udiCode")
    suspend fun find(communeCode: String, udiCode: String): FollowEntity?

    @Insert
    suspend fun insert(follow: FollowEntity): Long

    @Query("DELETE FROM suivi WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
internal interface CacheDao {
    /**
     * The latest samples a refresh cached for a unit ([SampleEntity.latest]),
     * taken less than a day before the newest of them, newest first (then by
     * code), re-emitted on every change of the table: those of the newest
     * published day are among them. A sample kept from the history is never
     * among them, even if it is newer.
     */
    @Query(
        "SELECT * FROM prelevement WHERE code_commune = :communeCode AND code_reseau = :udiCode " +
            "AND dernier = 1 AND date > (SELECT MAX(p.date) FROM prelevement AS p " +
            "WHERE p.code_commune = :communeCode AND p.code_reseau = :udiCode " +
            "AND p.dernier = 1) - $DAY_MS " +
            "ORDER BY date DESC, code_prelevement",
    )
    fun observeLatestSamples(communeCode: String, udiCode: String): Flow<List<SampleEntity>>

    /** One cached sample of a unit, latest or kept from the history, or null. */
    @Query(
        "SELECT * FROM prelevement WHERE code_commune = :communeCode AND code_reseau = :udiCode " +
            "AND code_prelevement = :sampleCode",
    )
    suspend fun sample(communeCode: String, udiCode: String, sampleCode: String): SampleEntity?

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
     * (epoch ms), except those taken less than a day before the newest
     * latest sample of their unit ([SampleEntity.latest]), so that a unit
     * always keeps its latest analyses (all of the newest day). A sample
     * kept from the history is purged like any other. Results follow by
     * cascade.
     */
    @Query(
        "DELETE FROM prelevement WHERE date < :cutoff AND date <= COALESCE((" +
            "SELECT MAX(p.date) FROM prelevement AS p " +
            "WHERE p.code_commune = prelevement.code_commune " +
            "AND p.code_reseau = prelevement.code_reseau AND p.dernier = 1) - $DAY_MS, :cutoff)",
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
     * Every unit with something cached, one row per unit: the last
     * successful fetch of any of its caches (latest sample, series,
     * timeline), null if none is known (a sample kept from the history has
     * no sync row), and the approximate size of its cached rows in every
     * cache table ([CachedUnitRow.size]); re-emitted on every change of
     * these tables. A sync row that only keeps the alert state of a
     * followed unit is not a cache.
     */
    @Query(
        "SELECT code_commune, code_reseau, MAX(dernier_succes) AS dernier_succes, " +
            "SUM(taille) AS taille FROM (" +
            "SELECT code_commune, code_reseau, dernier_succes, $SYNC_SIZE AS taille " +
            "FROM synchro WHERE dernier_succes IS NOT NULL " +
            "UNION ALL SELECT code_commune, code_reseau, dernier_succes, $SERIES_SIZE FROM serie " +
            "UNION ALL SELECT code_commune, code_reseau, dernier_succes, $SYNC_SIZE " +
            "FROM historique_synchro " +
            "UNION ALL SELECT code_commune, code_reseau, NULL, $SAMPLE_SIZE FROM prelevement " +
            "UNION ALL SELECT code_commune, code_reseau, NULL, $RESULT_SIZE FROM resultat " +
            "UNION ALL SELECT code_commune, code_reseau, NULL, $POINT_SIZE FROM serie_point " +
            "UNION ALL SELECT code_commune, code_reseau, NULL, $TIMELINE_SIZE " +
            "FROM historique_prelevement" +
            ") GROUP BY code_commune, code_reseau",
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

/** A day in milliseconds, for the queries on `prelevement.date`. */
private const val DAY_MS = 86_400_000L

/*
 * Approximate size of a cached row, in bytes, for the settings (SPEC §5 F8):
 * the UTF-8 length of every text column (0 if null) plus 8 bytes per
 * numeric or boolean column. It leaves out SQLite's own overhead (record
 * headers, pages, indices), so the file takes more: an order of magnitude,
 * not the room freed on disk by a purge.
 */

/** The UTF-8 length of the unit's codes, as SQL; and so on per table below. */
private const val UNIT_TEXT =
    "IFNULL(LENGTH(CAST(code_commune AS BLOB)), 0) + IFNULL(LENGTH(CAST(code_reseau AS BLOB)), 0)"

private const val SYNC_SIZE = "($UNIT_TEXT + 16)"

private const val COMPLIANCE_TEXT =
    "IFNULL(LENGTH(CAST(conclusion AS BLOB)), 0) " +
        "+ IFNULL(LENGTH(CAST(conformite_limites_bact AS BLOB)), 0) " +
        "+ IFNULL(LENGTH(CAST(conformite_limites_pc AS BLOB)), 0) " +
        "+ IFNULL(LENGTH(CAST(conformite_references_bact AS BLOB)), 0) " +
        "+ IFNULL(LENGTH(CAST(conformite_references_pc AS BLOB)), 0)"

private const val LABELS_TEXT =
    "IFNULL(LENGTH(CAST(code_parametre AS BLOB)), 0) " +
        "+ IFNULL(LENGTH(CAST(libelle AS BLOB)), 0) " +
        "+ IFNULL(LENGTH(CAST(unite AS BLOB)), 0) " +
        "+ IFNULL(LENGTH(CAST(limite AS BLOB)), 0) " +
        "+ IFNULL(LENGTH(CAST(reference AS BLOB)), 0)"

private const val SAMPLE_CODE_TEXT = "IFNULL(LENGTH(CAST(code_prelevement AS BLOB)), 0)"

private const val SAMPLE_SIZE =
    "($UNIT_TEXT + $SAMPLE_CODE_TEXT + $COMPLIANCE_TEXT " +
        "+ IFNULL(LENGTH(CAST(reseau_prelevement AS BLOB)), 0) " +
        "+ IFNULL(LENGTH(CAST(distributeur AS BLOB)), 0) " +
        "+ IFNULL(LENGTH(CAST(maitre_ouvrage AS BLOB)), 0) + 16)"

private const val RESULT_SIZE =
    "($UNIT_TEXT + $SAMPLE_CODE_TEXT + $LABELS_TEXT " +
        "+ IFNULL(LENGTH(CAST(type_parametre AS BLOB)), 0) " +
        "+ IFNULL(LENGTH(CAST(resultat_texte AS BLOB)), 0) + 16)"

private const val SERIES_SIZE =
    "($UNIT_TEXT + IFNULL(LENGTH(CAST(cle_parametres AS BLOB)), 0) + $LABELS_TEXT + 16)"

private const val POINT_SIZE =
    "($UNIT_TEXT + IFNULL(LENGTH(CAST(cle_parametres AS BLOB)), 0) + $SAMPLE_CODE_TEXT " +
        "+ IFNULL(LENGTH(CAST(resultat_texte AS BLOB)), 0) + 24)"

private const val TIMELINE_SIZE = "($UNIT_TEXT + $SAMPLE_CODE_TEXT + $COMPLIANCE_TEXT + 8)"

/**
 * A unit with something cached, its last successful fetch (epoch ms) and
 * the approximate size of its cached rows in bytes, see
 * [CacheDao.observeCachedUnits].
 */
internal data class CachedUnitRow(
    @ColumnInfo(name = "code_commune") val communeCode: String,
    @ColumnInfo(name = "code_reseau") val udiCode: String,
    @ColumnInfo(name = "dernier_succes") val lastSuccess: Long?,
    @ColumnInfo(name = "taille") val size: Long,
)
