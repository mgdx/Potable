package io.github.mgdx.potable.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.potable.R
import io.github.mgdx.potable.ui.common.BackTopBar
import io.github.mgdx.potable.ui.common.bidiWrap
import kotlinx.coroutines.launch

/** Settings (SPEC §5 F8). Every change is stored at once: there is no "save". */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onSearch: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val purge by viewModel.purge.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val purgedMessage = stringResource(R.string.settings_clear_cache_done)
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val noFollows by viewModel.noFollows.collectAsStateWithLifecycle()
    val cachedCommunes by viewModel.cachedCommunes.collectAsStateWithLifecycle()
    val communeToPurge by viewModel.communeToPurge.collectAsStateWithLifecycle()
    val communePurged by viewModel.communePurged.collectAsStateWithLifecycle()
    val context = LocalContext.current
    NotificationPermissionRequest(viewModel)
    communePurged?.let { commune ->
        val message = stringResource(R.string.settings_clear_commune_done, bidiWrap(commune.label))
        LaunchedEffect(commune) {
            // Same as the whole purge: acknowledged first, shown once.
            viewModel.onCommunePurgeMessageShown()
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }
    LaunchedEffect(purge) {
        if (purge == PurgeState.DONE) {
            // Acknowledged first, so that it is shown once; the message lives
            // in the screen's scope, which this state change does not cancel.
            viewModel.onPurgeMessageShown()
            scope.launch { snackbarHostState.showSnackbar(purgedMessage) }
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { BackTopBar(title = stringResource(R.string.settings_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        // Nothing is shown until the preferences are read (a few milliseconds),
        // rather than a choice that would then jump to another.
        val current = settings ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppearanceSection(current.themeMode, onSelect = viewModel::setThemeMode)

            HistorySection(
                years = current.historyYears,
                yearChoices = viewModel.yearChoices,
                onSelect = viewModel::setHistoryYears,
            )

            StorageSection(
                retentionYears = current.retentionYears,
                yearChoices = viewModel.yearChoices,
                purge = purge,
                onRetention = viewModel::setRetentionYears,
                onPurge = viewModel::requestPurge,
            )

            cachedCommunes?.let { communes ->
                CachedCommunesSection(communes, onClear = viewModel::requestCommunePurge)
            }

            AlertsSection(
                alerts = current.alerts,
                weekChoices = viewModel.alertWeekChoices,
                notice = notice,
                noFollows = noFollows,
                onAlert = viewModel::setAlert,
                onPeriod = viewModel::setAlertPeriodWeeks,
                onOpenNotificationSettings = { openNotificationSettings(context) },
                onSearch = onSearch,
            )
        }
    }
    communeToPurge?.let { commune ->
        AlertDialog(
            onDismissRequest = viewModel::dismissCommunePurge,
            title = {
                Text(
                    stringResource(
                        R.string.settings_clear_commune_confirm_title,
                        bidiWrap(commune.label),
                    ),
                )
            },
            text = { Text(stringResource(R.string.settings_clear_commune_confirm_text)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCommunePurge) {
                    Text(stringResource(R.string.settings_clear_cache_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCommunePurge) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (purge == PurgeState.CONFIRMING) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPurge,
            title = { Text(stringResource(R.string.settings_clear_cache_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_cache_confirm_text)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPurge) {
                    Text(stringResource(R.string.settings_clear_cache_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPurge) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
