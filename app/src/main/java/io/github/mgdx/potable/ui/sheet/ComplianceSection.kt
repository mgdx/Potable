package io.github.mgdx.potable.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.Derogation
import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.ui.common.SectionCard

/**
 * The four compliance codes of [sample]. When one is "D" or the conclusion
 * mentions a derogation ([Derogation]), a short factual note says what a
 * derogation is, under the codes: no colour, no icon, no verdict (SPEC §14).
 * When one is "S", another says what "not assessed" means; both may show.
 */
@Composable
internal fun ComplianceSection(sample: Sample) {
    SectionCard(title = stringResource(R.string.sheet_compliance_title)) {
        Text(
            text = stringResource(R.string.sheet_compliance_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ComplianceRow(R.string.sheet_limits_bacteriological, sample.bacteriologicalLimits)
        ComplianceRow(R.string.sheet_limits_physicochemical, sample.physicochemicalLimits)
        ComplianceRow(R.string.sheet_references_bacteriological, sample.bacteriologicalReferences)
        ComplianceRow(R.string.sheet_references_physicochemical, sample.physicochemicalReferences)
        val derogation = remember(sample) { Derogation.concerns(sample) }
        if (derogation) {
            Text(
                text = stringResource(R.string.sheet_derogation_explanation),
                style = MaterialTheme.typography.bodySmall.justified(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (sample.hasNotAssessedCompliance()) {
            Text(
                text = stringResource(R.string.sheet_not_assessed_explanation),
                style = MaterialTheme.typography.bodySmall.justified(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Whether one of the four compliance codes of this sample is "S"
 * ([Compliance.NOT_ASSESSED]): no parameter of that type was measured.
 */
internal fun Sample.hasNotAssessedCompliance(): Boolean = listOf(
    bacteriologicalLimits,
    physicochemicalLimits,
    bacteriologicalReferences,
    physicochemicalReferences,
).any { it == Compliance.NOT_ASSESSED }

/**
 * One compliance code, restituted as such. A non-compliance carries text and
 * a warning icon, never colour alone (SPEC §9).
 */
@Composable
private fun ComplianceRow(labelRes: Int, compliance: Compliance) {
    val nonCompliant = compliance == Compliance.NON_COMPLIANT
    val valueColor = if (nonCompliant) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    LabelValueRow(stringResource(labelRes)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (nonCompliant) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = valueColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = stringResource(compliance.labelRes()),
                style = MaterialTheme.typography.bodyLarge,
                color = valueColor,
                textAlign = TextAlign.End,
            )
        }
    }
}

private fun Compliance.labelRes(): Int = when (this) {
    Compliance.COMPLIANT -> R.string.compliance_compliant
    Compliance.COMPLIANT_UNDER_DEROGATION -> R.string.compliance_compliant_under_derogation
    Compliance.NON_COMPLIANT -> R.string.compliance_not_compliant
    Compliance.NOT_ASSESSED -> R.string.compliance_not_assessed
    Compliance.UNKNOWN -> R.string.compliance_unknown
}
