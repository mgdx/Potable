package io.github.mgdx.potable.ui.history

import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.core.model.SeriesPoint
import io.github.mgdx.potable.core.model.Threshold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Values published as bounds ("<0,500") on the history chart: no peak, no maximum. */
class ChartCensoredTest {
    private fun measured(date: String, value: Double) =
        SeriesPoint(Instant.parse("${date}T08:00:00Z"), "s-$date", value, value.toString(), false)

    private fun below(date: String, text: String = "<0,500") =
        SeriesPoint(Instant.parse("${date}T09:00:00Z"), "b-$date", 0.0, text, true)

    private fun series(vararg points: SeriesPoint) = ParameterSeries(
        parameterCode = "6276",
        label = "Total des pesticides analysés",
        unit = "µg/L",
        points = points.toList(),
        limitText = "<=0,5 µg/L",
        referenceText = null,
        limit = Threshold(null, 0.5, "µg/L"),
        reference = null,
    )

    /** Paris, total of pesticides: bounds at 0,5, on the quality limit, among measures of 0,01–0,1. */
    private val paris = series(
        measured("2024-01-10", 0.012),
        below("2024-02-10"),
        measured("2024-03-10", 0.036),
        below("2024-04-10"),
        below("2024-05-10"),
        measured("2024-06-10", 0.1),
        measured("2024-07-10", 0.02),
    )

    @Test
    fun `a bound equal to the limit is never joined and goes to the row`() {
        val model = ChartModel.of(paris)!!
        val bounds = model.points.filter { it.censored }
        assertEquals(3, bounds.size)
        assertTrue(bounds.all { it.inBoundRow && !it.joinsPrevious })
        // The line breaks across each bound: 0,012 | 0,036 | 0,1 – 0,02.
        val line = model.points.filterNot { it.censored }.map { it.joinsPrevious }
        assertEquals(listOf(false, false, false, true), line)
        assertEquals(model.boundRow, bounds)
    }

    @Test
    fun `bounds count neither as maximum nor minimum`() {
        val (lowest, highest) = ChartModel.of(paris)!!.extremes
        assertEquals(0.012, lowest.value, 1e-12)
        assertEquals(0.1, highest.value, 1e-12)
    }

    @Test
    fun `bounds of the row do not stretch the value scale`() {
        val model = ChartModel.of(paris)!!
        // Measures and the limit line only: the 0,5 here is the limit.
        assertEquals(listOf(0.012, 0.036, 0.1, 0.02, 0.5), model.coveredValues)
        val noLimit = ChartModel.of(paris.copy(limit = null))!!
        assertEquals(0.1, noLimit.coveredValues.max(), 1e-12)
    }

    @Test
    fun `a bound no higher than the measures stays at its bound, unjoined`() {
        val model = ChartModel.of(
            series(
                measured("2024-01-10", 0.02),
                below("2024-02-10", "<0,01"),
                measured("2024-03-10", 0.05),
            ),
        )!!
        val bound = model.points[1]
        assertTrue(bound.censored)
        assertFalse(bound.inBoundRow)
        assertEquals(0.01, bound.value, 1e-12)
        assertTrue(model.hasCensored)
        assertTrue(model.boundRow.isEmpty())
        assertEquals(listOf(false, false, false), model.points.map { it.joinsPrevious })
        assertEquals(0.02, model.extremes.first.value, 1e-12)
    }

    @Test
    fun `a series of bounds only gets no chart`() {
        val all = series(below("2024-01-10"), below("2024-02-10"), below("2024-03-10"))
        assertTrue(plotPoints(all.points).all { it.inBoundRow })
        assertNull(ChartModel.of(all))
    }

    @Test
    fun `a single measure among bounds stands alone`() {
        val model = ChartModel.of(
            series(below("2024-01-10"), measured("2024-02-10", 0.03), below("2024-03-10")),
        )!!
        assertEquals(listOf(true, false, true), model.points.map { it.inBoundRow })
        assertTrue(model.points.none { it.joinsPrevious })
        assertEquals(0.03, model.extremes.second.value, 1e-12)
        // The limit, 16 times the single measure, is said in text: the scale is the measure's.
        assertEquals(listOf(0.03), model.coveredValues)
    }

    @Test
    fun `a bound on the day of a measure does not break the line, whatever the order`() {
        val sameDay = listOf(
            measured("2024-01-10", 0.02),
            below("2024-02-10"),
            measured("2024-02-10", 0.04),
            measured("2024-03-10", 0.03),
        )
        for (points in listOf(sameDay, listOf(sameDay[0], sameDay[2], sameDay[1], sameDay[3]))) {
            val line = plotPoints(points).filterNot { it.censored }.map { it.joinsPrevious }
            assertEquals(listOf(false, true, true), line)
        }
        // A `<SEUIL` on the day of a measure: same rule.
        val seuil = SeriesPoint(Instant.parse("2024-02-10T10:00:00Z"), "x", 0.0, "<SEUIL", true)
        val line = plotPoints(listOf(sameDay[0], seuil, sameDay[2])).map { it.joinsPrevious }
        assertEquals(listOf(false, true), line)
    }

    @Test
    fun `two measures off the line between measures break it`() {
        val line = plotPoints(
            listOf(
                measured("2024-01-10", 0.02),
                below("2024-02-10"),
                below("2024-03-10"),
                measured("2024-03-10", 0.04),
            ),
        ).filterNot { it.censored }.map { it.joinsPrevious }
        assertEquals(listOf(false, false), line)
    }
}
