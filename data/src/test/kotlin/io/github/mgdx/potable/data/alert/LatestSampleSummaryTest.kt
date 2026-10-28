package io.github.mgdx.potable.data.alert

import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.data.DataError
import io.github.mgdx.potable.data.HubEauClient
import io.github.mgdx.potable.data.HubEauJson.NEXT
import io.github.mgdx.potable.data.HubEauJson.page
import io.github.mgdx.potable.data.HubEauJson.response
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class LatestSampleSummaryTest {
    @get:Rule
    val serverRule = MockWebServerRule()

    private val server get() = serverRule.server

    private val client by lazy {
        HubEauClient(OkHttpClient(), server.url("/api/v1/qualite_eau_potable/"))
    }

    /** A row as the API answers it with the light request's `fields`. */
    private fun headerRow(sample: String, date: String, references: String) =
        """{"code_prelevement":"$sample","code_commune":"45234",""" +
            """"date_prelevement":"$date",""" +
            """"conclusion_conformite_prelevement":"Eau d'alimentation conforme aux limites """ +
            """de qualité et non conforme aux références de qualité.",""" +
            """"conformite_limites_bact_prelevement":"C",""" +
            """"conformite_limites_pc_prelevement":"C",""" +
            """"conformite_references_bact_prelevement":"S",""" +
            """"conformite_references_pc_prelevement":"$references"}"""

    @Test
    fun `the newest sample is one sorted, filtered row with the header fields only`() = runTest {
        server.enqueue(
            response(
                page(1_234, NEXT, listOf(headerRow("04500171347", "2026-08-26T12:50:00Z", "N"))),
                partial = true,
            ),
        )

        val summary = client.latestSampleSummary("45234", "045000474")!!

        assertEquals("04500171347", summary.code)
        assertEquals(Instant.parse("2026-08-26T12:50:00Z"), summary.date)
        assertEquals(
            "Eau d'alimentation conforme aux limites de qualité et non conforme aux " +
                "références de qualité.",
            summary.conclusion,
        )
        assertEquals(Compliance.COMPLIANT, summary.bacteriologicalLimits)
        assertEquals(Compliance.COMPLIANT, summary.physicochemicalLimits)
        assertEquals(Compliance.NOT_ASSESSED, summary.bacteriologicalReferences)
        assertEquals(Compliance.NON_COMPLIANT, summary.physicochemicalReferences)

        assertEquals(1, server.requestCount)
        val url = server.takeRequest().url
        assertEquals("/api/v1/qualite_eau_potable/resultats_dis", url.encodedPath)
        assertEquals("45234", url.queryParameter("code_commune"))
        assertEquals("045000474", url.queryParameter("code_reseau"))
        assertEquals("desc", url.queryParameter("sort"))
        assertEquals("1", url.queryParameter("page"))
        assertEquals("1", url.queryParameter("size"))
        assertEquals(
            "code_prelevement,code_commune,date_prelevement,conclusion_conformite_prelevement," +
                "conformite_limites_bact_prelevement,conformite_limites_pc_prelevement," +
                "conformite_references_bact_prelevement,conformite_references_pc_prelevement",
            url.queryParameter("fields"),
        )
        // No parameter or date filter: the newest row is the newest sample.
        assertNull(url.queryParameter("code_parametre"))
        assertNull(url.queryParameter("date_min_prelevement"))
        assertNull(url.queryParameter("date_max_prelevement"))
    }

    @Test
    fun `a unit without any sample has no latest sample`() = runTest {
        server.enqueue(response(page(0, null, emptyList())))

        assertNull(client.latestSampleSummary("45234", "045000474"))
    }

    @Test
    fun `an unreadable date is a format error`() = runTest {
        server.enqueue(response(page(1, null, listOf(headerRow("P1", "26/08/2026", "C")))))

        try {
            client.latestSampleSummary("45234", "045000474")
            fail("expected DataError.Format")
        } catch (_: DataError.Format) {
            // Expected.
        }
    }

    @Test
    fun `an HTTP error is reported as such`() = runTest {
        // Twice: the client retries a 503 once.
        repeat(2) { server.enqueue(MockResponse.Builder().code(503).build()) }

        try {
            client.latestSampleSummary("45234", "045000474")
            fail("expected DataError.Http")
        } catch (e: DataError.Http) {
            assertEquals(503, e.code)
        }
    }
}
