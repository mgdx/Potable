package io.github.mgdx.potable.ui.follows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
                FollowList(
                    follows = current.follows,
                    onOpen = onOpen,
                    onMoveUp = viewModel::moveUp,
                    onMoveDown = viewModel::moveDown,
                    onSetPrimary = viewModel::setPrimary,
                    onRemove = viewModel::requestRemoval,
                    modifier = modifier,
                )
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

@Composable
private fun FollowList(
    follows: List<Follow>,
    onOpen: (Follow) -> Unit,
    onMoveUp: (Follow) -> Unit,
    onMoveDown: (Follow) -> Unit,
    onSetPrimary: (Follow) -> Unit,
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
        itemsIndexed(follows, key = { _, follow -> follow.id }) { index, follow ->
            FollowRow(
                follow = follow,
                isFirst = index == 0,
                isLast = index == follows.lastIndex,
                onOpen = { onOpen(follow) },
                onMoveUp = { onMoveUp(follow) },
                onMoveDown = { onMoveDown(follow) },
                onSetPrimary = { onSetPrimary(follow) },
                onRemove = { onRemove(follow) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun FollowRow(
    follow: Follow,
    isFirst: Boolean,
    isLast: Boolean,
    onOpen: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onSetPrimary: () -> Unit,
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
            if (follow.isPrimary) {
                // The text carries the information; the icon only echoes it.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.follows_primary),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            // The rest of the row: with a large font or a long translation,
            // "Set as primary" wraps rather than pushing the delete button out.
            Box(modifier = Modifier.weight(1f)) {
                if (!follow.isPrimary) {
                    val description =
                        stringResource(R.string.follows_set_primary_description, label)
                    TextButton(
                        onClick = onSetPrimary,
                        modifier = Modifier.semantics { contentDescription = description },
                    ) {
                        Text(stringResource(R.string.follows_set_primary))
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.follows_remove, label),
                )
            }
        }
    }
}
