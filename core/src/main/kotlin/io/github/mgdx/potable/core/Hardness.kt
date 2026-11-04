package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.AnalysisResult

/**
 * One hardness measure expressed in other units: German degrees (°dH) and
 * millimoles of calcium carbonate per litre. The same measure, not a
 * category (soft, hard…), which would be a verdict of our own (SPEC §14).
 */
data class HardnessEquivalents(val germanDegrees: Double, val millimolesPerLitre: Double)

/**
 * Converts the hardness published by Hub'Eau ("Titre hydrotimétrique", in
 * French degrees, `°f`, see `docs/hubeau-api.md`, "Paramètres mis en avant")
 * into the units dishwashers and softeners sold elsewhere use:
 * 1 °f = 0.56 °dH = 0.1 mmol/L.
 */
object Hardness {
    private const val GERMAN_DEGREES_PER_FRENCH_DEGREE = 0.56
    private const val MILLIMOLES_PER_FRENCH_DEGREE = 0.1

    /** The unit Hub'Eau publishes hardness in (`libelle_unite`). */
    const val FRENCH_DEGREES_UNIT = "°f"

    private val PLAIN_NUMBER = Regex("""[+-]?\d+(?:[.,]\d+)?""")

    fun equivalents(frenchDegrees: Double): HardnessEquivalents = HardnessEquivalents(
        germanDegrees = frenchDegrees * GERMAN_DEGREES_PER_FRENCH_DEGREE,
        millimolesPerLitre = frenchDegrees * MILLIMOLES_PER_FRENCH_DEGREE,
    )

    /**
     * The equivalents of [result] when it is a hardness ([FeaturedParameter.HARDNESS])
     * published as a plain number in `°f`; null otherwise: another parameter or
     * unit, no number, or a text that is not that number alone ("<1", "N.M.",
     * "<SEUIL"), whose number is a bound or a placeholder, not a measure.
     */
    fun equivalentsOf(result: AnalysisResult): HardnessEquivalents? {
        if (FeaturedParameter.of(result.parameterCode) != FeaturedParameter.HARDNESS) return null
        if (result.unit?.trim() != FRENCH_DEGREES_UNIT) return null
        val number = result.resultNumber ?: return null
        val text = result.resultText?.trim()
        if (!text.isNullOrEmpty() && !PLAIN_NUMBER.matches(text)) return null
        return equivalents(number)
    }
}
