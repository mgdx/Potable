package io.github.mgdx.potable.alert

import io.github.mgdx.potable.data.alert.AlertChecker
import io.github.mgdx.potable.data.preferences.AppPreferences
import kotlinx.coroutines.flow.first
import java.time.Clock

/**
 * One run of the alert check, whatever starts it (the periodic worker, or
 * the debug tools). Nothing is asked of Hub'Eau when no alert is on, nor when
 * notifications cannot be shown: the last seen samples then stay as they
 * are, and the analyses published meanwhile are announced once allowed.
 * When the check ends, its time is recorded as the last check if Hub'Eau
 * answered for every followed unit, as the last failure otherwise: the
 * settings show it.
 */
class AlertRun(
    private val preferences: AppPreferences,
    private val checker: () -> AlertChecker,
    private val poster: AlertPoster,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** What the run did, for the tests, the debug tools and "Check now". */
    enum class Outcome {
        ALERTS_OFF,
        CANNOT_NOTIFY,

        /** At least one notification was posted. */
        NOTIFIED,

        /** Hub'Eau answered for every followed unit, with nothing to notify. */
        NOTHING_NEW,

        /** Nothing to notify, but Hub'Eau did not answer for every followed unit. */
        FAILED,
    }

    suspend fun run(): Outcome {
        val switches = AlertSwitches(
            newAnalysis = preferences.alertNewAnalysis.first(),
            unfavourable = preferences.alertUnfavourable.first(),
        )
        if (!switches.anyEnabled) return Outcome.ALERTS_OFF
        if (!poster.canPost()) return Outcome.CANNOT_NOTIFY
        val check = checker().checkFollows()
        val events = notifiable(check.events, switches)
        poster.post(events)
        val now = clock.instant()
        if (check.complete) {
            preferences.setLastAlertCheck(now)
        } else {
            preferences.setLastAlertFailure(now)
        }
        return when {
            events.isNotEmpty() -> Outcome.NOTIFIED
            check.complete -> Outcome.NOTHING_NEW
            else -> Outcome.FAILED
        }
    }
}
