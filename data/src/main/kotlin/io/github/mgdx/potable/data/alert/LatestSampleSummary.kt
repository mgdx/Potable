package io.github.mgdx.potable.data.alert

import io.github.mgdx.potable.core.Series
import io.github.mgdx.potable.core.hubeau.HubEauFormatException
import io.github.mgdx.potable.core.model.SampleSummary
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.data.HubEauClient
import io.github.mgdx.potable.data.RemoteHistoryRepository

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
    return try {
        Series.sampleSummaries(rows).firstOrNull()
    } catch (e: HubEauFormatException) {
        throw DataError.Format(e)
    }
}

/** Rows asked for by [latestSampleSummary]: about 1.6 KB, 0.25 s. */
internal const val LATEST_SAMPLE_ROWS = 1
