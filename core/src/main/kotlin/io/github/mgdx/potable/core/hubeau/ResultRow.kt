package io.github.mgdx.potable.core.hubeau

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of `resultats_dis`: one analysis result. Sample-level fields
 * (conclusion, compliance codes) are repeated on every row of a sample.
 */
@Serializable
data class ResultRow(
    @SerialName("code_prelevement")
    val codePrelevement: String,
    @SerialName("code_commune")
    val codeCommune: String,
    @SerialName("nom_commune")
    val nomCommune: String? = null,
    /** Absent from current responses, which carry [reseaux] instead. */
    @SerialName("code_reseau")
    val codeReseau: String? = null,
    @SerialName("reseaux")
    val reseaux: List<Network>? = null,
    /** ISO instant, "2026-08-26T12:50:00Z". */
    @SerialName("date_prelevement")
    val datePrelevement: String? = null,
    @SerialName("conclusion_conformite_prelevement")
    val conclusionConformitePrelevement: String? = null,
    @SerialName("conformite_limites_bact_prelevement")
    val conformiteLimitesBactPrelevement: String? = null,
    @SerialName("conformite_limites_pc_prelevement")
    val conformiteLimitesPcPrelevement: String? = null,
    @SerialName("conformite_references_bact_prelevement")
    val conformiteReferencesBactPrelevement: String? = null,
    @SerialName("conformite_references_pc_prelevement")
    val conformiteReferencesPcPrelevement: String? = null,
    @SerialName("nom_distributeur")
    val nomDistributeur: String? = null,
    @SerialName("nom_moa")
    val nomMoa: String? = null,
    /**
     * Facility upstream of the sampling point (treatment plant, reservoir,
     * or the network itself); the same on every row of a sample, absent on
     * some (Paris).
     */
    @SerialName("nom_installation_amont")
    val nomInstallationAmont: String? = null,
    /**
     * Null on some rows that describe the sample rather than a parameter
     * (Toulouse: "Prélèvement sous acréditation", `code_parametre_se`
     * `PLVACR`). Such a row still carries the sample-level fields.
     */
    @SerialName("code_parametre")
    val codeParametre: String? = null,
    @SerialName("libelle_parametre")
    val libelleParametre: String? = null,
    @SerialName("code_type_parametre")
    val codeTypeParametre: String? = null,
    @SerialName("resultat_alphanumerique")
    val resultatAlphanumerique: String? = null,
    @SerialName("resultat_numerique")
    val resultatNumerique: Double? = null,
    @SerialName("libelle_unite")
    val libelleUnite: String? = null,
    @SerialName("limite_qualite_parametre")
    val limiteQualiteParametre: String? = null,
    @SerialName("reference_qualite_parametre")
    val referenceQualiteParametre: String? = null,
    /**
     * Where the measure was made: "L" laboratory, "T" on site (terrain).
     * Paris publishes most pH twice in a sample, once of each.
     */
    @SerialName("code_lieu_analyse")
    val codeLieuAnalyse: String? = null,
) {
    /** One entry of `reseaux`: a network the sample point belongs to. */
    @Serializable
    data class Network(
        @SerialName("code")
        val code: String,
        @SerialName("nom")
        val nom: String? = null,
        /** Share of the supply, "100 %"; often absent. */
        @SerialName("debit")
        val debit: String? = null,
    )
}
