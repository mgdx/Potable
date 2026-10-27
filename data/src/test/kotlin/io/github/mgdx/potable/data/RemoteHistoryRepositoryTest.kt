package io.github.mgdx.potable.data

import io.github.mgdx.potable.data.HubEauJson.NEXT
import io.github.mgdx.potable.data.HubEauJson.page
import io.github.mgdx.potable.data.HubEauJson.response
import io.github.mgdx.potable.data.HubEauJson.resultRow
import io.github.mgdx.potable.data.local.MutableClock
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
import java.time.LocalDate

class RemoteHistoryRepositoryTest {
    @get:Rule
    val serverRule = MockWebServerRule()

    private val server get() = serverRule.server

    // 2026-10-25 00:30 in Paris, still the 24th in UTC: "tomorrow" is the 26th.
    private val clock = MutableClock(Instant.parse("2026-10-24T22:30:00Z"))

    private val repository by lazy {
        RemoteHistoryRepository(
            HubEauClient(OkHttpClient(), server.url("/api/v1/qualite_eau_potable/")),
            clock,
        )
    }

    private val since = LocalDate.parse("2023-10-24")

    @Test
    fun `a series is one bounded, filtered request`() = runTest {
        server.enqueue(
            response(
                page(
                    2,
                    null,
                    listOf(
                        resultRow("P2", "2026-08-26T12:50:00Z", "7972"),
                        resultRow("P1", "2025-01-02T10:00:00Z", "1345"),
                    ),
                ),
            ),
        )

        val series = repository.parameterSeries(
            "45234",
            "045000474",
            setOf("7972", "1345"),
            since,
        )!!

        assertEquals(listOf("P1", "P2"), series.points.map { it.sampleCode })
        assertEquals("7972", series.parameterCode)
        val url = server.takeRequest().url
        assertEquals("/api/v1/qualite_eau_potable/resultats_dis", url.encodedPath)
        assertEquals("45234", url.queryParameter("code_commune"))
        assertEquals("045000474", url.queryParameter("code_reseau"))
        assertEquals("1345,7972", url.queryParameter("code_parametre"))
        assertEquals("2023-10-24", url.queryParameter("date_min_prelevement"))
        assertEquals("2026-10-26", url.queryParameter("date_max_prelevement"))
        assertEquals("desc", url.queryParameter("sort"))
        assertEquals("1", url.queryParameter("page"))
        assertEquals("5000", url.queryParameter("size"))
        assertEquals(
            RemoteHistoryRepository.SERIES_FIELDS.joinToString(","),
            url.queryParameter("fields"),
        )
    }

    @Test
    fun `the days before a cached window are one request ending at it`() = runTest {
        server.enqueue(
            response(page(1, null, listOf(resultRow("P1", "2024-03-01T09:00:00Z", "1340")))),
        )
        server.enqueue(response(page(0, null, emptyList())))
        val until = LocalDate.parse("2025-10-24")

        val series = repository.parameterSeriesBefore(
            "45234",
            "045000474",
            setOf("1340"),
            since,
            until,
        )
        repository.sampleTimelineBefore("45234", "045000474", since, until)

        assertEquals(listOf("P1"), series!!.points.map { it.sampleCode })
        for (expected in listOf("1340", "1302,1382,1449")) {
            val url = server.takeRequest().url
            assertEquals(expected, url.queryParameter("code_parametre"))
            assertEquals("2023-10-24", url.queryParameter("date_min_prelevement"))
            // date_max_prelevement is exclusive: the cached day is not asked again.
            assertEquals("2025-10-24", url.queryParameter("date_max_prelevement"))
        }
    }

    @Test
    fun `no row is no series`() = runTest {
        server.enqueue(response(page(0, null, emptyList())))

        assertNull(repository.parameterSeries("45234", "045000474", setOf("1340"), since))
    }

    @Test
    fun `full pages are followed until a short one`() = runTest {
        val size = RemoteHistoryRepository.PAGE_SIZE
        val full = List(size) { resultRow("P$it", "2026-01-01T00:00:00Z", "1302") }
        server.enqueue(response(page(size + 1, NEXT, full), partial = true))
        // Last page: the API still answers 206 with `next` set.
        server.enqueue(
            response(
                page(size + 1, NEXT, listOf(resultRow("OLD", "2025-01-01T00:00:00Z", "1302"))),
                partial = true,
            ),
        )

        val timeline = repository.sampleTimeline("45234", "045000474", since)

        assertEquals(size + 1, timeline.size)
        assertEquals("OLD", timeline.last().code)
        assertEquals(2, server.requestCount)
        val first = server.takeRequest().url
        assertEquals("1302,1382,1449", first.queryParameter("code_parametre"))
        assertEquals(
            RemoteHistoryRepository.TIMELINE_FIELDS.joinToString(","),
            first.queryParameter("fields"),
        )
        assertEquals("2", server.takeRequest().url.queryParameter("page"))
    }

    @Test
    fun `an empty page ends the walk`() = runTest {
        val size = RemoteHistoryRepository.PAGE_SIZE
        val full = List(size) { resultRow("P$it", "2026-01-01T00:00:00Z", "1302") }
        server.enqueue(response(page(size, NEXT, full), partial = true))
        server.enqueue(response(page(size, NEXT, emptyList())))

        assertEquals(size, repository.sampleTimeline("45234", "045000474", since).size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `the walk is bounded`() = runTest {
        val size = RemoteHistoryRepository.PAGE_SIZE
        val full = response(
            page(size, NEXT, List(size) { resultRow("P", "2026-01-01T00:00:00Z", "1302") }),
            partial = true,
        )
        repeat(RemoteHistoryRepository.MAX_PAGES + 1) { server.enqueue(full) }

        repository.sampleTimeline("45234", "045000474", since)

        assertEquals(RemoteHistoryRepository.MAX_PAGES, server.requestCount)
    }

    @Test
    fun `a row without a date is a format error`() = runTest {
        server.enqueue(
            response(page(1, null, listOf("""{"code_prelevement":"P","code_commune":"45234"}"""))),
        )

        try {
            repository.sampleTimeline("45234", "045000474", since)
            fail("expected DataError.Format")
        } catch (_: DataError.Format) {
            // Expected.
        }
    }

    @Test
    fun `an HTTP error is a DataError`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())

        try {
            repository.parameterSeries("45234", "045000474", setOf("1340"), since)
            fail("expected DataError.Http")
        } catch (e: DataError.Http) {
            assertEquals(500, e.code)
        }
    }
}
