package io.github.mgdx.potable.data

import io.github.mgdx.potable.core.Samples
import io.github.mgdx.potable.core.hubeau.HubEauFormatException
import io.github.mgdx.potable.core.hubeau.ResultRow
import io.github.mgdx.potable.core.model.Sample

/** Every result of one given sample, whatever its age (SPEC §5 F4, opened from the history). */
interface SampleRepository {
    /**
     * The sample [sampleCode] of the unit [udiCode] serving [communeCode],
     * with all its results; null when Hub'Eau publishes no row of it.
     *
     * @throws DataError
     */
    suspend fun sample(communeCode: String, udiCode: String, sampleCode: String): Sample?
}

/**
 * [SampleRepository] straight from Hub'Eau, never cached: `resultats_dis`
 * filtered on `code_prelevement`, with the fields of the latest sample
 * ([RemoteWaterRepository.RESULT_FIELDS]), page after page.
 */
class RemoteSampleRepository(private val client: HubEauClient) : SampleRepository {
    override suspend fun sample(communeCode: String, udiCode: String, sampleCode: String): Sample? {
        val rows = mutableListOf<ResultRow>()
        for (page in 1..MAX_PAGES) {
            val result =
                client.resultats(
                    codeCommune = communeCode,
                    codeReseau = udiCode,
                    page = page,
                    size = PAGE_SIZE,
                    fields = RemoteWaterRepository.RESULT_FIELDS,
                    sortDesc = true,
                    codePrelevement = sampleCode,
                )
            rows += result.data
            if (result.next == null || result.data.size < PAGE_SIZE) break
        }
        return try {
            Samples.group(rows).firstOrNull { it.code == sampleCode }
        } catch (e: HubEauFormatException) {
            throw DataError.Format(e)
        }
    }

    companion object {
        /** Rows per request: a sample has a few dozen to several hundred (Paris: 643). */
        const val PAGE_SIZE = 1_000

        /** Bound on the requests for one sample: 10 000 rows. */
        const val MAX_PAGES = 10
    }
}
