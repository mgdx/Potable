package io.github.mgdx.potable.data

import io.github.mgdx.potable.core.Samples
import io.github.mgdx.potable.core.UdiSelection
import io.github.mgdx.potable.core.hubeau.ResultRow
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.core.model.UdiChoice

class RemoteWaterRepository(private val client: HubEauClient) : WaterRepository {
    override suspend fun udis(communeCode: String): UdiChoice =
        UdiSelection.choose(client.communesUdi(communeCode))

    /**
     * Reads `resultats_dis` newest first, page after page, while the last row
     * read still has the date of the very first one: the newest sample is then
     * complete, whatever its number of results. Date filters are not used, see
     * `docs/hubeau-api.md`.
     */
    override suspend fun latestSample(communeCode: String, networkCode: String?): Sample? {
        val rows = mutableListOf<ResultRow>()
        for (page in 1..MAX_SAMPLE_PAGES) {
            val result =
                client.resultats(
                    codeCommune = communeCode,
                    codeReseau = networkCode,
                    page = page,
                    size = SAMPLE_PAGE_SIZE,
                    fields = RESULT_FIELDS,
                    sortDesc = true,
                )
            rows += result.data
            val newestDate = rows.firstOrNull()?.datePrelevement
            if (result.next == null ||
                result.data.isEmpty() ||
                result.data.last().datePrelevement != newestDate
            ) {
                break
            }
        }
        return Samples.group(rows).firstOrNull()
    }

    companion object {
        /** A sample has from a few dozen to a few hundred rows. */
        const val SAMPLE_PAGE_SIZE = 500

        /** Absolute bound on the requests made for one sample. */
        const val MAX_SAMPLE_PAGES = 10

        /**
         * The fields [ResultRow] reads. `code_reseau` is left out on purpose:
         * asked for in `fields`, the API returns it as an array of codes, which
         * [ResultRow.codeReseau] cannot read; [ResultRow.reseaux] carries them.
         */
        val RESULT_FIELDS: List<String> =
            listOf(
                "code_prelevement",
                "code_commune",
                "nom_commune",
                "reseaux",
                "date_prelevement",
                "conclusion_conformite_prelevement",
                "conformite_limites_bact_prelevement",
                "conformite_limites_pc_prelevement",
                "conformite_references_bact_prelevement",
                "conformite_references_pc_prelevement",
                "nom_distributeur",
                "nom_moa",
                "code_parametre",
                "libelle_parametre",
                "code_type_parametre",
                "resultat_alphanumerique",
                "resultat_numerique",
                "libelle_unite",
                "limite_qualite_parametre",
                "reference_qualite_parametre",
            )
    }
}
