package io.github.mgdx.potable.core.model

/** Raw Hub'Eau compliance code of a sample, restituted, never interpreted. */
enum class Compliance {
    COMPLIANT,

    /** Compliant under a derogation granted by the authorities (code "D"). */
    COMPLIANT_UNDER_DEROGATION,
    NON_COMPLIANT,
    NOT_ASSESSED,
    UNKNOWN,
    ;

    companion object {
        /**
         * Maps a Hub'Eau `conformite_*_prelevement` code.
         *
         * Source: the API's swagger,
         * https://hubeau.eaufrance.fr/api/v1/qualite_eau_potable/api-docs
         * (read 2026-10-25), which lists for the four fields "vide / C :
         * conforme / N : non conforme / D : conforme dans le cadre d'une
         * dérogation / S : sans objet : lorsqu'aucun paramètre du type
         * considéré (bactériologique ou chimique) n'a été mesuré".
         *
         * "C" is [COMPLIANT], "D" [COMPLIANT_UNDER_DEROGATION] (kept apart
         * from [COMPLIANT] so that the derogation is restituted, not hidden),
         * "N" [NON_COMPLIANT], "S" [NOT_ASSESSED]. An empty code, null or any
         * undocumented value is [UNKNOWN].
         */
        fun fromCode(code: String?): Compliance = when (code) {
            "C" -> COMPLIANT
            "D" -> COMPLIANT_UNDER_DEROGATION
            "N" -> NON_COMPLIANT
            "S" -> NOT_ASSESSED
            else -> UNKNOWN
        }
    }
}
