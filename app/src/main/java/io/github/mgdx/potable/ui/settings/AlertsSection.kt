package io.github.mgdx.potable.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.potable.R

/**
 * Asks for `POST_NOTIFICATIONS` when the ViewModel says so, that is only
 * when the user switches an alert on (Android 13+); the answer goes back to
 * the ViewModel. Notifications allowed or not in the system settings are
 * read again each time the screen resumes.
 */
@Composable
internal fun NotificationPermissionRequest(viewModel: SettingsViewModel) {
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
 * cannot be changed, and its description says why. While no commune is
 * followed ([noFollows]), a sentence says that the alerts only check the
 * followed ones, with a way to the search.
 */
@Composable
internal fun AlertsSection(
    alerts: AlertSettings,
    weekChoices: List<Int>,
    notice: NotificationNotice,
    noFollows: Boolean,
    onAlert: (AlertKind, Boolean) -> Unit,
    onPeriod: (Int) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSearch: () -> Unit,
) = SettingsCard(stringResource(R.string.settings_alerts_title)) {
    Description(stringResource(R.string.settings_alerts_description))
    if (noFollows) {
        Description(stringResource(R.string.settings_alerts_no_follows))
        OutlinedButton(
            onClick = onSearch,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.action_search_commune))
        }
    }
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

/** The application's notification settings, or its details screen if they cannot be opened. */
internal fun openNotificationSettings(context: Context) {
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
