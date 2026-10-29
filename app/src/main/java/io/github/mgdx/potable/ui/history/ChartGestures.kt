package io.github.mgdx.potable.ui.history

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import io.github.mgdx.potable.core.model.ParameterSeries

/** Saves a [ChartWindow] across configuration changes and process death. */
internal val WindowSaver = listSaver<ChartWindow, Double>(
    save = { listOf(it.start, it.end) },
    restore = { ChartWindow(it[0], it[1]) },
)

/**
 * The touch gestures of the chart of [model]: pinching zooms the time axis,
 * tapping selects the nearest visible measure, a double tap shows the whole
 * period again, touching and holding opens the report of a measure, dragging
 * moves along the time axis when zoomed and selects the measures it passes
 * otherwise. The detectors outlive a recomposition: they read [geometry],
 * [zoomed] and [window] when they need them, and every callback must read its
 * own current state.
 */
internal fun Modifier.chartGestures(
    series: ParameterSeries,
    model: ChartModel,
    geometry: () -> ChartGeometry,
    zoomed: () -> Boolean,
    window: () -> ChartWindow,
    onWindowChange: (ChartWindow) -> Unit,
    onSelect: (Int) -> Unit,
    onOpenSample: (String) -> Unit,
    haptics: HapticFeedback,
): Modifier {
    fun visibleIndex(x: Float): Int? = geometry().let {
        nearestVisibleIndex(it.pointXs, x, it.plotLeft, it.plotRight)
    }
    return this
        .pointerInput(model) {
            // Two fingers zoom and move the time axis. Read
            // before the other detectors (initial pass), which
            // then see the events consumed and stand down.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, PointerEventPass.Initial)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        val g = geometry()
                        val plotWidth = g.plotRight - g.plotLeft
                        val focus = (event.calculateCentroid().x - g.plotLeft) /
                            plotWidth
                        var next = zoomWindow(
                            window(),
                            model.time,
                            event.calculateZoom().toDouble(),
                            focus.toDouble(),
                        )
                        next = panWindow(
                            next,
                            model.time,
                            dragDays(event.calculatePan().x, plotWidth, next),
                        )
                        onWindowChange(next)
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
        .pointerInput(model) {
            detectTapGestures(
                onTap = { offset -> visibleIndex(offset.x)?.let(onSelect) },
                onDoubleTap = { onWindowChange(fullWindow(model.time)) },
                onLongPress = { offset ->
                    visibleIndex(offset.x)?.let { index ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelect(index)
                        val point = series.points[model.points[index].index]
                        onOpenSample(point.sampleCode)
                    }
                },
            )
        }
        .pointerInput(model) {
            // Zoomed, dragging moves along the time axis;
            // otherwise it selects the measures it passes.
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    if (!zoomed()) visibleIndex(offset.x)?.let(onSelect)
                },
            ) { change, dragAmount ->
                change.consume()
                if (zoomed()) {
                    val g = geometry()
                    onWindowChange(
                        panWindow(
                            window(),
                            model.time,
                            dragDays(dragAmount, g.plotRight - g.plotLeft, window()),
                        ),
                    )
                } else {
                    visibleIndex(change.position.x)?.let(onSelect)
                }
            }
        }
}

/**
 * The keys of the chart once it has the focus: the arrows step through the
 * measures, Home and End reach the first and the last. Left is always
 * earlier: the time axis does not mirror.
 */
internal fun Modifier.chartKeys(
    pointCount: Int,
    selected: () -> Int?,
    onSelect: (Int) -> Unit,
): Modifier = onKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
    val delta = when (event.key) {
        Key.DirectionLeft -> -1
        Key.DirectionRight -> 1
        Key.MoveHome -> -pointCount
        Key.MoveEnd -> pointCount
        else -> return@onKeyEvent false
    }
    stepSelection(selected(), delta, pointCount)?.let(onSelect)
    true
}
