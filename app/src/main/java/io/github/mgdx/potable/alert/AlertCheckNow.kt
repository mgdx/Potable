package io.github.mgdx.potable.alert

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Where the last "Check now" stands. */
sealed interface CheckNowState {
    /** None was asked for, or WorkManager no longer keeps it. */
    data object Idle : CheckNowState

    /** Enqueued: waits for a network connection. */
    data object Waiting : CheckNowState

    data object Running : CheckNowState

    /** Ended: [outcome] is that of the run, null if it did not end normally. */
    data class Finished(val id: UUID, val outcome: AlertRun.Outcome?) : CheckNowState
}

/** One alert check on demand, besides the periodic one (Settings → Alerts). */
interface AlertCheckNow {
    val state: Flow<CheckNowState>

    /** Starts one check, unless one is already waiting or running. */
    fun start()
}

/**
 * [AlertCheckNow] with WorkManager: one unique one-time request of the same
 * [AlertWorker] as the periodic check, which it leaves as it is.
 */
class WorkManagerAlertCheckNow(context: Context) : AlertCheckNow {
    private val workManager by lazy { WorkManager.getInstance(context.applicationContext) }

    override val state: Flow<CheckNowState>
        get() = workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_NAME)
            .map { infos -> infos.lastOrNull()?.toState() ?: CheckNowState.Idle }
            .distinctUntilChanged()

    override fun start() {
        // KEEP: a tap while one waits or runs adds nothing.
        workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request())
    }

    companion object {
        const val UNIQUE_NAME = "alert-check-now"

        /**
         * On any network, not expedited: below Android 12 an expedited
         * request runs as a foreground service, which the check does not need.
         */
        fun request(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<AlertWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()

        internal fun WorkInfo.toState(): CheckNowState = when (state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> CheckNowState.Waiting
            WorkInfo.State.RUNNING -> CheckNowState.Running
            WorkInfo.State.SUCCEEDED -> CheckNowState.Finished(
                id,
                outputData.getString(AlertWorker.OUTCOME)?.let { name ->
                    AlertRun.Outcome.entries.firstOrNull { it.name == name }
                },
            )
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> CheckNowState.Finished(id, null)
        }
    }
}
