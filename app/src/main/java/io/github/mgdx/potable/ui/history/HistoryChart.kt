package io.github.mgdx.potable.ui.history

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.ui.common.bidiWrap
import io.github.mgdx.potable.ui.sample.currentLocale
import io.github.mgdx.potable.ui.sheet.localizedDateFormatter
import io.github.mgdx.potable.ui.sheet.publishedDate
import io.github.mgdx.potable.ui.sheet.withDigitsOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Height of the plotting area, axis labels included. */
private val CHART_HEIGHT = 240.dp
private val MARKER_RADIUS = 4.dp
private val LABEL_GAP = 4.dp
private val TICK_LENGTH = 4.dp

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

/** Pixel layout of the chart for one size; computed outside the drawing. */
private class ChartGeometry(
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
private fun computeGeometry(
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

/** Saves a [ChartWindow] across configuration changes and process death. */
private val WindowSaver = listSaver<ChartWindow, Double>(
    save = { listOf(it.start, it.end) },
    restore = { ChartWindow(it[0], it[1]) },
)

/**
 * The line chart of a parameter (SPEC §5 F5): measures joined in date order,
 * censored values as hollow markers, the quality limit as a solid line and
 * the quality reference as a dashed one. Colours come from the theme only and
 * never judge a value (SPEC §14): the series in `secondary` (the brand's
 * light accent), the limit in `onSurface`, the reference in
 * `onSurfaceVariant`, the grid in `outlineVariant`, the axis in `outline`,
 * the tooltip in `inverseSurface`.
 *
 * The time axis runs left to right in every language (SPEC §11.2): the
 * plotting area is laid out left to right; the tooltip inside it, like the
 * legend outside, follows the layout of the interface.
 *
 * Touching selects the nearest visible measure ([onSelect], an index in
 * [ChartModel.points]); so does dragging along the whole chart, the arrow
 * keys once the chart has the focus, and the "previous / next measurement"
 * actions of screen readers. Pinching zooms the time axis, dragging then
 * moves along it, and a double tap shows the whole period again. Touching and
 * holding a measure opens the full report of its sample ([onOpenSample], a
 * `code_prelevement`), as does a screen reader action on the selected one.
 */
@Composable
fun HistoryChart(
    series: ParameterSeries,
    model: ChartModel,
    selected: Int?,
    onSelect: (Int) -> Unit,
    onOpenSample: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val uiDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = colors.onSurfaceVariant,
        textDirection = TextDirection.Ltr,
    )
    var window by rememberSaveable(model, stateSaver = WindowSaver) {
        mutableStateOf(fullWindow(model.time))
    }
    // The first composition after the death of the process restores the saved
    // window whatever the model: never keep one outside this time axis.
    windowWithin(window, model.time).let { if (it != window) window = it }
    val zoomed = isZoomed(window, model.time)
    val description = chartDescription(series, model, locale)
    val selectedPoint = selected?.let {
        model.points.getOrNull(it)
    }?.let { series.points[it.index] }
    val selectedDate = selectedPoint?.let { mediumDate(publishedDate(it.date), locale) }
    val selectedValue = selectedPoint?.let {
        pointValueText(it, series.unit, locale) ?: stringResource(R.string.sample_no_value)
    }
    val selectedState = if (selectedDate != null && selectedValue != null) {
        stringResource(R.string.history_selected_point, selectedDate, bidiWrap(selectedValue))
    } else {
        null
    }
    val zoomState = if (zoomed) {
        val (first, last) = windowDays(window)
        stringResource(
            R.string.history_zoomed_state,
            mediumDate(first, locale),
            mediumDate(last, locale),
        )
    } else {
        null
    }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnOpenSample by rememberUpdatedState(onOpenSample)
    val currentSelected by rememberUpdatedState(selected)
    val previousLabel = stringResource(R.string.history_previous_measurement)
    val nextLabel = stringResource(R.string.history_next_measurement)
    val openLabel = stringResource(R.string.history_open_report)
    // Screen readers cannot touch a point: they step through the measures,
    // and open the report of the selected one.
    val stepActions = remember(previousLabel, nextLabel, model) {
        listOf(previousLabel to -1, nextLabel to 1).map { (label, delta) ->
            CustomAccessibilityAction(label) {
                val step = stepSelection(currentSelected, delta, model.points.size)
                step?.let(currentOnSelect)
                step != null
            }
        }
    }
    val openAction = selectedPoint?.let { point ->
        CustomAccessibilityAction(openLabel) {
            currentOnOpenSample(point.sampleCode)
            true
        }
    }
    var focused by remember { mutableStateOf(false) }

    // A measure selected out of view (keys, screen reader) is brought into it.
    LaunchedEffect(selected, model) {
        val day = selected?.let { model.points.getOrNull(it) }?.day ?: return@LaunchedEffect
        window = revealDay(window, model.time, day)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            val width = constraints.maxWidth.toFloat()
            val height = with(density) { CHART_HEIGHT.toPx() }
            val geometry = remember(model, window, width, height, labelStyle, locale, density) {
                computeGeometry(model, window, width, height, measurer, labelStyle, locale, density)
            }
            // Read by the gesture detectors, which outlive a change of window.
            val currentGeometry by rememberUpdatedState(geometry)
            val currentZoomed by rememberUpdatedState(zoomed)

            fun visibleIndex(x: Float): Int? = currentGeometry.let {
                nearestVisibleIndex(it.pointXs, x, it.plotLeft, it.plotRight)
            }

            Column {
                SelectionStrip(
                    x = selected?.let { geometry.pointXs.getOrNull(it) },
                    uiDirection = uiDirection,
                ) {
                    if (selectedDate != null && selectedValue != null) {
                        Tooltip(selectedDate, selectedValue)
                    } else {
                        Text(
                            text = stringResource(R.string.history_touch_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CHART_HEIGHT)
                        .border(
                            width = 2.dp,
                            color = if (focused) {
                                colors.primary
                            } else {
                                colors.surface.copy(
                                    alpha = 0f,
                                )
                            },
                            shape = MaterialTheme.shapes.small,
                        )
                        .pointerInput(model) {
                            // Two fingers zoom and move the time axis. Read
                            // before the other detectors (initial pass), which
                            // then see the events consumed and stand down.
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, PointerEventPass.Initial)
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.changes.count { it.pressed } >= 2) {
                                        val g = currentGeometry
                                        val plotWidth = g.plotRight - g.plotLeft
                                        val focus = (event.calculateCentroid().x - g.plotLeft) /
                                            plotWidth
                                        var next = zoomWindow(
                                            window,
                                            model.time,
                                            event.calculateZoom().toDouble(),
                                            focus.toDouble(),
                                        )
                                        next = panWindow(
                                            next,
                                            model.time,
                                            dragDays(event.calculatePan().x, plotWidth, next),
                                        )
                                        window = next
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .pointerInput(model) {
                            detectTapGestures(
                                onTap = { offset -> visibleIndex(offset.x)?.let(currentOnSelect) },
                                onDoubleTap = { window = fullWindow(model.time) },
                                onLongPress = { offset ->
                                    visibleIndex(offset.x)?.let { index ->
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        currentOnSelect(index)
                                        val point = series.points[model.points[index].index]
                                        currentOnOpenSample(point.sampleCode)
                                    }
                                },
                            )
                        }
                        .pointerInput(model) {
                            // Zoomed, dragging moves along the time axis;
                            // otherwise it selects the measures it passes.
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    if (!currentZoomed) visibleIndex(offset.x)?.let(currentOnSelect)
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                if (currentZoomed) {
                                    val g = currentGeometry
                                    window = panWindow(
                                        window,
                                        model.time,
                                        dragDays(dragAmount, g.plotRight - g.plotLeft, window),
                                    )
                                } else {
                                    visibleIndex(change.position.x)?.let(currentOnSelect)
                                }
                            }
                        }
                        .onFocusChanged { focused = it.isFocused }
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            // Left is always earlier: the time axis does not mirror.
                            val delta = when (event.key) {
                                Key.DirectionLeft -> -1
                                Key.DirectionRight -> 1
                                Key.MoveHome -> -model.points.size
                                Key.MoveEnd -> model.points.size
                                else -> return@onKeyEvent false
                            }
                            stepSelection(currentSelected, delta, model.points.size)
                                ?.let(currentOnSelect)
                            true
                        }
                        .focusable()
                        .semantics {
                            contentDescription = description
                            val state = listOfNotNull(selectedState, zoomState)
                            if (state.isNotEmpty()) stateDescription = state.joinToString(". ")
                            customActions = stepActions + listOfNotNull(openAction)
                        },
                ) {
                    drawChart(geometry, model, selected, colors)
                }
                if (zoomed) {
                    // At the start of the interface's layout, like the legend.
                    CompositionLocalProvider(LocalLayoutDirection provides uiDirection) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { window = fullWindow(model.time) }) {
                                Text(stringResource(R.string.history_zoom_reset))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawChart(
    geometry: ChartGeometry,
    model: ChartModel,
    selected: Int?,
    colors: ColorScheme,
) {
    val radius = MARKER_RADIUS.toPx()
    val gap = LABEL_GAP.toPx()
    val tick = TICK_LENGTH.toPx()
    val thin = 1.dp.toPx()
    val left = geometry.plotLeft
    val right = geometry.plotRight

    // Value graduations: grid lines and labels before the plot.
    geometry.gridYs.forEach { y ->
        drawLine(colors.outlineVariant, Offset(left, y), Offset(right, y), thin)
    }
    geometry.yLabels.forEach { (y, text) ->
        drawText(text, topLeft = Offset(left - gap - text.size.width, y - text.size.height / 2))
    }

    // Time axis and its graduations; a label that would overlap the
    // previous one is skipped.
    drawLine(
        colors.outline,
        Offset(left, geometry.plotBottom),
        Offset(right, geometry.plotBottom),
        thin,
    )
    var lastEnd = Float.NEGATIVE_INFINITY
    geometry.xLabels.forEach { (x, text) ->
        drawLine(
            colors.outline,
            Offset(x, geometry.plotBottom),
            Offset(x, geometry.plotBottom + tick),
            thin,
        )
        val labelWidth = text.size.width.toFloat()
        val start = (x - labelWidth / 2).coerceIn(0f, max(0f, size.width - labelWidth))
        if (start >= lastEnd + gap) {
            drawText(text, topLeft = Offset(start, geometry.plotBottom + tick + gap / 2))
            lastEnd = start + labelWidth
        }
    }

    // Quality reference (dashed), then quality limit (solid).
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    geometry.referenceYs.forEach { y ->
        drawLine(
            colors.onSurfaceVariant,
            Offset(left, y),
            Offset(right, y),
            1.5.dp.toPx(),
            pathEffect = dash,
        )
    }
    geometry.limitYs.forEach { y ->
        drawLine(colors.onSurface, Offset(left, y), Offset(right, y), 2.dp.toPx())
    }

    // Zoomed in, measures out of the window are cut at the plot's edges; a
    // marker on an edge keeps its full width.
    clipRect(left = left - radius, top = 0f, right = right + radius, bottom = size.height) {
        // Selection guide, below the series.
        selected?.let { geometry.pointXs.getOrNull(it) }?.let { x ->
            drawLine(
                colors.outline,
                Offset(x, geometry.plotTop),
                Offset(x, geometry.plotBottom),
                thin,
            )
        }

        val series = colors.secondary
        val path = Path()
        geometry.pointXs.forEachIndexed { i, x ->
            if (i == 0) path.moveTo(x, geometry.pointYs[i]) else path.lineTo(x, geometry.pointYs[i])
        }
        drawPath(path, series, style = Stroke(width = 2.dp.toPx()))
        model.points.forEachIndexed { i, point ->
            val center = Offset(geometry.pointXs[i], geometry.pointYs[i])
            if (point.censored) {
                drawCircle(colors.surface, radius, center)
                drawCircle(series, radius, center, style = Stroke(width = 1.5.dp.toPx()))
            } else {
                drawCircle(series, radius, center)
            }
            if (i == selected) {
                drawCircle(series, radius * 2, center, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

/**
 * A strip above the plot holding [content]: the tooltip, centred on [x]
 * (a position in the plot, left to right) and kept inside the strip, or the
 * hint, at the start of the interface's layout when nothing is selected.
 */
@Composable
private fun SelectionStrip(
    x: Float?,
    uiDirection: LayoutDirection,
    content: @Composable () -> Unit,
) {
    Layout(
        content = {
            CompositionLocalProvider(LocalLayoutDirection provides uiDirection, content = content)
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(bottom = 4.dp),
    ) { measurables, constraints ->
        val placeable = measurables.firstOrNull()?.measure(
            constraints.copy(minWidth = 0, minHeight = 0),
        )
        val width = constraints.maxWidth
        val height = max(constraints.minHeight, placeable?.height ?: 0)
        layout(width, height) {
            placeable?.let {
                val room = max(0, width - it.width)
                val start = when {
                    x != null -> (x - it.width / 2f).roundToInt().coerceIn(0, room)
                    uiDirection == LayoutDirection.Rtl -> room
                    else -> 0
                }
                // Physical position: the strip is laid out left to right.
                it.place(start, height - it.height)
            }
        }
    }
}

/** The selected measure: its date, and its value as one left-to-right block. */
@Composable
private fun Tooltip(date: String, value: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(text = date, style = MaterialTheme.typography.labelMedium)
            Text(
                text = bidiWrap(value),
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
            )
        }
    }
}

/** A date of the history in the medium format of [locale]. */
fun mediumDate(date: LocalDate, locale: Locale): String =
    localizedDateFormatter(FormatStyle.MEDIUM, locale).format(date)

/** What a screen reader says of the whole chart: count, period, lowest and highest. */
@Composable
private fun chartDescription(series: ParameterSeries, model: ChartModel, locale: Locale): String {
    val first = model.points.first()
    val last = model.points.last()
    val lowest = series.points[model.points.minBy { it.value }.index]
    val highest = series.points[model.points.maxBy { it.value }.index]
    val noValue = stringResource(R.string.sample_no_value)
    return pluralStringResource(
        R.plurals.history_chart_description,
        model.points.size,
        model.points.size,
        mediumDate(LocalDate.ofEpochDay(first.day), locale),
        mediumDate(LocalDate.ofEpochDay(last.day), locale),
        bidiWrap(pointValueText(lowest, series.unit, locale) ?: noValue),
        bidiWrap(pointValueText(highest, series.unit, locale) ?: noValue),
    )
}
