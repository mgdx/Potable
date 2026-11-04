package io.github.mgdx.potable.ui.substances

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.SubstanceMatch
import io.github.mgdx.potable.ui.common.BackTopBar
import io.github.mgdx.potable.ui.common.CenteredColumn
import io.github.mgdx.potable.ui.common.LoadError
import io.github.mgdx.potable.ui.common.LoadingState
import io.github.mgdx.potable.ui.common.PillSearchField
import io.github.mgdx.potable.ui.common.ReadableInsetBox
import io.github.mgdx.potable.ui.common.asApiText
import io.github.mgdx.potable.ui.common.bidiWrap
import io.github.mgdx.potable.ui.common.messageRes
import io.github.mgdx.potable.ui.common.readableWidth
import io.github.mgdx.potable.ui.history.pointValueText
import io.github.mgdx.potable.ui.sample.SumThresholdNote
import io.github.mgdx.potable.ui.sample.currentLocale
import io.github.mgdx.potable.ui.sheet.formatSampleDate
import io.github.mgdx.potable.ui.sheet.localizedDateFormatter
import java.time.LocalDate
import java.time.format.FormatStyle

/**
 * "Search for a substance": the parameters measured on a unit over the
 * history window whose label holds the words typed (SPEC §3). Each result
 * gives the label, the latest value and unit as published (a censored
 * "<0,05" stays as is) with its date, and how many samples measured it; no
 * comparison, no verdict (SPEC §14). Touching a result opens its history
 * ([onOpenHistory]). The field takes the focus, and the keyboard, the first
 * time the screen opens only: not back from a history, nor after a rotation.
 */
@Composable
fun SubstanceSearchScreen(
    viewModel: SubstanceSearchViewModel,
    communeName: String,
    onBack: () -> Unit,
    onOpenHistory: (SubstanceMatch) -> Unit,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isSlow by viewModel.isSlow.collectAsStateWithLifecycle()
    val field = remember { FocusRequester() }
    var focusGiven by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BackTopBar(
                title = stringResource(R.string.substance_search_title),
                subtitle = communeName.takeIf { it.isNotBlank() },
                subtitleIsData = true,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Box(modifier = Modifier.readableWidth()) {
                PillSearchField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    label = stringResource(R.string.substance_search_field),
                    onSearch = viewModel::submit,
                    focusRequester = field,
                )
                // Here, not above the Scaffold: its content is composed later.
                LaunchedEffect(Unit) {
                    if (!focusGiven) {
                        focusGiven = true
                        field.requestFocus()
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (val current = state) {
                    SubstanceSearchUiState.Idle -> Hint()
                    is SubstanceSearchUiState.Loading ->
                        LoadingState(
                            stringResource(R.string.substance_search_loading),
                            slow = isSlow,
                        )
                    is SubstanceSearchUiState.Error -> SearchError(current.error, viewModel::retry)
                    is SubstanceSearchUiState.Empty -> NoMatch(current.query, current.since)
                    is SubstanceSearchUiState.Loaded -> Matches(current, onOpenHistory)
                }
            }
        }
    }
}

/** What to type: whole words, as Hub'Eau matches them. */
@Composable
private fun Hint() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .readableWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.substance_search_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Nothing found, and that the substance may be published under another name. */
@Composable
private fun NoMatch(query: String, since: LocalDate) {
    CenteredColumn(Modifier) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(STATE_ICON_SIZE),
        )
        Text(
            text = stringResource(R.string.substance_search_empty, query, formatDay(since)),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.substance_search_other_name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Like `ErrorState`, with one more sentence offline: nothing of the search
 * is kept on the phone.
 */
@Composable
private fun SearchError(error: LoadError, onRetry: () -> Unit) {
    CenteredColumn(Modifier) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(STATE_ICON_SIZE),
        )
        Text(
            text = stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (error == LoadError.NETWORK) {
            Text(
                text = stringResource(R.string.substance_search_offline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        FilledTonalButton(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

@Composable
private fun Matches(
    loaded: SubstanceSearchUiState.Loaded,
    onOpenHistory: (SubstanceMatch) -> Unit,
) {
    val since = formatDay(loaded.since)
    ReadableInsetBox(modifier = Modifier.fillMaxSize()) { inset ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = inset),
        ) {
            item(key = "summary") {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val count = loaded.result.matches.size
                    Text(
                        text = pluralStringResource(
                            R.plurals.substance_search_summary,
                            count,
                            count,
                            loaded.query,
                            since,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (loaded.result.partial) {
                        PartialNote(loaded.result.rowsRead, loaded.result.totalRows)
                    }
                }
                HorizontalDivider()
            }
            items(loaded.result.matches, key = { it.parameterCodes.sorted().joinToString(",") }) {
                MatchRow(it, since, onClick = { onOpenHistory(it) })
                HorizontalDivider()
            }
        }
    }
}

/** The reading stopped at its bound: said with a text and an icon, not a colour alone. */
@Composable
private fun PartialNote(rowsRead: Int, totalRows: Int) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = pluralStringResource(
                    R.plurals.substance_search_partial,
                    rowsRead,
                    rowsRead,
                    totalRows,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** One parameter, read as a single element; touching it opens its history. */
@Composable
private fun MatchRow(match: SubstanceMatch, since: String, onClick: () -> Unit) {
    val locale = currentLocale()
    val value = remember(match, locale) { pointValueText(match.latest, match.unit, locale) }
    val date = remember(match.latest.date, locale) { formatSampleDate(match.latest.date, locale) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.sheet_parameter_history_action, match.label),
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // The label as published, in its own direction.
            Text(text = match.label, style = MaterialTheme.typography.titleSmall.asApiText())
            // As ResultValue: one left-to-right block, value and unit as published.
            Text(
                text = bidiWrap(value ?: stringResource(R.string.sample_no_value)),
                style = MaterialTheme.typography.titleMedium.copy(
                    textDirection = TextDirection.Ltr,
                ),
                fontStyle = if (value == null) FontStyle.Italic else null,
            )
            SumThresholdNote(match.latest.text)
            Text(
                text = stringResource(R.string.substance_search_latest, date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.substance_search_count,
                    match.measureCount,
                    match.measureCount,
                    since,
                ),
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

/** Size of the icon of the empty and error states, as in `StateViews`. */
private val STATE_ICON_SIZE = 48.dp

/** A calendar date (start of the window) in the long format of the locale. */
@Composable
private fun formatDay(day: LocalDate): String {
    val locale = currentLocale()
    return remember(day, locale) { localizedDateFormatter(FormatStyle.LONG, locale).format(day) }
}
