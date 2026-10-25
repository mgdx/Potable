package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.hubeau.HubEauFormatException
import io.github.mgdx.potable.core.hubeau.ResultRow
import io.github.mgdx.potable.core.model.AnalysisResult
import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.Sample
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

object Samples {
    /**
     * Groups result rows by `code_prelevement`, newest sample first (then by
     * code). Sample-level fields (date, conclusion, compliance codes,
     * distributor) are repeated on every row of a sample by Hub'Eau: they are
     * taken from the first row of each group, never aggregated row by row
     * (SPEC §4.1). Results keep the API order; a row repeating the same
     * parameter with the same value is kept once. A row without
     * `code_parametre` counts for the sample (date, header fields) but is
     * not an analysis result.
     *
     * @throws HubEauFormatException if a row has a missing or unreadable date.
     */
    fun group(rows: List<ResultRow>): List<Sample> {
        val groups = LinkedHashMap<String, MutableList<ResultRow>>()
        val dates = HashMap<String, Instant>()
        for (row in rows) {
            val date = parseDate(row)
            groups.getOrPut(row.codePrelevement) { mutableListOf() }.add(row)
            dates.putIfAbsent(row.codePrelevement, date)
        }
        return groups.map { (code, group) -> toSample(code, dates.getValue(code), group) }
            .sortedWith(compareByDescending<Sample> { it.date }.thenBy { it.code })
    }

    /**
     * The calendar day a sample was taken, as Hub'Eau publishes it: the date
     * read in UTC. The times of `date_prelevement` are most probably French
     * legal time wrongly suffixed with `Z` (`docs/hubeau-api.md`), so this is
     * the day in France (`docs/decisions.md`).
     */
    fun publishedDay(date: Instant): LocalDate = date.atOffset(ZoneOffset.UTC).toLocalDate()

    /** [publishedDay] of [row]'s `date_prelevement`, or null if it is missing or unreadable. */
    fun publishedDayOrNull(row: ResultRow): LocalDate? = try {
        publishedDay(parseDate(row))
    } catch (_: HubEauFormatException) {
        null
    }

    /**
     * Of [samples], newest first as [group] returns them, every one taken
     * on the [publishedDay] of the first: the latest samples, when Hub'Eau
     * publishes several on the same day (newest first). Empty if [samples]
     * is.
     */
    fun latestDay(samples: List<Sample>): List<Sample> {
        val day = samples.firstOrNull()?.let { publishedDay(it.date) } ?: return emptyList()
        return samples.filter { publishedDay(it.date) == day }
    }

    private fun toSample(code: String, date: Instant, group: List<ResultRow>): Sample {
        val head = group.first()
        val seen = HashSet<ResultKey>()
        val results = group.mapNotNull { row ->
            val parameter = row.codeParametre ?: return@mapNotNull null
            val key = ResultKey(parameter, row.resultatAlphanumerique, row.resultatNumerique)
            if (seen.add(key)) row.toResult(parameter) else null
        }
        return Sample(
            code = code,
            communeCode = head.codeCommune,
            networkCode = head.codeReseau ?: head.reseaux?.firstOrNull()?.code,
            date = date,
            conclusion = head.conclusionConformitePrelevement,
            bacteriologicalLimits = Compliance.fromCode(head.conformiteLimitesBactPrelevement),
            physicochemicalLimits = Compliance.fromCode(head.conformiteLimitesPcPrelevement),
            bacteriologicalReferences = Compliance.fromCode(
                head.conformiteReferencesBactPrelevement,
            ),
            physicochemicalReferences = Compliance.fromCode(head.conformiteReferencesPcPrelevement),
            distributor = head.nomDistributeur,
            owner = head.nomMoa,
            results = results,
        )
    }

    /** Label falls back on the parameter code, so that a row is never lost. */
    private fun ResultRow.toResult(parameter: String) = AnalysisResult(
        parameterCode = parameter,
        parameterLabel = libelleParametre ?: parameter,
        parameterTypeCode = codeTypeParametre,
        resultText = resultatAlphanumerique,
        resultNumber = resultatNumerique,
        unit = libelleUnite,
        qualityLimit = limiteQualiteParametre,
        qualityReference = referenceQualiteParametre,
    )

    private data class ResultKey(val parameter: String, val text: String?, val number: Double?)

    /** ISO-8601 instant ("2026-08-26T12:50:00Z"), with offset, or date only (UTC midnight). */
    private fun parseDate(row: ResultRow): Instant {
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
