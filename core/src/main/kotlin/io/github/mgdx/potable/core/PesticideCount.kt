package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.AnalysisResult

/**
 * How many pesticides and breakdown products a sample searched for
 * ([analysed]) and how many of them it quantified ([quantified]): facts of
 * the sample, not a verdict (SPEC §14). Quantified means a measure above the
 * quantification limit of the laboratory, whatever the quality limit.
 */
data class PesticideCount(val analysed: Int, val quantified: Int)

/**
 * Counts the pesticides of a sample ([ParameterFamilies]): each parameter
 * once (the laboratory result when there are two), sums left out, as they
 * would count their substances twice ("Total des pesticides analysés",
 * "Atrazine et ses métabolites", "Somme DDT, DDD, DDE", "DDT somme",
 * "Endosulfan total").
 *
 * A result is quantified when its published text is not a bound
 * ([Series.isCensored]: "<0,005") and its number is above zero; a text
 * without a number ("N.M.", "Traces") is not. As for the families, the count
 * inherits their limits: an organic compound missing from their lists and
 * measured in µg/L counts as a pesticide.
 */
object PesticideCounts {
    /** The count for [results]; null when none of them is a pesticide. */
    fun of(results: List<AnalysisResult>): PesticideCount? {
        val pesticides = results
            .filter { ParameterFamilies.of(it) == ParameterFamily.PESTICIDES && !isSum(it) }
            .groupBy { it.parameterCode }
            .map { (_, same) -> same.firstOrNull { !it.isOnSite } ?: same.first() }
        if (pesticides.isEmpty()) return null
        return PesticideCount(
            analysed = pesticides.size,
            quantified = pesticides.count(::isQuantified),
        )
    }

    private fun isQuantified(result: AnalysisResult): Boolean =
        !Series.isCensored(result.resultText) && (result.resultNumber ?: 0.0) > 0.0

    private fun isSum(result: AnalysisResult): Boolean {
        val label = TextNormalizer.normalize(result.parameterLabel)
        val words = label.split(' ')
        return result.parameterCode == TOTAL_PESTICIDES ||
            words.firstOrNull() in sumFirstWords ||
            words.lastOrNull() in sumLastWords ||
            label.contains("et ses metabolites")
    }

    private const val TOTAL_PESTICIDES = "6276"

    private val sumFirstWords = setOf("total", "somme")

    private val sumLastWords = setOf("total", "totaux", "somme")
}
