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
import io.github.mgdx.potable.ui.common.spokenAs
import io.github.mgdx.potable.ui.common.spokenQuantity
import io.github.mgdx.potable.ui.sample.currentLocale
import java.text.NumberFormat

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
        MetaboliteRelevanceExplanation()
    }
}

/** The ANSES limit of a relevant metabolite, and the indicative value of another, in µg/L. */
private const val RELEVANT_LIMIT = 0.1
private const val NOT_RELEVANT_VALUE = 0.9
private const val METABOLITE_UNIT = "µg/L"

/**
 * What the ANSES classification means, its values shown as "0,1 µg/L" and
 * said in words by screen readers ([spokenQuantity]).
 */
@Composable
internal fun MetaboliteRelevanceExplanation(modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val format = remember(locale) { NumberFormat.getNumberInstance(locale) }

    // A no-break space, never a line between the number and its unit.
    fun shown(value: Double) = "${format.format(value)} $METABOLITE_UNIT"
    val spoken = stringResource(
        R.string.metabolite_relevance_explanation,
        spokenQuantity(RELEVANT_LIMIT, METABOLITE_UNIT),
        spokenQuantity(NOT_RELEVANT_VALUE, METABOLITE_UNIT),
    )
    Text(
        text = stringResource(
            R.string.metabolite_relevance_explanation,
            shown(RELEVANT_LIMIT),
            shown(NOT_RELEVANT_VALUE),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.spokenAs(spoken),
    )
}
