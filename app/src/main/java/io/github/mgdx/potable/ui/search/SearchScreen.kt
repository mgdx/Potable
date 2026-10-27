package io.github.mgdx.potable.ui.search

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.model.Commune
import io.github.mgdx.potable.data.preferences.RecentUnit
import io.github.mgdx.potable.ui.common.CenteredColumn
import io.github.mgdx.potable.ui.common.EmptyState
import io.github.mgdx.potable.ui.common.LoadingState
import io.github.mgdx.potable.ui.common.PillSearchField
import io.github.mgdx.potable.ui.common.SectionCard
import io.github.mgdx.potable.ui.common.asApiText
import io.github.mgdx.potable.ui.common.bidiWrap
import io.github.mgdx.potable.ui.common.postalCodesText
import io.github.mgdx.potable.ui.common.topBarHeight
import io.github.mgdx.potable.ui.settings.OverflowMenu

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onCommuneSelected: (commune: Commune, choose: Boolean) -> Unit,
    onKnownUdiSelected: (commune: Commune, communeName: String, udi: KnownUdi) -> Unit,
    onRecentSelected: (RecentUnit) -> Unit,
    onMyCommunes: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val recentUnits by viewModel.recentUnits.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nearby by viewModel.nearby.collectAsStateWithLifecycle()
    val knownUdis by viewModel.knownUdis.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The permission is asked for here, when "near me" is tapped, and never
    // at launch (SPEC §5 F1).
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.locateNearby() else viewModel.onLocationPermissionDenied()
    }
    SearchContent(
        query = query,
        state = state,
        nearby = nearby,
        knownUdis = knownUdis,
        recentUnits = recentUnits,
        onQueryChange = viewModel::onQueryChange,
        onCommuneSelected = onCommuneSelected,
        onKnownUdiSelected = { commune, communeName, udi ->
            viewModel.rememberChoice(commune.queryCode, udi)
            onKnownUdiSelected(commune, communeName, udi)
        },
        onRecentSelected = onRecentSelected,
        onRecentRemoved = viewModel::removeRecent,
        onNearby = {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.locateNearby()
            } else {
                permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        },
        onMyCommunes = onMyCommunes,
        onSettings = onSettings,
        onAbout = onAbout,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchContent(
    query: String,
    state: SearchUiState,
    nearby: NearbyState,
    knownUdis: Map<String, List<KnownUdi>>,
    recentUnits: List<RecentUnit>,
    onQueryChange: (String) -> Unit,
    onCommuneSelected: (Commune, Boolean) -> Unit,
    onKnownUdiSelected: (Commune, String, KnownUdi) -> Unit,
    onRecentSelected: (RecentUnit) -> Unit,
    onRecentRemoved: (RecentUnit) -> Unit,
    onNearby: () -> Unit,
    onMyCommunes: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                expandedHeight = topBarHeight(hasSubtitle = false),
                title = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Decorative: the name beside it says it all.
                        Icon(
                            painter = painterResource(R.drawable.ic_logo_mark),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onMyCommunes) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.follows_title),
                        )
                    }
                    OverflowMenu(onSettings = onSettings, onAbout = onAbout)
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            PillSearchField(
                value = query,
                onValueChange = onQueryChange,
                label = stringResource(R.string.search_label),
            )
            NearbySection(
                nearby = nearby,
                onNearby = onNearby,
                onCommuneSelected = { commune ->
                    onCommuneSelected(commune, commune.queryCode in knownUdis)
                },
            )
            when (state) {
                SearchUiState.Idle -> if (recentUnits.isNotEmpty()) {
                    RecentSection(
                        recentUnits = recentUnits,
                        onSelected = onRecentSelected,
                        onRemoved = onRecentRemoved,
                        modifier = Modifier.weight(1f, fill = false).then(ListCardMargins),
                    )
                } else if (nearby !is NearbyState.Found) {
                    SearchInvitation()
                }
                SearchUiState.Loading -> LoadingState(stringResource(R.string.search_loading))
                is SearchUiState.NoResults -> EmptyState(
                    stringResource(R.string.search_no_results, bidiWrap(state.query)),
                )
                is SearchUiState.Results -> {
                    val rows = remember(state.communes, knownUdis) {
                        searchRows(state.communes, knownUdis)
                    }
                    ListCard(modifier = Modifier.weight(1f, fill = false).then(ListCardMargins)) {
                        itemsIndexed(rows, key = { _, row -> row.key() }) { index, row ->
                            if (index > 0) ListDivider()
                            val commune = row.commune
                            val parentName = commune.hubEauCode?.let { state.parentNames[it] }
                            when (row) {
                                is SearchRow.Place -> CommuneRow(
                                    commune = commune,
                                    parentName = parentName,
                                    onClick = { onCommuneSelected(commune, row.choose) },
                                )
                                is SearchRow.KnownUnit -> {
                                    // The city the unit serves, rather than an
                                    // arrondissement or a delegated commune.
                                    val communeName = parentName ?: commune.name
                                    KnownUdiRow(
                                        communeName = communeName,
                                        udi = row.udi,
                                        onClick = {
                                            onKnownUdiSelected(commune, communeName, row.udi)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Around a list card, which is as tall as its rows, up to this bottom margin. */
private val ListCardMargins =
    Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)

/**
 * A list of ListItems in a card that scrolls them, under an optional [title].
 * Vertical padding only: ListItem brings its own horizontal inset, which the
 * title takes too.
 */
@Composable
private fun ListCard(
    modifier: Modifier,
    title: String? = null,
    content: LazyListScope.() -> Unit,
) {
    SectionCard(modifier = modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .semantics { heading() },
            )
        }
        LazyColumn(content = content)
    }
}

/** No recent unit and nothing typed: the logo mark, then what to do. */
@Composable
private fun SearchInvitation() {
    CenteredColumn(Modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_logo_mark),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(96.dp),
        )
        Text(
            text = stringResource(R.string.search_hint),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/** Between list rows, inset like their text. */
@Composable
private fun ListDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** List rows sit on the background of what holds them. */
@Composable
private fun transparentListItemColors() =
    ListItemDefaults.colors(containerColor = Color.Transparent)

/** The "near me" button, then what it found: a commune to select, or why not. */
@Composable
private fun NearbySection(
    nearby: NearbyState,
    onNearby: () -> Unit,
    onCommuneSelected: (Commune) -> Unit,
) {
    AssistChip(
        onClick = onNearby,
        enabled = nearby !is NearbyState.Locating,
        modifier = Modifier.padding(horizontal = 16.dp),
        label = { Text(stringResource(R.string.nearby_action)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
    )
    when (nearby) {
        NearbyState.Idle -> Unit
        NearbyState.Locating -> Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(R.string.nearby_locating),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        is NearbyState.Failed -> Text(
            text = stringResource(nearby.reason.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        is NearbyState.Found -> {
            Text(
                text = stringResource(R.string.nearby_found_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    .semantics { heading() },
            )
            CommuneRow(
                commune = nearby.commune,
                parentName = nearby.parentName,
                onClick = { onCommuneSelected(nearby.commune) },
            )
            ListDivider()
        }
    }
}

private fun NearbyFailure.messageRes(): Int = when (this) {
    NearbyFailure.PERMISSION_DENIED -> R.string.nearby_permission_denied
    NearbyFailure.LOCATION_DISABLED -> R.string.nearby_disabled
    NearbyFailure.UNAVAILABLE -> R.string.nearby_unavailable
    NearbyFailure.NO_COMMUNE -> R.string.nearby_no_commune
}

@Composable
private fun CommuneRow(commune: Commune, parentName: String?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = transparentListItemColors(),
        headlineContent = { Text(commune.name, style = LocalTextStyle.current.asApiText()) },
        supportingContent = {
            Column {
                postalCodesText(commune.postalCodes)?.let { Text(it) }
                commune.hubEauCode?.let { parentCode ->
                    Text(
                        text = stringResource(
                            R.string.search_published_under,
                            // The code stands in when the parent is not in the referential.
                            bidiWrap(parentName ?: parentCode),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

/** Commune codes have no "/": the key of a unit row never equals a commune's. */
private fun SearchRow.key(): String = when (this) {
    is SearchRow.Place -> commune.code
    is SearchRow.KnownUnit -> "${commune.queryCode}/${udi.code}"
}

/** "Commune, unit": a unit of the commune the user already knows, opened directly. */
@Composable
private fun KnownUdiRow(communeName: String, udi: KnownUdi, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = transparentListItemColors(),
        headlineContent = {
            Text(
                stringResource(
                    R.string.commune_and_unit,
                    bidiWrap(communeName),
                    bidiWrap(udi.name),
                ),
            )
        },
        supportingContent = if (udi.isFollowed) {
            {
                Text(
                    text = stringResource(R.string.search_unit_followed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
    )
}

/** The units last opened, most recent first: each opens its sheet, or leaves the list. */
@Composable
private fun RecentSection(
    recentUnits: List<RecentUnit>,
    onSelected: (RecentUnit) -> Unit,
    onRemoved: (RecentUnit) -> Unit,
    modifier: Modifier,
) {
    ListCard(modifier = modifier, title = stringResource(R.string.search_recent_title)) {
        itemsIndexed(recentUnits, key = { _, unit -> unit.key() }) { index, unit ->
            if (index > 0) ListDivider()
            ListItem(
                modifier = Modifier.clickable { onSelected(unit) },
                colors = transparentListItemColors(),
                headlineContent = {
                    Text(unit.communeName, style = LocalTextStyle.current.asApiText())
                },
                supportingContent = {
                    Text(unit.udiName, style = LocalTextStyle.current.asApiText())
                },
                trailingContent = {
                    IconButton(onClick = { onRemoved(unit) }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(
                                R.string.search_recent_remove,
                                stringResource(
                                    R.string.commune_and_unit,
                                    bidiWrap(unit.communeName),
                                    bidiWrap(unit.udiName),
                                ),
                            ),
                        )
                    }
                },
            )
        }
    }
}

/** Commune and unit codes have no "/": two units never share a key. */
private fun RecentUnit.key(): String = "$communeCode/$udiCode"
