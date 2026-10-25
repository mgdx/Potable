package io.github.mgdx.potable.alert

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.mgdx.potable.data.alert.AlertChecker
import io.github.mgdx.potable.data.preferences.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** The periodic alert task, as the scheduler sees it. */
interface AlertWork {
    /** Plans the check every [periodWeeks] weeks, or updates the plan already there. */
    fun schedule(periodWeeks: Int)

    /** Removes the plan; nothing runs any more. */
    fun cancel()
}

/** The alert settings that decide the task. */
private data class AlertPlan(val switches: AlertSwitches, val periodWeeks: Int)

/**
 * Keeps the periodic task in line with the alert settings, from the start of
 * the process and at each change (SPEC §5 F7): planned when an alert is on,
 * every `alertPeriodWeeks` weeks, cancelled when none is. When alerts are
 * switched on while none was, the latest samples are first recorded as seen
 * ([AlertChecker.setBaseline]), so that the first notification is about a
 * sample published afterwards. At start, alerts already on are not a switch:
 * the baseline was recorded then.
 *
 * Changes are handled one at a time, in order: a quick toggle never skips the
 * baseline of the change that needs it.
 */
class AlertScheduler(
    private val preferences: AppPreferences,
    private val checker: () -> AlertChecker,
    private val work: AlertWork,
) {
    fun start(scope: CoroutineScope): Job = scope.launch { observe() }

    internal suspend fun observe() {
        var previous: AlertPlan? = null
        combine(
            preferences.alertNewAnalysis,
            preferences.alertUnfavourable,
            preferences.alertPeriodWeeks,
        ) { newAnalysis, unfavourable, weeks ->
            AlertPlan(AlertSwitches(newAnalysis, unfavourable), weeks)
        }
            .distinctUntilChanged()
            .collect { plan ->
                val before = previous
                previous = plan
                if (!plan.switches.anyEnabled) {
                    work.cancel()
                    return@collect
                }
                if (before != null && !before.switches.anyEnabled) setBaseline()
                work.schedule(plan.periodWeeks)
            }
    }

    /** Best effort: a unit without a baseline gets one at its first check. */
    private suspend fun setBaseline() {
        try {
            checker().setBaseline()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline or Hub'Eau down: the task still has to be planned.
        }
    }
}

/** [AlertWork] with WorkManager: one unique periodic request. */
class WorkManagerAlertWork(context: Context) : AlertWork {
    private val workManager by lazy { WorkManager.getInstance(context.applicationContext) }

    override fun schedule(periodWeeks: Int) {
        // UPDATE keeps the schedule of a request already planned: a new
        // start of the application does not postpone the next check.
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest(periodWeeks),
        )
    }

    override fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_NAME)
    }

    companion object {
        const val UNIQUE_NAME = "alert-check"

        /**
         * Every [periodWeeks] weeks, on any network, with WorkManager's
         * default exponential backoff. The first run waits one period: the
         * baseline has just been recorded.
         */
        fun periodicRequest(periodWeeks: Int): PeriodicWorkRequest {
            val days = periodWeeks.coerceAtLeast(1) * DAYS_PER_WEEK
            return PeriodicWorkRequestBuilder<AlertWorker>(days, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setInitialDelay(days, TimeUnit.DAYS)
                .build()
        }

        private const val DAYS_PER_WEEK = 7L
    }
}
