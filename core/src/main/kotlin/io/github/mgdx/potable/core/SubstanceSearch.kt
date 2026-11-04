package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.hubeau.HubEauFormatException
import io.github.mgdx.potable.core.hubeau.ResultRow
import io.github.mgdx.potable.core.model.SeriesPoint
import java.text.Collator
import java.util.Locale

/**
 * A parameter found by a search of the labels of a unit's history: its
 * label and unit as published, its latest measure as published (censored
 * bounds such as "<0,05" and `<SEUIL` kept as text), and how many samples
 * measured it in the rows read.
 *
 * [parameterCodes] are every code the parameter is published under (hardness
 * has two, [FeaturedParameter]); [parameterCode] is the code of the latest
 * measure.
 */
data class SubstanceMatch(
    val parameterCodes: Set<String>,
    val parameterCode: String,
    val label: String,
    val unit: String?,
    val latest: SeriesPoint,
    val measureCount: Int,
)

/**
 * What a search of the labels answered. [partial] is true when the rows read
 * stopped before the last one ([rowsRead] of [totalRows], the newest first):
 * then the counts are low, and a parameter measured only before the oldest
 * row read is missing.
 */
data class SubstanceSearchResult(
    val matches: List<SubstanceMatch>,
    val rowsRead: Int,
    val totalRows: Int,
    val partial: Boolean,
)

/** Search of a unit's history by parameter label (SPEC §3, "chercher un pesticide donné"). */
object SubstanceSearch {
    /** Shortest query sent to Hub'Eau, spaces excluded. */
    const val MIN_QUERY_LENGTH = 3

    /** [text] trimmed, inner runs of spaces made one: the query sent. */
    fun normalize(text: String): String = text.trim().split(WHITESPACE).joinToString(" ")

    /** True when [text], once normalized, is long enough to be sent. */
    fun isSearchable(text: String): Boolean = normalize(text).length >= MIN_QUERY_LENGTH

    /**
     * The parameters of [rows], in any order, one per parameter: the codes of
     * a [FeaturedParameter] together, every other `code_parametre` alone.
     * Rows without `code_parametre` are ignored. Each parameter is read like
     * its history ([Series.parameterSeries]: one measure per sample, the
     * laboratory one first), so that its count and latest measure are those
     * of the history it opens. Sorted by label in French dictionary order,
     * then by code.
     *
     * @throws HubEauFormatException if a kept row has a missing or unreadable date.
     */
    fun group(rows: List<ResultRow>): List<SubstanceMatch> {
        val byParameter = LinkedHashMap<Set<String>, MutableList<ResultRow>>()
        for (row in rows) {
            val code = row.codeParametre ?: continue
            val codes = FeaturedParameter.of(code)?.codes ?: setOf(code)
            byParameter.getOrPut(codes) { mutableListOf() } += row
        }
        val matches = byParameter.mapNotNull { (codes, parameterRows) ->
            val series = Series.parameterSeries(parameterRows, codes) ?: return@mapNotNull null
            SubstanceMatch(
                parameterCodes = codes,
                parameterCode = series.parameterCode,
                label = series.label,
                unit = series.unit,
                latest = series.points.last(),
                measureCount = series.points.size,
            )
        }
        // A new collator each time: a Collator is not safe to share between threads.
        val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.TERTIARY }
        return matches.sortedWith(
            compareBy<SubstanceMatch, String>(collator) { it.label }.thenBy { it.parameterCode },
        )
    }

    private val WHITESPACE = Regex("\\s+")
}
