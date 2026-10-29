package io.github.mgdx.potable.ui.sheet

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.UnfavourableDigest
import io.github.mgdx.potable.ui.common.SectionCard
import io.github.mgdx.potable.ui.sample.currentLocale

/**
 * A discreet box: how many samples of the last 12 months were published
 * non-compliant with the quality limits, with the quality references only,
 * or with a restriction in their conclusion only, and the most recent one's
 * date and conclusion as published (SPEC §14: no verdict, no reassuring message when
 * there is none, in which case the box is not shown). Opens the timeline.
 */
@Composable
internal fun RecentUnfavourableCard(digest: UnfavourableDigest) {
    val locale = currentLocale()
    val date = remember(digest.latest.date, locale) {
        formatSampleDate(digest.latest.date, locale)
    }
    SectionCard(title = stringResource(R.string.sheet_recent_unfavourable_title)) {
        // What was published, limits and references told apart (SPEC §5 F3).
        listOf(
            R.plurals.sheet_recent_limits to digest.limitsCount,
            R.plurals.sheet_recent_references_only to digest.referencesOnlyCount,
            R.plurals.sheet_recent_conclusion_only to digest.conclusionOnlyCount,
        ).filter { it.second > 0 }.forEach { (res, count) ->
            Text(
                text = pluralStringResource(res, count, count),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (digest.limitsCount + digest.referencesOnlyCount > 0) {
            Text(
                text = stringResource(R.string.sheet_compliance_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.sheet_recent_unfavourable_latest, date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ConclusionText(digest.latest.conclusion, style = MaterialTheme.typography.bodyMedium)
    }
}
