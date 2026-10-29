package io.github.mgdx.potable.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.ui.common.asApiText
import io.github.mgdx.potable.ui.sample.currentLocale
import java.time.Instant

/*
 * The sections of one sample, shared by the sheet (SPEC §5 F3) and the full
 * report of a sample opened from a parameter's history (SPEC §5 F4).
 */

/** Language of the Hub'Eau labels and conclusions (SPEC §11.2). */
private const val FRENCH_LANGUAGE_TAG = "fr-FR"

/**
 * Above this font scale (the system's 115 % step), running text is no longer
 * justified but aligned on its start: a column of a few words per line leaves
 * gaps between them that hyphenation cannot fill; at 130 % on a 1080 px phone
 * the conclusions kept gaps of about three spaces (docs/decisions.md).
 */
private const val JUSTIFIED_MAX_FONT_SCALE = 1.15f

/**
 * Running text justified, words hyphenated in the language of the text.
 * [Hyphens.Auto] needs [LineBreak.Paragraph]: the default, [LineBreak.Simple],
 * is Android's simple break strategy, which adds no automatic hyphen. Above
 * [JUSTIFIED_MAX_FONT_SCALE], the text is aligned on its start instead.
 */
@Composable
internal fun TextStyle.justified(): TextStyle {
    val justify = LocalDensity.current.fontScale <= JUSTIFIED_MAX_FONT_SCALE
    return copy(
        textAlign = if (justify) TextAlign.Justify else TextAlign.Start,
        lineBreak = LineBreak.Paragraph,
        hyphens = Hyphens.Auto,
    )
}

/**
 * When the sample(s) of a conclusion card were taken, with their age (SPEC §5
 * F3), right under its heading, so that the first screen dates the conclusion.
 */
@Composable
internal fun SampledOnLine(textRes: Int, date: Instant, age: RelativeAge) {
    Text(
        text = stringResource(textRes, formatDate(date), relativeAgeText(age)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The ARS conclusion, verbatim: never translated, summarised or scored (SPEC
 * §14), under [titleRes] (the sheet's latest sample by default).
 */
@Composable
internal fun ConclusionCard(
    sample: Sample,
    age: RelativeAge,
    titleRes: Int = R.string.sheet_conclusion_title,
) {
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
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            SampledOnLine(R.string.sheet_conclusion_sampled_on, sample.date, age)
            ConclusionText(sample.conclusion)
        }
    }
}

/** One published conclusion, verbatim, or the sentence saying there is none. */
@Composable
internal fun ConclusionText(
    conclusion: String?,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    if (conclusion.isNullOrBlank()) {
        Text(
            text = stringResource(R.string.sheet_no_conclusion),
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
        )
    } else {
        // Tagged as French so that screen readers pronounce it as such.
        Text(
            text = AnnotatedString(
                conclusion,
                SpanStyle(localeList = LocaleList(FRENCH_LANGUAGE_TAG)),
            ),
            style = style.asApiText().justified(),
        )
        Text(
            text = stringResource(R.string.sheet_official_wording),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The date as published by Hub'Eau, read in UTC ([publishedDate]). */
@Composable
internal fun formatDate(date: Instant): String {
    val locale = currentLocale()
    return remember(date, locale) { formatSampleDate(date, locale) }
}

@Composable
internal fun relativeAgeText(age: RelativeAge): String = when (age) {
    RelativeAge.Today -> stringResource(R.string.age_today)
    is RelativeAge.Days -> pluralStringResource(R.plurals.age_days, age.count, age.count)
    is RelativeAge.Weeks -> pluralStringResource(R.plurals.age_weeks, age.count, age.count)
    is RelativeAge.Months -> pluralStringResource(R.plurals.age_months, age.count, age.count)
    is RelativeAge.Years -> pluralStringResource(R.plurals.age_years, age.count, age.count)
}
