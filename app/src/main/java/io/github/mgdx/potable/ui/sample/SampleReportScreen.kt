package io.github.mgdx.potable.ui.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.potable.R
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
import io.github.mgdx.potable.ui.sheet.ComplianceSection
import io.github.mgdx.potable.ui.sheet.ConclusionCard
import io.github.mgdx.potable.ui.sheet.KeyParametersCard
import io.github.mgdx.potable.ui.sheet.RelativeAge
import io.github.mgdx.potable.ui.sheet.SampleInfoCard
import io.github.mgdx.potable.ui.sheet.formatSampleDate
import java.time.Clock

/**
 * The full report of one sample loaded by its code (SPEC §5 F4), opened from
 * the history of a parameter: the sections of the sheet's sample (conclusion
 * verbatim, key parameters, sample card, compliance), then access to every
 * result ([onShowAllResults]). Nothing of the unit itself (follow, freshness,
 * watched substances, last 12 months, history): it is about one sample.
 */
@Composable
fun SampleReportScreen(
    viewModel: SampleReportViewModel,
    onBack: () -> Unit,
    onShowAllResults: () -> Unit,
    onParameterHistory: (codes: Set<String>, label: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isSlow by viewModel.isSlow.collectAsStateWithLifecycle()
    val loaded = state as? SampleDetailUiState.Loaded
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BackTopBar(
                title = stringResource(R.string.sample_report_title),
                subtitle = loaded?.let {
                    stringResource(
                        R.string.sheet_sampled_on,
                        formatSampleDate(it.sample.date, currentLocale()),
                    )
                },
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (val current = state) {
            SampleDetailUiState.Loading ->
                LoadingState(stringResource(R.string.sample_loading), modifier, slow = isSlow)
            is SampleDetailUiState.Error -> ErrorState(current.error, viewModel::retry, modifier)
            SampleDetailUiState.Unavailable ->
                EmptyState(stringResource(R.string.sample_report_not_found), modifier)
            is SampleDetailUiState.Loaded ->
                ReportContent(current.sample, onShowAllResults, onParameterHistory, modifier)
        }
    }
}

@Composable
private fun ReportContent(
    sample: Sample,
    onShowAllResults: () -> Unit,
    onParameterHistory: (Set<String>, String) -> Unit,
    modifier: Modifier,
) {
    val age = remember(sample.date) { RelativeAge.of(sample.date, Clock.systemUTC()) }
    val showAllResults = @Composable {
        if (sample.results.isNotEmpty()) {
            FilledTonalButton(onClick = onShowAllResults, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sheet_show_all_results))
            }
        } else {
            Text(
                text = stringResource(R.string.sample_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    // One scroll state for both layouts, kept when the window is resized.
    val scrollState = rememberScrollState()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val twoPane = isExpandedWidth(maxWidth)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .readableWidth(if (twoPane) TwoPaneMaxWidth else ReadableMaxWidth)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (twoPane) {
                // The conclusion and what it is about, then the measures.
                TwoPanes(
                    start = {
                        ConclusionCard(sample, age, R.string.sample_report_conclusion_title)
                        SampleInfoCard(sample, age)
                        ComplianceSection(sample)
                        showAllResults()
                    },
                    end = { KeyParametersCard(sample, onParameterHistory) },
                )
            } else {
                ConclusionCard(sample, age, R.string.sample_report_conclusion_title)
                KeyParametersCard(sample, onParameterHistory)
                SampleInfoCard(sample, age)
                ComplianceSection(sample)
                showAllResults()
            }
        }
    }
}
