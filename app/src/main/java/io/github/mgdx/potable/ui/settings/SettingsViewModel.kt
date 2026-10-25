package io.github.mgdx.potable.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mgdx.potable.AppContainer
import io.github.mgdx.potable.alert.NotificationAccess
import io.github.mgdx.potable.data.WaterRepository
import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.data.preferences.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The stored alert settings (SPEC §5 F7). */
data class AlertSettings(val newAnalysis: Boolean, val unfavourable: Boolean, val periodWeeks: Int)

/** The stored settings, as read from [AppPreferences]. */
data class Settings(
    val themeMode: ThemeMode,
    val historyYears: Int,
    val retentionYears: Int,
    val alerts: AlertSettings,
)

/** Where the "clear cached data" action stands. */
enum class PurgeState {
    IDLE,

    /** The confirmation dialog is shown; nothing is deleted yet. */
    CONFIRMING,

    RUNNING,

    /** The cache was cleared: a confirmation message is due, once. */
    DONE,
}

/** The two alerts the user can switch on. */
enum class AlertKind { NEW_ANALYSIS, UNFAVOURABLE }

/** What the screen says about the notification permission, if anything. */
enum class NotificationNotice {
    NONE,

    /** The user just refused, or cannot be asked: the alert stayed off. */
    DENIED,

    /** An alert is on but notifications are not allowed (any more). */
    BLOCKED,
}

/** Settings (SPEC §5 F8): theme, default history window, cache retention and purge, alerts. */
class SettingsViewModel(
    private val preferences: AppPreferences,
    private val water: WaterRepository,
    private val notifications: NotificationAccess,
) : ViewModel() {
    /** Null until the preferences are read, so that no wrong choice shows first. */
    val settings: StateFlow<Settings?> = combine(
        preferences.themeMode,
        preferences.historyYears,
        preferences.retentionYears,
        combine(
            preferences.alertNewAnalysis,
            preferences.alertUnfavourable,
            preferences.alertPeriodWeeks,
            ::AlertSettings,
        ),
        ::Settings,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _purge = MutableStateFlow(PurgeState.IDLE)
    val purge: StateFlow<PurgeState> = _purge.asStateFlow()

    val yearChoices: List<Int> = AppPreferences.YEAR_CHOICES

    val alertWeekChoices: List<Int> = AppPreferences.ALERT_WEEK_CHOICES

    /** Read again by [refreshNotificationAccess] each time the screen resumes. */
    private val canPost = MutableStateFlow(notifications.canPost())

    private val denied = MutableStateFlow(false)

    val notice: StateFlow<NotificationNotice> = combine(settings, canPost, denied) {
            current,
            allowed,
            refused,
        ->
        val anyOn = current?.alerts?.let { it.newAnalysis || it.unfavourable } ?: false
        when {
            allowed -> NotificationNotice.NONE
            refused -> NotificationNotice.DENIED
            anyOn -> NotificationNotice.BLOCKED
            else -> NotificationNotice.NONE
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        NotificationNotice.NONE,
    )

    private val _permissionRequest = MutableStateFlow(false)

    /** True when the screen must ask for `POST_NOTIFICATIONS`, then call [onPermissionRequested]. */
    val permissionRequest: StateFlow<Boolean> = _permissionRequest.asStateFlow()

    /** The alert to switch on once the permission is granted. */
    private var pending: AlertKind? = null

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setHistoryYears(years: Int) {
        if (years !in yearChoices) return
        viewModelScope.launch { preferences.setHistoryYears(years) }
    }

    /**
     * Stores the retention, then applies it to the cache at once rather than
     * at the next refresh (SPEC §7).
     */
    fun setRetentionYears(years: Int) {
        if (years !in yearChoices) return
        viewModelScope.launch {
            preferences.setRetentionYears(years)
            water.purgeOlderThanRetention()
        }
    }

    /**
     * Switches an alert. Switching one on needs notifications: when they are
     * not allowed, the permission is asked for (Android 13+) and the alert is
     * only stored once granted; when the system cannot ask, the alert stays
     * off and the screen explains why.
     */
    fun setAlert(kind: AlertKind, enabled: Boolean) {
        if (!enabled) {
            store(kind, false)
            return
        }
        canPost.value = notifications.canPost()
        when {
            canPost.value -> {
                denied.value = false
                store(kind, true)
            }
            notifications.canRequest -> {
                pending = kind
                _permissionRequest.value = true
            }
            else -> denied.value = true
        }
    }

    /** The permission dialog was launched: it is not asked again for this tap. */
    fun onPermissionRequested() {
        _permissionRequest.value = false
    }

    fun onPermissionResult(granted: Boolean) {
        val kind = pending
        pending = null
        canPost.value = notifications.canPost()
        if (granted && canPost.value) {
            denied.value = false
            if (kind != null) store(kind, true)
        } else {
            denied.value = true
        }
    }

    /** The user may have changed the permission in the system settings. */
    fun refreshNotificationAccess() {
        canPost.value = notifications.canPost()
    }

    fun setAlertPeriodWeeks(weeks: Int) {
        if (weeks !in alertWeekChoices) return
        viewModelScope.launch { preferences.setAlertPeriodWeeks(weeks) }
    }

    private fun store(kind: AlertKind, enabled: Boolean) {
        viewModelScope.launch {
            when (kind) {
                AlertKind.NEW_ANALYSIS -> preferences.setAlertNewAnalysis(enabled)
                AlertKind.UNFAVOURABLE -> preferences.setAlertUnfavourable(enabled)
            }
        }
    }

    /** Asks for confirmation; the cache is only cleared by [confirmPurge]. */
    fun requestPurge() {
        _purge.update { if (it == PurgeState.RUNNING) it else PurgeState.CONFIRMING }
    }

    fun dismissPurge() {
        _purge.update { if (it == PurgeState.CONFIRMING) PurgeState.IDLE else it }
    }

    /** Clears the cache once, and only if the dialog is shown: a double tap does nothing more. */
    fun confirmPurge() {
        if (!_purge.compareAndSet(PurgeState.CONFIRMING, PurgeState.RUNNING)) return
        viewModelScope.launch {
            water.purgeCache()
            _purge.value = PurgeState.DONE
        }
    }

    /** The confirmation message was shown. */
    fun onPurgeMessageShown() {
        _purge.update { if (it == PurgeState.DONE) PurgeState.IDLE else it }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    container.preferences,
                    container.water,
                    container.notificationAccess,
                )
            }
        }
    }
}
