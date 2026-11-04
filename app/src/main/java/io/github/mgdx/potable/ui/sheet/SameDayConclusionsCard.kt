package io.github.mgdx.potable.ui.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.Samples
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.ui.common.asApiText
import io.github.mgdx.potable.ui.sample.currentLocale

/**
 * The conclusions of the samples taken on the latest day, newest first, each
 * verbatim (SPEC §14), with access to the results of each sample. A sample
 * whose conclusion is its own shows it under its published time, or its
 * number when the times do not tell them apart. Samples whose conclusions are
 * the same character for character ([Samples.byConclusion]) show it once,
 * then one line per sample, which opens its results.
 */
@Composable
internal fun SameDayConclusionsCard(
    samples: List<Sample>,
    age: RelativeAge,
    onShowAllResults: (Sample) -> Unit,
) {
    val groups = remember(samples) { Samples.byConclusion(samples) }
    val labels = sampleLabels(samples)
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
            groups.forEachIndexed { index, group ->
                if (index > 0) HorizontalDivider()
                if (group.size == 1) {
                    val sample = group.single()
                    SingleSample(sample, labels.getValue(sample.code), onShowAllResults)
                } else {
                    SharedConclusion(group, labels, onShowAllResults)
                }
            }
        }
    }
}

/** The subheading of each sample, by code: its published time, or its number among [samples]. */
@Composable
private fun sampleLabels(samples: List<Sample>): Map<String, String> {
    val times = remember(samples) { distinctSampleTimes(samples.map { it.date }) }
    val locale = currentLocale()
    return samples.withIndex().associate { (index, sample) ->
        val time = times[index]
        sample.code to if (time != null) {
            stringResource(R.string.sheet_sample_taken_at, formatSampleTime(time, locale))
        } else {
            stringResource(R.string.sheet_sample_number, index + 1, samples.size)
        }
    }
}

/** A sample whose conclusion no other sample of the day shares. */
@Composable
private fun SingleSample(sample: Sample, label: String, onShowAllResults: (Sample) -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.semantics { heading() },
    )
    ConclusionText(sample.conclusion)
    if (sample.results.isNotEmpty()) {
        // As under the conclusion of a single sample (ConclusionCard).
        ShowAllResultsButton { onShowAllResults(sample) }
    }
}

/** The conclusion shared by the samples of [group], once, then a line per sample. */
@Composable
private fun SharedConclusion(
    group: List<Sample>,
    labels: Map<String, String>,
    onShowAllResults: (Sample) -> Unit,
) {
    Text(
        text = pluralStringResource(R.plurals.sheet_same_conclusion, group.size, group.size),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.semantics { heading() },
    )
    ConclusionText(group.first().conclusion)
    Column {
        for (sample in group) {
            SampleLine(sample, labels.getValue(sample.code), onShowAllResults)
        }
    }
}

/**
 * One sample of a shared conclusion: its subheading, and its upstream
 * facility when published. The whole line opens its results, when it has some.
 */
@Composable
private fun SampleLine(sample: Sample, label: String, onShowAllResults: (Sample) -> Unit) {
    val opens = sample.results.isNotEmpty()
    val actionLabel = stringResource(R.string.sheet_show_all_results)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (opens) {
                    Modifier.clickable(role = Role.Button, onClickLabel = actionLabel) {
                        onShowAllResults(sample)
                    }
                } else {
                    Modifier
                },
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            sample.upstreamFacility?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall.asApiText(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (opens) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
