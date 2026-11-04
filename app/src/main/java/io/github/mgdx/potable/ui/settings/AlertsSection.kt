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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.potable.R
import io.github.mgdx.potable.ui.sample.currentLocale
import io.github.mgdx.potable.ui.sheet.withDigitsOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Asks for `POST_NOTIFICATIONS` when the ViewModel says so, that is only
 * when the user switches an alert on (Android 13+); the answer goes back to
 * the ViewModel. Notifications allowed or not in the system settings are
 * read again each time the screen resumes.
 */
@Composable
internal fun NotificationPermissionRequest(viewModel: SettingsViewModel) {
    val request by viewModel.permissionRequest.collectAsStateWithLifecycle()
    NotificationPermissionRequest(
        request,
        viewModel::onPermissionRequested,
        viewModel::onPermissionResult,
    )
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshNotificationAccess()
        onPauseOrDispose {}
    }
}

/**
 * Asks for `POST_NOTIFICATIONS` while [request] is true (Android 13+), after
 * calling [onRequested] so that it is asked once per tap; the answer goes to
 * [onResult]. Shared by the settings and the sheet's offer of the alerts.
 */
@Composable
internal fun NotificationPermissionRequest(
    request: Boolean,
    onRequested: () -> Unit,
    onResult: (Boolean) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onResult(it)
    }
    LaunchedEffect(request) {
        if (request && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onRequested()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Alerts (SPEC §5 F7): the two kinds, then how often. "Unfavourable only"
 * is a subset of "new analyses": while those are on, it shows as on and
 * cannot be changed, and its description says why. While no commune is
 * followed ([noFollows]), a sentence says that the alerts only check the
 * followed ones, with a way to the search. While an alert is on and a
 * commune is followed ([lastCheck] not null), when the check last ran.
 */
@Composable
internal fun AlertsSection(
    alerts: AlertSettings,
    weekChoices: List<Int>,
    notice: NotificationNotice,
    noFollows: Boolean,
    lastCheck: LastAlertCheck?,
    onAlert: (AlertKind, Boolean) -> Unit,
    onPeriod: (Int) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSearch: () -> Unit,
    checkNow: CheckNowUi = CheckNowUi(),
    onCheckNow: () -> Unit = {},
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
    lastCheck?.let { LastCheckLines(it) }
    if (checkNow.visible) CheckNowRow(checkNow, onCheckNow)
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
    val anyOn = alerts.newAnalysis || alerts.unfavourable
    if (!anyOn) Description(stringResource(R.string.settings_alert_period_off))
    ChoiceGroup(
        enabled = anyOn,
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

/**
 * "Check now", disabled while no commune is followed or notifications are
 * not allowed (the section says why), and while a check waits or runs,
 * with an indicator and what it is doing, read by screen readers.
 */
@Composable
private fun CheckNowRow(checkNow: CheckNowUi, onCheckNow: () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onCheckNow, enabled = checkNow.enabled) {
            Text(stringResource(R.string.settings_alert_check_now))
        }
        if (checkNow.progress != CheckNowProgress.IDLE) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(
                    if (checkNow.progress == CheckNowProgress.WAITING) {
                        R.string.settings_alert_check_waiting
                    } else {
                        R.string.settings_alert_checking
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** When the alerts last checked, and last failed to if that came after. */
@Composable
private fun LastCheckLines(lastCheck: LastAlertCheck) {
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    Description(
        lastCheck.checkedAt?.let {
            stringResource(R.string.settings_alert_last_check, formatCheckTime(it, locale, zone))
        } ?: stringResource(R.string.settings_alert_last_check_none),
    )
    val failedAt = lastCheck.failedAt
    if (lastCheck.lastFailed && failedAt != null) {
        Description(
            stringResource(
                R.string.settings_alert_last_failure,
                formatCheckTime(failedAt, locale, zone),
            ),
        )
    }
}

/** [at] as a medium date and a short time of [locale] in [zone], digits included. */
internal fun formatCheckTime(at: Instant, locale: Locale, zone: ZoneId): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withDigitsOf(locale)
        .format(at.atZone(zone))

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
