package io.github.mgdx.potable.data

import io.github.mgdx.potable.core.hubeau.HubEauFormatException
import io.github.mgdx.potable.core.hubeau.HubEauParser
import io.github.mgdx.potable.core.hubeau.ResultRow
import io.github.mgdx.potable.core.hubeau.UdiRow
import io.github.mgdx.potable.core.model.Page
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Thin client of the Hub'Eau `qualite_eau_potable` API (SPEC §4.1).
 * Observed behaviour of the API: `docs/hubeau-api.md`.
 *
 * Every call runs on [Dispatchers.IO], is cancelled with the coroutine that
 * waits for it (a `CancellationException`, never a [DataError]) and
 * throws [DataError] on failure ([DataError.Timeout] when connected but the
 * answer took longer than the read timeout), or [IllegalArgumentException] for a request
 * the API would refuse (URL too long, page out of range). Redirections are
 * never followed, whatever the [OkHttpClient] it is given. A body larger than
 * [maxBodyBytes] is refused as [DataError.Format] before it fills the memory.
 *
 * Hub'Eau sometimes holds a request without answering, then a retry of the
 * same request answers at once (`docs/hubeau-api.md`, "Blocages"). So a small
 * request (`communes_udi`, or a `resultats_dis` page of at most
 * [QUICK_PAGE_SIZE] rows) waits [quickReadTimeout] for the answer, a larger
 * page [readTimeout]; and a request whose answer did not come in time, or
 * came as a `503`, is sent again **once**, at once. The second failure is
 * thrown as is.
 */
class HubEauClient(
    http: OkHttpClient,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL,
    readTimeout: Duration = Duration.ofSeconds(READ_TIMEOUT_SECONDS),
    private val maxBodyBytes: Long = MAX_BODY_BYTES,
    quickReadTimeout: Duration = Duration.ofSeconds(QUICK_READ_TIMEOUT_SECONDS),
) {
    // The API is genuinely slow on large queries: longer than OkHttp's 10 s.
    private val http: OkHttpClient =
        http
            .newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(readTimeout)
            // Hub'Eau answers HTTPS without redirecting: a 3xx is refused as
            // DataError.Http, so that no request ever leaves its host (SPEC §10).
            .followRedirects(false)
            .followSslRedirects(false)
            // Tells a slow Hub'Eau from an unreachable one (see [get]).
            .eventListenerFactory { ConnectionTracker() }
            .build()

    /** [http] with the read timeout of the small requests (same pool and settings). */
    private val quickHttp: OkHttpClient =
        this.http.newBuilder().readTimeout(minOf(quickReadTimeout, readTimeout)).build()

    /** Marks the call's [Progress] once a connection to the server is held. */
    private class ConnectionTracker : EventListener() {
        override fun connectionAcquired(call: Call, connection: Connection) {
            call.request().tag(Progress::class.java)?.connected = true
        }
    }

    private class Progress {
        @Volatile
        var connected = false
    }

    /** Every `communes_udi` row of [codeCommune], all pages followed. */
    suspend fun communesUdi(codeCommune: String): List<UdiRow> {
        val rows = mutableListOf<UdiRow>()
        for (page in 1..MAX_UDI_PAGES) {
            val url =
                endpoint("communes_udi")
                    .addQueryParameter("code_commune", codeCommune)
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("size", UDI_PAGE_SIZE.toString())
                    .build()
            val result = parse(get(url, quick = true), HubEauParser::parseCommunesUdi)
            rows += result.data
            // Past the last page the API answers an empty page whose `next`
            // is still set: both conditions end the walk.
            if (result.next == null || result.data.isEmpty()) break
        }
        return rows
    }

    /**
     * One page of `resultats_dis`, sorted by `date_prelevement`.
     *
     * [dateMin] and [dateMax] are sent as plain dates; the API reads them as
     * midnight, [dateMin] inclusive and [dateMax] exclusive. [codeParametre]
     * may be a comma-separated list of codes, which the API reads as "any of".
     * [codePrelevement] restricts the rows to one sample (a single code: the
     * API answers nothing to a list).
     */
    suspend fun resultats(
        codeCommune: String,
        codeReseau: String?,
        page: Int,
        size: Int,
        fields: List<String>?,
        sortDesc: Boolean = true,
        codeParametre: String? = null,
        dateMin: LocalDate? = null,
        dateMax: LocalDate? = null,
        codePrelevement: String? = null,
    ): Page<ResultRow> {
        require(page >= 1) { "page must be >= 1" }
        require(size in 1..MAX_PAGE_SIZE) { "size must be in 1..$MAX_PAGE_SIZE" }
        val url =
            endpoint("resultats_dis")
                .addQueryParameter("code_commune", codeCommune)
                .apply {
                    codeReseau?.let { addQueryParameter("code_reseau", it) }
                    codeParametre?.let { addQueryParameter("code_parametre", it) }
                    codePrelevement?.let { addQueryParameter("code_prelevement", it) }
                    dateMin?.let { addQueryParameter("date_min_prelevement", it.toString()) }
                    dateMax?.let { addQueryParameter("date_max_prelevement", it.toString()) }
                    if (!fields.isNullOrEmpty()) {
                        addQueryParameter("fields", fields.joinToString(","))
                    }
                }.addQueryParameter("sort", if (sortDesc) "desc" else "asc")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("size", size.toString())
                .build()
        return parse(get(url, quick = size <= QUICK_PAGE_SIZE), HubEauParser::parseResultats)
    }

    private fun endpoint(operation: String): HttpUrl.Builder =
        baseUrl.newBuilder().addPathSegment(operation)

    /** [attempt], and a single retry of a GET that Hub'Eau held (see the class). */
    private suspend fun get(url: HttpUrl, quick: Boolean): String {
        val text = url.toString()
        require(text.length <= MAX_URL_LENGTH) { "URL longer than $MAX_URL_LENGTH characters" }
        val client = if (quick) quickHttp else http
        return try {
            attempt(client, url)
        } catch (e: DataError) {
            val held = e is DataError.Timeout || (e is DataError.Http && e.code == HTTP_UNAVAILABLE)
            if (!held) throw e
            attempt(client, url)
        }
    }

    private suspend fun attempt(client: OkHttpClient, url: HttpUrl): String {
        val progress = Progress()
        val request = Request.Builder().url(url).get().tag(Progress::class.java, progress).build()
        val call = client.newCall(request)
        return withContext(Dispatchers.IO) {
            // execute() blocks in a socket read that neither the coroutine nor
            // Thread.interrupt() stops: cancelling the coroutine cancels the
            // call, which closes the socket and ends the read, body included.
            val outer = this
            val canceller =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        if (!outer.isActive) call.cancel()
                    }
                }
            try {
                call.execute().use { response ->
                    // 206 Partial Content means "more pages follow": a success.
                    if (response.code != HTTP_OK && response.code != HTTP_PARTIAL) {
                        throw DataError.Http(response.code)
                    }
                    response.body.boundedString(maxBodyBytes)
                }
            } catch (e: SocketTimeoutException) {
                ensureActive()
                // Connected, then no answer in time: Hub'Eau is too slow
                // (a connect timeout stays a network error).
                throw if (progress.connected) DataError.Timeout(e) else DataError.Network(e)
            } catch (e: IOException) {
                // OkHttp's "Canceled" is the coroutine's cancellation, not a
                // network error: it surfaces as a CancellationException.
                ensureActive()
                throw DataError.Network(e)
            } finally {
                canceller.cancel()
            }
        }
    }

    /**
     * The body as text, read only up to [limit] bytes: past it, a
     * [DataError.Format] rather than an [OutOfMemoryError] (a hostile or
     * runaway server). Said length first, then the bytes actually received
     * (a chunked body has no length).
     */
    private fun ResponseBody.boundedString(limit: Long): String {
        if (contentLength() > limit) throw tooLarge()
        val source = source()
        if (source.request(limit + 1)) throw tooLarge()
        return source.buffer.readString(contentType()?.charset() ?: Charsets.UTF_8)
    }

    private fun tooLarge() = DataError.Format(IOException("body larger than $maxBodyBytes bytes"))

    private fun <T> parse(json: String, parser: (String) -> Page<T>): Page<T> = try {
        parser(json)
    } catch (e: HubEauFormatException) {
        throw DataError.Format(e)
    }

    companion object {
        val DEFAULT_BASE_URL: HttpUrl =
            "https://hubeau.eaufrance.fr/api/v1/qualite_eau_potable/".toHttpUrl()

        /** Longest URL the API accepts (SPEC §4.1). */
        const val MAX_URL_LENGTH = 2083

        /** Largest `size` honoured by both operations; above, the API silently uses 5 000. */
        const val MAX_PAGE_SIZE = 20_000

        /**
         * Largest body read: 32 MiB. Measured on 2026-10-25, the largest page
         * the application can ask for (`resultats_dis`, Paris CENTRE, 10 years,
         * `size` = 20 000, timeline `fields`) is 8.3 MB, and the same page
         * without `fields`, never sent, 23.1 MB (`docs/decisions.md`).
         */
        const val MAX_BODY_BYTES = 32L * 1024 * 1024

        const val CONNECT_TIMEOUT_SECONDS = 15L

        /**
         * Read timeout of a page of more than [QUICK_PAGE_SIZE] rows: about
         * three times the slowest first byte measured (a page of 5 000 rows,
         * 7 to 11 s; `docs/hubeau-api.md`). Retry included, a held page costs
         * at most 60 s, the former single timeout.
         */
        const val READ_TIMEOUT_SECONDS = 30L

        /**
         * Read timeout of the small requests, which answer in 0.2 to 1.6 s
         * (17 s seen once during an outage): a held one is retried after
         * 15 s rather than waited for until the read timeout.
         */
        const val QUICK_READ_TIMEOUT_SECONDS = 15L

        /** Largest `resultats_dis` page read with [QUICK_READ_TIMEOUT_SECONDS]. */
        const val QUICK_PAGE_SIZE = 500

        private const val UDI_PAGE_SIZE = 5_000

        /** A commune has a few dozen rows; the bound only stops a runaway server. */
        private const val MAX_UDI_PAGES = 20

        private const val HTTP_OK = 200
        private const val HTTP_PARTIAL = 206
        private const val HTTP_UNAVAILABLE = 503
    }
}
