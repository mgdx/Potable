package io.github.mgdx.potable.core.model

data class Commune(
    /** INSEE code of this entry. */
    val code: String,
    /** Official name, with accents ("Orléans"). */
    val name: String,
    val postalCodes: List<String>,
    val latitude: Double,
    val longitude: Double,
    /**
     * INSEE code under which Hub'Eau publishes this place, when it differs
     * (delegated/former commune, municipal arrondissement). Null otherwise.
     */
    val hubEauCode: String? = null,
    /**
     * Municipal population (INSEE, via geo.api.gouv.fr) of a current commune
     * or an arrondissement; null when unknown (delegated or former commune).
     * Only used to rank search results.
     */
    val population: Int? = null,
) {
    val queryCode: String get() = hubEauCode ?: code
}
