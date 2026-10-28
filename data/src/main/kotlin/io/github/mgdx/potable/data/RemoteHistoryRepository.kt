package io.github.mgdx.potable.data

import io.github.mgdx.potable.core.Series
import io.github.mgdx.potable.core.hubeau.HubEauFormatException
import io.github.mgdx.potable.core.hubeau.ResultRow
import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.core.model.SampleSummary
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * [HistoryRepository] straight from Hub'Eau, never cached; requests and
 * measures in `docs/hubeau-api.md` ("Historique"). [force] has no effect.
 */
class RemoteHistoryRepository(
    private val client: HubEauClient,
    private val clock: Clock = Clock.systemUTC(),
) : HistoryRepository {
    override suspend fun parameterSeries(
        communeCode: String,
        udiCode: String,
        parameterCodes: Set<String>,
        since: LocalDate,
        force: Boolean,
    ): ParameterSeries? {
        require(parameterCodes.isNotEmpty()) { "no parameter code" }
        val rows = allRows(communeCode, udiCode, parameterCodes.sorted(), since, SERIES_FIELDS)
        return formatChecked { Series.parameterSeries(rows, parameterCodes) }
    }

    override suspend fun sampleTimeline(
        communeCode: String,
        udiCode: String,
        since: LocalDate,
        force: Boolean,
    ): List<SampleSummary> {
        val rows = allRows(communeCode, udiCode, TIMELINE_PARAMETERS, since, TIMELINE_FIELDS)
        return formatChecked { Series.sampleSummaries(rows) }
    }

    override suspend fun parameterSeriesBefore(
        communeCode: String,
        udiCode: String,
        parameterCodes: Set<String>,
        since: LocalDate,
        until: LocalDate,
    ): ParameterSeries? {
        require(parameterCodes.isNotEmpty()) { "no parameter code" }
        val rows =
            allRows(communeCode, udiCode, parameterCodes.sorted(), since, SERIES_FIELDS, until)
        return formatChecked { Series.parameterSeries(rows, parameterCodes) }
    }

    override suspend fun sampleTimelineBefore(
        communeCode: String,
        udiCode: String,
        since: LocalDate,
        until: LocalDate,
    ): List<SampleSummary> {
        val rows = allRows(communeCode, udiCode, TIMELINE_PARAMETERS, since, TIMELINE_FIELDS, until)
        return formatChecked { Series.sampleSummaries(rows) }
    }

    /**
     * Every row from [since] to [until] (excluded: `date_max_prelevement` is
     * exclusive, `docs/hubeau-api.md`), newest first, page after page. The
     * window is bounded on both sides (by default up to tomorrow in France),
     * the form the API answers fastest.
     */
    private suspend fun allRows(
        communeCode: String,
        udiCode: String,
        parameterCodes: List<String>,
        since: LocalDate,
        fields: List<String>,
        until: LocalDate = clock.instant().atZone(PARIS).toLocalDate().plusDays(1),
    ): List<ResultRow> {
        val rows = mutableListOf<ResultRow>()
        for (page in 1..MAX_PAGES) {
            val result =
                client.resultats(
                    codeCommune = communeCode,
                    codeReseau = udiCode,
                    page = page,
                    size = PAGE_SIZE,
                    fields = fields,
                    sortDesc = true,
                    codeParametre = parameterCodes.joinToString(","),
                    dateMin = since,
                    dateMax = until,
                )
            rows += result.data
            if (result.next == null || result.data.size < PAGE_SIZE) break
        }
        return rows
    }

    private inline fun <T> formatChecked(block: () -> T): T = try {
        block()
    } catch (e: HubEauFormatException) {
        throw DataError.Format(e)
    }

    companion object {
        /**
         * Rows per request. 5 000 rows answer in about 10 s; one request of
         * 20 000 took up to 60 s, beyond the client's read timeout.
         */
        const val PAGE_SIZE = 5_000

        /** Bound on the requests for one history: 100 000 rows. */
        const val MAX_PAGES = 20

        /**
         * Parameters measured in nearly every sample, whose rows carry the
         * sample header used by the timeline: pH (1302), Escherichia coli
         * (1449), and lead (1382) for the lead campaigns, which measure
         * nothing else. Coverage measured in `docs/hubeau-api.md`.
         */
        val TIMELINE_PARAMETERS: List<String> = listOf("1302", "1382", "1449")

        /** `code_commune` is required by [ResultRow]. */
        val TIMELINE_FIELDS: List<String> =
            listOf(
                "code_prelevement",
                "code_commune",
                "date_prelevement",
                "conclusion_conformite_prelevement",
                "conformite_limites_bact_prelevement",
                "conformite_limites_pc_prelevement",
                "conformite_references_bact_prelevement",
                "conformite_references_pc_prelevement",
            )

        val SERIES_FIELDS: List<String> =
            listOf(
                "code_prelevement",
                "code_commune",
                "date_prelevement",
                "code_parametre",
                "libelle_parametre",
                "code_lieu_analyse",
                "resultat_alphanumerique",
                "resultat_numerique",
                "libelle_unite",
                "limite_qualite_parametre",
                "reference_qualite_parametre",
            )

        private val PARIS: ZoneId = ZoneId.of("Europe/Paris")
    }
}
