package io.github.mgdx.potable.data

import io.github.mgdx.potable.data.HubEauJson.NEXT
import io.github.mgdx.potable.data.HubEauJson.page
import io.github.mgdx.potable.data.HubEauJson.response
import io.github.mgdx.potable.data.HubEauJson.resultRow
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class RemoteSampleRepositoryTest {
    @get:Rule
    val serverRule = MockWebServerRule()

    private val server get() = serverRule.server

    private val repository by lazy {
        RemoteSampleRepository(
            HubEauClient(OkHttpClient(), server.url("/api/v1/qualite_eau_potable/")),
        )
    }

    private fun rows(sample: String, date: String, count: Int, firstParameter: Int = 1) =
        List(count) { resultRow(sample, date, (firstParameter + it).toString()) }

    @Test
    fun sample_queriesOneSampleWithTheFieldsOfTheLatestSample() = runTest {
        server.enqueue(response(page(0, null, emptyList())))

        repository.sample("45234", "045000474", "04500171315")

        val url = server.takeRequest().url
        assertEquals("/api/v1/qualite_eau_potable/resultats_dis", url.encodedPath)
        assertEquals("45234", url.queryParameter("code_commune"))
        assertEquals("045000474", url.queryParameter("code_reseau"))
        assertEquals("04500171315", url.queryParameter("code_prelevement"))
        assertEquals("1", url.queryParameter("page"))
        assertEquals(RemoteSampleRepository.PAGE_SIZE.toString(), url.queryParameter("size"))
        assertEquals(
            RemoteWaterRepository.RESULT_FIELDS.joinToString(","),
            url.queryParameter("fields"),
        )
        assertNull(url.queryParameter("code_parametre"))
        assertNull(url.queryParameter("date_min_prelevement"))
    }

    @Test
    fun sample_noRowIsNull() = runTest {
        server.enqueue(response(page(0, null, emptyList())))

        assertNull(repository.sample("45234", "045000474", "UNKNOWN"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun sample_groupsEveryPage() = runTest {
        val date = "2026-08-26T12:50:00Z"
        val size = RemoteSampleRepository.PAGE_SIZE
        server.enqueue(response(page(size + 2, NEXT, rows("P1", date, size)), partial = true))
        server.enqueue(response(page(size + 2, null, rows("P1", date, 2, size + 1))))

        val sample = repository.sample("45234", "045000474", "P1")!!

        assertEquals(2, server.requestCount)
        assertEquals("P1", sample.code)
        assertEquals(Instant.parse(date), sample.date)
        assertEquals(size + 2, sample.results.size)
    }

    @Test
    fun sample_ignoresRowsOfAnotherSample() = runTest {
        server.enqueue(
            response(
                page(
                    3,
                    null,
                    rows("OTHER", "2026-09-01T08:00:00Z", 1) +
                        rows("P1", "2026-08-26T12:50:00Z", 2),
                ),
            ),
        )

        val sample = repository.sample("45234", "045000474", "P1")!!

        assertEquals("P1", sample.code)
        assertEquals(2, sample.results.size)
    }

    @Test
    fun sample_makesAtMostTenRequests() = runTest {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val page = request.url.queryParameter("page")!!.toInt()
                    val rows = rows(
                        "P1",
                        "2026-08-26T12:50:00Z",
                        RemoteSampleRepository.PAGE_SIZE,
                        firstParameter = page * RemoteSampleRepository.PAGE_SIZE,
                    )
                    return response(page(1_000_000, NEXT, rows), partial = true)
                }
            }

        repository.sample("45234", "045000474", "P1")

        assertEquals(RemoteSampleRepository.MAX_PAGES, server.requestCount)
    }

    @Test(expected = DataError.Format::class)
    fun sample_unreadableDateIsAFormatError() = runTest {
        server.enqueue(response(page(1, null, rows("P1", "not a date", 1))))

        repository.sample("45234", "045000474", "P1")
    }

    @Test(expected = DataError.Http::class)
    fun sample_propagatesErrors() = runTest {
        // Twice: the client retries a 503 once.
        repeat(2) { server.enqueue(MockResponse.Builder().code(503).build()) }

        repository.sample("45234", "045000474", "P1")
    }
}
