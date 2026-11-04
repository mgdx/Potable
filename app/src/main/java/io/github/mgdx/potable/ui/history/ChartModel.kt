package io.github.mgdx.potable.ui.history

import androidx.compose.runtime.Immutable
import io.github.mgdx.potable.core.model.ParameterSeries

/**
 * What the chart of a series draws: its drawable points, the time span of
 * all its measures (a `<SEUIL` one included, though not drawn), and the
 * bounds of the quality limit and reference placed against the data on the
 * value scale (the points of the row of bounds left out).
 * Null from [of] when fewer than two measures can be drawn, or when none is
 * on the value scale.
 */
@Immutable
data class ChartModel(
    val points: List<PlotPoint>,
    val time: TimeAxis,
    val limit: List<PlacedBound>,
    val reference: List<PlacedBound>,
) {
    /** The points drawn at their height, the row of bounds left out. */
    val scaled: List<PlotPoint> get() = points.filterNot { it.inBoundRow }

    /** The values the value axis covers: the points on the scale and the bounds drawn. */
    val coveredValues: List<Double>
        get() = scaled.map { it.value } +
            (limit + reference).filter { it.placement == BoundPlacement.DRAWN }.map { it.value }

    /** True when a censored value is drawn at its bound, on the value scale. */
    val hasCensored: Boolean get() = scaled.any { it.censored }

    /**
     * True when there are too many points for a marker each
     * ([DENSE_POINT_COUNT]): the measured values are drawn by the line alone.
     */
    val dense: Boolean get() = points.size > DENSE_POINT_COUNT

    /** Whether each point gets its marker ([markersShown]); computed once. */
    val markers: List<Boolean> by lazy { markersShown(points, dense) }

    /** The points of the row of bounds, under the time axis. */
    val boundRow: List<PlotPoint> get() = points.filter { it.inBoundRow }

    /**
     * The lowest and highest points for a summary: measured values only, a
     * bound being no measure; the points on the scale when none is measured.
     */
    val extremes: Pair<PlotPoint, PlotPoint>
        get() {
            val candidates = scaled.filterNot { it.censored }.ifEmpty { scaled }
            return candidates.minBy { it.value } to candidates.maxBy { it.value }
        }

    companion object {
        fun of(series: ParameterSeries): ChartModel? {
            val points = plotPoints(series.points)
            if (points.size < 2) return null
            val scaled = points.filterNot { it.inBoundRow }
            if (scaled.isEmpty()) return null
            val low = scaled.minOf { it.value }
            val high = scaled.maxOf { it.value }
            return ChartModel(
                points = points,
                // Every measure, drawn or not: the chart reaches the latest.
                time = timeAxis(series.points.map(::measureDay)),
                limit = placeThreshold(series.limit, low, high),
                reference = placeThreshold(series.reference, low, high),
            )
        }
    }
}
