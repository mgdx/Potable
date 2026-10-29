package io.github.mgdx.potable.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Large screens (tablets, foldables, landscape): the content of a screen is a
 * reading column of bounded width, centred; top bars and backgrounds keep the
 * full width. Below these widths, a phone in portrait renders as before.
 */

/**
 * Widest a reading column gets: about 70 to 80 characters of body text per
 * line, the comfortable measure; wider, cards stretch and lines get hard to
 * follow. A phone in portrait (360 to 480 dp) is narrower: nothing changes.
 */
val ReadableMaxWidth: Dp = 640.dp

/**
 * From this available width on, a screen may show two columns side by side:
 * the lower bound of the Material 3 "expanded" window width class (tablets in
 * landscape, unfolded foldables, desktops). Compared with the width available
 * to the screen, not the device's, so that split screen and free-form windows
 * get the layout that fits them.
 */
val ExpandedMinWidth: Dp = 840.dp

/** Space between the two columns of a two-column layout. */
val PaneSpacing: Dp = 24.dp

/** Widest a two-column layout gets: two reading columns and their spacing. */
val TwoPaneMaxWidth: Dp = ReadableMaxWidth * 2 + PaneSpacing

/** Whether [availableWidth] is wide enough for two columns ([ExpandedMinWidth]). */
fun isExpandedWidth(availableWidth: Dp): Boolean = availableWidth >= ExpandedMinWidth

/**
 * Fills the available width up to [maxWidth] ([ReadableMaxWidth] by default),
 * centred in what is left. To be placed after a scroll modifier, so that the
 * whole width still scrolls, and before the content padding.
 */
fun Modifier.readableWidth(maxWidth: Dp = ReadableMaxWidth): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = maxWidth)
    .fillMaxWidth()

/**
 * The horizontal inset that centres a column of at most [maxWidth] in
 * [availableWidth]; zero when the column fills it (a phone in portrait).
 */
fun readableInset(availableWidth: Dp, maxWidth: Dp = ReadableMaxWidth): Dp =
    ((availableWidth - maxWidth) / 2).coerceAtLeast(0.dp)

/**
 * For lazy lists, which take the inset as content padding rather than a
 * bounded width: the margins then still scroll the list and pull to refresh.
 * Gives [content] the [readableInset] of the width available to [modifier].
 */
@Composable
fun ReadableInsetBox(modifier: Modifier = Modifier, content: @Composable (inset: Dp) -> Unit) {
    BoxWithConstraints(modifier = modifier) {
        content(readableInset(maxWidth))
    }
}

/**
 * Two columns side by side, each as wide as the other, for a window at least
 * [ExpandedMinWidth] wide; the parent scrolls them as one. Each column is a
 * traversal group, so that screen readers and keyboards go through the whole
 * [start] column, then the [end] one, rather than across them line by line.
 */
@Composable
fun TwoPanes(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    start: @Composable ColumnScope.() -> Unit,
    end: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(PaneSpacing)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics { isTraversalGroup = true },
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = start,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics { isTraversalGroup = true },
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = end,
        )
    }
}
