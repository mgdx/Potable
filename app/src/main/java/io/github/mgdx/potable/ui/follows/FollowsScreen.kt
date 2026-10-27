package io.github.mgdx.potable.ui.follows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.potable.R
import io.github.mgdx.potable.data.follow.Follow
import io.github.mgdx.potable.ui.common.BackTopBar
import io.github.mgdx.potable.ui.common.CenteredColumn
import io.github.mgdx.potable.ui.common.LoadingState
import io.github.mgdx.potable.ui.common.asApiText
import io.github.mgdx.potable.ui.common.bidiWrap

@Composable
fun FollowsScreen(
    viewModel: FollowsViewModel,
    onBack: () -> Unit,
    onOpen: (Follow) -> Unit,
    onSearch: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingRemoval by viewModel.pendingRemoval.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { BackTopBar(title = stringResource(R.string.follows_title), onBack = onBack) },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (val current = state) {
            FollowsUiState.Loading -> LoadingState(
                stringResource(R.string.follows_loading),
                modifier,
            )
            is FollowsUiState.Loaded -> if (current.follows.isEmpty()) {
                EmptyFollows(onSearch, modifier)
            } else {
                Column(modifier = modifier.fillMaxSize()) {
                    FollowFilterField(query = current.query, onQueryChange = viewModel::setQuery)
                    FollowList(
                        follows = current.shown,
                        canReorder = !current.isFiltered,
                        onOpen = onOpen,
                        onMoveUp = viewModel::moveUp,
                        onMoveDown = viewModel::moveDown,
                        onRemove = viewModel::requestRemoval,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    pendingRemoval?.let { follow ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRemoval,
            title = {
                Text(stringResource(R.string.follows_remove_title, bidiWrap(follow.communeName)))
            },
            text = { Text(stringResource(R.string.follows_remove_text, bidiWrap(follow.udiName))) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRemoval) {
                    Text(stringResource(R.string.follows_remove_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRemoval) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun EmptyFollows(onSearch: () -> Unit, modifier: Modifier) {
    CenteredColumn(modifier) {
        Text(
            text = stringResource(R.string.follows_empty),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onSearch) {
            Text(stringResource(R.string.action_search_commune))
        }
    }
}

/** Filters the list; above it, so that it stays in place while the list scrolls. */
@Composable
private fun FollowFilterField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        label = { Text(stringResource(R.string.follows_filter_label)) },
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
    )
}

/**
 * [follows] as shown. While a query filters them, [canReorder] is false and
 * the move buttons are hidden: moving within a partial list would move past
 * rows the user cannot see.
 */
@Composable
private fun FollowList(
    follows: List<Follow>,
    canReorder: Boolean,
    onOpen: (Follow) -> Unit,
    onMoveUp: (Follow) -> Unit,
    onMoveDown: (Follow) -> Unit,
    onRemove: (Follow) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Text(
                text = stringResource(R.string.follows_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            HorizontalDivider()
        }
        if (follows.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.follows_filter_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                )
            }
        }
        itemsIndexed(follows, key = { _, follow -> follow.id }) { index, follow ->
            FollowRow(
                follow = follow,
                canReorder = canReorder,
                isFirst = index == 0,
                isLast = index == follows.lastIndex,
                onOpen = { onOpen(follow) },
                onMoveUp = { onMoveUp(follow) },
                onMoveDown = { onMoveDown(follow) },
                onRemove = { onRemove(follow) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun FollowRow(
    follow: Follow,
    canReorder: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onOpen: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val label = stringResource(
        R.string.commune_and_unit,
        bidiWrap(follow.communeName),
        bidiWrap(follow.udiName),
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(follow.communeName, style = MaterialTheme.typography.titleMedium.asApiText())
            Text(
                text = follow.udiName,
                style = MaterialTheme.typography.bodyMedium.asApiText(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canReorder) {
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.follows_move_up, label),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.follows_move_down, label),
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.follows_remove, label),
                )
            }
        }
    }
}
