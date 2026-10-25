package io.github.mgdx.potable.data

import io.github.mgdx.potable.data.HubEauJson.NEXT
import io.github.mgdx.potable.data.HubEauJson.page
import io.github.mgdx.potable.data.HubEauJson.response
import io.github.mgdx.potable.data.HubEauJson.resultRow
import io.github.mgdx.potable.data.HubEauJson.udiRow
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class HubEauClientTest {
    @get:Rule
    val serverRule = MockWebServerRule()

    private val server get() = serverRule.server

    private fun client(http: OkHttpClient = OkHttpClient()) =
        HubEauClient(http, server.url("/api/v1/qualite_eau_potable/"))

    private suspend inline fun <reified E : Throwable> assertThrows(
        crossinline block: suspend () -> Unit,
    ): E {
        try {
            block()
        } catch (e: Throwable) {
            if (e is E) return e
            throw AssertionError("expected ${E::class.simpleName}, got $e", e)
        }
        fail("expected ${E::class.simpleName}, nothing thrown")
        throw IllegalStateException()
    }

    @Test
    fun communesUdi_sendsCommuneAndFirstPage() = runTest {
        server.enqueue(response(page(1, null, listOf(udiRow("045000474", 2026)))))

        val rows = client().communesUdi("45234")

        assertEquals(1, rows.size)
        val url = server.takeRequest().url
        assertEquals("/api/v1/qualite_eau_potable/communes_udi", url.encodedPath)
        assertEquals("45234", url.queryParameter("code_commune"))
        assertEquals("1", url.queryParameter("page"))
        assertEquals("5000", url.queryParameter("size"))
    }

    @Test
    fun communesUdi_followsEveryPage() = runTest {
        server.enqueue(
            response(
                page(3, NEXT, listOf(udiRow("A", 2026), udiRow("B", 2026))),
                partial = true,
            ),
        )
        server.enqueue(response(page(3, null, listOf(udiRow("C", 2025))), partial = true))

        val rows = client().communesUdi("45234")

        assertEquals(listOf("A", "B", "C"), rows.map { it.codeReseau })
        assertEquals(2, server.requestCount)
        assertEquals("1", server.takeRequest().url.queryParameter("page"))
        assertEquals("2", server.takeRequest().url.queryParameter("page"))
    }

    @Test
    fun communesUdi_stopsOnEmptyPageEvenWithNext() = runTest {
        // Past the last page the API answers 200, no rows, and a `next`.
        server.enqueue(response(page(1, NEXT, listOf(udiRow("A", 2026))), partial = true))
        server.enqueue(response(page(1, NEXT, emptyList())))

        val rows = client().communesUdi("45234")

        assertEquals(1, rows.size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun resultats_sendsEveryParameter() = runTest {
        server.enqueue(response(page(0, null, emptyList())))

        client().resultats(
            codeCommune = "45234",
            codeReseau = "045000474",
            page = 3,
            size = 500,
            fields = listOf("code_prelevement", "date_prelevement"),
            sortDesc = true,
            codeParametre = "1340",
            dateMin = LocalDate.of(2026, 8, 1),
            dateMax = LocalDate.of(2026, 9, 1),
        )

        val url = server.takeRequest().url
        assertEquals("/api/v1/qualite_eau_potable/resultats_dis", url.encodedPath)
        assertEquals("45234", url.queryParameter("code_commune"))
        assertEquals("045000474", url.queryParameter("code_reseau"))
        assertEquals("1340", url.queryParameter("code_parametre"))
        assertEquals("2026-08-01", url.queryParameter("date_min_prelevement"))
        assertEquals("2026-09-01", url.queryParameter("date_max_prelevement"))
        assertEquals("code_prelevement,date_prelevement", url.queryParameter("fields"))
        assertEquals("desc", url.queryParameter("sort"))
        assertEquals("3", url.queryParameter("page"))
        assertEquals("500", url.queryParameter("size"))
    }

    @Test
    fun resultats_omitsAbsentParameters() = runTest {
        server.enqueue(response(page(0, null, emptyList())))

        client().resultats(
            "45234",
            codeReseau = null,
            page = 1,
            size = 10,
            fields = null,
            sortDesc = false,
        )

        val url = server.takeRequest().url
        assertEquals("asc", url.queryParameter("sort"))
        assertEquals(
            setOf("code_commune", "sort", "page", "size"),
            url.queryParameterNames,
        )
    }

    @Test
    fun resultats_partialContentIsASuccess() = runTest {
        val rows = listOf(resultRow("P1", "2026-08-26T12:50:00Z", "1340"))
        server.enqueue(response(page(79087, NEXT, rows), partial = true))

        val result = client().resultats("45234", null, 1, 1, null)

        assertEquals(79087, result.count)
        assertEquals(NEXT, result.next)
        assertEquals("P1", result.data.single().codePrelevement)
    }

    @Test
    fun serverError_isHttpError() = runTest {
        server.enqueue(MockResponse.Builder().code(500).body("<html>oops</html>").build())

        val error = assertThrows<DataError.Http> { client().communesUdi("45234") }

        assertEquals(500, error.code)
        assertFalse(error.message.orEmpty().contains("45234"))
    }

    @Test
    fun badRequest_isHttpError() = runTest {
        server.enqueue(MockResponse.Builder().code(400).body("{}").build())

        val error =
            assertThrows<DataError.Http> { client().resultats("45234", null, 1, 1, null) }

        assertEquals(400, error.code)
    }

    @Test
    fun cutConnection_isNetworkError() = runTest {
        server.enqueue(
            MockResponse.Builder().onResponseStart(SocketEffect.ShutdownConnection).build(),
        )
        val http = OkHttpClient.Builder().retryOnConnectionFailure(false).build()

        val error =
            assertThrows<DataError.Network> {
                client(http).resultats("45234", null, 1, 1, null)
            }

        assertFalse(error.message.orEmpty().contains("45234"))
    }

    private fun impatientClient() = HubEauClient(
        OkHttpClient(),
        server.url("/api/v1/qualite_eau_potable/"),
        readTimeout = Duration.ofMillis(200),
    )

    @Test
    fun answerSlowerThanReadTimeout_isTimeoutNotNetwork() = runTest {
        // Connected, but the headers come after the read timeout (Hub'Eau
        // overloaded: Paris pH, 3 years, ~56 s then 500, 2026-10-25).
        server.enqueue(
            MockResponse.Builder()
                .headersDelay(2, TimeUnit.SECONDS)
                .code(200)
                .body(page(0, null, emptyList()))
                .build(),
        )

        assertThrows<DataError.Timeout> { impatientClient().resultats("45234", null, 1, 1, null) }
    }

    @Test
    fun bodySlowerThanReadTimeout_isTimeout() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .bodyDelay(2, TimeUnit.SECONDS)
                .code(200)
                .body(page(0, null, emptyList()))
                .build(),
        )

        assertThrows<DataError.Timeout> { impatientClient().communesUdi("45234") }
    }

    @Test
    fun answerWithinReadTimeout_succeeds() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .headersDelay(50, TimeUnit.MILLISECONDS)
                .code(200)
                .body(page(0, null, emptyList()))
                .build(),
        )

        assertTrue(impatientClient().communesUdi("45234").isEmpty())
    }

    @Test
    fun unreachableServer_isNetworkError() = runTest {
        val client = client()
        server.close()

        assertThrows<DataError.Network> { client.communesUdi("45234") }
    }

    @Test
    fun invalidJson_isFormatError() = runTest {
        server.enqueue(response("{\"count\": "))

        assertThrows<DataError.Format> { client().resultats("45234", null, 1, 1, null) }
    }

    @Test
    fun unexpectedJson_isFormatError() = runTest {
        server.enqueue(response("[1, 2, 3]"))

        assertThrows<DataError.Format> { client().communesUdi("45234") }
    }

    @Test
    fun tooLongUrl_isRefusedBeforeAnyRequest() = runTest {
        val fields = List(200) { "field_number_$it" }

        assertThrows<IllegalArgumentException> {
            client().resultats("45234", null, 1, 1, fields)
        }

        assertEquals(0, server.requestCount)
    }

    @Test
    fun redirectToAnotherHost_isNotFollowed() = runTest {
        MockWebServer().use { other ->
            other.start()
            other.enqueue(response(page(0, null, emptyList())))
            server.enqueue(
                MockResponse.Builder()
                    .code(302)
                    .setHeader("Location", other.url("/elsewhere").toString())
                    .build(),
            )

            val error = assertThrows<DataError.Http> { client().communesUdi("45234") }

            assertEquals(302, error.code)
            assertEquals(0, other.requestCount)
        }
    }

    @Test
    fun redirectOnTheSameHost_isNotFollowedEither() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(301)
                .setHeader("Location", server.url("/api/v1/qualite_eau_potable/moved").toString())
                .build(),
        )
        server.enqueue(response(page(0, null, emptyList())))

        val error = assertThrows<DataError.Http> { client().communesUdi("45234") }

        assertEquals(301, error.code)
        assertEquals(1, server.requestCount)
    }

    private fun smallClient() = HubEauClient(
        OkHttpClient(),
        server.url("/api/v1/qualite_eau_potable/"),
        maxBodyBytes = BODY_LIMIT,
    )

    /** An empty page padded with spaces to exactly [size] bytes. */
    private fun paddedPage(size: Long): String {
        val json = page(0, null, emptyList())
        return json + " ".repeat((size - json.length).toInt())
    }

    @Test
    fun bodyAtTheLimit_isRead() = runTest {
        server.enqueue(response(paddedPage(BODY_LIMIT)))

        assertTrue(smallClient().communesUdi("45234").isEmpty())
    }

    @Test
    fun bodyOverTheLimit_withLength_isFormatError() = runTest {
        server.enqueue(response(paddedPage(BODY_LIMIT + 1)))

        assertThrows<DataError.Format> { smallClient().communesUdi("45234") }
    }

    @Test
    fun bodyOverTheLimit_chunked_isFormatError() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .chunkedBody(paddedPage(BODY_LIMIT * 4), 512)
                .build(),
        )

        assertThrows<DataError.Format> { smallClient().communesUdi("45234") }
    }

    @Test
    fun bodyLimit_leavesRoomForTheLargestRealPage() {
        // 8.3 MB measured for the largest page the application asks for.
        assertTrue(HubEauClient.MAX_BODY_BYTES >= 3 * 8_300_000L)
    }

    @Test
    fun urlAtTheLimit_isSent() = runTest {
        val base = server.url("/api/v1/qualite_eau_potable/").toString().length
        // Fills the URL up to exactly MAX_URL_LENGTH characters.
        val fixed = "resultats_dis?code_commune=45234&fields=&sort=desc&page=1&size=1".length
        val field = "x".repeat(HubEauClient.MAX_URL_LENGTH - base - fixed)
        server.enqueue(response(page(0, null, emptyList())))

        client().resultats("45234", null, 1, 1, listOf(field))

        val sent = server.takeRequest().url.toString()
        assertEquals(HubEauClient.MAX_URL_LENGTH, sent.length)
    }

    @Test
    fun outOfRangePaging_isRefused() = runTest {
        assertThrows<IllegalArgumentException> { client().resultats("45234", null, 0, 1, null) }
        assertThrows<IllegalArgumentException> {
            client().resultats("45234", null, 1, 20_001, null)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun emptyResult_hasNoRowsAndNoNext() = runTest {
        server.enqueue(response(page(0, null, emptyList())))

        val result = client().resultats("99999", null, 1, 1, null)

        assertTrue(result.data.isEmpty())
        assertNull(result.next)
    }

    private companion object {
        const val BODY_LIMIT = 4_096L
    }
}
