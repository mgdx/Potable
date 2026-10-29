package io.github.mgdx.potable.ui.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.MetaboliteRelevance
import io.github.mgdx.potable.core.ParameterFamily
import io.github.mgdx.potable.core.PesticideCount
import io.github.mgdx.potable.core.Relevance
import io.github.mgdx.potable.core.model.AnalysisResult
import io.github.mgdx.potable.ui.common.BackTopBar
import io.github.mgdx.potable.ui.common.EmptyState
import io.github.mgdx.potable.ui.common.ErrorState
import io.github.mgdx.potable.ui.common.LoadingState
import io.github.mgdx.potable.ui.common.ReadableInsetBox
import io.github.mgdx.potable.ui.common.asApiText
import io.github.mgdx.potable.ui.common.bidiWrap
import io.github.mgdx.potable.ui.common.parameterExplanationRes
import io.github.mgdx.potable.ui.sheet.formatSampleDate
import io.github.mgdx.potable.ui.sheet.justified

/** Language of the Hub'Eau labels (SPEC §11.2). */
private const val FRENCH_LANGUAGE_TAG = "fr-FR"

/**
 * Every parameter analysed in a sample, grouped by family, with a text
 * filter (SPEC §5 F4). Values and limits are restituted as published: no
 * colour, no verdict, no comparison between a value and its limit (SPEC §14).
 */
@Composable
fun SampleDetailScreen(
    viewModel: SampleDetailViewModel,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenHistory: (AnalysisResult) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    SampleDetailContent(
        state = state,
        query = query,
        onQueryChange = viewModel::onQueryChange,
        onRetry = onRetry,
        onBack = onBack,
        onOpenHistory = onOpenHistory,
        unavailableText = stringResource(R.string.sample_unavailable),
    )
}

/**
 * Every result of the sample of a report ([SampleReportScreen]): the same
 * screen as [SampleDetailScreen], fed by the report's loaded sample.
 */
@Composable
fun SampleReportResultsScreen(
    viewModel: SampleReportResultsViewModel,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenHistory: (AnalysisResult) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    SampleDetailContent(
        state = state,
        query = query,
        onQueryChange = viewModel::onQueryChange,
        onRetry = onRetry,
        onBack = onBack,
        onOpenHistory = onOpenHistory,
        unavailableText = stringResource(R.string.sample_report_not_found),
    )
}

/**
 * Every result of a sample in [state], stateless: [unavailableText] says why
 * there is no sample to show; [slow] when Hub'Eau is slow to answer.
 */
@Composable
fun SampleDetailContent(
    state: SampleDetailUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenHistory: (AnalysisResult) -> Unit,
    unavailableText: String,
    slow: Boolean = false,
) {
    val loaded = state as? SampleDetailUiState.Loaded
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BackTopBar(
                title = stringResource(R.string.sample_detail_title),
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
        when (state) {
            SampleDetailUiState.Loading ->
                LoadingState(stringResource(R.string.sample_loading), modifier, slow = slow)
            is SampleDetailUiState.Error -> ErrorState(state.error, onRetry, modifier)
            SampleDetailUiState.Unavailable -> EmptyState(unavailableText, modifier)
            is SampleDetailUiState.Loaded ->
                if (state.sample.results.isEmpty()) {
                    EmptyState(stringResource(R.string.sample_no_results), modifier)
                } else {
                    ResultList(state, query, onQueryChange, onOpenHistory, modifier)
                }
        }
    }
}

@Composable
private fun ResultList(
    state: SampleDetailUiState.Loaded,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenHistory: (AnalysisResult) -> Unit,
    modifier: Modifier,
) {
    ReadableInsetBox(modifier = modifier.fillMaxSize().imePadding()) { inset ->
        ResultListContent(state, query, onQueryChange, onOpenHistory, inset)
    }
}

/** [ResultList] with the [inset] that centres it on a large screen. */
@Composable
private fun ResultListContent(
    state: SampleDetailUiState.Loaded,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenHistory: (AnalysisResult) -> Unit,
    inset: Dp,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SearchField(query, onQueryChange, Modifier.padding(horizontal = inset))
        if (state.groups.isEmpty()) {
            EmptyState(stringResource(R.string.sample_no_match, bidiWrap(query.trim())))
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = inset, end = inset, bottom = 16.dp),
        ) {
            item(key = "notice") { Notice() }
            for (group in state.groups) {
                item(key = "family-${group.family}") { FamilyHeader(group.family) }
                val pesticides = state.pesticides
                if (group.family == ParameterFamily.PESTICIDES && pesticides != null) {
                    val classified = group.results.any {
                        MetaboliteRelevance.of(it.parameterCode) != null
                    }
                    item(key = "pesticides-count") { PesticideCountHeader(pesticides, classified) }
                }
                items(group.results) { result ->
                    ResultItem(
                        result = result,
                        communeCode = state.sample.communeCode,
                        onClick = { onOpenHistory(result) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(stringResource(R.string.sample_search_label)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.action_clear_search),
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Notice() {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.sample_official_labels),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.sample_values_as_published),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FamilyHeader(family: ParameterFamily) {
    Text(
        text = stringResource(family.titleRes()),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/**
 * How many pesticides the whole sample searched for and quantified, under
 * their heading, and what quantified means: facts, no verdict (SPEC §14).
 * When the list shows a metabolite classified by ANSES ([classifiedShown]),
 * what the classification means, once for all of them.
 */
@Composable
private fun PesticideCountHeader(count: PesticideCount, classifiedShown: Boolean) {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = pluralStringResource(
                R.plurals.sample_pesticides_count,
                count.analysed,
                count.analysed,
                count.quantified,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.pesticides_quantified_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (classifiedShown) {
            Text(
                text = stringResource(R.string.metabolite_relevance_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One result: the API label, the value block, and the limit and reference as
 * published, and, for a parameter of the sheet's editorial tables, the same
 * explanation as on the sheet ([parameterExplanationRes], in the commune of
 * [communeCode]). Tapping it opens the history of the parameter ([onClick]).
 */
@Composable
private fun ResultItem(result: AnalysisResult, communeCode: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.sample_open_history),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ResultText(result, communeCode, Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultText(result: AnalysisResult, communeCode: String, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Tagged as French so that screen readers pronounce it as such.
        Text(
            text = AnnotatedString(
                result.parameterLabel,
                SpanStyle(localeList = LocaleList(FRENCH_LANGUAGE_TAG)),
            ),
            style = MaterialTheme.typography.bodyLarge.asApiText(),
        )
        ResultValue(result, style = MaterialTheme.typography.titleMedium, selectable = false)
        if (result.isOnSite) {
            Text(
                text = stringResource(R.string.result_measured_on_site),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        result.qualityLimit?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = stringResource(R.string.quality_limit, bidiWrap(it)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        result.qualityReference?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = stringResource(R.string.quality_reference, bidiWrap(it)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // A fact of the ANSES table, read with the result (the row merges
        // its texts), never set against the value (SPEC §14).
        MetaboliteRelevance.of(result.parameterCode)?.let { relevance ->
            Text(
                text = stringResource(relevance.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // As on the sheet; no text for a parameter without an editorial one.
        val explanation = remember(result.parameterCode, communeCode) {
            parameterExplanationRes(result.parameterCode, communeCode)
        }
        explanation?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall.justified(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Relevance.labelRes(): Int = when (this) {
    Relevance.RELEVANT -> R.string.metabolite_relevant
    Relevance.NOT_RELEVANT -> R.string.metabolite_not_relevant
}

private fun ParameterFamily.titleRes(): Int = when (this) {
    ParameterFamily.MICROBIOLOGY -> R.string.family_microbiology
    ParameterFamily.PHYSICOCHEMISTRY -> R.string.family_physicochemistry
    ParameterFamily.DISINFECTION -> R.string.family_disinfection
    ParameterFamily.PESTICIDES -> R.string.family_pesticides
    ParameterFamily.METALS -> R.string.family_metals
    ParameterFamily.ORGANIC_COMPOUNDS -> R.string.family_organic_compounds
    ParameterFamily.RADIOACTIVITY -> R.string.family_radioactivity
    ParameterFamily.OTHER -> R.string.family_other
}
