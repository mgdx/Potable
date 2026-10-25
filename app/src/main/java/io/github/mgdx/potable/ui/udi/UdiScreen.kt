package io.github.mgdx.potable.ui.udi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.model.Udi
import io.github.mgdx.potable.ui.common.BackTopBar
import io.github.mgdx.potable.ui.common.EmptyState
import io.github.mgdx.potable.ui.common.ErrorState
import io.github.mgdx.potable.ui.common.LoadingState
import io.github.mgdx.potable.ui.common.asApiText
import io.github.mgdx.potable.ui.common.bidiWrap

@Composable
fun UdiScreen(
    viewModel: UdiViewModel,
    communeName: String,
    onBack: () -> Unit,
    onUdiResolved: (Udi) -> Unit,
    onUdiRemembered: (Udi) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isSlow by viewModel.isSlow.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val currentOnUdiResolved by rememberUpdatedState(onUdiResolved)
    val currentOnUdiRemembered by rememberUpdatedState(onUdiRemembered)
    val forward = state
    LaunchedEffect(forward) {
        // A single unit is chosen without asking, and so is the unit followed
        // or chosen last time among several (SPEC §5 F2), or just picked.
        when (forward) {
            is UdiUiState.Resolved -> currentOnUdiResolved(forward.udi)
            is UdiUiState.Remembered -> currentOnUdiRemembered(forward.udi)
            else -> Unit
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { BackTopBar(title = communeName, onBack = onBack, titleIsData = true) },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (val current = state) {
            UdiUiState.Loading, is UdiUiState.Resolved, is UdiUiState.Remembered ->
                LoadingState(stringResource(R.string.udi_loading), modifier, slow = isSlow)
            is UdiUiState.Error -> ErrorState(current.error, viewModel::retry, modifier)
            UdiUiState.None -> EmptyState(stringResource(R.string.udi_none), modifier)
            is UdiUiState.Choice -> UdiChoiceList(
                udis = current.udis,
                currentCode = current.currentCode,
                query = query,
                onQueryChange = viewModel::onQueryChange,
                onUdiSelected = viewModel::select,
                modifier = modifier,
            )
        }
    }
}

/**
 * The units to choose from, after a title and an explanation; a long list
 * ([showsUdiFilter]) starts with a filter field.
 */
@Composable
private fun UdiChoiceList(
    udis: List<Udi>,
    currentCode: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onUdiSelected: (Udi) -> Unit,
    modifier: Modifier,
) {
    val filterable = showsUdiFilter(udis.size)
    val shown = remember(udis, query, filterable) {
        if (filterable) filterUdis(udis, query) else udis
    }
    LazyColumn(modifier = modifier.fillMaxSize().imePadding()) {
        item(key = "header") {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.udi_choice_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.udi_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
        if (filterable) {
            item(key = "filter") { FilterField(query, onQueryChange) }
            if (shown.isEmpty()) {
                item(key = "no-match") {
                    Text(
                        text = stringResource(R.string.udi_no_match, bidiWrap(query.trim())),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        items(shown, key = { it.code }) { udi ->
            ListItem(
                modifier = Modifier.clickable { onUdiSelected(udi) },
                headlineContent = { Text(udi.name, style = LocalTextStyle.current.asApiText()) },
                supportingContent = udi.district?.let { district ->
                    { Text(district, style = LocalTextStyle.current.asApiText()) }
                },
                overlineContent = if (udi.code == currentCode) {
                    { Text(stringResource(R.string.udi_current_choice)) }
                } else {
                    null
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun FilterField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(stringResource(R.string.udi_search_label)) },
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
