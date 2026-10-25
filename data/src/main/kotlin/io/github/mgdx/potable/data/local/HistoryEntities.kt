package io.github.mgdx.potable.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/*
 * History cache of the units looked at (SPEC §5 F5, §7), schema v2. Kept apart
 * from the "latest sample" tables (`prelevement`, `resultat`, `synchro`),
 * whose rows hold full results: a history only needs one value per sample,
 * or a sample's header. Same unit key (code_commune, code_reseau of the
 * unit) as the other cached rows. Dates of coverage are epoch days of the
 * published date (read in UTC, see `docs/decisions.md`).
 */

/**
 * One cached series (table `serie`): the parameter's labels as last
 * published and its sync state. [parameterKey] is the requested codes,
 * sorted and comma-separated ("1345,7972"). A null [label] records that
 * Hub'Eau had no measure: the series is null, but fetched today.
 */
@Entity(tableName = "serie", primaryKeys = ["code_commune", "code_reseau", "cle_parametres"])
internal data class SeriesEntity(
    @ColumnInfo(name = "code_commune") val communeCode: String,
    @ColumnInfo(name = "code_reseau") val udiCode: String,
    @ColumnInfo(name = "cle_parametres") val parameterKey: String,
    @ColumnInfo(name = "code_parametre") val parameterCode: String?,
    val libelle: String?,
    val unite: String?,
    val limite: String?,
    val reference: String?,
    /** First day the cached points cover (epoch day): every point since is cached. */
    val depuis: Long,
    /** When the last successful Hub'Eau fetch of the series ended (epoch ms). */
    @ColumnInfo(name = "dernier_succes") val lastSuccess: Long,
)

/** One point of a cached series (table `serie_point`). */
@Entity(
    tableName = "serie_point",
    primaryKeys = ["code_commune", "code_reseau", "cle_parametres", "code_prelevement"],
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["code_commune", "code_reseau", "cle_parametres"],
            childColumns = ["code_commune", "code_reseau", "cle_parametres"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class SeriesPointEntity(
    @ColumnInfo(name = "code_commune") val communeCode: String,
    @ColumnInfo(name = "code_reseau") val udiCode: String,
    @ColumnInfo(name = "cle_parametres") val parameterKey: String,
    @ColumnInfo(name = "code_prelevement") val sampleCode: String,
    /** `date_prelevement`, epoch milliseconds. */
    val date: Long,
    @ColumnInfo(name = "resultat_numerique") val value: Double?,
    @ColumnInfo(name = "resultat_texte") val text: String?,
    val censure: Boolean,
)

/**
 * One sample of the cached timeline of conclusions (table
 * `historique_prelevement`); compliances as raw Hub'Eau codes, like
 * `prelevement`.
 */
@Entity(
    tableName = "historique_prelevement",
    primaryKeys = ["code_commune", "code_reseau", "code_prelevement"],
)
internal data class TimelineSampleEntity(
    @ColumnInfo(name = "code_commune") val communeCode: String,
    @ColumnInfo(name = "code_reseau") val udiCode: String,
    @ColumnInfo(name = "code_prelevement") val code: String,
    /** `date_prelevement`, epoch milliseconds. */
    val date: Long,
    val conclusion: String?,
    @ColumnInfo(name = "conformite_limites_bact") val bacteriologicalLimits: String?,
    @ColumnInfo(name = "conformite_limites_pc") val physicochemicalLimits: String?,
    @ColumnInfo(name = "conformite_references_bact") val bacteriologicalReferences: String?,
    @ColumnInfo(name = "conformite_references_pc") val physicochemicalReferences: String?,
)

/** Sync state of a unit's cached timeline (table `historique_synchro`). */
@Entity(tableName = "historique_synchro", primaryKeys = ["code_commune", "code_reseau"])
internal data class TimelineSyncEntity(
    @ColumnInfo(name = "code_commune") val communeCode: String,
    @ColumnInfo(name = "code_reseau") val udiCode: String,
    /** First day the cached samples cover (epoch day). */
    val depuis: Long,
    /** When the last successful Hub'Eau fetch of the timeline ended (epoch ms). */
    @ColumnInfo(name = "dernier_succes") val lastSuccess: Long,
)
