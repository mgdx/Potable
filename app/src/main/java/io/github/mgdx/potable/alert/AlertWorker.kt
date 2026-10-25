package io.github.mgdx.potable.alert

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.mgdx.potable.PotableApplication

/**
 * The alert check run by WorkManager (SPEC §5 F7). WorkManager's default
 * initialisation builds it by reflection; it takes its dependencies from the
 * application's [io.github.mgdx.potable.AppContainer], so that no
 * `WorkerFactory` (nor injection framework) is needed.
 *
 * The check never fails as a whole (a unit whose request fails is checked
 * again next time), so the run always succeeds: WorkManager's retry and
 * backoff are never needed, the next period suffices.
 */
class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PotableApplication).container
        container.alertRun.run()
        return Result.success()
    }
}
