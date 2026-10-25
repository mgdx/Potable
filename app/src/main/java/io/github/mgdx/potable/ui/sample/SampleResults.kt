package io.github.mgdx.potable.ui.sample

import io.github.mgdx.potable.core.ParameterFamilies
import io.github.mgdx.potable.core.ParameterFamily
import io.github.mgdx.potable.core.TextNormalizer
import io.github.mgdx.potable.core.model.AnalysisResult
import java.text.NumberFormat
import java.util.Locale

/** The results of one family, in the order Hub'Eau published them. */
data class FamilyGroup(val family: ParameterFamily, val results: List<AnalysisResult>)

/**
 * The results whose label contains [query], ignoring case and accents
 * ([TextNormalizer]); all of them when the query is blank.
 */
fun filterResults(results: List<AnalysisResult>, query: String): List<AnalysisResult> {
    val needle = TextNormalizer.normalize(query)
    if (needle.isEmpty()) return results
    return results.filter { TextNormalizer.normalize(it.parameterLabel).contains(needle) }
}

/** [results] grouped by [ParameterFamilies.of], families in enum order, empty ones left out. */
fun groupByFamily(results: List<AnalysisResult>): List<FamilyGroup> {
    val byFamily = results.groupBy { ParameterFamilies.of(it) }
    return ParameterFamily.entries.mapNotNull { family ->
        byFamily[family]?.let { FamilyGroup(family, it) }
    }
}

/** The unit Hub'Eau gives to qualitative results: "not applicable", not a unit. */
private const val NO_UNIT = "SANS OBJET"

/**
 * The value of [result] followed by its unit, as one piece of data: the text
 * as published ("<0,01", "N.M.", "Aspect normal") when there is one,
 * otherwise the number formatted for [locale]; null when neither is given.
 * The unit is the API's, verbatim, left out when it is `SANS OBJET`.
 * The value is never compared with any limit.
 */
fun valueWithUnit(result: AnalysisResult, locale: Locale): String? {
    val value = result.resultText?.takeIf { it.isNotBlank() }?.trim()
        ?: result.resultNumber?.let { formatNumber(it, locale) }
        ?: return null
    val unit = result.unit?.trim()?.takeUnless { it.isEmpty() || it.equals(NO_UNIT, true) }
    return if (unit == null) value else "$value $unit"
}

private const val MAX_FRACTION_DIGITS = 6

private fun formatNumber(number: Double, locale: Locale): String {
    val format = NumberFormat.getNumberInstance(locale)
    format.maximumFractionDigits = MAX_FRACTION_DIGITS
    return format.format(number)
}
