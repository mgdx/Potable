package io.github.mgdx.potable.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.ui.common.SectionCard
import io.github.mgdx.potable.ui.common.asApiText

/** The date, distributor, authority and upstream facility of [sample], as published. */
@Composable
internal fun SampleInfoCard(sample: Sample, age: RelativeAge) {
    SectionCard(title = stringResource(R.string.sheet_sample_card_title)) {
        LabelValueRow(stringResource(R.string.sheet_sampled_on_label)) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatDate(sample.date),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.End,
                )
                Text(
                    text = relativeAgeText(age),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
        sample.distributor?.let {
            LabelValueRow(stringResource(R.string.sheet_distributor_label)) {
                ApiValueText(it)
            }
        }
        sample.owner?.let {
            LabelValueRow(stringResource(R.string.sheet_owner_label)) {
                ApiValueText(it)
            }
        }
        sample.upstreamFacility?.takeIf { it.isNotBlank() }?.let {
            LabelValueRow(stringResource(R.string.sheet_upstream_facility_label)) {
                ApiValueText(it)
            }
        }
    }
}

/**
 * A label at the start and its value at the end of the same line; a long
 * value wraps under itself, aligned to the end. The value fills its half of
 * the line, so that a short one sits at the end too, not just after the label.
 */
@Composable
internal fun LabelValueRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.TopEnd,
        ) {
            value()
        }
    }
}

/** A name published by Hub'Eau, laid out in its own direction ([asApiText]). */
@Composable
private fun ApiValueText(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodyLarge.asApiText(),
        textAlign = TextAlign.End,
    )
}
