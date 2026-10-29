package io.github.mgdx.potable.ui.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.PesticideCount
import io.github.mgdx.potable.core.UnfavourableDigest
import io.github.mgdx.potable.core.WatchedMeasure
import io.github.mgdx.potable.core.WatchedSubstance
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.ui.common.BackTopBar
import io.github.mgdx.potable.ui.common.EmptyState
import io.github.mgdx.potable.ui.common.ErrorState
import io.github.mgdx.potable.ui.common.LoadingState
import io.github.mgdx.potable.ui.common.SectionCard
import io.github.mgdx.potable.ui.common.bidiWrap
import io.github.mgdx.potable.ui.common.messageRes
import io.github.mgdx.potable.ui.history.pointValueText
import io.github.mgdx.potable.ui.sample.currentLocale
import io.github.mgdx.potable.ui.settings.OverflowMenu
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.FormatStyle

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
    onParameterHistory: (codes: Set<String>, label: String) -> Unit,
    onMyCommunes: (() -> Unit)?,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onSearch: (() -> Unit)?,
    onChangeUdi: (() -> Unit)?,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val freshness by viewModel.freshness.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isFollowed by viewModel.isFollowed.collectAsStateWithLifecycle()
    val isSlow by viewModel.isSlow.collectAsStateWithLifecycle()
    val recentUnfavourable by viewModel.recentUnfavourable.collectAsStateWithLifecycle()
    val watched by viewModel.watched.collectAsStateWithLifecycle()
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
                onSubtitleClick = onChangeUdi,
                onBack = onBack,
                actions = {
                    isFollowed?.let { FollowButton(it, viewModel::toggleFollow) }
                    if (onMyCommunes != null) {
                        IconButton(onClick = onMyCommunes) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = stringResource(R.string.follows_title),
                            )
                        }
                    } else {
                        OverflowMenu(onSettings = onSettings, onAbout = onAbout)
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
            val content = @Composable {
                when (val current = state) {
                    SheetUiState.Loading ->
                        LoadingState(stringResource(R.string.sheet_loading), slow = isSlow)
                    is SheetUiState.Error -> ErrorState(current.error, viewModel::retry)
                    SheetUiState.NoSample -> EmptyState(stringResource(R.string.sheet_no_sample))
                    is SheetUiState.Loaded -> SampleContent(
                        samples = current.samples,
                        age = current.age,
                        recentUnfavourable = recentUnfavourable,
                        watched = watched,
                        onRetryWatched = viewModel::retryWatched,
                        onShowAllResults = onShowAllResults,
                        onShowHistory = onShowHistory,
                        onParameterHistory = onParameterHistory,
                        showSourceNotice = !pinnedNotice,
                        modifier = Modifier,
                    )
                }
            }
            // The cache of any unit is refreshed on demand (SPEC §7).
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::pullToRefresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                content()
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
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * The latest sample, or the latest [samples] when several were taken the
 * same day (newest first): each conclusion is shown, with its own access to
 * its results; the rest of the sheet is about the most recent one.
 */
@Composable
private fun SampleContent(
    samples: List<Sample>,
    age: RelativeAge,
    recentUnfavourable: UnfavourableDigest?,
    watched: WatchedUiState?,
    onRetryWatched: () -> Unit,
    onShowAllResults: (Sample) -> Unit,
    onShowHistory: () -> Unit,
    onParameterHistory: (Set<String>, String) -> Unit,
    showSourceNotice: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val sample = samples.first()
        if (samples.size == 1) {
            ConclusionCard(sample, age)
        } else {
            SameDayConclusionsCard(samples, age, onShowAllResults)
            Text(
                text = stringResource(R.string.sheet_details_of_latest),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        KeyParametersCard(sample, onParameterHistory)
        watched?.let { WatchedSubstancesCard(it, onRetryWatched, onParameterHistory) }
        SampleInfoCard(sample, age)
        ComplianceSection(sample)
        // With several samples, each has its own button in the conclusions.
        if (samples.size == 1 && sample.results.isNotEmpty()) {
            FilledTonalButton(
                onClick = { onShowAllResults(sample) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.sheet_show_all_results))
            }
        }
        // Just above the history it opens.
        recentUnfavourable?.let { RecentUnfavourableCard(it) }
        FilledTonalButton(onClick = onShowHistory, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.timeline_title))
        }
        // Below the last 12 months and the history they open.
        HealthAgencyCard(sample.communeCode, sample.distributor)
        if (showSourceNotice) SourceNotice(pinned = false)
    }
}

/**
 * The latest known measure of each watched substance on the unit (SPEC §5
 * F3), however old within the history window: its translated name, its value
 * as published, its date and age, the quality limit as published when there
 * is one, and what the substance is. Value and limit are only shown side by
 * side, never compared (SPEC §14). Each one opens the history of that
 * substance. A failure here only shows in this card.
 */
@Composable
private fun WatchedSubstancesCard(
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

/**
 * The conclusions of the samples taken on the latest day, newest first, each
 * verbatim (SPEC §14) under its published time, or its number when the times
 * do not tell them apart, and each with access to its results.
 */
@Composable
private fun SameDayConclusionsCard(
    samples: List<Sample>,
    age: RelativeAge,
    onShowAllResults: (Sample) -> Unit,
) {
    val times = remember(samples) { distinctSampleTimes(samples.map { it.date }) }
    val locale = currentLocale()
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
                text = stringResource(R.string.sheet_conclusions_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            SampledOnLine(R.string.sheet_conclusions_sampled_on, samples.first().date, age)
            Text(
                text = pluralStringResource(
                    R.plurals.sheet_same_day_samples,
                    samples.size,
                    samples.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            samples.forEachIndexed { index, sample ->
                if (index > 0) HorizontalDivider()
                val time = times[index]
                Text(
                    text = if (time != null) {
                        stringResource(
                            R.string.sheet_sample_taken_at,
                            formatSampleTime(time, locale),
                        )
                    } else {
                        stringResource(R.string.sheet_sample_number, index + 1, samples.size)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                ConclusionText(sample.conclusion)
                if (sample.results.isNotEmpty()) {
                    // No start padding: the label lines up with the conclusion.
                    TextButton(
                        onClick = { onShowAllResults(sample) },
                        contentPadding = PaddingValues(
                            horizontal = 0.dp,
                            vertical = ButtonDefaults.TextButtonContentPadding
                                .calculateTopPadding(),
                        ),
                    ) {
                        Text(stringResource(R.string.sheet_show_all_results))
                    }
                }
            }
        }
    }
}

/**
 * A discreet box: how many samples of the last 12 months were published
 * non-compliant with the quality limits, with the quality references only,
 * or with a restriction in their conclusion only, and the most recent one's
 * date and conclusion as published (SPEC §14: no verdict, no reassuring message when
 * there is none, in which case the box is not shown). Opens the timeline.
 */
@Composable
private fun RecentUnfavourableCard(digest: UnfavourableDigest) {
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

/**
 * Permanent notice: where the data comes from and how often it changes.
 * [pinned] in the bottom bar, it pads for the navigation bar; otherwise it
 * closes the scrolling content, a card among the others.
 */
@Composable
private fun SourceNotice(pinned: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = if (pinned) RectangleShape else MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.sheet_source_notice),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (pinned) Modifier.navigationBarsPadding() else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
