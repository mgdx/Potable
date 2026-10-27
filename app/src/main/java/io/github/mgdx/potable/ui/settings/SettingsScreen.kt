package io.github.mgdx.potable.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.potable.R
import io.github.mgdx.potable.data.preferences.ThemeMode
import io.github.mgdx.potable.ui.common.BackTopBar
import io.github.mgdx.potable.ui.common.asApiText
import io.github.mgdx.potable.ui.common.bidiWrap
import io.github.mgdx.potable.ui.sample.currentLocale
import io.github.mgdx.potable.ui.sheet.formatFetchedDate
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Settings (SPEC §5 F8). Every change is stored at once: there is no "save". */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val purge by viewModel.purge.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val purgedMessage = stringResource(R.string.settings_clear_cache_done)
    val notice by viewModel.notice.collectAsStateWithLifecycle()
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
                .padding(vertical = 8.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_appearance_title))
            ChoiceGroup(
                choices = ThemeMode.entries,
                selected = current.themeMode,
                label = { stringResource(it.labelRes()) },
                onSelect = viewModel::setThemeMode,
            )

            SectionTitle(stringResource(R.string.settings_history_title))
            Description(stringResource(R.string.settings_history_description))
            ChoiceGroup(
                choices = viewModel.yearChoices,
                selected = current.historyYears,
                label = { pluralStringResource(R.plurals.settings_years, it, it) },
                onSelect = viewModel::setHistoryYears,
            )

            SectionTitle(stringResource(R.string.settings_storage_title))
            Description(stringResource(R.string.settings_retention_description))
            ChoiceGroup(
                choices = viewModel.yearChoices,
                selected = current.retentionYears,
                label = { pluralStringResource(R.plurals.settings_years, it, it) },
                onSelect = viewModel::setRetentionYears,
            )
            Description(stringResource(R.string.settings_clear_cache_description))
            OutlinedButton(
                onClick = viewModel::requestPurge,
                enabled = purge != PurgeState.RUNNING,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.settings_clear_cache))
            }
            CachedCommunes(cachedCommunes, onClear = viewModel::requestCommunePurge)

            AlertsSection(
                alerts = current.alerts,
                weekChoices = viewModel.alertWeekChoices,
                notice = notice,
                onAlert = viewModel::setAlert,
                onPeriod = viewModel::setAlertPeriodWeeks,
                onOpenNotificationSettings = { openNotificationSettings(context) },
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

/**
 * The communes in the cache (SPEC §7), each with a button that clears its
 * cache alone. Nothing until the cache is read; a sentence when it is empty.
 */
@Composable
private fun CachedCommunes(
    communes: List<CachedCommuneItem>?,
    onClear: (CachedCommuneItem) -> Unit,
) {
    communes ?: return
    Text(
        text = stringResource(R.string.settings_cached_communes_title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
            .semantics { heading() },
    )
    if (communes.isEmpty()) {
        Description(stringResource(R.string.settings_cached_communes_empty))
        return
    }
    val locale = currentLocale()
    val context = LocalContext.current
    communes.forEach { commune ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = commune.label,
                    style = MaterialTheme.typography.bodyLarge.asApiText(),
                )
                commune.lastFetched?.let { fetched ->
                    val date = remember(fetched, locale) {
                        formatFetchedDate(fetched, locale, ZoneId.systemDefault())
                    }
                    Text(
                        text = stringResource(R.string.sheet_data_from, date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Localised by the system ("42 kB", "42 ko").
            Text(
                text = Formatter.formatShortFileSize(context, commune.sizeBytes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
            IconButton(onClick = { onClear(commune) }) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(
                        R.string.settings_clear_commune_description,
                        bidiWrap(commune.label),
                    ),
                )
            }
        }
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

/**
 * A radio group: each whole row is the touch target and is announced as a
 * radio button, selected or not; the button itself takes no click of its own.
 */
@Composable
private fun <T> ChoiceGroup(
    choices: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        choices.forEach { choice ->
            val isSelected = choice == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelect(choice) },
                        role = Role.RadioButton,
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isSelected, onClick = null)
                Text(
                    text = label(choice),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}

/**
 * Asks for `POST_NOTIFICATIONS` when the ViewModel says so, that is only
 * when the user switches an alert on (Android 13+); the answer goes back to
 * the ViewModel. Notifications allowed or not in the system settings are
 * read again each time the screen resumes.
 */
@Composable
private fun NotificationPermissionRequest(viewModel: SettingsViewModel) {
    val request by viewModel.permissionRequest.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onPermissionResult(it)
    }
    LaunchedEffect(request) {
        if (request && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            viewModel.onPermissionRequested()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshNotificationAccess()
        onPauseOrDispose {}
    }
}

/**
 * Alerts (SPEC §5 F7): the two kinds, then how often. "Unfavourable only"
 * is a subset of "new analyses": while those are on, it shows as on and
 * cannot be changed, and its description says why.
 */
@Composable
private fun AlertsSection(
    alerts: AlertSettings,
    weekChoices: List<Int>,
    notice: NotificationNotice,
    onAlert: (AlertKind, Boolean) -> Unit,
    onPeriod: (Int) -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    SectionTitle(stringResource(R.string.settings_alerts_title))
    Description(stringResource(R.string.settings_alerts_description))
    SwitchRow(
        title = stringResource(R.string.alert_new_analyses),
        description = stringResource(R.string.settings_alert_new_description),
        checked = alerts.newAnalysis,
        enabled = true,
        onChange = { onAlert(AlertKind.NEW_ANALYSIS, it) },
    )
    SwitchRow(
        title = stringResource(R.string.settings_alert_unfavourable),
        description = stringResource(
            if (alerts.newAnalysis) {
                R.string.settings_alert_unfavourable_included
            } else {
                R.string.settings_alert_unfavourable_description
            },
        ),
        checked = alerts.newAnalysis || alerts.unfavourable,
        enabled = !alerts.newAnalysis,
        onChange = { onAlert(AlertKind.UNFAVOURABLE, it) },
    )
    if (notice != NotificationNotice.NONE) {
        Description(
            stringResource(
                if (notice == NotificationNotice.DENIED) {
                    R.string.settings_notifications_denied
                } else {
                    R.string.settings_notifications_blocked
                },
            ),
        )
        OutlinedButton(
            onClick = onOpenNotificationSettings,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.settings_notifications_open))
        }
    }
    Description(stringResource(R.string.settings_alert_period))
    ChoiceGroup(
        choices = weekChoices,
        selected = alerts.periodWeeks,
        label = {
            if (it == 1) {
                stringResource(R.string.settings_alert_weekly)
            } else {
                pluralStringResource(R.plurals.settings_alert_weeks, it, it)
            }
        },
        onSelect = onPeriod,
    )
}

/** A whole row that toggles a switch, announced as one switch with its title. */
@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
                },
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Material's opacity for disabled content. */
private const val DISABLED_ALPHA = 0.38f

/** The application's notification settings, or its details screen if they cannot be opened. */
private fun openNotificationSettings(context: Context) {
    val notifications = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    val details =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
    for (intent in listOf(notifications, details)) {
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next one.
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Description(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
