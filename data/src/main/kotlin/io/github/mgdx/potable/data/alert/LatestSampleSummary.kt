package io.github.mgdx.potable.data.alert

import io.github.mgdx.potable.core.Series
import io.github.mgdx.potable.core.hubeau.HubEauFormatException
import io.github.mgdx.potable.core.hubeau.ResultRow
import io.github.mgdx.potable.core.model.SampleSummary
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.data.HubEauClient
import io.github.mgdx.potable.data.RemoteHistoryRepository
import java.time.LocalDate

/**
 * The header of the newest sample of a unit, from the lightest request
 * Hub'Eau answers (SPEC §5 F7): `resultats_dis` newest first, filtered by
 * `code_reseau`, [LATEST_SAMPLE_ROWS] row, the timeline's header fields
 * only. The rows of a sample all carry its header, so the newest row is
 * enough. Measures in `docs/hubeau-api.md` ("Alertes"). Null when the unit
 * has no sample.
 *
 * @throws DataError
 */
internal suspend fun HubEauClient.latestSampleSummary(
    communeCode: String,
    udiCode: String,
): SampleSummary? {
    val rows =
        resultats(
            codeCommune = communeCode,
            codeReseau = udiCode,
            page = 1,
            size = LATEST_SAMPLE_ROWS,
            fields = RemoteHistoryRepository.TIMELINE_FIELDS,
            sortDesc = true,
        ).data
    return formatChecked { Series.sampleSummaries(rows).firstOrNull() }
}

/** Rows asked for by [latestSampleSummary]: about 1.6 KB, 0.25 s. */
internal const val LATEST_SAMPLE_ROWS = 1

/**
 * The headers of the samples of a unit taken from [since] to [until]
 * (excluded; days as Hub'Eau's date filters read them), newest first: the
 * rows of [ALERT_PARAMETERS] only, with the timeline's header fields, page
 * after page of [ALERT_PAGE_SIZE] rows (SPEC §5 F7). A sample that measures
 * none of them is missing, unless it is the newest one, which
 * [latestSampleSummary] asks for apart. Measures in `docs/hubeau-api.md`
 * ("Alertes").
 *
 * @throws DataError
 */
internal suspend fun HubEauClient.alertSampleSummaries(
    communeCode: String,
    udiCode: String,
    since: LocalDate,
    until: LocalDate,
): List<SampleSummary> {
    val rows = mutableListOf<ResultRow>()
    for (page in 1..ALERT_MAX_PAGES) {
        val result =
            resultats(
                codeCommune = communeCode,
                codeReseau = udiCode,
                page = page,
                size = ALERT_PAGE_SIZE,
                fields = RemoteHistoryRepository.TIMELINE_FIELDS,
                sortDesc = true,
                codeParametre = ALERT_PARAMETERS.joinToString(","),
                dateMin = since,
                dateMax = until,
            )
        rows += result.data
        if (result.next == null || result.data.size < ALERT_PAGE_SIZE) break
    }
    return formatChecked { Series.sampleSummaries(rows) }
}

private inline fun <T> formatChecked(block: () -> T): T = try {
    block()
} catch (e: HubEauFormatException) {
    throw DataError.Format(e)
}

/**
 * Parameters whose rows give the alert the headers of the samples of its
 * window: those of the timeline (pH, lead, E. coli). Total pesticides
 * (`6276`) and the sum of PFAS (`8847`) were measured too and added no
 * sample on the units tried (`docs/hubeau-api.md`, "Alertes"), so they are
 * not asked for. Kept apart from [RemoteHistoryRepository.TIMELINE_PARAMETERS]
 * so that either can change alone.
 */
internal val ALERT_PARAMETERS: List<String> = listOf("1302", "1382", "1449")

/**
 * Rows per page of [alertSampleSummaries]: a small request, read with the
 * client's short timeout ([HubEauClient.QUICK_PAGE_SIZE]). Three months of
 * Paris CENTRE take about 120 rows.
 */
internal const val ALERT_PAGE_SIZE = HubEauClient.QUICK_PAGE_SIZE

/** Bound on the pages of one window: 10 000 rows, years of the largest unit. */
internal const val ALERT_MAX_PAGES = 20
