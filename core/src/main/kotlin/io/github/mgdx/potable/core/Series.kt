package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.hubeau.HubEauFormatException
import io.github.mgdx.potable.core.hubeau.ResultRow
import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.core.model.SampleSummary
import io.github.mgdx.potable.core.model.SeriesPoint
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/** History built from `resultats_dis` rows (SPEC §5 F5). */
object Series {
    /**
     * The series of the parameter known under any of [parameterCodes], from
     * [rows] in any order; rows of other parameters, or without
     * `code_parametre`, are ignored. Null if no row is left.
     *
     * One point per `code_prelevement`: a sample can publish the same
     * parameter twice (Paris: pH measured on site, `code_lieu_analyse` "T",
     * and in the laboratory, "L"; anthraquinone as "(HAP)" without limit and
     * as "(pesticide)" with one, same code, same place), or under two of its
     * codes. The laboratory measure is kept, then one that publishes a limit
     * or a reference, otherwise the first row read. Points are oldest first
     * (then by sample code). Label, unit, limit and reference are those of
     * the newest point's row: they follow the regulation as it is now
     * published. A limit or reference that row lacks is taken from another
     * row of the same sample, never from an older sample.
     *
     * @throws HubEauFormatException if a kept row has a missing or unreadable date.
     */
    fun parameterSeries(rows: List<ResultRow>, parameterCodes: Set<String>): ParameterSeries? {
        val bySample = LinkedHashMap<String, MutableList<ResultRow>>()
        for (row in rows) {
            if (row.codeParametre == null || row.codeParametre !in parameterCodes) continue
            bySample.getOrPut(row.codePrelevement) { mutableListOf() } += row
        }
        if (bySample.isEmpty()) return null
        val dated = bySample.values
            .map { sampleRows ->
                // maxWith keeps the first of equal rows: API order breaks ties.
                val kept = sampleRows.maxWith(PREFERRED_ROW)
                Dated(kept, publishedDate(kept))
            }.sortedWith(compareBy({ it.date }, { it.row.codePrelevement }))
        val newest = dated.last().row
        val newestRows = bySample.getValue(newest.codePrelevement)
        val limitText = newest.limiteQualiteParametre.orPublished(
            newestRows.map { it.limiteQualiteParametre },
        )
        val referenceText = newest.referenceQualiteParametre.orPublished(
            newestRows.map { it.referenceQualiteParametre },
        )
        val code = checkNotNull(newest.codeParametre)
        return ParameterSeries(
            parameterCode = code,
            label = newest.libelleParametre ?: code,
            unit = newest.libelleUnite,
            limitText = limitText,
            referenceText = referenceText,
            limit = Thresholds.parse(limitText),
            reference = Thresholds.parse(referenceText),
            points = dated.map { (row, date) ->
                SeriesPoint(
                    date = date,
                    sampleCode = row.codePrelevement,
                    value = row.resultatNumerique,
                    text = row.resultatAlphanumerique,
                    censored = isCensored(row.resultatAlphanumerique),
                )
            },
        )
    }

    /**
     * One summary per `code_prelevement` of [rows], newest first (then by
     * code); sample-level fields come from the first row of each sample, as
     * in [Samples.group], which builds them.
     *
     * @throws HubEauFormatException if a row has a missing or unreadable date.
     */
    fun sampleSummaries(rows: List<ResultRow>): List<SampleSummary> = Samples.group(rows).map {
        SampleSummary(
            code = it.code,
            date = it.date,
            conclusion = it.conclusion,
            bacteriologicalLimits = it.bacteriologicalLimits,
            physicochemicalLimits = it.physicochemicalLimits,
            bacteriologicalReferences = it.bacteriologicalReferences,
            physicochemicalReferences = it.physicochemicalReferences,
        )
    }

    /** True when the published text is a bound ("<0,01", ">80", "≤1"), not a measure. */
    fun isCensored(text: String?): Boolean {
        val first = text?.trimStart()?.firstOrNull() ?: return false
        return first == '<' || first == '>' || first == '≤' || first == '≥'
    }

    private data class Dated(val row: ResultRow, val date: Instant)

    private fun ResultRow.isLaboratory() = codeLieuAnalyse == LABORATORY

    private fun ResultRow.hasThreshold() =
        !limiteQualiteParametre.isNullOrBlank() || !referenceQualiteParametre.isNullOrBlank()

    /** This text if published, otherwise the first published one of [others]. */
    private fun String?.orPublished(others: List<String?>): String? =
        takeUnless { it.isNullOrBlank() } ?: others.firstOrNull { !it.isNullOrBlank() } ?: this

    private const val LABORATORY = "L"

    /** The row a sample's point is read from: laboratory first, then one with a threshold. */
    private val PREFERRED_ROW: Comparator<ResultRow> =
        compareBy<ResultRow> { it.isLaboratory() }.thenBy { it.hasThreshold() }

    /** Same reading as [Samples.group]: ISO instant, with offset, or date only (UTC). */
    private fun publishedDate(row: ResultRow): Instant {
        val text = row.datePrelevement
            ?: throw HubEauFormatException("sample ${row.codePrelevement}: missing date")
        try {
            return Instant.parse(text)
        } catch (_: DateTimeParseException) {
            // Try the other ISO-8601 forms below.
        }
        try {
            return OffsetDateTime.parse(text).toInstant()
        } catch (_: DateTimeParseException) {
            // Try the date-only form below.
        }
        try {
            return LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant()
        } catch (e: DateTimeParseException) {
            throw HubEauFormatException("sample ${row.codePrelevement}: unreadable date", e)
        }
    }
}
