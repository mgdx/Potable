package io.github.mgdx.potable.ui.common

import android.content.res.Resources
import android.icu.text.PluralRules
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.Thresholds
import io.github.mgdx.potable.ui.sample.currentLocale
import java.text.NumberFormat
import java.util.Locale

/*
 * What a screen reader says of a value, a unit or a threshold published by
 * Hub'Eau (SPEC §11.2). The screen shows the text as published ("<=50 mg/L",
 * "<0,02", "12 n/(100mL)"), which TalkBack would spell out symbol by symbol;
 * these spoken forms are set as the content description of that text, which
 * stays unchanged on screen. They only put the same data into words, never
 * compare it with anything (SPEC §14).
 */

/**
 * The units published by Hub'Eau (`libelle_unite`, and the units of the
 * thresholds, `docs/hubeau-api.md`), plus those of the hardness converted by
 * the app, with their spoken form: a plural whose argument is the number,
 * formatted for the language. Matched exactly: an unknown unit is spoken as
 * published.
 */
internal val SPOKEN_UNITS: Map<String, Int> = mapOf(
    "mg/L" to R.plurals.spoken_unit_mg_per_l,
    "µg/L" to R.plurals.spoken_unit_ug_per_l,
    "ng/L" to R.plurals.spoken_unit_ng_per_l,
    "mg(Cl2)/L" to R.plurals.spoken_unit_mg_cl2_per_l,
    "mg(Cu)/L" to R.plurals.spoken_unit_mg_cu_per_l,
    "mg(C)/L" to R.plurals.spoken_unit_mg_c_per_l,
    "mg(Pt)/L" to R.plurals.spoken_unit_mg_pt_per_l,
    "µg(Se)/L" to R.plurals.spoken_unit_ug_se_per_l,
    "µg(CN)/L" to R.plurals.spoken_unit_ug_cn_per_l,
    "n/(100mL)" to R.plurals.spoken_unit_count_per_100_ml,
    "n/mL" to R.plurals.spoken_unit_count_per_ml,
    "NPP/100mL" to R.plurals.spoken_unit_mpn_per_100_ml,
    "°f" to R.plurals.spoken_unit_french_degrees,
    "°dH" to R.plurals.spoken_unit_german_degrees,
    "mmol/L" to R.plurals.spoken_unit_mmol_per_l,
    "unité pH" to R.plurals.spoken_unit_ph,
    "µS/cm" to R.plurals.spoken_unit_us_per_cm,
    "NFU" to R.plurals.spoken_unit_nfu,
    "°C" to R.plurals.spoken_unit_celsius,
    "mSv/a" to R.plurals.spoken_unit_msv_per_year,
    "Bq/L" to R.plurals.spoken_unit_bq_per_l,
)

/** The unit Hub'Eau gives to qualitative results: "not applicable", not a unit. */
private const val NO_UNIT = "SANS OBJET"

private const val MAX_FRACTION_DIGITS = 6

private val PLAIN_NUMBER = Regex("""[+-]?\d+(?:[.,]\d+)?""")
private val UPPER_INCLUSIVE = Regex("<=|=<|≤")
private val LOWER_INCLUSIVE = Regex(">=|=>|≥")
private val ANY_COMPARATOR = Regex("[<>≤≥]")

private fun shownUnit(unit: String?): String? =
    unit?.trim()?.takeUnless { it.isEmpty() || it.equals(NO_UNIT, ignoreCase = true) }

/**
 * [formatted], the text of [number] in the language, followed by [unit] in
 * words ("50 milligrams per litre"); by the unit as published when it is not
 * in [SPOKEN_UNITS]; alone without a unit.
 */
internal fun spokenQuantity(
    resources: Resources,
    number: Double,
    formatted: String,
    unit: String?,
    locale: Locale,
): String {
    val shown = shownUnit(unit) ?: return formatted
    val id = SPOKEN_UNITS[shown] ?: return "$formatted $shown"
    return resources.getQuantityString(id, pluralCount(number, locale), formatted)
}

/** [spokenQuantity] with [number] formatted for [locale]. */
internal fun spokenQuantity(
    resources: Resources,
    number: Double,
    unit: String?,
    locale: Locale,
): String = spokenQuantity(resources, number, formatNumber(number, locale), unit, locale)

/**
 * A whole number that selects the same plural form as [number] in [locale]:
 * plurals resources only take whole numbers, but "1.5" is "other" in English
 * and "one" in French.
 */
private fun pluralCount(number: Double, locale: Locale): Int {
    val magnitude = kotlin.math.abs(number)
    if (magnitude == kotlin.math.floor(magnitude) &&
        magnitude <= Int.MAX_VALUE
    ) {
        return magnitude.toInt()
    }
    val rules = PluralRules.forLocale(locale)
    val keyword = rules.select(magnitude)
    return rules.getSamples(keyword)
        .firstOrNull { it == kotlin.math.floor(it) }
        ?.toInt()
        ?: magnitude.toInt()
}

private fun formatNumber(number: Double, locale: Locale): String {
    val format = NumberFormat.getNumberInstance(locale)
    format.maximumFractionDigits = MAX_FRACTION_DIGITS
    return format.format(number)
}

private fun parseNumber(text: String): Double? = text.replace(',', '.').toDoubleOrNull()

/**
 * The spoken form of a threshold or of a bounded result, such as "<=50 mg/L",
 * ">=6,5 et <=9 unité pH" or "<0,02" ([Thresholds.parse]), its unit taken
 * from the text, or [unit] when the text has none. A strict upper bound of a
 * result ([isResult], "<0,02") reads "below the threshold of 0.02…". Null when
 * the text is not such a comparison.
 */
private fun spokenComparison(
    resources: Resources,
    text: String,
    unit: String?,
    locale: Locale,
    isResult: Boolean,
): String? {
    val threshold = Thresholds.parse(text) ?: return null
    val thresholdUnit = threshold.unit ?: unit
    fun quantity(value: Double) = spokenQuantity(resources, value, thresholdUnit, locale)
    val lower = threshold.lower
    val upper = threshold.upper
    if (lower != null && lower == upper && !ANY_COMPARATOR.containsMatchIn(text)) {
        return resources.getString(R.string.spoken_equal_to, quantity(lower))
    }
    val lowerText = lower?.let {
        val id = if (LOWER_INCLUSIVE.containsMatchIn(text)) {
            R.string.spoken_greater_than_or_equal_to
        } else {
            R.string.spoken_greater_than
        }
        resources.getString(id, quantity(it))
    }
    val upperText = upper?.let {
        val id = when {
            UPPER_INCLUSIVE.containsMatchIn(text) -> R.string.spoken_less_than_or_equal_to
            isResult -> R.string.spoken_below_threshold_of
            else -> R.string.spoken_less_than
        }
        resources.getString(id, quantity(it))
    }
    return when {
        lowerText != null && upperText != null ->
            resources.getString(R.string.spoken_both_bounds, lowerText, upperText)
        else -> lowerText ?: upperText
    }
}

/**
 * The spoken form of a result published as [text] (`resultat_alphanumerique`)
 * or [number] (`resultat_numerique`), in [unit] (`libelle_unite`): "12.5
 * milligrams per litre", "below the threshold of 0.02 micrograms per litre".
 * Null when there is no value, or when the text is neither a number nor a
 * bound ("N.M.", "<SEUIL", "Aspect normal"): it is then read as shown.
 */
fun spokenResult(
    resources: Resources,
    text: String?,
    number: Double?,
    unit: String?,
    locale: Locale,
): String? {
    val published = text?.trim()?.takeIf { it.isNotEmpty() }
    if (published == null) {
        return number?.let { spokenQuantity(resources, it, unit, locale) }
    }
    if (PLAIN_NUMBER.matches(published)) {
        val value = parseNumber(published) ?: return null
        return spokenQuantity(resources, value, unit, locale)
    }
    return spokenComparison(resources, published, unit, locale, isResult = true)
}

/**
 * The spoken form of a quality limit or reference as published ("<=50 mg/L":
 * "less than or equal to 50 milligrams per litre"); the text itself when it
 * is not a comparison Hub'Eau publishes ([Thresholds.parse]).
 */
fun spokenThreshold(resources: Resources, text: String, locale: Locale): String =
    spokenComparison(resources, text.trim(), null, locale, isResult = false) ?: text

/** [spokenResult] in the language of the current configuration. */
@Composable
fun spokenResult(text: String?, number: Double?, unit: String?): String? {
    val resources = LocalResources.current
    val locale = currentLocale()
    return remember(text, number, unit, resources, locale) {
        spokenResult(resources, text, number, unit, locale)
    }
}

/** [spokenThreshold] in the language of the current configuration. */
@Composable
fun spokenThreshold(text: String): String {
    val resources = LocalResources.current
    val locale = currentLocale()
    return remember(text, resources, locale) { spokenThreshold(resources, text, locale) }
}

/**
 * Has screen readers say [spoken] instead of the text shown, which stays as
 * published; nothing changes when it is null.
 */
fun Modifier.spokenAs(spoken: String?): Modifier =
    if (spoken == null) this else semantics { contentDescription = spoken }
