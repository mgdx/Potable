package io.github.mgdx.potable.data

import io.github.mgdx.potable.core.SubstanceSearch
import io.github.mgdx.potable.core.SubstanceSearchResult
import io.github.mgdx.potable.core.hubeau.HubEauFormatException
import io.github.mgdx.potable.core.hubeau.ResultRow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Search of a unit's history by parameter label (SPEC §3). */
fun interface SubstanceSearchRepository {
    /**
     * The parameters of the unit [udiCode] serving [communeCode] measured
     * since [since] whose label holds the words of [query], as Hub'Eau
     * matches them. Never cached: each call asks Hub'Eau.
     * @throws DataError
     */
    suspend fun search(
        communeCode: String,
        udiCode: String,
        query: String,
        since: LocalDate,
    ): SubstanceSearchResult
}

/**
 * [SubstanceSearchRepository] straight from Hub'Eau: `resultats_dis` filtered
 * by `libelle_parametre`, bounded to the unit and to the window, newest first,
 * at most [MAX_PAGES] pages of [PAGE_SIZE] rows. A query that matches more
 * rows ("eau" in Paris over ten years) gives a partial result, said as such.
 * Requests and measures in `docs/hubeau-api.md` ("Recherche par libellé").
 */
class RemoteSubstanceSearchRepository(
    private val client: HubEauClient,
    private val clock: Clock = Clock.systemUTC(),
) : SubstanceSearchRepository {
    override suspend fun search(
        communeCode: String,
        udiCode: String,
        query: String,
        since: LocalDate,
    ): SubstanceSearchResult {
        val words = SubstanceSearch.normalize(query)
        require(words.isNotEmpty()) { "empty query" }
        // date_max_prelevement is exclusive: tomorrow in France, as the history.
        val until = clock.instant().atZone(PARIS).toLocalDate().plusDays(1)
        val rows = mutableListOf<ResultRow>()
        var total = 0
        var partial = false
        for (page in 1..MAX_PAGES) {
            val result =
                client.resultats(
                    codeCommune = communeCode,
                    codeReseau = udiCode,
                    page = page,
                    size = PAGE_SIZE,
                    fields = FIELDS,
                    sortDesc = true,
                    dateMin = since,
                    dateMax = until,
                    libelleParametre = words,
                )
            if (page == 1) total = result.count
            rows += result.data
            if (result.next == null || result.data.size < PAGE_SIZE) break
            // A full last page with more after it: the bound stops the reading.
            partial = page == MAX_PAGES
        }
        val matches = try {
            SubstanceSearch.group(rows)
        } catch (e: HubEauFormatException) {
            throw DataError.Format(e)
        }
        return SubstanceSearchResult(
            matches = matches,
            rowsRead = rows.size,
            totalRows = maxOf(total, rows.size),
            partial = partial,
        )
    }

    companion object {
        /** Rows per request: answers in about 10 s at worst (`docs/hubeau-api.md`). */
        const val PAGE_SIZE = 5_000

        /**
         * Bound on the requests of one search, 10 000 rows: a precise name
         * reads far fewer (atrazine in Orléans, 3 years: 161 rows).
         */
        const val MAX_PAGES = 2

        /**
         * What a match needs, no threshold (the history shows them);
         * `code_commune` is required by [ResultRow].
         */
        val FIELDS: List<String> =
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
            )

        private val PARIS: ZoneId = ZoneId.of("Europe/Paris")
    }
}
