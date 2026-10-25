package io.github.mgdx.potable.core.hubeau

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One row of `communes_udi`, field for field. */
@Serializable
data class UdiRow(
    @SerialName("code_commune") val codeCommune: String,
    @SerialName("nom_commune") val nomCommune: String? = null,
    @SerialName("nom_quartier") val nomQuartier: String? = null,
    @SerialName("code_reseau") val codeReseau: String,
    @SerialName("nom_reseau") val nomReseau: String? = null,
    /** ISO date, "2010-09-02". */
    @SerialName("debut_alim") val debutAlim: String? = null,
    /** Year as a string, "2026". */
    @SerialName("annee") val annee: String? = null,
)
