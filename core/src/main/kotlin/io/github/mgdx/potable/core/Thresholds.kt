package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.Threshold

/**
 * Reads the bounds of `limite_qualite_parametre` / `reference_qualite_parametre`
 * so that a chart can draw them (SPEC §5 F5); the application never compares
 * a value with them (SPEC §14).
 *
 * Accepted form, as published (texts listed in `docs/hubeau-api.md`): one or
 * two comparisons joined by "et", then an optional unit, e.g. "<=50 mg/L",
 * ">=6,5 et <=9 unité pH", "<=0 n/(100mL)". Comparators `<=`, `<`, `≤`
 * give the upper bound, `>=`, `>`, `≥` the lower one (strict or not makes no
 * difference on a chart); `=` gives both. Decimal comma or point. The unit
 * is kept as published, except "SANS OBJET" ("no unit"), read as none.
 *
 * Anything else — no number ("Absence", "Acceptable pour les
 * consommateurs…"), two bounds on the same side, a lower bound above the
 * upper one, a comparison left after the unit — gives null rather than a
 * guess.
 */
object Thresholds {
    /*
     * Anchored with matchAt, never with \G and find(start): on Android (ICU),
     * \G after find(start) only matches at the start of the text, so the
     * second bound of ">=6,5 et <=9 unité pH" was never read and the whole
     * range parsed as null. The JVM honours \G at `start`, which hid it.
     */
    internal val BOUND = Regex("""\s*(<=|=<|>=|=>|≤|≥|<|>|=)\s*([+-]?\d+(?:[.,]\d+)?)""")
    internal val JOIN = Regex("""\s*(?:et|and|&)(?=\s*[<>=≤≥])""", RegexOption.IGNORE_CASE)
    private val COMPARATOR = Regex("[<>=≤≥]")

    private const val NO_UNIT = "SANS OBJET"

    fun parse(text: String?): Threshold? {
        if (text.isNullOrBlank()) return null
        var lower: Double? = null
        var upper: Double? = null
        var position = 0
        while (true) {
            val bound = BOUND.matchAt(text, position) ?: break
            val value = bound.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
            when (bound.groupValues[1]) {
                "<=", "=<", "≤", "<" -> {
                    if (upper != null) return null
                    upper = value
                }

                ">=", "=>", "≥", ">" -> {
                    if (lower != null) return null
                    lower = value
                }

                else -> {
                    if (lower != null || upper != null) return null
                    lower = value
                    upper = value
                }
            }
            position = bound.range.last + 1
            val join = JOIN.matchAt(text, position) ?: break
            position = join.range.last + 1
        }
        if (lower == null && upper == null) return null
        if (lower != null && upper != null && lower > upper) return null
        val rest = text.substring(position).trim()
        if (COMPARATOR.containsMatchIn(rest)) return null
        val unit = rest.takeUnless { it.isEmpty() || it.equals(NO_UNIT, ignoreCase = true) }
        return Threshold(lower = lower, upper = upper, unit = unit)
    }
}
