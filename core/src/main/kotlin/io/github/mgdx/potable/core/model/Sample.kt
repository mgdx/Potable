package io.github.mgdx.potable.core.model

import java.time.Instant

data class AnalysisResult(
    val parameterCode: String,
    val parameterLabel: String,
    /** `code_type_parametre`. */
    val parameterTypeCode: String?,
    /** `resultat_alphanumerique`. */
    val resultText: String?,
    /** `resultat_numerique`. */
    val resultNumber: Double?,
    /** `libelle_unite`. */
    val unit: String?,
    /** `limite_qualite_parametre`. */
    val qualityLimit: String?,
    /** `reference_qualite_parametre`. */
    val qualityReference: String?,
    /** `code_lieu_analyse`: "L" laboratory, "T" on site (terrain), or null. */
    val analysisPlace: String? = null,
) {
    /** Measured on site rather than in the laboratory. */
    val isOnSite: Boolean get() = analysisPlace == ON_SITE

    private companion object {
        const val ON_SITE = "T"
    }
}

data class Sample(
    /** `code_prelevement`. */
    val code: String,
    val communeCode: String,
    /** `code_reseau`, or the first of `reseaux`. */
    val networkCode: String?,
    val date: Instant,
    val conclusion: String?,
    val bacteriologicalLimits: Compliance,
    val physicochemicalLimits: Compliance,
    val bacteriologicalReferences: Compliance,
    val physicochemicalReferences: Compliance,
    /** `nom_distributeur`. */
    val distributor: String?,
    /** `nom_moa`. */
    val owner: String?,
    val results: List<AnalysisResult>,
    /** `nom_installation_amont`: the facility upstream of the sampling point. */
    val upstreamFacility: String? = null,
)
