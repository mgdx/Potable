package io.github.mgdx.potable.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.UnfavourableDigest
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.ui.common.BackTopBar
import io.github.mgdx.potable.ui.common.EmptyState
import io.github.mgdx.potable.ui.common.ErrorState
import io.github.mgdx.potable.ui.common.LoadingState
import io.github.mgdx.potable.ui.common.ReadableMaxWidth
import io.github.mgdx.potable.ui.common.TwoPaneMaxWidth
import io.github.mgdx.potable.ui.common.TwoPanes
import io.github.mgdx.potable.ui.common.isExpandedWidth
import io.github.mgdx.potable.ui.common.readableWidth
import io.github.mgdx.potable.ui.sample.currentLocale
import io.github.mgdx.potable.ui.settings.OverflowMenu
import java.time.ZoneId

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
    // Depending on the width of the window: one reading column, or two columns side by side.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val twoPane = isExpandedWidth(maxWidth)
        val contentMaxWidth = if (twoPane) TwoPaneMaxWidth else ReadableMaxWidth
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
                                    contentDescription = stringResource(
                                        R.string.action_search_commune,
                                    ),
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = { if (pinnedNotice) SourceNotice(pinned = true, contentMaxWidth) },
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                freshness?.let { FreshnessBanner(it, contentMaxWidth) }
                val content = @Composable {
                    when (val current = state) {
                        SheetUiState.Loading ->
                            LoadingState(stringResource(R.string.sheet_loading), slow = isSlow)
                        is SheetUiState.Error -> ErrorState(current.error, viewModel::retry)
                        SheetUiState.NoSample -> EmptyState(
                            stringResource(R.string.sheet_no_sample),
                        )
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
                            twoPane = twoPane,
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
private fun FreshnessBanner(freshness: Freshness, maxWidth: Dp) {
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
            .readableWidth(maxWidth)
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
    twoPane: Boolean,
    modifier: Modifier,
) {
    val sample = samples.first()
    val conclusion = @Composable {
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
    }
    val measures = @Composable {
        KeyParametersCard(sample, onParameterHistory)
        watched?.let { WatchedSubstancesCard(it, onRetryWatched, onParameterHistory) }
    }
    val sampleDetails = @Composable {
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
    }
    val unit = @Composable {
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
    }
    // One scroll state for both layouts, kept when the window is resized.
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .readableWidth(if (twoPane) TwoPaneMaxWidth else ReadableMaxWidth)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (twoPane) {
            // The sample's conclusion and details, then the measures and the unit.
            TwoPanes(
                start = {
                    conclusion()
                    sampleDetails()
                },
                end = {
                    measures()
                    unit()
                },
            )
        } else {
            conclusion()
            measures()
            sampleDetails()
            unit()
        }
        if (showSourceNotice) SourceNotice(pinned = false)
    }
}

/**
 * Permanent notice: where the data comes from and how often it changes.
 * [pinned] in the bottom bar, it pads for the navigation bar; otherwise it
 * closes the scrolling content, a card among the others.
 */
@Composable
private fun SourceNotice(pinned: Boolean, maxWidth: Dp = ReadableMaxWidth) {
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
                .readableWidth(maxWidth)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
