package io.github.mgdx.potable.alert

import io.github.mgdx.potable.data.alert.AlertChecker
import io.github.mgdx.potable.data.preferences.AppPreferences
import kotlinx.coroutines.flow.first

/**
 * One run of the alert check, whatever starts it (the periodic worker, or
 * the debug tools). Nothing is asked of Hub'Eau when no alert is on, nor when
 * notifications cannot be shown: the last seen samples then stay as they
 * are, and the analyses published meanwhile are announced once allowed.
 */
class AlertRun(
    private val preferences: AppPreferences,
    private val checker: () -> AlertChecker,
    private val poster: AlertPoster,
) {
    /** What the run did, for the tests and the debug tools. */
    enum class Outcome { ALERTS_OFF, CANNOT_NOTIFY, CHECKED }

    suspend fun run(): Outcome {
        val switches = AlertSwitches(
            newAnalysis = preferences.alertNewAnalysis.first(),
            unfavourable = preferences.alertUnfavourable.first(),
        )
        if (!switches.anyEnabled) return Outcome.ALERTS_OFF
        if (!poster.canPost()) return Outcome.CANNOT_NOTIFY
        poster.post(notifiable(checker().check(), switches))
        return Outcome.CHECKED
    }
}
