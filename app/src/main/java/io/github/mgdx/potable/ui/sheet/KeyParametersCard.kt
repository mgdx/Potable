package io.github.mgdx.potable.ui.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.FeaturedParameters
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.ui.common.SectionCard
import io.github.mgdx.potable.ui.common.bidiWrap
import io.github.mgdx.potable.ui.common.explanationRes
import io.github.mgdx.potable.ui.common.nameRes
import io.github.mgdx.potable.ui.sample.ResultValue
import io.github.mgdx.potable.ui.sample.SumThresholdNote

/**
 * The featured parameters of [samples] (SPEC §4.3), newest first: the samples
 * of one day, each parameter taken from the newest that measured it. Each has its translated
 * name, its value as published, the quality limit and reference as published
 * when there are some, and what it is useful for. Absent when the samples
 * have none of them. Value and thresholds are only shown side by side, never
 * compared (SPEC §14). Each one opens the history of that parameter.
 */
@Composable
internal fun KeyParametersCard(
    samples: List<Sample>,
    onParameterHistory: (Set<String>, String) -> Unit,
) {
    val present = remember(samples) { FeaturedParameters.presentIn(samples) }
    if (present.isEmpty()) return
    SectionCard(title = stringResource(R.string.sheet_key_parameters_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((parameter, result) in present) {
                val name = stringResource(parameter.nameRes())
                val actionLabel = stringResource(R.string.sheet_parameter_history_action, name)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = actionLabel) {
                            onParameterHistory(parameter.codes, name)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(text = name, style = MaterialTheme.typography.titleSmall)
                        // Not selectable: the whole row is the click target.
                        ResultValue(
                            result,
                            style = MaterialTheme.typography.titleMedium,
                            selectable = false,
                        )
                        SumThresholdNote(result.resultText)
                        result.qualityLimit?.takeIf { it.isNotBlank() }?.let {
                            ThresholdText(stringResource(R.string.quality_limit, bidiWrap(it)))
                        }
                        result.qualityReference?.takeIf { it.isNotBlank() }?.let {
                            ThresholdText(stringResource(R.string.quality_reference, bidiWrap(it)))
                        }
                        Text(
                            text = stringResource(parameter.explanationRes()),
                            style = MaterialTheme.typography.bodySmall.justified(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThresholdText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
