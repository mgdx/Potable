package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.core.model.SeriesPoint

/**
 * Substances users look for first (PFAS, pesticides, a few pollutants in the
 * news), rarely analysed in every sample: the sheet shows the latest known
 * measure of each on the unit, however old within the history window
 * (SPEC §4.3, §5 F3), not that of the latest sample.
 *
 * Every code was checked against the API, not taken from memory (SPEC §4.3).
 * Check of 2026-10-28: the latest 20 000 rows of `resultats_dis` for Orléans
 * (45234), Paris (75056), Marseille (13055), Bordeaux (33063), Lyon (69123)
 * and Toulouse (31555), queried with
 * `resultats_dis?code_commune=…&size=20000&fields=code_parametre,libelle_parametre,libelle_unite&sort=desc`,
 * then each code over the whole history of the six communes, and the
 * labels absent from them nationwide with `libelle_parametre=…` (see
 * `docs/hubeau-api.md`, "Substances surveillées"):
 *
 * | Substance | Code | `libelle_parametre` | Unit | Limit published | Seen in |
 * |---|---|---|---|---|---|
 * | Sum of 20 PFAS | 8847 | Somme de 20 substances perfluoroalkylées (PFAS) | µg/L | <=0,1 µg/L | all six |
 * | Total pesticides | 6276 | Total des pesticides analysés | µg/L | <=0,5 µg/L | all six |
 * | TFA | 8858 | acide trifluoroacétique | µg/L | none | none of the six; 13 communes nationwide |
 * | Vinyl chloride | 1753 | Chlorure de vinyl monomère | µg/L | <=0.5 µg/L | all six |
 * | Lead | 1382 | Plomb | µg/L | <=10 µg/L | all six |
 * | Arsenic | 1369 | Arsenic (Orléans also: Arsenic dissous) | µg/L | <=10 µg/L | all six |
 * | Perchlorate | 6219 | Perchlorate | µg/L | none | all but Marseille and Lyon |
 * | Chlordecone | 1866 | Chlordécone | µg/L | <=0,1 µg/L | none of the six; overseas (check of 2026-10-29) |
 *
 * Chlordecone, sprayed on the banana plantations of Guadeloupe and
 * Martinique, is read in the overseas departments only ([readIn]): since
 * 2023, Hub'Eau has rows for 971, 972, 973, 974 and 976, and 3 for the whole
 * of mainland France (62). Its absence is said in 971 and 972 only
 * ([absenceShownIn]): elsewhere "not analysed" would only be noise.
 *
 * Each substance was found under a single code; the set allows more, as
 * for [FeaturedParameter.HARDNESS]. Deliberately left out: "Somme de 4
 * substances perfluoroalkylées (PFOA+PFNA+PFHXS+PFOS)" (9268) and each PFAS
 * alone (5347, 6561…), which the sum of 20 covers.
 */
enum class WatchedSubstance(
    val codes: Set<String>,
    /** The departments where it is read; null for everywhere. */
    private val readIn: Set<String>? = null,
    /** The departments where "not analysed" is said; null for everywhere it is read. */
    private val absenceShownIn: Set<String>? = null,
) {
    PFAS(setOf("8847")),
    TOTAL_PESTICIDES(setOf("6276")),
    TFA(setOf("8858")),
    VINYL_CHLORIDE(setOf("1753")),
    LEAD(setOf("1382")),
    ARSENIC(setOf("1369")),
    PERCHLORATE(setOf("6219")),
    CHLORDECONE(
        setOf("1866"),
        readIn = setOf("971", "972", "973", "974", "976"),
        absenceShownIn = setOf("971", "972"),
    ),
    ;

    /** Whether it is read for the commune of INSEE code [communeCode]. */
    fun isReadIn(communeCode: String): Boolean = readIn?.contains(department(communeCode)) ?: true

    /** Whether, read but never measured, it is said "not analysed" for [communeCode]. */
    fun isAbsenceShownIn(communeCode: String): Boolean = isReadIn(communeCode) &&
        (absenceShownIn?.contains(department(communeCode)) ?: true)

    companion object {
        private val byCode: Map<String, WatchedSubstance> =
            entries.flatMap { substance -> substance.codes.map { it to substance } }.toMap()

        /** The watched substance published under this `code_parametre`, if any. */
        fun of(code: String): WatchedSubstance? = byCode[code]

        /** The department of an INSEE commune code: 3 characters overseas ("97…"), else 2. */
        private fun department(communeCode: String): String =
            communeCode.take(if (communeCode.startsWith("97")) 3 else 2)
    }
}

/**
 * The latest known measure of [substance] on a unit: [point] as published,
 * with the label, unit and quality limit of the series ([ParameterSeries]:
 * those of the newest point's row). Never compared with the limit (SPEC §14).
 */
data class WatchedMeasure(
    val substance: WatchedSubstance,
    val point: SeriesPoint,
    val label: String,
    val unit: String?,
    val limitText: String?,
)

object WatchedSubstances {
    /** The substances read for the commune of INSEE code [communeCode], in table order. */
    fun readFor(communeCode: String): List<WatchedSubstance> =
        WatchedSubstance.entries.filter { it.isReadIn(communeCode) }

    /**
     * The substances of [series] read without any measure in the window, to
     * be said "not analysed" for [communeCode], in table order. A substance
     * missing from [series] (not read, or its read failed) is not one of
     * them: its absence is not known. Not analysed says nothing of whether
     * the substance is there (SPEC §14).
     */
    fun notAnalysed(
        series: Map<WatchedSubstance, ParameterSeries?>,
        communeCode: String,
    ): List<WatchedSubstance> = WatchedSubstance.entries.filter { substance ->
        series.containsKey(substance) &&
            series[substance]?.points.isNullOrEmpty() &&
            substance.isAbsenceShownIn(communeCode)
    }

    /**
     * The latest measure of each substance of [series], in the order of
     * [WatchedSubstance]; a substance without series or without any point is
     * left out. The latest is the newest point (then the highest sample
     * code, as [Series.parameterSeries] sorts them); within a sample, the
     * series already kept the laboratory measure over the on-site one.
     */
    fun latest(series: Map<WatchedSubstance, ParameterSeries?>): List<WatchedMeasure> =
        WatchedSubstance.entries.mapNotNull { substance ->
            val found = series[substance] ?: return@mapNotNull null
            val point = found.points.maxWithOrNull(
                compareBy<SeriesPoint>({ it.date }, { it.sampleCode }),
            ) ?: return@mapNotNull null
            WatchedMeasure(
                substance = substance,
                point = point,
                label = found.label,
                unit = found.unit,
                limitText = found.limitText?.takeIf { it.isNotBlank() },
            )
        }
}
