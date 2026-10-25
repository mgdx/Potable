package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.AnalysisResult

/**
 * The editorial table of the most consulted parameters (SPEC §4.3): they are
 * shown first on the sheet, each with a sentence explaining what it is for.
 *
 * Every code was checked against the API, not taken from memory (SPEC §4.3).
 * Check of 2026-10-25: the latest 20 000 rows of `resultats_dis` for Orléans
 * (45234), Paris (75056), Marseille (13055), Bordeaux (33063), Lyon (69123)
 * and Toulouse (31555), queried with
 * `resultats_dis?code_commune=…&size=20000&fields=code_parametre,libelle_parametre,code_parametre_se,code_type_parametre,libelle_unite&sort=desc`
 * (see `docs/hubeau-api.md`, "Paramètres mis en avant"):
 *
 * | Parameter | Code | `code_parametre_se` | `libelle_parametre` | Unit | Seen in |
 * |---|---|---|---|---|---|
 * | Nitrates | 1340 | NO3 | Nitrates (en NO3) | mg/L | all six |
 * | Hardness | 7972 | TH | Titre hydrotimétrique | °f | all six |
 * | Hardness | 1345 | TH | Titre hydrotimétrique | °f | all but Paris |
 * | pH | 1302 | PH | pH | unité pH | all six |
 * | Free chlorine | 1398 | CL2LIB | Chlore libre | mg(Cl2)/L | all six |
 *
 * A parameter may be published under several codes (hardness: 7972 and
 * 1345, same label, same unit), hence a set. Neighbouring parameters are
 * deliberately left out: "Chlore total" (1399), "pH d'équilibre à la t°
 * échantillon" (6488), "Titre alcalimétrique complet" (1347).
 */
enum class FeaturedParameter(val codes: Set<String>) {
    NITRATES(setOf("1340")),
    HARDNESS(setOf("7972", "1345")),
    PH(setOf("1302")),
    FREE_CHLORINE(setOf("1398")),
    ;

    companion object {
        private val byCode: Map<String, FeaturedParameter> =
            entries.flatMap { parameter -> parameter.codes.map { it to parameter } }.toMap()

        /** The featured parameter published under this `code_parametre`, if any. */
        fun of(code: String): FeaturedParameter? = byCode[code]
    }
}

object FeaturedParameters {
    /**
     * The featured parameters present among [results], in the order of
     * [FeaturedParameter], each with the first result published for it (API
     * order).
     */
    fun present(results: List<AnalysisResult>): List<Pair<FeaturedParameter, AnalysisResult>> {
        val found = LinkedHashMap<FeaturedParameter, AnalysisResult>()
        for (result in results) {
            val parameter = FeaturedParameter.of(result.parameterCode) ?: continue
            found.putIfAbsent(parameter, result)
        }
        return FeaturedParameter.entries.mapNotNull { parameter ->
            found[parameter]?.let { parameter to it }
        }
    }
}
