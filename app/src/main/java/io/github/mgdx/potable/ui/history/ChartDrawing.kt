package io.github.mgdx.potable.ui.history

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Draws [geometry] of [model]: grid and graduations, the quality reference
 * and limit, then the series with the [selected] measure marked. Colours come
 * from the theme only (see [HistoryChart]).
 */
internal fun DrawScope.drawChart(
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
