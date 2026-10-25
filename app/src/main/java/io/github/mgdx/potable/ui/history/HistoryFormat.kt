package io.github.mgdx.potable.ui.history

import io.github.mgdx.potable.core.FeaturedParameter
import io.github.mgdx.potable.core.model.SeriesPoint
import java.text.NumberFormat
import java.util.Locale

/** The unit Hub'Eau gives to qualitative results: "not applicable", not a unit. */
private const val NO_UNIT = "SANS OBJET"

private const val MAX_FRACTION_DIGITS = 6

/** [unit] as published, or null when there is none to show (blank, `SANS OBJET`). */
fun displayUnit(unit: String?): String? =
    unit?.trim()?.takeUnless { it.isEmpty() || it.equals(NO_UNIT, ignoreCase = true) }

/**
 * The value of [point] followed by [unit], as one piece of data: the text as
 * published ("<0,01", "N.M.") when there is one, otherwise the number
 * formatted for [locale]; null when neither is given. Never compared with
 * anything.
 */
fun pointValueText(point: SeriesPoint, unit: String?, locale: Locale): String? {
    val value = point.text?.takeIf { it.isNotBlank() }?.trim()
        ?: point.value?.let { formatNumber(it, MAX_FRACTION_DIGITS, locale) }
        ?: return null
    val shownUnit = displayUnit(unit)
    return if (shownUnit == null) value else "$value $shownUnit"
}

/** [number] in the format of [locale], with at most [fractionDigits] decimals. */
fun formatNumber(number: Double, fractionDigits: Int, locale: Locale): String {
    val format = NumberFormat.getNumberInstance(locale)
    format.maximumFractionDigits = fractionDigits
    return format.format(number)
}

/**
 * The codes to ask the history of a parameter published under
 * [parameterCode]: every code of the same featured parameter (hardness has
 * two), or that code alone.
 */
fun historyCodes(parameterCode: String): Set<String> =
    FeaturedParameter.of(parameterCode)?.codes ?: setOf(parameterCode)
