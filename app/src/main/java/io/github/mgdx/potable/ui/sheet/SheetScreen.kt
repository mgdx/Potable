package io.github.mgdx.potable.ui.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.FeaturedParameter
import io.github.mgdx.potable.core.FeaturedParameters
import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.ui.common.BackTopBar
import io.github.mgdx.potable.ui.common.EmptyState
import io.github.mgdx.potable.ui.common.ErrorState
import io.github.mgdx.potable.ui.common.LoadingState
import io.github.mgdx.potable.ui.common.asApiText
import io.github.mgdx.potable.ui.common.bidiWrap
import io.github.mgdx.potable.ui.sample.ResultValue
import io.github.mgdx.potable.ui.sample.currentLocale
import java.time.Instant
import java.time.ZoneId

/** Language of the Hub'Eau labels and conclusions (SPEC §11.2). */
private const val FRENCH_LANGUAGE_TAG = "fr-FR"

/**
 * Above this font scale, the source notice of a loaded sheet leaves the bottom
 * bar for the end of the scrolling content: pinned, it took a quarter of the
 * screen at 200 % (docs/decisions.md).
 */
private const val PINNED_NOTICE_MAX_FONT_SCALE = 1.3f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetScreen(
    viewModel: SheetViewModel,
    communeName: String,
    udiName: String?,
    onBack: (() -> Unit)?,
    onShowAllResults: (Sample) -> Unit,
    onShowHistory: () -> Unit,
    onParameterHistory: (parameter: FeaturedParameter, label: String) -> Unit,
    onMyCommunes: () -> Unit,
    onSearch: (() -> Unit)?,
    onChangeUdi: (() -> Unit)?,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val freshness by viewModel.freshness.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isFollowed by viewModel.isFollowed.collectAsStateWithLifecycle()
    val isSlow by viewModel.isSlow.collectAsStateWithLifecycle()
    val largeFont = LocalDensity.current.fontScale > PINNED_NOTICE_MAX_FONT_SCALE
    // Loading, error and empty states have room to spare: the notice stays pinned.
    val pinnedNotice = !largeFont || state !is SheetUiState.Loaded
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BackTopBar(
                title = communeName,
                subtitle = udiName,
                titleIsData = true,
                subtitleIsData = true,
                onBack = onBack,
                actions = {
                    isFollowed?.let { FollowButton(it, viewModel::toggleFollow) }
                    IconButton(onClick = onMyCommunes) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.follows_title),
                        )
                    }
                    onSearch?.let { search ->
                        IconButton(onClick = search) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.action_search_commune),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = { if (pinnedNotice) SourceNotice(pinned = true) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            freshness?.let { FreshnessBanner(it) }
            onChangeUdi?.let { change ->
                TextButton(
                    onClick = change,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.sheet_change_udi))
                }
            }
            val content = @Composable {
                when (val current = state) {
                    SheetUiState.Loading ->
                        LoadingState(stringResource(R.string.sheet_loading), slow = isSlow)
                    is SheetUiState.Error -> ErrorState(current.error, viewModel::retry)
                    SheetUiState.NoSample -> EmptyState(stringResource(R.string.sheet_no_sample))
                    is SheetUiState.Loaded -> SampleContent(
                        sample = current.sample,
                        age = current.age,
                        onShowAllResults = onShowAllResults,
                        onShowHistory = onShowHistory,
                        onParameterHistory = onParameterHistory,
                        showSourceNotice = !pinnedNotice,
                        modifier = Modifier,
                    )
                }
            }
            val modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
            if (isFollowed == true) {
                // The cache of a followed unit is refreshed on demand (SPEC §7).
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = viewModel::pullToRefresh,
                    modifier = modifier,
                ) {
                    content()
                }
            } else {
                Box(modifier = modifier) { content() }
            }
        }
    }
}

/**
 * "Follow" / "Following": a labelled toggle, not an icon alone. A bookmark,
 * outlined then filled: a neutral sign of following, not a "like" nor a
 * rating (SPEC §14, docs/decisions.md).
 */
@Composable
private fun FollowButton(followed: Boolean, onToggle: () -> Unit) {
    val description = stringResource(
        if (followed) R.string.sheet_unfollow_description else R.string.sheet_follow_description,
    )
    TextButton(
        onClick = onToggle,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Icon(
            painter = painterResource(
                if (followed) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border,
            ),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(if (followed) R.string.sheet_following else R.string.sheet_follow))
    }
}

/**
 * Says how old the cached data are; stands out, with a text and an icon,
 * when they could not be refreshed (SPEC §7, §9).
 */
@Composable
private fun FreshnessBanner(freshness: Freshness) {
    val locale = currentLocale()
    val date = remember(freshness.fetchedAt, locale) {
        formatFetchedDate(freshness.fetchedAt, locale, ZoneId.systemDefault())
    }
    val failed = freshness.refreshFailed
    Surface(
        color = if (failed) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (failed) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (failed) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = stringResource(
                    if (failed) R.string.sheet_refresh_failed else R.string.sheet_data_from,
                    date,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SampleContent(
    sample: Sample,
    age: RelativeAge,
    onShowAllResults: (Sample) -> Unit,
    onShowHistory: () -> Unit,
    onParameterHistory: (FeaturedParameter, String) -> Unit,
    showSourceNotice: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ConclusionCard(sample.conclusion)
        KeyParametersCard(sample, onParameterHistory)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.sheet_sampled_on, formatDate(sample.date)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = relativeAgeText(age),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            sample.distributor?.let {
                Text(stringResource(R.string.sheet_distributor, bidiWrap(it)))
            }
            sample.owner?.let {
                Text(stringResource(R.string.sheet_owner, bidiWrap(it)))
            }
        }
        HorizontalDivider()
        ComplianceSection(sample)
        if (sample.results.isNotEmpty()) {
            OutlinedButton(
                onClick = { onShowAllResults(sample) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.sheet_show_all_results))
            }
        }
        OutlinedButton(onClick = onShowHistory, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.timeline_title))
        }
        if (showSourceNotice) SourceNotice(pinned = false)
    }
}

/**
 * The featured parameters of the sample (SPEC §4.3), each with its translated
 * name, its value as published and what it is useful for. Absent when the
 * sample has none of them. No value is compared with anything. Each one
 * opens the history of that parameter.
 */
@Composable
private fun KeyParametersCard(
    sample: Sample,
    onParameterHistory: (FeaturedParameter, String) -> Unit,
) {
    val present = remember(sample) { FeaturedParameters.present(sample.results) }
    if (present.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.sheet_key_parameters_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            for ((parameter, result) in present) {
                val name = stringResource(parameter.nameRes())
                val actionLabel = stringResource(R.string.sheet_parameter_history_action, name)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = actionLabel) {
                            onParameterHistory(parameter, name)
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
                            style = MaterialTheme.typography.bodySmall,
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

/** The ARS conclusion, verbatim: never translated, summarised or scored (SPEC §14). */
@Composable
private fun ConclusionCard(conclusion: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.sheet_conclusion_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
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
                    style = MaterialTheme.typography.titleMedium.asApiText(),
                )
                Text(
                    text = stringResource(R.string.sheet_official_wording),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ComplianceSection(sample: Sample) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.sheet_compliance_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.sheet_compliance_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ComplianceRow(R.string.sheet_limits_bacteriological, sample.bacteriologicalLimits)
        ComplianceRow(R.string.sheet_limits_physicochemical, sample.physicochemicalLimits)
        ComplianceRow(R.string.sheet_references_bacteriological, sample.bacteriologicalReferences)
        ComplianceRow(R.string.sheet_references_physicochemical, sample.physicochemicalReferences)
    }
}

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
    Column {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

/**
 * Permanent notice: where the data comes from and how often it changes.
 * [pinned] in the bottom bar, it pads for the navigation bar; otherwise it
 * closes the scrolling content, a card among the others.
 */
@Composable
private fun SourceNotice(pinned: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = if (pinned) RectangleShape else MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.sheet_source_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (pinned) Modifier.navigationBarsPadding() else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** The date as published by Hub'Eau, read in UTC ([publishedDate]). */
@Composable
private fun formatDate(date: Instant): String {
    val locale = currentLocale()
    return remember(date, locale) { formatSampleDate(date, locale) }
}

@Composable
private fun relativeAgeText(age: RelativeAge): String = when (age) {
    RelativeAge.Today -> stringResource(R.string.age_today)
    is RelativeAge.Days -> pluralStringResource(R.plurals.age_days, age.count, age.count)
    is RelativeAge.Weeks -> pluralStringResource(R.plurals.age_weeks, age.count, age.count)
    is RelativeAge.Months -> pluralStringResource(R.plurals.age_months, age.count, age.count)
    is RelativeAge.Years -> pluralStringResource(R.plurals.age_years, age.count, age.count)
}
