package io.github.mgdx.potable.data

import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.core.model.SampleSummary
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * History of a unit (SPEC §5 F5). The result is cached for any unit looked
 * at (SPEC §7) and the network is called at most once a day unless [force].
 */
interface HistoryRepository {
    /**
     * Every measure since [since] of the parameter known under any of
     * [parameterCodes] (a parameter can have several codes, e.g. hardness),
     * oldest first; null if Hub'Eau has none.
     * @throws DataError
     */
    suspend fun parameterSeries(
        communeCode: String,
        udiCode: String,
        parameterCodes: Set<String>,
        since: LocalDate,
        force: Boolean = false,
    ): ParameterSeries?

    /**
     * The samples since [since] with their conclusions, newest first.
     * @throws DataError
     */
    suspend fun sampleTimeline(
        communeCode: String,
        udiCode: String,
        since: LocalDate,
        force: Boolean = false,
    ): List<SampleSummary>

    /**
     * [parameterSeries] over the days from [since] to [until] (excluded),
     * days of the published date read in UTC: what a cache already holding
     * the days since [until] lacks. The default asks [parameterSeries] and
     * drops the newer points; null as there.
     * @throws DataError
     */
    suspend fun parameterSeriesBefore(
        communeCode: String,
        udiCode: String,
        parameterCodes: Set<String>,
        since: LocalDate,
        until: LocalDate,
    ): ParameterSeries? =
        parameterSeries(communeCode, udiCode, parameterCodes, since, force = true)?.let { series ->
            series.copy(points = series.points.filter { it.date < until.startUtc() })
        }

    /**
     * [sampleTimeline] over the days from [since] to [until] (excluded),
     * like [parameterSeriesBefore].
     * @throws DataError
     */
    suspend fun sampleTimelineBefore(
        communeCode: String,
        udiCode: String,
        since: LocalDate,
        until: LocalDate,
    ): List<SampleSummary> = sampleTimeline(communeCode, udiCode, since, force = true)
        .filter { it.date < until.startUtc() }
}

/** Start of a day as Hub'Eau's date filters read it (published date, UTC). */
internal fun LocalDate.startUtc() = atStartOfDay(ZoneOffset.UTC).toInstant()
