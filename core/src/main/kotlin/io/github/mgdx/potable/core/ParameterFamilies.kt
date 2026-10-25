package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.AnalysisResult
import java.util.Locale

/** Presentation groups of the analysed parameters (SPEC §5 F4). */
enum class ParameterFamily {
    MICROBIOLOGY,
    PHYSICOCHEMISTRY,
    DISINFECTION,
    PESTICIDES,
    METALS,
    ORGANIC_COMPOUNDS,
    RADIOACTIVITY,
    OTHER,
}

/**
 * Sorts a result into a [ParameterFamily], to group the list of a sample.
 * This is a **presentation** order, never a judgement on the result: the
 * family says nothing of the value, of its limit or of the conclusion.
 *
 * Hub'Eau publishes no family. Probe of 2026-10-25 (latest 20 000 rows of six
 * large communes, 836 distinct parameters, see `docs/hubeau-api.md`):
 * `code_type_parametre` only tells numeric (`N`) from qualitative (`O`)
 * results, and `code_parametre_se` is an abbreviation of the label, with no
 * grouping either. The rule therefore reads the unit and the label, as
 * published (after [TextNormalizer.normalize]), and stops at the first match:
 *
 * 1. **Radioactivity** — unit in becquerels or sieverts (`Bq/L`, `mSv/a`).
 * 2. **Microbiology** — unit counting organisms (`n/(100mL)`, `n/mL`,
 *    `n/(10L)`, `UFC/L`, `NPP`), or a label naming them ("Bactéries…",
 *    "Entérocoques…", "Escherichia coli…", "Legionella…", "Kystes…").
 * 3. **Disinfection** — disinfectant residuals and disinfection by-products:
 *    "Chlore libre", "Chlore total", "Bioxyde de chlore", "Chlorite",
 *    "Chlorate", "Bromates", trihalomethanes and haloacetic acids.
 * 4. **Metals** — the label starts with the name of a metal or metalloid
 *    ("Fer total", "Manganèse total", "Plomb", "Bore mg/L", "Uranium en µg/l");
 *    "Activité Plomb 210" stays under radioactivity.
 * 5. **Other** — results that are not a substance: "Prélèvement sous
 *    accréditation", "Chlorophylle A", microcystins.
 * 6. **Physico-chemistry**, for the inorganic ions measured in µg/L
 *    ("Perchlorate", "Cyanures totaux").
 * 7. **Organic compounds** — solvents and aromatics (words ending in
 *    "-benzène", "-toluène", "-éthane", "-méthane", "-éthylène"…), polycyclic
 *    aromatic hydrocarbons, PFAS ("perfluoro…"), PCB, bisphenol A,
 *    nonylphenol, ethers, organotins, acrylamide, epichlorohydrin, vinyl
 *    chloride, 17b-estradiol, benzotriazole.
 * 8. **Pesticides** — any other result measured in `µg/L` or `ng/L`: in the
 *    probe, the remaining ~600 parameters in these units are all pesticides,
 *    their metabolites ("ESA metolachlore", "AMPA") or their sums ("Total des
 *    pesticides analysés").
 * 9. **Physico-chemistry** — any other result with a unit, qualitative ones
 *    included (`SANS OBJET`: aspect, colour, odour, taste).
 * 10. **Other** — no unit at all.
 *
 * Limits, accepted for a presentation group: an organic compound missing from
 * rule 7 and measured in µg/L lands under pesticides; hexachlorobenzene and
 * pentachlorobenzene (pesticides as well as industrial compounds) land under
 * organic compounds, chlorophenols under pesticides. A new parameter is never
 * lost: at worst it lands under [ParameterFamily.OTHER].
 */
object ParameterFamilies {
    fun of(result: AnalysisResult): ParameterFamily {
        val unit = result.unit?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val label = TextNormalizer.normalize(result.parameterLabel)
        val words = label.split(' ')
        return when {
            isRadioactivity(unit, label) -> ParameterFamily.RADIOACTIVITY
            isMicrobiology(unit, words) -> ParameterFamily.MICROBIOLOGY
            disinfection.any { label == it || label.startsWith("$it ") } ->
                ParameterFamily.DISINFECTION
            words.first() in metals -> ParameterFamily.METALS
            notSubstances.any { label.startsWith(it) } || label.contains("microcystine") ->
                ParameterFamily.OTHER
            words.first() in inorganicTraces -> ParameterFamily.PHYSICOCHEMISTRY
            isOrganic(label, words) -> ParameterFamily.ORGANIC_COMPOUNDS
            unit in traceUnits -> ParameterFamily.PESTICIDES
            unit.isNotEmpty() -> ParameterFamily.PHYSICOCHEMISTRY
            else -> ParameterFamily.OTHER
        }
    }

    private fun isRadioactivity(unit: String, label: String) =
        unit.contains("bq") || unit.contains("sv") || label.startsWith("dose indicative")

    private fun isMicrobiology(unit: String, words: List<String>) = unit.startsWith("n/") ||
        unit.contains("ufc") ||
        unit.contains("npp") ||
        organisms.any { prefix -> words.any { it.startsWith(prefix) } }

    private fun isOrganic(label: String, words: List<String>) =
        organicPhrases.any { label.contains(it) } ||
            words.any { word ->
                organicWordStarts.any { word.startsWith(it) } ||
                    organicWordEnds.any { word.endsWith(it) }
            }

    private val traceUnits = setOf("µg/l", "ng/l")

    private val organisms = listOf(
        "bact", "coliforme", "enterocoque", "escherichia", "legionel", "pseudomonas",
        "kystes", "oocystes", "spores", "germes", "clostridium", "staphylocoque", "salmonel",
    )

    /** Normalised labels, or label prefixes followed by a space. */
    private val disinfection = listOf(
        "chlore", "bioxyde de chlore", "dioxyde de chlore", "chlorite", "chlorites",
        "chlorate", "chlorates", "bromate", "bromates", "ozone", "trihalomethanes",
        "acides haloacetiques", "acide monochloroacetique", "acide dichloroacetique",
        "acide trichloroacetique", "acide bromoacetique", "acide dibromoacetique",
        "chloroforme", "bromoforme", "chlorodibromomethane", "dichloromonobromomethane",
    )

    /** First word of the label. */
    private val metals = setOf(
        "aluminium", "antimoine", "argent", "arsenic", "baryum", "beryllium", "bore",
        "cadmium", "chrome", "cobalt", "cuivre", "etain", "fer", "manganese", "mercure",
        "molybdene", "nickel", "plomb", "selenium", "thallium", "titane", "uranium",
        "vanadium", "zinc",
    )

    private val notSubstances = listOf("prelevement", "chlorophylle")

    private val inorganicTraces = setOf("perchlorate", "cyanures")

    private val organicPhrases = listOf("chlorure de vinyl", "tetrachlorure de carbone")

    private val organicWordStarts = listOf(
        "perfluoro", "pfas", "pcb", "benzo(", "dibenzo(", "indeno(", "hydrocarbures",
        "anthraquinone", "styrene", "biphenyle", "bisphenol", "nonylphenol", "cresol",
        "estradiol", "benzotriazole", "acrylamide", "epichlorohydrine", "chloroprene",
        "ether", "dibutyletain", "tributyltin",
    )

    private val organicWordEnds = listOf(
        "benzene", "toluene", "xylene", "xylenes", "ethane", "methane", "ethylene", "cumene",
        "mesitylene", "anthracene", "fluoranthene",
        "pyrene", "naphtalene", "phenantrene", "phenanthrene", "chrysene", "fluorene",
        "perylene", "acenaphtene", "acenaphthylene",
    )
}
