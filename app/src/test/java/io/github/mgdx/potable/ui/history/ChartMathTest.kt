package io.github.mgdx.potable.ui.history

import io.github.mgdx.potable.core.model.SeriesPoint
import io.github.mgdx.potable.core.model.Threshold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

class ChartMathTest {
    private fun point(
        date: String,
        value: Double?,
        text: String? = value?.toString(),
        censored: Boolean = false,
    ) = SeriesPoint(Instant.parse("${date}T08:00:00Z"), "s-$date", value, text, censored)

    @Test
    fun `a censored value is drawn at its published bound`() {
        assertEquals(0.01, plotValue(point("2026-01-01", 0.0, "<0,01", censored = true))!!, 1e-12)
        assertEquals(80.0, plotValue(point("2026-01-01", null, ">80", censored = true))!!, 1e-12)
        // Unreadable text: the published number.
        assertEquals(0.5, plotValue(point("2026-01-01", 0.5, "<LQ", censored = true))!!, 1e-12)
    }

    @Test
    fun `qualitative and missing results are not drawn`() {
        assertNull(plotValue(point("2026-01-01", 0.0, "Aspect normal")))
        assertNull(plotValue(point("2026-01-01", null, "N.M.")))
        assertEquals(12.5, plotValue(point("2026-01-01", 12.5, "12,5"))!!, 1e-12)
        assertEquals(3.0, plotValue(point("2026-01-01", 3.0, null))!!, 1e-12)
    }

    @Test
    fun `plot points are in date order and keep their index`() {
        val points = plotPoints(
            listOf(
                point("2026-03-01", 2.0),
                point("2026-01-01", 1.0),
                point("2026-02-01", 0.0, "Aspect normal"),
            ),
        )
        assertEquals(listOf(1, 0), points.map { it.index })
        assertEquals(LocalDate.of(2026, 1, 1).toEpochDay(), points.first().day)
    }

    @Test
    fun `a limit near the data is drawn`() {
        // Nitrates around 20 mg/L, limit 50.
        assertEquals(BoundPlacement.DRAWN, placeBound(50.0, 18.0, 25.0))
        // pH 7.5 to 8, limits 6.5 and 9.
        assertEquals(BoundPlacement.DRAWN, placeBound(6.5, 7.5, 8.0))
        assertEquals(BoundPlacement.DRAWN, placeBound(9.0, 7.5, 8.0))
        // A bound inside the data.
        assertEquals(BoundPlacement.DRAWN, placeBound(0.0, 0.0, 0.0))
    }

    @Test
    fun `a limit 100 times larger than the data is not drawn`() {
        assertEquals(BoundPlacement.ABOVE, placeBound(50.0, 0.3, 0.5))
        assertEquals(BoundPlacement.ABOVE, placeBound(0.1, 0.0, 0.0))
        assertEquals(BoundPlacement.BELOW, placeBound(-100.0, 1.0, 2.0))
    }

    @Test
    fun `threshold placement sums up its bounds`() {
        val drawn = placeThreshold(Threshold(6.5, 9.0, "unité pH"), 7.5, 8.0)
        assertEquals(ThresholdPlacement.DRAWN, thresholdPlacement(drawn))
        val above = placeThreshold(Threshold(null, 50.0, "mg/L"), 0.3, 0.5)
        assertEquals(ThresholdPlacement.ABOVE, thresholdPlacement(above))
        val partly = placeThreshold(Threshold(0.5, 500.0, null), 1.0, 2.0)
        assertEquals(ThresholdPlacement.PARTLY_OUTSIDE, thresholdPlacement(partly))
        assertNull(thresholdPlacement(placeThreshold(null, 1.0, 2.0)))
        assertNull(thresholdPlacement(placeThreshold(Threshold(null, null, null), 1.0, 2.0)))
    }

    @Test
    fun `nice steps are 1, 2 or 5 times a power of ten`() {
        assertEquals(1.0, niceStep(0.9), 1e-12)
        assertEquals(2.0, niceStep(1.3), 1e-12)
        assertEquals(5.0, niceStep(4.2), 1e-12)
        assertEquals(10.0, niceStep(6.0), 1e-12)
        assertEquals(0.02, niceStep(0.017), 1e-12)
    }

    @Test
    fun `the value axis covers the data and the drawn limit on round numbers`() {
        val axis = valueAxis(listOf(18.0, 25.0, 50.0), maxTicks = 6)
        assertEquals(10.0, axis.min, 1e-12)
        assertEquals(50.0, axis.max, 1e-12)
        assertEquals(listOf(10.0, 20.0, 30.0, 40.0, 50.0), axis.ticks)
        assertTrue(axis.ticks.size <= 6)
        assertEquals(0, axis.fractionDigits)
    }

    @Test
    fun `the value axis respects its tick budget`() {
        for (max in 2..8) {
            val axis = valueAxis(listOf(0.013, 0.071), max)
            assertTrue("$max: ${axis.ticks}", axis.ticks.size <= max)
            assertTrue(axis.min <= 0.013 && axis.max >= 0.071)
        }
        val fine = valueAxis(listOf(0.013, 0.071), 8)
        assertEquals(2, fine.fractionDigits)
    }

    @Test
    fun `a flat series gets room around its value`() {
        val axis = valueAxis(listOf(7.8, 7.8), maxTicks = 5)
        assertTrue(axis.min < 7.8 && axis.max > 7.8)
        val zero = valueAxis(listOf(0.0, 0.0), maxTicks = 5)
        assertTrue(zero.min < 0.0 && zero.max > 0.0)
    }

    @Test
    fun `measures of a single day get a time span`() {
        val axis = timeAxis(listOf(100L, 100L))
        assertEquals(100L - SAME_DAY_PADDING, axis.startDay)
        assertEquals(100L + SAME_DAY_PADDING, axis.endDay)
        assertEquals(TimeAxis(10L, 20L), timeAxis(listOf(20L, 10L, 15L)))
    }

    @Test
    fun `date ticks fall on months, then years, within the budget`() {
        val start = LocalDate.of(2023, 10, 20)
        val end = LocalDate.of(2026, 10, 1)
        val monthly = dateTicks(start, end, maxTicks = 40)
        assertEquals(DateTickUnit.MONTH, monthly.unit)
        assertEquals(LocalDate.of(2023, 11, 1), monthly.dates.first())
        assertEquals(LocalDate.of(2026, 10, 1), monthly.dates.last())

        val quarterly = dateTicks(start, end, maxTicks = 12)
        assertEquals(DateTickUnit.MONTH, quarterly.unit)
        assertTrue(quarterly.dates.all { it.dayOfMonth == 1 && (it.monthValue - 1) % 3 == 0 })

        val yearly = dateTicks(start, end, maxTicks = 3)
        assertEquals(DateTickUnit.YEAR, yearly.unit)
        assertEquals(
            listOf(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1)),
            yearly.dates,
        )
    }

    @Test
    fun `a span too short for months is graduated by its ends`() {
        val start = LocalDate.of(2026, 3, 3)
        val end = LocalDate.of(2026, 3, 20)
        assertEquals(DateTicks(listOf(start, end), DateTickUnit.DAY), dateTicks(start, end, 5))
        assertEquals(DateTicks(listOf(start), DateTickUnit.DAY), dateTicks(start, end, 1))
    }

    @Test
    fun `date ticks accept any fitting rule`() {
        val start = LocalDate.of(2016, 1, 1)
        val end = LocalDate.of(2026, 1, 1)
        val ticks = dateTicks(start, end) { it.unit == DateTickUnit.YEAR && it.dates.size <= 6 }
        assertEquals(DateTickUnit.YEAR, ticks.unit)
        assertEquals(6, ticks.dates.size)
    }

    @Test
    fun `projection maps linearly, upside down for values`() {
        assertEquals(50f, project(5.0, 0.0, 10.0, 0f, 100f), 1e-4f)
        assertEquals(200f, project(0.0, 0.0, 10.0, 200f, 0f), 1e-4f)
    }

    @Test
    fun `the nearest point is chosen, the earlier on a tie`() {
        val xs = listOf(10f, 20f, 40f)
        assertEquals(0, nearestIndex(xs, -5f))
        assertEquals(1, nearestIndex(xs, 24f))
        assertEquals(1, nearestIndex(xs, 30f))
        assertEquals(2, nearestIndex(xs, 1000f))
        assertNull(nearestIndex(emptyList(), 3f))
    }

    @Test
    fun `keys step through the points and stop at the ends`() {
        assertEquals(0, stepSelection(null, 1, 3))
        assertEquals(2, stepSelection(null, -1, 3))
        assertEquals(2, stepSelection(1, 1, 3))
        assertEquals(2, stepSelection(2, 1, 3))
        assertEquals(0, stepSelection(1, -5, 3))
        assertNull(stepSelection(null, 1, 0))
    }

    @Test
    fun `values are written as published, with their unit`() {
        val locale = Locale.FRANCE
        assertEquals(
            "<0,01 mg/L",
            pointValueText(point("2026-01-01", 0.0, "<0,01"), "mg/L", locale),
        )
        assertEquals("12,5 mg/L", pointValueText(point("2026-01-01", 12.5, null), "mg/L", locale))
        assertEquals("7,8", pointValueText(point("2026-01-01", 7.8, null), "SANS OBJET", locale))
        assertNull(pointValueText(point("2026-01-01", null, null), "mg/L", locale))
    }

    @Test
    fun `hardness history asks both its codes`() {
        assertEquals(setOf("7972", "1345"), historyCodes("1345"))
        assertEquals(setOf("1340"), historyCodes("1340"))
        assertEquals(setOf("9999"), historyCodes("9999"))
    }
}
