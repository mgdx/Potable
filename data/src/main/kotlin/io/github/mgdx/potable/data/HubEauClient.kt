package io.github.mgdx.potable.data

import io.github.mgdx.potable.core.hubeau.HubEauFormatException
import io.github.mgdx.potable.core.hubeau.HubEauParser
import io.github.mgdx.potable.core.hubeau.ResultRow
import io.github.mgdx.potable.core.hubeau.UdiRow
import io.github.mgdx.potable.core.model.Page
import kotlinx.coroutines.Dispatchers
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
 * Every call runs on [Dispatchers.IO], makes one attempt (no retry) and
 * throws [DataError] on failure ([DataError.Timeout] when connected but the
 * answer took longer than the read timeout), or [IllegalArgumentException] for a request
 * the API would refuse (URL too long, page out of range). Redirections are
 * never followed, whatever the [OkHttpClient] it is given. A body larger than
 * [maxBodyBytes] is refused as [DataError.Format] before it fills the memory.
 */
class HubEauClient(
    http: OkHttpClient,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL,
    readTimeout: Duration = Duration.ofSeconds(READ_TIMEOUT_SECONDS),
    private val maxBodyBytes: Long = MAX_BODY_BYTES,
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
            val result = parse(get(url), HubEauParser::parseCommunesUdi)
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
        return parse(get(url), HubEauParser::parseResultats)
    }

    private fun endpoint(operation: String): HttpUrl.Builder =
        baseUrl.newBuilder().addPathSegment(operation)

    private suspend fun get(url: HttpUrl): String {
        val text = url.toString()
        require(text.length <= MAX_URL_LENGTH) { "URL longer than $MAX_URL_LENGTH characters" }
        val progress = Progress()
        val request = Request.Builder().url(url).get().tag(Progress::class.java, progress).build()
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response ->
                    // 206 Partial Content means "more pages follow": a success.
                    if (response.code != HTTP_OK && response.code != HTTP_PARTIAL) {
                        throw DataError.Http(response.code)
                    }
                    response.body.boundedString(maxBodyBytes)
                }
            } catch (e: SocketTimeoutException) {
                // Connected, then no answer in time: Hub'Eau is too slow
                // (a connect timeout stays a network error).
                throw if (progress.connected) DataError.Timeout(e) else DataError.Network(e)
            } catch (e: IOException) {
                throw DataError.Network(e)
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
        const val READ_TIMEOUT_SECONDS = 60L

        private const val UDI_PAGE_SIZE = 5_000

        /** A commune has a few dozen rows; the bound only stops a runaway server. */
        private const val MAX_UDI_PAGES = 20

        private const val HTTP_OK = 200
        private const val HTTP_PARTIAL = 206
    }
}
