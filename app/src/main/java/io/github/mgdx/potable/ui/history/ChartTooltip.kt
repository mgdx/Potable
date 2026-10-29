package io.github.mgdx.potable.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.ui.common.bidiWrap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A strip above the plot holding [content]: the tooltip, centred on [x]
 * (a position in the plot, left to right) and kept inside the strip, or the
 * hint, at the start of the interface's layout when nothing is selected.
 */
@Composable
internal fun SelectionStrip(
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
internal fun Tooltip(date: String, value: String) {
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
