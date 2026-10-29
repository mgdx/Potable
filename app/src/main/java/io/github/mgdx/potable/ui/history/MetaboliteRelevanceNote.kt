package io.github.mgdx.potable.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.MetaboliteRelevance
import io.github.mgdx.potable.core.Relevance

/** The label of an ANSES classification, as under a result of the sample detail. */
internal fun Relevance.labelRes(): Int = when (this) {
    Relevance.RELEVANT -> R.string.metabolite_relevant
    Relevance.NOT_RELEVANT -> R.string.metabolite_not_relevant
}

/**
 * The ANSES classification of the pesticide metabolite published under
 * [parameterCode], and what it means; nothing when the metabolite is not
 * classified. A fact of the ANSES table, never set against the measures
 * (SPEC §14).
 */
@Composable
internal fun MetaboliteRelevanceNote(parameterCode: String, modifier: Modifier = Modifier) {
    val relevance = remember(parameterCode) { MetaboliteRelevance.of(parameterCode) } ?: return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(relevance.labelRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.metabolite_relevance_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
