package io.github.mgdx.potable.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.ui.sample.currentLocale

/**
 * The conclusions of the samples taken on the latest day, newest first, each
 * verbatim (SPEC §14) under its published time, or its number when the times
 * do not tell them apart, and each with access to its results.
 */
@Composable
internal fun SameDayConclusionsCard(
    samples: List<Sample>,
    age: RelativeAge,
    onShowAllResults: (Sample) -> Unit,
) {
    val times = remember(samples) { distinctSampleTimes(samples.map { it.date }) }
    val locale = currentLocale()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.sheet_conclusions_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            SampledOnLine(R.string.sheet_conclusions_sampled_on, samples.first().date, age)
            Text(
                text = pluralStringResource(
                    R.plurals.sheet_same_day_samples,
                    samples.size,
                    samples.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            samples.forEachIndexed { index, sample ->
                if (index > 0) HorizontalDivider()
                val time = times[index]
                Text(
                    text = if (time != null) {
                        stringResource(
                            R.string.sheet_sample_taken_at,
                            formatSampleTime(time, locale),
                        )
                    } else {
                        stringResource(R.string.sheet_sample_number, index + 1, samples.size)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                ConclusionText(sample.conclusion)
                if (sample.results.isNotEmpty()) {
                    // No start padding: the label lines up with the conclusion.
                    TextButton(
                        onClick = { onShowAllResults(sample) },
                        contentPadding = PaddingValues(
                            horizontal = 0.dp,
                            vertical = ButtonDefaults.TextButtonContentPadding
                                .calculateTopPadding(),
                        ),
                    ) {
                        Text(stringResource(R.string.sheet_show_all_results))
                    }
                }
            }
        }
    }
}
