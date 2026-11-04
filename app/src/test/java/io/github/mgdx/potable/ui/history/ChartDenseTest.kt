package io.github.mgdx.potable.ui.history

import androidx.compose.material3.lightColorScheme
import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.core.model.SeriesPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** A chart of many measures (Paris, nitrates, 10 years): no solid band, selection by date. */
class ChartDenseTest {
    private val start = LocalDate.of(2016, 1, 1)

    private fun point(
        day: Int,
        value: Double,
        text: String = value.toString(),
        censored: Boolean = false,
    ) = SeriesPoint(
        Instant.parse("${start.plusDays(day.toLong())}T08:00:00Z"),
        "s-$day",
        value,
        text,
        censored,
    )

    private fun series(points: List<SeriesPoint>) = ParameterSeries(
        parameterCode = "1340",
        label = "Nitrates (en NO3)",
        unit = "mg/L",
        points = points,
        limitText = "<=50 mg/L",
        referenceText = null,
        limit = null,
        reference = null,
    )

    private fun plot(day: Long, censored: Boolean = false, joins: Boolean = !censored) =
        PlotPoint(day.toInt(), day, 1.0, censored, joins)

    @Test
    fun `the chart is dense above the threshold only`() {
        val atThreshold = ChartModel.of(
            series(
                (0 until DENSE_POINT_COUNT).map {
                    point(it, 20.0)
                },
            ),
        )!!
        assertFalse(atThreshold.dense)
        assertTrue(atThreshold.markers.all { it })
        val above = ChartModel.of(series((0..DENSE_POINT_COUNT).map { point(it, 20.0) }))!!
        assertTrue(above.dense)
        // Every measure is still a point of the chart, none averaged.
        assertEquals(DENSE_POINT_COUNT + 1, above.points.size)
        assertTrue(above.markers.none { it })
    }

    @Test
    fun `dense, bounds and points the line does not reach keep their marker`() {
        val points = listOf(
            plot(0, joins = false),
            plot(1),
            plot(2, censored = true),
            // Alone between two bounds: no segment would show it.
            plot(3, joins = false),
            plot(4, censored = true),
            plot(5, joins = false),
            plot(6),
        )
        assertEquals(
            listOf(false, false, true, true, true, false, false),
            markersShown(points, dense = true),
        )
        assertTrue(markersShown(points, dense = false).all { it })
    }

    @Test
    fun `selection takes the measure nearest in date, the earlier on a tie`() {
        val points = listOf(plot(10), plot(20), plot(20), plot(40))
        val window = ChartWindow(0.0, 50.0)
        assertEquals(0, nearestDayIndex(points, 14.9, window))
        assertEquals(0, nearestDayIndex(points, 15.0, window))
        assertEquals(1, nearestDayIndex(points, 15.1, window))
        assertEquals(1, nearestDayIndex(points, 22.0, window))
        assertEquals(3, nearestDayIndex(points, 31.0, window))
        // Out of the window, a measure is not selected.
        assertEquals(1, nearestDayIndex(points, 45.0, ChartWindow(0.0, 30.0)))
        assertNull(nearestDayIndex(points, 45.0, ChartWindow(41.0, 50.0)))
    }

    @Test
    fun `the day under the finger follows the window`() {
        val window = ChartWindow(100.0, 200.0)
        assertEquals(100.0, dayAt(10f, 10f, 210f, window), 1e-9)
        assertEquals(150.0, dayAt(110f, 10f, 210f, window), 1e-9)
        assertEquals(200.0, dayAt(210f, 10f, 210f, window), 1e-9)
    }

    @Test
    fun `the legend of a dense chart shows the thin line alone`() {
        val sparse = ChartModel.of(series((0 until DENSE_POINT_COUNT).map { point(it, 20.0) }))!!
        assertEquals(LegendMark.MEASURE, measureLegendMark(sparse))
        val dense = ChartModel.of(series((0..DENSE_POINT_COUNT).map { point(it, 20.0) }))!!
        assertEquals(LegendMark.DENSE_MEASURE, measureLegendMark(dense))
        assertTrue(measureLineWidth(dense = true) < measureLineWidth(dense = false))
    }

    @Test
    fun `the measured values have one colour, on the chart and in the legend`() {
        val colors = lightColorScheme()
        assertEquals(colors.secondary, measureColor(colors))
    }
}
