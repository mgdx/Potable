package io.github.mgdx.potable.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.potable.R
import io.github.mgdx.potable.ui.common.BackTopBar
import io.github.mgdx.potable.ui.common.EmptyState
import io.github.mgdx.potable.ui.common.ErrorState
import io.github.mgdx.potable.ui.common.LoadError
import io.github.mgdx.potable.ui.common.LoadingState
import io.github.mgdx.potable.ui.common.PeriodSelector
import io.github.mgdx.potable.ui.common.asApiText
import io.github.mgdx.potable.ui.common.messageRes
import io.github.mgdx.potable.ui.sample.currentLocale
import io.github.mgdx.potable.ui.sheet.formatSampleDate
import io.github.mgdx.potable.ui.sheet.localizedDateFormatter
import java.time.LocalDate
import java.time.format.FormatStyle

/** Language of the Hub'Eau conclusions (SPEC §11.2). */
private const val FRENCH_LANGUAGE_TAG = "fr-FR"

/**
 * "Sample history": every sample of the window with its conclusion as
 * published (SPEC §5 F5). A non-compliance is shown with an icon and the text
 * of the compliance concerned, never by colour alone (SPEC §9); no verdict
 * of the application's own is added (SPEC §14). Touching a sample opens its
 * full report ([onOpenSample], a `code_prelevement`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    communeName: String,
    onBack: () -> Unit,
    onOpenSample: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isSlow by viewModel.isSlow.collectAsStateWithLifecycle()
    val years by viewModel.years.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val refreshFailed by viewModel.refreshFailed.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BackTopBar(
                title = stringResource(R.string.timeline_title),
                subtitle = communeName,
                subtitleIsData = true,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            years?.let { PeriodSelector(it, viewModel::selectYears) }
            refreshFailed?.let { RefreshFailedBanner(it) }
            val content = @Composable {
                when (val current = state) {
                    TimelineUiState.Loading ->
                        LoadingState(stringResource(R.string.timeline_loading), slow = isSlow)
                    is TimelineUiState.Error -> ErrorState(current.error, viewModel::retry)
                    is TimelineUiState.Empty ->
                        EmptyState(
                            stringResource(R.string.timeline_empty, formatDay(current.since)),
                        )
                    is TimelineUiState.Loaded -> TimelineList(current, onOpenSample)
                }
            }
            // The cache of any unit is refreshed on demand (SPEC §7).
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                content()
            }
        }
    }
}

/** The data shown are kept; the failure is said with a text and an icon. */
@Composable
private fun RefreshFailedBanner(error: LoadError) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(
                    R.string.timeline_refresh_failed,
                    stringResource(error.messageRes()),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TimelineList(loaded: TimelineUiState.Loaded, onOpenSample: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "summary") {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val count = loaded.entries.size
                Text(
                    text = pluralStringResource(
                        R.plurals.timeline_summary,
                        count,
                        count,
                        formatDay(loaded.since),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.sheet_official_wording),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
        itemsIndexed(loaded.entries, key = { _, entry -> entry.sample.code }) { index, entry ->
            TimelineRow(
                entry,
                isLast = index == loaded.entries.lastIndex,
                onClick = { onOpenSample(entry.sample.code) },
            )
        }
    }
}

/**
 * One sample: a marker on the line of the timeline, its date, its
 * conclusion verbatim and its non-compliances. Read as a single element;
 * touching it calls [onClick].
 */
@Composable
private fun TimelineRow(entry: TimelineEntry, isLast: Boolean, onClick: () -> Unit) {
    val locale = currentLocale()
    val date = remember(entry.sample.date, locale) { formatSampleDate(entry.sample.date, locale) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.timeline_open_report),
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TimelineMarker(isLast)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = date, style = MaterialTheme.typography.titleSmall)
            val conclusion = entry.sample.conclusion
            if (conclusion.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.sheet_no_conclusion),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                )
            } else {
                // Tagged as French so that screen readers pronounce it as such.
                Text(
                    text = AnnotatedString(
                        conclusion,
                        SpanStyle(localeList = LocaleList(FRENCH_LANGUAGE_TAG)),
                    ),
                    style = MaterialTheme.typography.bodyMedium.asApiText(),
                )
            }
            entry.nonCompliances.forEach { kind -> NonComplianceLine(kind) }
        }
    }
}

/** A neutral dot on a vertical line: the same for every sample, whatever its conclusion. */
@Composable
private fun TimelineMarker(isLast: Boolean) {
    val lineColor = MaterialTheme.colorScheme.secondary
    Column(
        modifier = Modifier
            .width(12.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(16.dp)
                .background(lineColor),
        )
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(lineColor, CircleShape),
        )
        if (!isLast) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(lineColor),
            )
        }
    }
}

@Composable
private fun NonComplianceLine(kind: ComplianceKind) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(
                R.string.timeline_non_compliance,
                stringResource(kind.labelRes()),
                stringResource(R.string.compliance_not_compliant),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun ComplianceKind.labelRes(): Int = when (this) {
    ComplianceKind.BACTERIOLOGICAL_LIMITS -> R.string.sheet_limits_bacteriological
    ComplianceKind.PHYSICOCHEMICAL_LIMITS -> R.string.sheet_limits_physicochemical
    ComplianceKind.BACTERIOLOGICAL_REFERENCES -> R.string.sheet_references_bacteriological
    ComplianceKind.PHYSICOCHEMICAL_REFERENCES -> R.string.sheet_references_physicochemical
}

/** A calendar date (start of the window) in the long format of the locale. */
@Composable
private fun formatDay(day: LocalDate): String {
    val locale = currentLocale()
    return remember(day, locale) {
        localizedDateFormatter(FormatStyle.LONG, locale).format(day)
    }
}
