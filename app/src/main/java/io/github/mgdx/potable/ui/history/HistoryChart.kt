package io.github.mgdx.potable.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.ui.common.bidiWrap
import io.github.mgdx.potable.ui.sample.currentLocale
import io.github.mgdx.potable.ui.sheet.localizedDateFormatter
import io.github.mgdx.potable.ui.sheet.publishedDate
import java.time.LocalDate
import java.time.format.FormatStyle
import java.util.Locale

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
                        .chartGestures(
                            series = series,
                            model = model,
                            geometry = { currentGeometry },
                            zoomed = { currentZoomed },
                            window = { window },
                            onWindowChange = { window = it },
                            onSelect = { currentOnSelect(it) },
                            onOpenSample = { currentOnOpenSample(it) },
                            haptics = haptics,
                        )
                        .onFocusChanged { focused = it.isFocused }
                        .chartKeys(
                            pointCount = model.points.size,
                            selected = { currentSelected },
                            onSelect = { currentOnSelect(it) },
                        )
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
