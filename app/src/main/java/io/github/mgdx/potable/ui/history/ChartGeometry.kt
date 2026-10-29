package io.github.mgdx.potable.ui.history

import android.text.format.DateFormat
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.ui.sheet.localizedDateFormatter
import io.github.mgdx.potable.ui.sheet.withDigitsOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.max

/** Height of the plotting area, axis labels included. */
internal val CHART_HEIGHT = 240.dp
internal val MARKER_RADIUS = 4.dp
internal val LABEL_GAP = 4.dp
internal val TICK_LENGTH = 4.dp

/** Pixel layout of the chart for one size; computed outside the drawing. */
internal class ChartGeometry(
    val plotLeft: Float,
    val plotTop: Float,
    val plotRight: Float,
    val plotBottom: Float,
    val yLabels: List<Pair<Float, TextLayoutResult>>,
    val xLabels: List<Pair<Float, TextLayoutResult>>,
    val gridYs: List<Float>,
    val pointXs: List<Float>,
    val pointYs: List<Float>,
    val limitYs: List<Float>,
    val referenceYs: List<Float>,
)

/** How the date graduations of [unit] are written in [locale]. */
private fun dateFormatter(unit: DateTickUnit, locale: Locale): DateTimeFormatter = when (unit) {
    DateTickUnit.DAY -> localizedDateFormatter(FormatStyle.MEDIUM, locale)
    DateTickUnit.MONTH ->
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMy"))
            .withDigitsOf(locale)
    DateTickUnit.YEAR ->
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "y"))
            .withDigitsOf(locale)
}

/**
 * The layout of [model] for one size, showing [window] of its time axis: the
 * value axis covers the whole series, so that it stays still while the user
 * zooms; the date graduations are those of the window.
 */
internal fun computeGeometry(
    model: ChartModel,
    window: ChartWindow,
    width: Float,
    height: Float,
    measurer: TextMeasurer,
    style: TextStyle,
    locale: Locale,
    density: Density,
): ChartGeometry {
    val radius = with(density) { MARKER_RADIUS.toPx() }
    val gap = with(density) { LABEL_GAP.toPx() }
    val tick = with(density) { TICK_LENGTH.toPx() }
    val lineHeight = measurer.measure("0", style).size.height.toFloat()

    val plotTop = max(radius, lineHeight / 2) + gap
    val plotBottom = height - lineHeight - tick - gap
    // Value labels at least two lines apart.
    val maxYTicks = ((plotBottom - plotTop) / (lineHeight * 2)).toInt() + 1
    val axis = valueAxis(model.coveredValues, maxYTicks)
    val yTexts = axis.ticks.map {
        measurer.measure(formatNumber(it, axis.fractionDigits, locale), style)
    }
    val plotLeft = (yTexts.maxOfOrNull { it.size.width } ?: 0) + gap * 2
    val plotRight = width - radius - gap
    fun xOf(day: Long) = project(day.toDouble(), window.start, window.end, plotLeft, plotRight)
    fun yOf(value: Double) = project(value, axis.min, axis.max, plotBottom, plotTop)

    val measured = mutableMapOf<Pair<LocalDate, DateTickUnit>, TextLayoutResult>()
    fun label(date: LocalDate, unit: DateTickUnit) = measured.getOrPut(date to unit) {
        measurer.measure(dateFormatter(unit, locale).format(date), style)
    }
    val (firstDay, lastDay) = windowDays(window)
    val ticks = dateTicks(firstDay, lastDay) { candidate ->
        // Consecutive labels, centred on their graduation, must not touch.
        candidate.dates.zipWithNext().all { (a, b) ->
            val room = xOf(b.toEpochDay()) - xOf(a.toEpochDay())
            room >=
                (label(a, candidate.unit).size.width + label(b, candidate.unit).size.width) / 2 +
                gap * 2
        }
    }
    return ChartGeometry(
        plotLeft = plotLeft,
        plotTop = plotTop,
        plotRight = plotRight,
        plotBottom = plotBottom,
        yLabels = axis.ticks.zip(yTexts).map { (value, text) -> yOf(value) to text },
        xLabels = ticks.dates.map { xOf(it.toEpochDay()) to label(it, ticks.unit) },
        gridYs = axis.ticks.map(::yOf),
        pointXs = model.points.map { xOf(it.day) },
        pointYs = model.points.map { yOf(it.value) },
        limitYs = model.limit.filter { it.placement == BoundPlacement.DRAWN }.map { yOf(it.value) },
        referenceYs = model.reference
            .filter { it.placement == BoundPlacement.DRAWN }
            .map { yOf(it.value) },
    )
}
