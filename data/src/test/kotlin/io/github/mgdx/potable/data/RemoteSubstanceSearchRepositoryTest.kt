package io.github.mgdx.potable.data

import io.github.mgdx.potable.data.HubEauJson.NEXT
import io.github.mgdx.potable.data.HubEauJson.page
import io.github.mgdx.potable.data.HubEauJson.response
import io.github.mgdx.potable.data.HubEauJson.resultRow
import io.github.mgdx.potable.data.local.MutableClock
import kotlinx.coroutines.test.runTest
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RemoteSubstanceSearchRepositoryTest {
    @get:Rule
    val serverRule = MockWebServerRule()

    private val server get() = serverRule.server

    // 2026-10-25 00:30 in Paris, still the 24th in UTC: "tomorrow" is the 26th.
    private val clock = MutableClock(Instant.parse("2026-10-24T22:30:00Z"))

    private val repository by lazy {
        RemoteSubstanceSearchRepository(
            HubEauClient(OkHttpClient(), server.url("/api/v1/qualite_eau_potable/")),
            clock,
        )
    }

    private val since = LocalDate.parse("2023-10-24")

    @Test
    fun `a search is one request bounded to the unit, the window and the label`() = runTest {
        server.enqueue(
            response(
                page(
                    3,
                    null,
                    listOf(
                        resultRow("P2", "2026-08-26T12:50:00Z", "1830"),
                        resultRow("P1", "2025-01-02T10:00:00Z", "1830"),
                        resultRow("P1", "2025-01-02T10:00:00Z", "1107"),
                    ),
                ),
            ),
        )

        val result = repository.search("45234", "045000474", "  atrazine  déséthyl ", since)

        assertEquals(listOf("1107", "1830"), result.matches.map { it.parameterCode })
        assertEquals(2, result.matches[1].measureCount)
        assertFalse(result.partial)
        assertEquals(3, result.rowsRead)
        val url = server.takeRequest().url
        assertEquals("/api/v1/qualite_eau_potable/resultats_dis", url.encodedPath)
        assertEquals("45234", url.queryParameter("code_commune"))
        assertEquals("045000474", url.queryParameter("code_reseau"))
        assertEquals("atrazine déséthyl", url.queryParameter("libelle_parametre"))
        assertNull(url.queryParameter("code_parametre"))
        assertEquals("2023-10-24", url.queryParameter("date_min_prelevement"))
        assertEquals("2026-10-26", url.queryParameter("date_max_prelevement"))
        assertEquals("desc", url.queryParameter("sort"))
        assertEquals("1", url.queryParameter("page"))
        assertEquals("5000", url.queryParameter("size"))
        assertEquals(
            RemoteSubstanceSearchRepository.FIELDS.joinToString(","),
            url.queryParameter("fields"),
        )
    }

    @Test
    fun `reading stops at the bound and the result says it is partial`() = runTest {
        val full = List(RemoteSubstanceSearchRepository.PAGE_SIZE) {
            resultRow("P$it", "2026-08-26T12:50:00Z", "1302")
        }
        repeat(RemoteSubstanceSearchRepository.MAX_PAGES) {
            server.enqueue(response(page(12_000, NEXT, full), partial = true))
        }

        val result = repository.search("75056", "075000221", "eau", since)

        assertTrue(result.partial)
        assertEquals(10_000, result.rowsRead)
        assertEquals(12_000, result.totalRows)
        assertEquals(RemoteSubstanceSearchRepository.MAX_PAGES, server.requestCount)
    }

    @Test
    fun `a last page that is not full is not partial`() = runTest {
        val full = List(RemoteSubstanceSearchRepository.PAGE_SIZE) {
            resultRow("P$it", "2026-08-26T12:50:00Z", "1302")
        }
        server.enqueue(response(page(5_001, NEXT, full), partial = true))
        server.enqueue(
            response(page(5_001, null, listOf(resultRow("Q", "2020-01-01T10:00:00Z", "1302")))),
        )

        val result = repository.search("75056", "075000221", "ph eau", since)

        assertFalse(result.partial)
        assertEquals(5_001, result.matches.single().measureCount)
        server.takeRequest()
        assertEquals("2", server.takeRequest().url.queryParameter("page"))
    }
}
