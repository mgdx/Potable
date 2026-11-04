package io.github.mgdx.potable.alert

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.mgdx.potable.PotableApplication

/**
 * The alert check run by WorkManager (SPEC §5 F7). WorkManager's default
 * initialisation builds it by reflection; it takes its dependencies from the
 * application's [io.github.mgdx.potable.AppContainer], so that no
 * `WorkerFactory` (nor injection framework) is needed.
 *
 * The check never fails as a whole (a unit whose request fails is checked
 * again next time), so the run always succeeds: WorkManager's retry and
 * backoff are never needed, the next period suffices. What the run did is
 * in the output data ([OUTCOME]), read by "Check now".
 */
class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PotableApplication).container
        val outcome = container.alertRun.run()
        return Result.success(workDataOf(OUTCOME to outcome.name))
    }

    companion object {
        /** The [AlertRun.Outcome] name of the run, in the output data. */
        const val OUTCOME = "outcome"
    }
}
