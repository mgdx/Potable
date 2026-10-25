package io.github.mgdx.potable.core.hubeau

import io.github.mgdx.potable.core.model.Page
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object HubEauParser {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val udiPage = Envelope.serializer(UdiRow.serializer())
    private val resultPage = Envelope.serializer(ResultRow.serializer())

    /** @throws HubEauFormatException if [json] is not a `communes_udi` page. */
    fun parseCommunesUdi(json: String): Page<UdiRow> = decode(json, udiPage, "communes_udi")

    /** @throws HubEauFormatException if [json] is not a `resultats_dis` page. */
    fun parseResultats(json: String): Page<ResultRow> = decode(json, resultPage, "resultats_dis")

    private fun <T> decode(
        text: String,
        serializer: KSerializer<Envelope<T>>,
        operation: String,
    ): Page<T> {
        val envelope = try {
            json.decodeFromString(serializer, text)
        } catch (e: SerializationException) {
            throw HubEauFormatException("invalid $operation page", e)
        } catch (e: IllegalArgumentException) {
            throw HubEauFormatException("invalid $operation page", e)
        }
        return Page(envelope.count, envelope.next, envelope.data)
    }

    /** The paging envelope shared by every Hub'Eau operation. */
    @Serializable
    private class Envelope<T>(
        @SerialName("count") val count: Int,
        @SerialName("next") val next: String? = null,
        @SerialName("data") val data: List<T>,
    )
}

class HubEauFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)
