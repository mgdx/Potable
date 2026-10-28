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

/**
 * The part of a [TimeAxis] the chart shows, from [start] to [end] in epoch
 * days, fractional while the user zooms; [end] > [start].
 */
data class ChartWindow(val start: Double, val end: Double) {
    val span: Double get() = end - start

    /** True when [day] (epoch day) is within the window. */
    operator fun contains(day: Long): Boolean = day.toDouble() in start..end
}

/** Smallest span a window can be zoomed down to, in days (the whole axis if shorter). */
const val MIN_WINDOW_DAYS = 14.0

/** The window showing the whole of [time]. */
fun fullWindow(time: TimeAxis): ChartWindow =
    ChartWindow(time.startDay.toDouble(), time.endDay.toDouble())

/**
 * [window] when it lies within [time], the whole of [time] otherwise: a
 * window saved for another period, restored after the death of the process,
 * would squeeze the measures into a corner of the plot.
 */
fun windowWithin(window: ChartWindow, time: TimeAxis): ChartWindow {
    val full = fullWindow(time)
    val inside = window.start >= full.start - WINDOW_EPSILON &&
        window.end <= full.end + WINDOW_EPSILON &&
        window.span > 0.0
    return if (inside) window else full
}

/** True when [window] shows less than the whole of [time]. */
fun isZoomed(window: ChartWindow, time: TimeAxis): Boolean =
    window.span < fullWindow(time).span - WINDOW_EPSILON

/**
 * [window] zoomed by [factor] (above 1 zooms in, below 1 zooms out) around
 * [focus], a fraction of the plot's width from its left edge: the day under
 * the focus stays under it. The span stays between [MIN_WINDOW_DAYS] and the
 * whole of [time], and the window inside [time].
 */
fun zoomWindow(window: ChartWindow, time: TimeAxis, factor: Double, focus: Double): ChartWindow {
    if (!(factor > 0) || !factor.isFinite()) return window
    val full = fullWindow(time)
    val minSpan = min(MIN_WINDOW_DAYS, full.span)
    val span = (window.span / factor).coerceIn(minSpan, full.span)
    val at = if (focus.isFinite()) focus.coerceIn(0.0, 1.0) else 0.5
    val anchor = window.start + at * window.span
    return placeWindow(anchor - at * span, span, full)
}

/** [window] moved by [days] (positive: later), kept inside [time]. */
fun panWindow(window: ChartWindow, time: TimeAxis, days: Double): ChartWindow =
    if (days.isFinite()) placeWindow(window.start + days, window.span, fullWindow(time)) else window

/**
 * The days a horizontal drag of [dx] pixels moves [window] by, on a plot
 * [plotWidth] pixels wide: dragging to the right shows earlier days.
 */
fun dragDays(dx: Float, plotWidth: Float, window: ChartWindow): Double =
    if (plotWidth > 0f) -dx.toDouble() / plotWidth * window.span else 0.0

/**
 * [window] moved as little as possible to show [day] (epoch day), within
 * [time]; unchanged when it already does.
 */
fun revealDay(window: ChartWindow, time: TimeAxis, day: Long): ChartWindow {
    val at = day.toDouble()
    return when {
        at < window.start -> panWindow(window, time, at - window.start)
        at > window.end -> panWindow(window, time, at - window.end)
        else -> window
    }
}

/** A window of [span] days starting at [start], moved back inside [full]. */
private fun placeWindow(start: Double, span: Double, full: ChartWindow): ChartWindow {
    val clamped = start.coerceIn(full.start, max(full.start, full.end - span))
    return ChartWindow(clamped, min(full.end, clamped + span))
}

private const val WINDOW_EPSILON = 1e-6

/**
 * The whole days of [window], first to last, that date graduations may
 * stand on: never outside the window. A window shorter than a day gives its
 * nearest day.
 */
fun windowDays(window: ChartWindow): Pair<LocalDate, LocalDate> {
    val first = ceil(window.start - WINDOW_EPSILON).toLong()
    val last = floor(window.end + WINDOW_EPSILON).toLong()
    return if (first <= last) {
        LocalDate.ofEpochDay(first) to LocalDate.ofEpochDay(last)
    } else {
        val day = LocalDate.ofEpochDay(((window.start + window.end) / 2).roundToLong())
        day to day
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
fun nearestIndex(xs: List<Float>, x: Float): Int? =
    nearestVisibleIndex(xs, x, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)

/**
 * [nearestIndex] among the points whose position in [xs] lies within
 * [from]..[to] (the visible plot); null when none does.
 */
fun nearestVisibleIndex(xs: List<Float>, x: Float, from: Float, to: Float): Int? {
    var best: Int? = null
    var bestDistance = Float.MAX_VALUE
    xs.forEachIndexed { index, position ->
        if (position < from || position > to) return@forEachIndexed
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
