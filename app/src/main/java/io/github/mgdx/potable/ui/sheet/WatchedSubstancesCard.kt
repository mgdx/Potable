package io.github.mgdx.potable.ui.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.PesticideCount
import io.github.mgdx.potable.core.WatchedMeasure
import io.github.mgdx.potable.core.WatchedSubstance
import io.github.mgdx.potable.ui.common.SectionCard
import io.github.mgdx.potable.ui.common.bidiWrap
import io.github.mgdx.potable.ui.common.messageRes
import io.github.mgdx.potable.ui.history.pointValueText
import io.github.mgdx.potable.ui.sample.currentLocale
import java.time.Clock
import java.time.LocalDate
import java.time.format.FormatStyle

/**
 * The latest known measure of each watched substance on the unit (SPEC §5
 * F3), however old within the history window: its translated name, its value
 * as published, its date and age, the quality limit as published when there
 * is one, and what the substance is. Value and limit are only shown side by
 * side, never compared (SPEC §14). Each one opens the history of that
 * substance. A failure here only shows in this card.
 */
@Composable
internal fun WatchedSubstancesCard(
    state: WatchedUiState,
    onRetry: () -> Unit,
    onParameterHistory: (Set<String>, String) -> Unit,
) {
    SectionCard(title = stringResource(R.string.sheet_watched_title)) {
        when (state) {
            WatchedUiState.Loading -> CardNote(stringResource(R.string.sheet_watched_loading))
            is WatchedUiState.Error -> {
                CardNote(stringResource(state.error.messageRes()))
                RetryButton(onRetry)
            }
            is WatchedUiState.Loaded -> {
                if (state.measures.isEmpty()) {
                    Text(
                        text = stringResource(R.string.sheet_watched_none, formatDay(state.since)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    CardNote(stringResource(R.string.sheet_watched_intro))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (measure in state.measures) {
                            val pesticides = state.pesticides
                                .takeIf { measure.substance == WatchedSubstance.TOTAL_PESTICIDES }
                            WatchedMeasureRow(measure, pesticides, onParameterHistory)
                        }
                    }
                    if (state.notAnalysed.isNotEmpty()) {
                        NotAnalysedNote(state.notAnalysed, state.since)
                    }
                }
                if (state.partialError != null) {
                    CardNote(stringResource(R.string.sheet_watched_partial_error))
                    RetryButton(onRetry)
                }
            }
        }
    }
}

@Composable
private fun WatchedMeasureRow(
    measure: WatchedMeasure,
    pesticides: PesticideCount?,
    onParameterHistory: (Set<String>, String) -> Unit,
) {
    val locale = currentLocale()
    val name = stringResource(measure.substance.nameRes())
    val actionLabel = stringResource(R.string.sheet_parameter_history_action, name)
    val value = remember(measure, locale) { pointValueText(measure.point, measure.unit, locale) }
    val date = remember(measure.point.date, locale) {
        localizedDateFormatter(FormatStyle.MEDIUM, locale).format(publishedDate(measure.point.date))
    }
    val age = remember(measure.point.date) { RelativeAge.of(measure.point.date, Clock.systemUTC()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = actionLabel) {
                onParameterHistory(measure.substance.codes, name)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = name, style = MaterialTheme.typography.titleSmall)
            // As ResultValue: one left-to-right block, value and unit as published.
            Text(
                text = bidiWrap(value ?: stringResource(R.string.sample_no_value)),
                style = MaterialTheme.typography.titleMedium.copy(
                    textDirection = TextDirection.Ltr,
                ),
                fontStyle = if (value == null) FontStyle.Italic else null,
            )
            measure.limitText?.let {
                Text(
                    text = stringResource(R.string.quality_limit, bidiWrap(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(
                    R.string.sheet_watched_measured_on,
                    date,
                    relativeAgeText(age),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            pesticides?.let { PesticideCountText(it) }
            Text(
                text = stringResource(measure.substance.explanationRes()),
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

/**
 * How many pesticides the sample of the latest total searched for and
 * quantified, and what quantified means: facts of the sample, no verdict.
 */
@Composable
private fun PesticideCountText(count: PesticideCount) {
    Text(
        text = pluralStringResource(
            R.plurals.sheet_pesticides_count,
            count.analysed,
            count.analysed,
            count.quantified,
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = stringResource(R.string.pesticides_quantified_explanation),
        style = MaterialTheme.typography.bodySmall.justified(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The substances read without any measure since [since]: said, rather than
 * left out, as no analysis is not an absence.
 */
@Composable
private fun NotAnalysedNote(substances: List<WatchedSubstance>, since: LocalDate) {
    val names = substances.map { stringResource(it.nameRes()) }.joinToString(", ")
    CardNote(
        pluralStringResource(
            R.plurals.sheet_watched_not_analysed,
            substances.size,
            formatDay(since),
            names,
        ),
    )
}

@Composable
private fun CardNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.justified(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** No start padding: the label lines up with the text above. */
@Composable
private fun RetryButton(onRetry: () -> Unit) {
    TextButton(
        onClick = onRetry,
        contentPadding = PaddingValues(
            horizontal = 0.dp,
            vertical = ButtonDefaults.TextButtonContentPadding.calculateTopPadding(),
        ),
    ) {
        Text(stringResource(R.string.action_retry))
    }
}

private fun WatchedSubstance.nameRes(): Int = when (this) {
    WatchedSubstance.PFAS -> R.string.watched_pfas
    WatchedSubstance.TOTAL_PESTICIDES -> R.string.watched_pesticides
    WatchedSubstance.TFA -> R.string.watched_tfa
    WatchedSubstance.VINYL_CHLORIDE -> R.string.watched_vinyl_chloride
    WatchedSubstance.LEAD -> R.string.watched_lead
    WatchedSubstance.ARSENIC -> R.string.watched_arsenic
    WatchedSubstance.PERCHLORATE -> R.string.watched_perchlorate
    WatchedSubstance.CHLORDECONE -> R.string.watched_chlordecone
}

private fun WatchedSubstance.explanationRes(): Int = when (this) {
    WatchedSubstance.PFAS -> R.string.watched_pfas_explanation
    WatchedSubstance.TOTAL_PESTICIDES -> R.string.watched_pesticides_explanation
    WatchedSubstance.TFA -> R.string.watched_tfa_explanation
    WatchedSubstance.VINYL_CHLORIDE -> R.string.watched_vinyl_chloride_explanation
    WatchedSubstance.LEAD -> R.string.watched_lead_explanation
    WatchedSubstance.ARSENIC -> R.string.watched_arsenic_explanation
    WatchedSubstance.PERCHLORATE -> R.string.watched_perchlorate_explanation
    WatchedSubstance.CHLORDECONE -> R.string.watched_chlordecone_explanation
}

/** A day of the history window in the long date format of the language. */
@Composable
private fun formatDay(day: LocalDate): String {
    val locale = currentLocale()
    return remember(day, locale) { localizedDateFormatter(FormatStyle.LONG, locale).format(day) }
}
