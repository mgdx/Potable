package io.github.mgdx.potable.ui.sheet

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mgdx.potable.R
import io.github.mgdx.potable.ui.settings.NotificationPermissionRequest
import io.github.mgdx.potable.ui.settings.openNotificationSettings

/**
 * The offer to switch the alerts on, made once as a unit is followed
 * (SPEC §5 F7), in [snackbar]; then, if notifications are not allowed, a
 * message with a way to the system settings, as the settings say it. A
 * snackbar cut by a rotation shows again; once dismissed or answered, never.
 */
@Composable
internal fun AlertOfferSnackbar(viewModel: SheetViewModel, snackbar: SnackbarHostState) {
    val offer by viewModel.alertOffer.collectAsStateWithLifecycle()
    val request by viewModel.permissionRequest.collectAsStateWithLifecycle()
    NotificationPermissionRequest(
        request,
        viewModel::onPermissionRequested,
        viewModel::onPermissionResult,
    )
    val context = LocalContext.current
    val offerText = stringResource(R.string.sheet_alerts_offer)
    val offerAction = stringResource(R.string.sheet_alerts_offer_action)
    val deniedText = stringResource(R.string.sheet_alerts_denied)
    val deniedAction = stringResource(R.string.sheet_alerts_denied_action)
    LaunchedEffect(offer) {
        val current = offer ?: return@LaunchedEffect
        val denied = current == AlertOffer.DENIED
        val result = snackbar.showSnackbar(
            message = if (denied) deniedText else offerText,
            actionLabel = if (denied) deniedAction else offerAction,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        viewModel.onAlertOfferShown(current)
        if (result == SnackbarResult.ActionPerformed) {
            if (denied) openNotificationSettings(context) else viewModel.enableAlerts()
        }
    }
}
