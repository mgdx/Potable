package io.github.mgdx.potable.ui.search

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.model.Commune
import io.github.mgdx.potable.data.preferences.RecentUnit
import io.github.mgdx.potable.ui.common.EmptyState
import io.github.mgdx.potable.ui.common.LoadingState
import io.github.mgdx.potable.ui.common.asApiText
import io.github.mgdx.potable.ui.common.bidiWrap
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
    val focusManager = LocalFocusManager.current
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                expandedHeight = topBarHeight(hasSubtitle = false),
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        modifier = Modifier.semantics { heading() },
                    )
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
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text(stringResource(R.string.search_label)) },
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
                    )
                } else if (nearby !is NearbyState.Found) {
                    EmptyState(stringResource(R.string.search_hint))
                }
                SearchUiState.Loading -> LoadingState(stringResource(R.string.search_loading))
                is SearchUiState.NoResults -> EmptyState(
                    stringResource(R.string.search_no_results, bidiWrap(state.query)),
                )
                is SearchUiState.Results -> {
                    val rows = remember(state.communes, knownUdis) {
                        searchRows(state.communes, knownUdis)
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(rows, key = { it.key() }) { row ->
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
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/** The "near me" button, then what it found: a commune to select, or why not. */
@Composable
private fun NearbySection(
    nearby: NearbyState,
    onNearby: () -> Unit,
    onCommuneSelected: (Commune) -> Unit,
) {
    OutlinedButton(
        onClick = onNearby,
        enabled = nearby !is NearbyState.Locating,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Text(
            text = stringResource(R.string.nearby_action),
            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
        )
    }
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
            HorizontalDivider()
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
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = RECENT_TITLE_KEY) {
            Text(
                text = stringResource(R.string.search_recent_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    .semantics { heading() },
            )
        }
        items(recentUnits, key = { "${it.communeCode}/${it.udiCode}" }) { unit ->
            ListItem(
                modifier = Modifier.clickable { onSelected(unit) },
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
            HorizontalDivider()
        }
    }
}

/** Unit keys hold a "/": none equals the title's. */
private const val RECENT_TITLE_KEY = "recent"

/** The first postal code, followed by "+n" when the commune has more. */
@Composable
private fun postalCodesText(postalCodes: List<String>): String? = when (postalCodes.size) {
    0 -> null
    1 -> postalCodes.first()
    else -> stringResource(
        R.string.search_postal_codes_more,
        bidiWrap(postalCodes.first()),
        postalCodes.size - 1,
    )
}
