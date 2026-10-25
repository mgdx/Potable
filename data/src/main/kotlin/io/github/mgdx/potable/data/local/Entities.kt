package io.github.mgdx.potable.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Tables of SPEC §7. Only public Hub'Eau data and the codes of the followed
 * units are stored: nothing about the user. A "unit" is the pair
 * (code_commune, code_reseau) the user follows; every cached row carries it,
 * so that unfollowing deletes exactly that unit's cache.
 */

/** A followed unit (table `suivi`). */
@Entity(
    tableName = "suivi",
    indices = [Index(value = ["code_commune", "code_reseau"], unique = true)],
)
internal data class FollowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "code_commune") val communeCode: String,
    @ColumnInfo(name = "nom_commune") val communeName: String,
    @ColumnInfo(name = "code_reseau") val udiCode: String,
    @ColumnInfo(name = "nom_reseau") val udiName: String,
    val position: Int,
    val principale: Boolean,
)

/**
 * A cached sample of a followed unit (table `prelevement`). The key includes
 * the unit: Hub'Eau publishes the same `code_prelevement` for every commune
 * (and network) the sampling point serves, and two followed units must not
 * share, nor delete, each other's rows.
 *
 * The four compliances are kept as their raw Hub'Eau code (C, D, N, S, or
 * null), never as an interpretation.
 */
@Entity(
    tableName = "prelevement",
    primaryKeys = ["code_commune", "code_reseau", "code_prelevement"],
)
internal data class SampleEntity(
    @ColumnInfo(name = "code_commune") val communeCode: String,
    /** The followed unit's network, the one the sample was queried with. */
    @ColumnInfo(name = "code_reseau") val udiCode: String,
    @ColumnInfo(name = "code_prelevement") val code: String,
    /** `Sample.networkCode` as published (first of `reseaux`), may differ from [udiCode]. */
    @ColumnInfo(name = "reseau_prelevement") val sampleNetworkCode: String?,
    /** `date_prelevement`, epoch milliseconds. */
    val date: Long,
    val conclusion: String?,
    @ColumnInfo(name = "conformite_limites_bact") val bacteriologicalLimits: String?,
    @ColumnInfo(name = "conformite_limites_pc") val physicochemicalLimits: String?,
    @ColumnInfo(name = "conformite_references_bact") val bacteriologicalReferences: String?,
    @ColumnInfo(name = "conformite_references_pc") val physicochemicalReferences: String?,
    val distributeur: String?,
    @ColumnInfo(name = "maitre_ouvrage") val owner: String?,
)

/** One analysed parameter of a cached sample (table `resultat`). */
@Entity(
    tableName = "resultat",
    primaryKeys = ["code_commune", "code_reseau", "code_prelevement", "ordre"],
    foreignKeys = [
        ForeignKey(
            entity = SampleEntity::class,
            parentColumns = ["code_commune", "code_reseau", "code_prelevement"],
            childColumns = ["code_commune", "code_reseau", "code_prelevement"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class ResultEntity(
    @ColumnInfo(name = "code_commune") val communeCode: String,
    @ColumnInfo(name = "code_reseau") val udiCode: String,
    @ColumnInfo(name = "code_prelevement") val sampleCode: String,
    /** Position in the published list, so that the order reads back unchanged. */
    val ordre: Int,
    @ColumnInfo(name = "code_parametre") val parameterCode: String,
    val libelle: String,
    @ColumnInfo(name = "type_parametre") val parameterTypeCode: String?,
    @ColumnInfo(name = "resultat_texte") val resultText: String?,
    @ColumnInfo(name = "resultat_numerique") val resultNumber: Double?,
    val unite: String?,
    val limite: String?,
    val reference: String?,
)

/** Sync state of a followed unit (table `synchro`). */
@Entity(tableName = "synchro", primaryKeys = ["code_commune", "code_reseau"])
internal data class SyncEntity(
    @ColumnInfo(name = "code_commune") val communeCode: String,
    @ColumnInfo(name = "code_reseau") val udiCode: String,
    /**
     * Date (epoch ms) of the newest sample the alert task has seen (SPEC
     * §5 F7). Written only by the alert task, never by a refresh.
     */
    @ColumnInfo(name = "dernier_prelevement_vu") val lastSeenSampleDate: Long?,
    /** When the last successful Hub'Eau fetch of the unit ended (epoch ms). */
    @ColumnInfo(name = "dernier_succes") val lastSuccess: Long?,
)
