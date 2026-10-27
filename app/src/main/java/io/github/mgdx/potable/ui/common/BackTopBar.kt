package io.github.mgdx.potable.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.R

/**
 * Top app bar with a back arrow (mirrored in RTL), an optional subtitle and
 * optional [actions]. Without [onBack] (the first screen), there is no arrow.
 * [titleIsData] and [subtitleIsData] mark a commune, unit or parameter name
 * from the API, laid out in its own direction ([asApiText]). The title is a
 * heading for screen readers; the bar grows with the font size
 * ([topBarHeight]) instead of cutting the title and subtitle. With
 * [onSubtitleClick], the subtitle is a button (changing the distribution
 * unit) marked by a drop-down arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    titleIsData: Boolean = false,
    subtitleIsData: Boolean = false,
    onSubtitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        expandedHeight = topBarHeight(hasSubtitle = subtitle != null),
        title = {
            Column {
                Text(
                    text = title,
                    style = LocalTextStyle.current.let { if (titleIsData) it.asApiText() else it },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                subtitle?.let {
                    val style = MaterialTheme.typography.bodyMedium
                    val clickLabel = stringResource(R.string.sheet_change_udi)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = if (onSubtitleClick != null) {
                            Modifier.clickable(
                                role = Role.Button,
                                onClickLabel = clickLabel,
                                onClick = onSubtitleClick,
                            )
                        } else {
                            Modifier
                        },
                    ) {
                        Text(
                            text = it,
                            style = if (subtitleIsData) style.asApiText() else style,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (onSubtitleClick != null) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }
        },
        actions = actions,
    )
}

/** Vertical room left around the title lines of a top bar. */
private val TOP_BAR_VERTICAL_PADDING = 16.dp

/**
 * Height of a top app bar holding a one-line title, and a one-line subtitle
 * if [hasSubtitle]: Material's 64 dp, or more when the user's font size
 * (up to 200 %, non-linear from Android 14) makes the lines taller.
 */
@Composable
fun topBarHeight(hasSubtitle: Boolean): Dp {
    val typography = MaterialTheme.typography
    val lines = with(LocalDensity.current) {
        typography.titleLarge.lineHeight.toDp() +
            if (hasSubtitle) typography.bodyMedium.lineHeight.toDp() else 0.dp
    }
    return maxOf(TopAppBarDefaults.TopAppBarExpandedHeight, lines + TOP_BAR_VERTICAL_PADDING)
}
