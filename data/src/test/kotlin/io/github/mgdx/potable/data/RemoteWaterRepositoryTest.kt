package io.github.mgdx.potable.data

import io.github.mgdx.potable.core.model.UdiChoice
import io.github.mgdx.potable.data.HubEauJson.NEXT
import io.github.mgdx.potable.data.HubEauJson.page
import io.github.mgdx.potable.data.HubEauJson.response
import io.github.mgdx.potable.data.HubEauJson.resultRow
import io.github.mgdx.potable.data.HubEauJson.udiRow
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class RemoteWaterRepositoryTest {
    @get:Rule
    val serverRule = MockWebServerRule()

    private val server get() = serverRule.server

    private val repository by lazy {
        RemoteWaterRepository(
            HubEauClient(OkHttpClient(), server.url("/api/v1/qualite_eau_potable/")),
        )
    }

    private fun rows(sample: String, date: String, count: Int, firstParameter: Int = 1) =
        List(count) { resultRow(sample, date, (firstParameter + it).toString()) }

    @Test
    fun udis_choosesAmongEveryRow() = runTest {
        server.enqueue(
            response(
                page(2, null, listOf(udiRow("045000474", 2026), udiRow("045000474", 2025))),
            ),
        )

        val choice = repository.udis("45234")

        assertTrue(choice is UdiChoice.Single)
        val udi = (choice as UdiChoice.Single).udi
        assertEquals("045000474", udi.code)
        assertEquals(2026, udi.year)
    }

    @Test
    fun udis_noRowIsNone() = runTest {
        server.enqueue(response(page(0, null, emptyList())))

        assertEquals(UdiChoice.None, repository.udis("99999"))
    }

    @Test
    fun latestSample_queriesNewestFirstWithKnownFields() = runTest {
        server.enqueue(response(page(0, null, emptyList())))

        repository.latestSample("45234", "045000474")

        val url = server.takeRequest().url
        assertEquals("/api/v1/qualite_eau_potable/resultats_dis", url.encodedPath)
        assertEquals("45234", url.queryParameter("code_commune"))
        assertEquals("045000474", url.queryParameter("code_reseau"))
        assertEquals("desc", url.queryParameter("sort"))
        assertEquals("1", url.queryParameter("page"))
        assertEquals(
            RemoteWaterRepository.SAMPLE_PAGE_SIZE.toString(),
            url.queryParameter("size"),
        )
        assertEquals(
            RemoteWaterRepository.RESULT_FIELDS.joinToString(","),
            url.queryParameter("fields"),
        )
        // Asked for in `fields`, `code_reseau` comes back as an array.
        assertTrue("code_reseau" !in RemoteWaterRepository.RESULT_FIELDS)
        assertNull(url.queryParameter("date_min_prelevement"))
    }

    @Test
    fun latestSample_withoutNetworkDoesNotFilter() = runTest {
        server.enqueue(response(page(0, null, emptyList())))

        repository.latestSample("45234", null)

        assertNull(server.takeRequest().url.queryParameter("code_reseau"))
    }

    @Test
    fun latestSample_noDataIsNull() = runTest {
        server.enqueue(response(page(0, null, emptyList())))

        assertNull(repository.latestSample("99999", null))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun latestSample_stopsWhenTheDayChangesWithinAPage() = runTest {
        val first = rows("P2", "2026-08-26T12:50:00Z", 3)
        val older = rows("P1", "2026-08-25T10:04:00Z", 2)
        server.enqueue(response(page(5000, NEXT, first + older), partial = true))

        val sample = repository.latestSample("45234", null)!!

        assertEquals(1, server.requestCount)
        assertEquals("P2", sample.code)
        assertEquals(Instant.parse("2026-08-26T12:50:00Z"), sample.date)
        assertEquals(3, sample.results.size)
    }

    @Test
    fun latestSample_rowWithoutParameterCodeDoesNotFailThePage() = runTest {
        // Toulouse publishes "Prélèvement sous acréditation" with a null code_parametre.
        val date = "2026-08-31T14:43:00Z"
        val body = listOf(resultRow("P1", date, null)) + rows("P1", date, 2)
        server.enqueue(response(page(3, null, body)))

        val sample = repository.latestSample("31555", null)!!

        assertEquals("P1", sample.code)
        assertEquals(Instant.parse(date), sample.date)
        assertEquals(listOf("1", "2"), sample.results.map { it.parameterCode })
    }

    @Test
    fun latestSample_followsPagesWhileTheDateHolds() = runTest {
        val date = "2026-08-26T12:50:00Z"
        server.enqueue(response(page(5000, NEXT, rows("P2", date, 4)), partial = true))
        server.enqueue(
            response(page(5000, NEXT, rows("P2", date, 4, firstParameter = 5)), partial = true),
        )
        server.enqueue(
            response(
                page(
                    5000,
                    NEXT,
                    rows("P2", date, 2, firstParameter = 9) +
                        rows("P1", "2026-08-12T10:54:00Z", 3),
                ),
                partial = true,
            ),
        )

        val sample = repository.latestSample("45234", null)!!

        assertEquals(3, server.requestCount)
        assertEquals(
            listOf("1", "2", "3"),
            List(3) {
                server.takeRequest().url.queryParameter("page")
            },
        )
        assertEquals("P2", sample.code)
        assertEquals(10, sample.results.size)
    }

    @Test
    fun latestSamples_readsEverySampleOfTheNewestDay() = runTest {
        // Hub'Eau sorts on the full date: the second sample of the day comes
        // after the last row of the first one, on the next page.
        server.enqueue(
            response(page(9, NEXT, rows("P3", "2026-08-26T15:10:00Z", 3)), partial = true),
        )
        server.enqueue(
            response(
                page(
                    9,
                    NEXT,
                    rows("P3", "2026-08-26T15:10:00Z", 1, firstParameter = 4) +
                        rows("P2", "2026-08-26T08:30:00Z", 2),
                ),
                partial = true,
            ),
        )
        server.enqueue(
            response(
                page(
                    9,
                    NEXT,
                    rows("P2", "2026-08-26T08:30:00Z", 1, firstParameter = 3) +
                        rows("P1", "2026-08-12T10:54:00Z", 2),
                ),
                partial = true,
            ),
        )

        val samples = repository.latestSamples("45234", null)

        assertEquals(3, server.requestCount)
        assertEquals(listOf("P3", "P2"), samples.map { it.code })
        assertEquals(listOf(4, 3), samples.map { it.results.size })
    }

    @Test
    fun latestSample_stopsOnTheLastPage() = runTest {
        val date = "2026-08-26T12:50:00Z"
        server.enqueue(response(page(6, NEXT, rows("P2", date, 3)), partial = true))
        server.enqueue(
            response(page(6, null, rows("P2", date, 3, firstParameter = 4)), partial = true),
        )

        val sample = repository.latestSample("45234", null)!!

        assertEquals(2, server.requestCount)
        assertEquals(6, sample.results.size)
    }

    @Test
    fun latestSample_makesAtMostTenRequests() = runTest {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val page = request.url.queryParameter("page")!!.toInt()
                    val body =
                        page(
                            1_000_000,
                            NEXT,
                            rows(
                                "P1",
                                "2026-08-26T12:50:00Z",
                                2,
                                firstParameter =
                                2 * page,
                            ),
                        )
                    return response(body, partial = true)
                }
            }

        val sample = repository.latestSample("45234", null)!!

        assertEquals(RemoteWaterRepository.MAX_SAMPLE_PAGES, server.requestCount)
        assertEquals("P1", sample.code)
    }

    @Test(expected = DataError.Http::class)
    fun latestSample_propagatesErrors() = runTest {
        // Twice: the client retries a 503 once.
        repeat(2) { server.enqueue(MockResponse.Builder().code(503).build()) }

        repository.latestSample("45234", null)
    }
}
