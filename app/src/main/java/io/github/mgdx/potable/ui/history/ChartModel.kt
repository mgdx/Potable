package io.github.mgdx.potable.ui.history

import androidx.compose.runtime.Immutable
import io.github.mgdx.potable.core.model.ParameterSeries

/**
 * What the chart of a series draws: its drawable points, the time span, and
 * the bounds of the quality limit and reference placed against the data.
 * Null from [of] when fewer than two measures can be drawn.
 */
@Immutable
data class ChartModel(
    val points: List<PlotPoint>,
    val time: TimeAxis,
    val limit: List<PlacedBound>,
    val reference: List<PlacedBound>,
) {
    /** The values the value axis covers: the data and the bounds drawn. */
    val coveredValues: List<Double>
        get() = points.map { it.value } +
            (limit + reference).filter { it.placement == BoundPlacement.DRAWN }.map { it.value }

    val hasCensored: Boolean get() = points.any { it.censored }

    companion object {
        fun of(series: ParameterSeries): ChartModel? {
            val points = plotPoints(series.points)
            if (points.size < 2) return null
            val low = points.minOf { it.value }
            val high = points.maxOf { it.value }
            return ChartModel(
                points = points,
                time = timeAxis(points.map { it.day }),
                limit = placeThreshold(series.limit, low, high),
                reference = placeThreshold(series.reference, low, high),
            )
        }
    }
}
