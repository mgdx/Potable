package io.github.mgdx.potable.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.model.ParameterSeries
import io.github.mgdx.potable.core.model.SeriesPoint
import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.ui.common.BackTopBar
import io.github.mgdx.potable.ui.common.EmptyState
import io.github.mgdx.potable.ui.common.ErrorState
import io.github.mgdx.potable.ui.common.LoadingState
import io.github.mgdx.potable.ui.common.bidiWrap
import io.github.mgdx.potable.ui.sample.currentLocale
import io.github.mgdx.potable.ui.sheet.publishedDate
import java.util.Locale

/**
 * The history of one parameter (SPEC §5 F5): [label] is the API label, shown
 * as published until the series brings its own. Values are restituted as
 * published and never compared with the limit (SPEC §14). Touching and
 * holding a measure of the chart opens the report of its sample
 * ([onOpenSample], a `code_prelevement`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParameterHistoryScreen(
    viewModel: ParameterHistoryViewModel,
    label: String,
    onBack: () -> Unit,
    onOpenSample: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val years by viewModel.years.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val refreshError by viewModel.refreshError.collectAsStateWithLifecycle()
    val isSlow by viewModel.isSlow.collectAsStateWithLifecycle()
    val series = (state as? HistoryUiState.Loaded)?.series
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BackTopBar(
                title = series?.label ?: label,
                titleIsData = true,
                subtitle = displayUnit(series?.unit)?.let { bidiWrap(it) },
                onBack = onBack,
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.history_refresh),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                WindowChips(years, viewModel::selectYears)
                Text(
                    text = stringResource(R.string.sample_official_labels),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                refreshError?.let { RefreshFailed() }
                when (val current = state) {
                    HistoryUiState.Loading ->
                        LoadingState(stringResource(R.string.history_loading), slow = isSlow)
                    is HistoryUiState.Error -> ErrorState(current.error, viewModel::retry)
                    HistoryUiState.Empty -> EmptyState(stringResource(R.string.history_empty))
                    is HistoryUiState.Loaded -> HistoryContent(current.series, onOpenSample)
                }
            }
        }
    }
}

/** The window, 1 to 10 years: one choice among [AppPreferences.YEAR_CHOICES]. */
@Composable
private fun WindowChips(years: Int?, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (choice in AppPreferences.YEAR_CHOICES) {
            FilterChip(
                selected = years == choice,
                onClick = { onSelect(choice) },
                label = { Text(pluralStringResource(R.plurals.settings_years, choice, choice)) },
            )
        }
    }
}

@Composable
private fun RefreshFailed() {
    Text(
        text = stringResource(R.string.history_refresh_failed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun HistoryContent(series: ParameterSeries, onOpenSample: (String) -> Unit) {
    val model = remember(series) { ChartModel.of(series) }
    var selected by rememberSaveable(series) { mutableStateOf<Int?>(null) }
    var showList by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            series.points.size == 1 -> {
                Paragraph(stringResource(R.string.history_single))
                MeasureRow(series.points.single(), series.unit)
                PublishedThresholds(series)
            }
            model == null -> {
                Paragraph(stringResource(R.string.history_not_numeric))
                PublishedThresholds(series)
                MeasureList(series)
            }
            else -> {
                HistoryChart(
                    series = series,
                    model = model,
                    selected = selected,
                    onSelect = { selected = it },
                    onOpenSample = onOpenSample,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Legend(series, model)
                Text(
                    text = stringResource(R.string.history_gesture_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                TextButton(
                    onClick = { showList = !showList },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    val hide = R.string.history_hide_list
                    Text(stringResource(if (showList) hide else R.string.history_show_list))
                }
                if (showList) MeasureList(series)
            }
        }
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/** The limit and reference texts as published, when nothing is drawn. */
@Composable
private fun PublishedThresholds(series: ParameterSeries) {
    series.limitText?.takeIf { it.isNotBlank() }?.let {
        LegendText(stringResource(R.string.quality_limit, bidiWrap(it)))
    }
    series.referenceText?.takeIf { it.isNotBlank() }?.let {
        LegendText(stringResource(R.string.quality_reference, bidiWrap(it)))
    }
}

@Composable
private fun LegendText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/** What the marks of the chart stand for, in the layout of the interface. */
private enum class LegendMark { MEASURE, CENSORED, LIMIT, REFERENCE }

@Composable
private fun Legend(series: ParameterSeries, model: ChartModel) {
    val locale = currentLocale()
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LegendRow(LegendMark.MEASURE, stringResource(R.string.history_legend_measure))
        if (model.hasCensored) {
            LegendRow(LegendMark.CENSORED, stringResource(R.string.history_legend_censored))
        }
        ThresholdLegend(
            text = thresholdText(series.limitText, model.limit, series.unit, locale),
            bounds = model.limit,
            mark = LegendMark.LIMIT,
            strings = LIMIT_STRINGS,
        )
        ThresholdLegend(
            text = thresholdText(series.referenceText, model.reference, series.unit, locale),
            bounds = model.reference,
            mark = LegendMark.REFERENCE,
            strings = REFERENCE_STRINGS,
        )
    }
}

/** The legend strings of a threshold, by [ThresholdPlacement], then when it is not drawn. */
private class ThresholdStrings(
    val drawn: Int,
    val above: Int,
    val below: Int,
    val partly: Int,
    val notDrawn: Int,
)

private val LIMIT_STRINGS = ThresholdStrings(
    drawn = R.string.quality_limit,
    above = R.string.history_legend_limit_above,
    below = R.string.history_legend_limit_below,
    partly = R.string.history_legend_limit_partly,
    notDrawn = R.string.history_legend_limit_not_drawn,
)

private val REFERENCE_STRINGS = ThresholdStrings(
    drawn = R.string.history_legend_reference,
    above = R.string.history_legend_reference_above,
    below = R.string.history_legend_reference_below,
    partly = R.string.history_legend_reference_partly,
    notDrawn = R.string.history_legend_reference_not_drawn,
)

/** The published text of a threshold, or its bounds written out when there is none. */
private fun thresholdText(
    published: String?,
    bounds: List<PlacedBound>,
    unit: String?,
    locale: Locale,
): String? {
    published?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    if (bounds.isEmpty()) return null
    val values = bounds.joinToString(" – ") { formatNumber(it.value, 6, locale) }
    return displayUnit(unit)?.let { "$values $it" } ?: values
}

@Composable
private fun ThresholdLegend(
    text: String?,
    bounds: List<PlacedBound>,
    mark: LegendMark,
    strings: ThresholdStrings,
) {
    if (text == null) return
    val value = bidiWrap(text)
    when (thresholdPlacement(bounds)) {
        null -> LegendRow(null, stringResource(strings.notDrawn, value))
        ThresholdPlacement.DRAWN -> LegendRow(mark, stringResource(strings.drawn, value))
        ThresholdPlacement.PARTLY_OUTSIDE -> LegendRow(mark, stringResource(strings.partly, value))
        ThresholdPlacement.ABOVE -> LegendRow(null, stringResource(strings.above, value))
        ThresholdPlacement.BELOW -> LegendRow(null, stringResource(strings.below, value))
    }
}

@Composable
private fun LegendRow(mark: LegendMark?, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (mark == null) Spacer(Modifier.size(24.dp, 16.dp)) else LegendSwatch(mark)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The mark of the legend, drawn like on the chart; decorative for screen readers. */
@Composable
private fun LegendSwatch(mark: LegendMark) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier = Modifier.size(24.dp, 16.dp)) {
        val y = size.height / 2
        val radius = 4.dp.toPx()
        val center = Offset(size.width / 2, y)
        when (mark) {
            LegendMark.MEASURE -> {
                drawLine(colors.primary, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                drawCircle(colors.primary, radius, center)
            }
            LegendMark.CENSORED -> {
                drawCircle(colors.surface, radius, center)
                drawCircle(colors.primary, radius, center, style = Stroke(width = 1.5.dp.toPx()))
            }
            LegendMark.LIMIT ->
                drawLine(colors.onSurface, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
            LegendMark.REFERENCE -> drawLine(
                colors.onSurfaceVariant,
                Offset(0f, y),
                Offset(size.width, y),
                1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
        }
    }
}

/** Every measure, newest first: the accessible alternative to the chart. */
@Composable
private fun MeasureList(series: ParameterSeries) {
    Column {
        Text(
            text = stringResource(R.string.history_values_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                .semantics { heading() },
        )
        series.points.sortedByDescending { it.date }.forEach { point ->
            MeasureRow(point, series.unit)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

/** One measure: its date, then its value as published, as one left-to-right block. */
@Composable
private fun MeasureRow(point: SeriesPoint, unit: String?) {
    val locale = currentLocale()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = mediumDate(publishedDate(point.date), locale),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = bidiWrap(
                pointValueText(point, unit, locale) ?: stringResource(R.string.sample_no_value),
            ),
            style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
        )
    }
}
