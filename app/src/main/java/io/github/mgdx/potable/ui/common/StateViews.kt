package io.github.mgdx.potable.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.R

/**
 * Full-screen loading state: an indicator and a sentence saying what loads;
 * when [slow] ([slowLoading]), a second sentence says that Hub'Eau is slow to
 * answer and that the application is still waiting.
 */
@Composable
fun LoadingState(text: String, modifier: Modifier = Modifier, slow: Boolean = false) {
    CenteredColumn(modifier) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (slow) {
            Text(
                text = stringResource(R.string.loading_slow),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Full-screen empty state: nothing to show, and why. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    CenteredColumn(modifier) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(STATE_ICON_SIZE),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/** Full-screen error state, with a Retry button: there is no automatic retry. */
@Composable
fun ErrorState(error: LoadError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    CenteredColumn(modifier) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(STATE_ICON_SIZE),
        )
        Text(
            text = stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

/** Size of the icon of the empty and error states. */
private val STATE_ICON_SIZE = 48.dp

/** The sentence telling the user why a load failed. */
internal fun LoadError.messageRes(): Int = when (this) {
    LoadError.NETWORK -> R.string.error_network
    LoadError.TOO_SLOW -> R.string.error_too_slow
    LoadError.UNAVAILABLE -> R.string.error_unavailable
    LoadError.UNEXPECTED_RESPONSE -> R.string.error_unexpected_response
    LoadError.UNEXPECTED -> R.string.error_unexpected
}

/**
 * Centred in the available height, and scrollable when the text does not fit
 * (large font sizes, SPEC §9). Inside a parent that already scrolls
 * vertically (unbounded height), it neither centres nor scrolls itself.
 */
@Composable
internal fun CenteredColumn(modifier: Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val bounded = constraints.hasBoundedHeight
        val scroll = if (bounded) {
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
        } else {
            Modifier
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(scroll)
                .readableWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}
