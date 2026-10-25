package io.github.mgdx.potable.ui.history

import io.github.mgdx.potable.core.model.SeriesPoint
import io.github.mgdx.potable.core.model.Threshold
import io.github.mgdx.potable.ui.sheet.publishedDate
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

// Pure functions of the history chart (SPEC §5 F5): what is drawn and where,
// independent of Compose so that they are tested on the JVM.

/**
 * A measure as drawn: [day] is the published date (epoch day), [value] the
 * height it is drawn at, [index] its position in the series's points.
 */
data class PlotPoint(val index: Int, val day: Long, val value: Double, val censored: Boolean)

/**
 * The height at which [point] is drawn, or null if it cannot be.
 *
 * A censored value ("<0,01", ">80") is drawn at its published bound, read
 * from the text, or at `resultat_numerique` if the text cannot be read. A
 * qualitative result ("Aspect normal", whose number is a meaningless `0.0`)
 * or a missing one ("N.M.") is not drawn.
 */
fun plotValue(point: SeriesPoint): Double? {
    val text = point.text?.trim().orEmpty()
    if (point.censored) {
        return parseDecimal(text.trimStart('<', '>', '=', '≤', '≥', ' ')) ?: point.value
    }
    if (text.isNotEmpty() && parseDecimal(text) == null) return null
    return point.value
}

/** The drawable points of [points], in date order. */
fun plotPoints(points: List<SeriesPoint>): List<PlotPoint> = points
    .mapIndexedNotNull { index, point ->
        plotValue(point)?.let { value ->
            PlotPoint(index, publishedDate(point.date).toEpochDay(), value, point.censored)
        }
    }
    .sortedWith(compareBy({ it.day }, { it.index }))

/** A decimal number as Hub'Eau writes it ("0,01", "12.5", "1 200"), or null. */
internal fun parseDecimal(text: String): Double? {
    val cleaned = text.filterNot { it.isWhitespace() || it == ' ' || it == ' ' }
        .replace(',', '.')
    return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** Where a bound of a [Threshold] goes with respect to the chart. */
enum class BoundPlacement {
    /** Inside the value scale: drawn as a line. */
    DRAWN,

    /** Too far above the data to be drawn without crushing them: said in text. */
    ABOVE,

    /** Too far below the data: said in text. */
    BELOW,
}

/**
 * How far from the data a bound may be and still be drawn, in multiples of
 * the data's magnitude (the larger of their spread and their largest absolute
 * value): up to 9, so that the data keep at least a tenth of the chart's
 * height. A limit 100 times larger than the data is said in text instead.
 */
const val BOUND_INCLUDE_FACTOR = 9.0

/** Whether [bound] is drawn on a chart of data spanning [dataMin]..[dataMax]. */
fun placeBound(bound: Double, dataMin: Double, dataMax: Double): BoundPlacement {
    if (bound in dataMin..dataMax) return BoundPlacement.DRAWN
    val magnitude = max(dataMax - dataMin, max(abs(dataMin), abs(dataMax)))
    val gap = if (bound > dataMax) bound - dataMax else dataMin - bound
    return when {
        gap <= BOUND_INCLUDE_FACTOR * magnitude -> BoundPlacement.DRAWN
        bound > dataMax -> BoundPlacement.ABOVE
        else -> BoundPlacement.BELOW
    }
}

/** A bound of a threshold and its placement. */
data class PlacedBound(val value: Double, val placement: BoundPlacement)

/** The lower and upper bounds of [threshold] that exist, placed against the data. */
fun placeThreshold(threshold: Threshold?, dataMin: Double, dataMax: Double): List<PlacedBound> =
    listOfNotNull(threshold?.lower, threshold?.upper)
        .filter { it.isFinite() }
        .map { PlacedBound(it, placeBound(it, dataMin, dataMax)) }

/** How a whole threshold relates to the chart, for its legend. */
enum class ThresholdPlacement { DRAWN, ABOVE, BELOW, PARTLY_OUTSIDE }

/** The placement of a threshold made of [bounds], null if it has none. */
fun thresholdPlacement(bounds: List<PlacedBound>): ThresholdPlacement? {
    if (bounds.isEmpty()) return null
    val placements = bounds.map { it.placement }.toSet()
    return when {
        placements == setOf(BoundPlacement.DRAWN) -> ThresholdPlacement.DRAWN
        BoundPlacement.DRAWN in placements -> ThresholdPlacement.PARTLY_OUTSIDE
        placements == setOf(BoundPlacement.ABOVE) -> ThresholdPlacement.ABOVE
        placements == setOf(BoundPlacement.BELOW) -> ThresholdPlacement.BELOW
        else -> ThresholdPlacement.PARTLY_OUTSIDE
    }
}

/** A value axis: from [min] to [max], graduated every [step] at [ticks]. */
data class ValueAxis(val min: Double, val max: Double, val step: Double, val ticks: List<Double>) {
    /** Fraction digits needed to write every tick. */
    val fractionDigits: Int get() = max(0, -floor(log10(step) + 1e-9).toInt())
}

/** The "nice" step (1, 2 or 5 times a power of ten) at least [raw]. */
fun niceStep(raw: Double): Double {
    require(raw > 0 && raw.isFinite()) { "step must be positive" }
    val exponent = floor(log10(raw))
    val power = 10.0.pow(exponent)
    val fraction = raw / power
    val nice = when {
        fraction <= 1.0 + 1e-9 -> 1.0
        fraction <= 2.0 + 1e-9 -> 2.0
        fraction <= 5.0 + 1e-9 -> 5.0
        else -> 10.0
    }
    return nice * power
}

/**
 * A value axis covering every one of [values] with at most [maxTicks]
 * graduations (at least 2) on round numbers. A flat series gets some room
 * around its value.
 */
fun valueAxis(values: List<Double>, maxTicks: Int): ValueAxis {
    require(values.isNotEmpty()) { "no value" }
    val limit = max(2, maxTicks)
    var low = values.min()
    var high = values.max()
    if (high - low <= 0.0) {
        val pad = if (low == 0.0) 1.0 else abs(low) * 0.1
        low -= pad
        high += pad
    }
    var step = niceStep((high - low) / (limit - 1))
    while (true) {
        val first = floor(low / step + 1e-9) * step
        val last = ceil(high / step - 1e-9) * step
        val count = ((last - first) / step).roundToLong().toInt() + 1
        if (count <= limit) {
            val ticks = (0 until count).map { round(first + it * step, step) }
            return ValueAxis(ticks.first(), ticks.last(), step, ticks)
        }
        step = niceStep(step * 1.5)
    }
}

/** [value] rounded to the precision of [step], to drop floating-point noise. */
private fun round(value: Double, step: Double): Double {
    val digits = max(0, -floor(log10(step) + 1e-9).toInt()) + 1
    val factor = 10.0.pow(digits)
    return (value * factor).roundToLong() / factor
}

/** Time span of the chart, in epoch days; never empty. */
data class TimeAxis(val startDay: Long, val endDay: Long)

/** Half the span given to a series whose measures are all on the same day. */
const val SAME_DAY_PADDING = 15L

/** A time axis from the first to the last of [days]. */
fun timeAxis(days: List<Long>): TimeAxis {
    require(days.isNotEmpty()) { "no day" }
    val first = days.min()
    val last = days.max()
    return if (first == last) {
        TimeAxis(first - SAME_DAY_PADDING, last + SAME_DAY_PADDING)
    } else {
        TimeAxis(first, last)
    }
}

/** What the date graduations stand for, which decides how they are written. */
enum class DateTickUnit { DAY, MONTH, YEAR }

/** Date graduations, in order. */
data class DateTicks(val dates: List<LocalDate>, val unit: DateTickUnit)

/** Candidate steps between two date graduations, in months. */
private val MONTH_STEPS = listOf(1, 2, 3, 6, 12, 24, 60, 120)

/**
 * The finest graduations between [start] and [end] that [fits] accepts:
 * first days of months, every 1, 2, 3 or 6 months (aligned on January), then
 * every 1, 2, 5 or 10 years. A span too short for two month graduations is
 * graduated by its first and last days. When nothing fits, the coarsest
 * graduations are returned; the drawing then skips labels that would overlap.
 */
fun dateTicks(start: LocalDate, end: LocalDate, fits: (DateTicks) -> Boolean): DateTicks {
    var candidate: DateTicks? = null
    for (step in MONTH_STEPS) {
        val dates = monthTicks(start, end, step)
        if (step == MONTH_STEPS.first() && dates.size < 2) {
            val days = DateTicks(listOf(start, end).distinct(), DateTickUnit.DAY)
            return if (fits(days)) days else DateTicks(listOf(start), DateTickUnit.DAY)
        }
        candidate = DateTicks(dates, if (step >= 12) DateTickUnit.YEAR else DateTickUnit.MONTH)
        if (fits(candidate)) return candidate
    }
    return checkNotNull(candidate)
}

/** [dateTicks] with at most [maxTicks] graduations. */
fun dateTicks(start: LocalDate, end: LocalDate, maxTicks: Int): DateTicks =
    dateTicks(start, end) { it.dates.size <= maxTicks }

private fun monthTicks(start: LocalDate, end: LocalDate, stepMonths: Int): List<LocalDate> {
    var month = start.withDayOfMonth(1)
    if (month < start) month = month.plusMonths(1)
    while ((month.year * 12 + month.monthValue - 1) % stepMonths != 0) {
        month = month.plusMonths(1)
    }
    val ticks = mutableListOf<LocalDate>()
    while (month <= end) {
        ticks += month
        month = month.plusMonths(stepMonths.toLong())
    }
    return ticks
}

/** Maps [value] in [from]..[to] linearly onto [start]..[end]; [to] > [from]. */
fun project(value: Double, from: Double, to: Double, start: Float, end: Float): Float =
    (start + (value - from) / (to - from) * (end - start)).toFloat()

/**
 * The index in [xs] (ascending positions of the points) of the point nearest
 * to [x]; on a tie, the earlier one. Null when there is no point.
 */
fun nearestIndex(xs: List<Float>, x: Float): Int? {
    var best: Int? = null
    var bestDistance = Float.MAX_VALUE
    xs.forEachIndexed { index, position ->
        val distance = abs(position - x)
        if (distance < bestDistance) {
            best = index
            bestDistance = distance
        }
    }
    return best
}

/**
 * The selection after a key press: [delta] points from [current], within
 * 0 until [count]; the first or last point when nothing was selected.
 */
fun stepSelection(current: Int?, delta: Int, count: Int): Int? {
    if (count <= 0) return null
    if (current == null) return if (delta >= 0) 0 else count - 1
    return min(count - 1, max(0, current + delta))
}
