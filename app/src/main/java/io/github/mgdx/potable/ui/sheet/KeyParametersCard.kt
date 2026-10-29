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
import io.github.mgdx.potable.core.FeaturedParameter
import io.github.mgdx.potable.core.FeaturedParameters
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.ui.common.SectionCard
import io.github.mgdx.potable.ui.sample.ResultValue

/**
 * The featured parameters of the sample (SPEC §4.3), each with its translated
 * name, its value as published and what it is useful for. Absent when the
 * sample has none of them. No value is compared with anything. Each one
 * opens the history of that parameter.
 */
@Composable
internal fun KeyParametersCard(sample: Sample, onParameterHistory: (Set<String>, String) -> Unit) {
    val present = remember(sample) { FeaturedParameters.present(sample.results) }
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

private fun FeaturedParameter.nameRes(): Int = when (this) {
    FeaturedParameter.NITRATES -> R.string.featured_nitrates
    FeaturedParameter.HARDNESS -> R.string.featured_hardness
    FeaturedParameter.PH -> R.string.featured_ph
    FeaturedParameter.FREE_CHLORINE -> R.string.featured_free_chlorine
}

private fun FeaturedParameter.explanationRes(): Int = when (this) {
    FeaturedParameter.NITRATES -> R.string.featured_nitrates_explanation
    FeaturedParameter.HARDNESS -> R.string.featured_hardness_explanation
    FeaturedParameter.PH -> R.string.featured_ph_explanation
    FeaturedParameter.FREE_CHLORINE -> R.string.featured_free_chlorine_explanation
}
