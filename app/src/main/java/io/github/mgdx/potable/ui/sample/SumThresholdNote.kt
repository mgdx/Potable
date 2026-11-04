package io.github.mgdx.potable.ui.sample

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.Series

/**
 * What `<SEUIL` means, under a value published as such
 * ([Series.isBelowSumThreshold]); nothing otherwise. The value itself stays
 * as published.
 */
@Composable
internal fun SumThresholdNote(text: String?, modifier: Modifier = Modifier) {
    if (!Series.isBelowSumThreshold(text)) return
    Text(
        text = stringResource(R.string.result_below_sum_threshold),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
