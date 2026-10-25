package io.github.mgdx.potable.data

import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.core.model.SampleSummary
import java.time.LocalDate

/**
 * History of a unit (SPEC §5 F5). For a followed unit the result is cached (SPEC §7) and the network
 * is called at most once a day unless [force]; for any other unit it comes
 * straight from Hub'Eau.
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
}
